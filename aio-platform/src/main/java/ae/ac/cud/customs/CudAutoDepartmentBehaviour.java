package ae.ac.cud.customs;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AccessPermission;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * =============================================================================
 * FILE        : CudAutoDepartmentBehaviour.java
 * PACKAGE     : ae.ac.cud.customs
 * CLASS       : CudAutoDepartmentBehaviour
 * MODULE      : aio-platform (CUD Customs Alfresco Platform AMP)
 * AUTHOR      : CUD Development Team
 * CREATED     : 2026-08-14
 * VERSION     : 1.0
 * =============================================================================
 *
 * SUMMARY
 * -------
 * Alfresco repository behaviour (policy) implementing
 * {@link NodeServicePolicies.OnCreateNodePolicy}. It listens for the creation
 * of cm:folder nodes and automatically provisions a complete "department"
 * structure whenever a plain folder is created directly under a node carrying
 * the custom aspect cud:departmentRoot (applied to the CUD space-template
 * root and any space created from it). The provisioning replicates the
 * original JavaScript rule: lifecycle sub-folders, department groups and a
 * fixed permission matrix.
 *
 * POLICY BINDING / TRIGGER
 * ------------------------
 * Policy     : NodeServicePolicies.OnCreateNodePolicy.QNAME
 * Bound to   : ContentModel.TYPE_FOLDER (class binding, registered in init())
 * Frequency  : Behaviour.NotificationFrequency.TRANSACTION_COMMIT
 * Conditions : - the new folder's PARENT has aspect cud:departmentRoot
 *              - the new node's type is exactly cm:folder
 *              - the folder name is non-empty
 *              - the name does NOT end with one of the lifecycle suffixes
 *                (_Draft / _Review / _Published / _Archive), which prevents
 *                recursive self-triggering
 * Execution  : provisioning runs as the system user via
 *              AuthenticationUtil.runAs(...), with errors logged (the
 *              original folder-creation transaction is not rolled back).
 *
 * ARTIFACTS CREATED FOR EACH DEPARTMENT FOLDER "{Dept}"
 * -----------------------------------------------------
 * 1. Child folders (cm:title set; cm:versionable aspect added):
 *      - {Dept}_Draft
 *      - {Dept}_Review
 *      - {Dept}_Published
 *      - {Dept}_Archive
 *
 * 2. Alfresco groups (short name sanitized to [a-zA-Z0-9_]):
 *      - {Dept}_Contributors  (display: "{Dept} Contributors")
 *      - {Dept}_Reviewers     (display: "{Dept} Reviewers")
 *      - {Dept}_Managers      (display: "{Dept} Managers")
 *      - {Dept}_Readers       (display: "{Dept} Readers")
 *
 * 3. Permissions (parent-permission inheritance DISABLED on all four folders):
 *      +------------+--------------+--------------+--------------+------------+
 *      | Folder     | Contributors | Reviewers    | Managers     | Readers    |
 *      +------------+--------------+--------------+--------------+------------+
 *      | _Draft     | Collaborator | Consumer     | Consumer     | Consumer   |
 *      | _Review    | Consumer     | Collaborator | Collaborator | Consumer   |
 *      | _Published | Consumer     | Consumer     | Collaborator | Consumer   |
 *      | _Archive   | Consumer     | Consumer     | Coordinator  | Consumer   |
 *      +------------+--------------+--------------+--------------+------------+
 *
 * PUBLIC METHODS
 * --------------
 * init()
 *     Spring lifecycle hook; binds this behaviour to OnCreateNodePolicy for
 *     cm:folder at TRANSACTION_COMMIT frequency.
 *
 * onCreateNode(ChildAssociationRef childAssocRef)
 *     Policy callback. Validates the created node/parent against the trigger
 *     conditions and, if it is a new department folder, dispatches
 *     createDepartmentStructure(...) as the system user.
 *
 * PRIVATE HELPER METHODS
 * ----------------------
 * createDepartmentStructure(NodeRef deptFolder, String deptName)
 *     Orchestrates the full provisioning: ensures the four lifecycle folders,
 *     ensures the four groups, applies the permission matrix, and adds the
 *     cm:versionable aspect to every lifecycle folder.
 *
 * ensureFolder(NodeRef parent, String name)
 *     Idempotent find-or-create of a child cm:folder (searchSimple first,
 *     then create). Sets cm:title to the folder name. Returns the NodeRef.
 *
 * ensureGroup(String shortName, String displayName)
 *     Idempotent find-or-create of a GROUP authority via AuthorityService.
 *     Returns the full authority name (e.g. GROUP_President_Contributors).
 *
 * applyPermissions(NodeRef folder, boolean inherit, String[]... entries)
 *     Sets parent-permission inheritance flag, then grants each
 *     (authority, permission) pair on the folder.
 *
 * perm(String authority, String permission)
 *     Small helper building a {authority, permission} pair array.
 *
 * setPolicyComponent / setNodeService / setFileFolderService /
 * setAuthorityService / setPermissionService
 *     Spring dependency-injection setters for the required services.
 *
 * INJECTED SERVICES
 * -----------------
 * PolicyComponent, NodeService, FileFolderService, AuthorityService,
 * PermissionService.
 *
 * CONSTANTS
 * ---------
 * CUD_MODEL_URI          = "http://www.cud.ac.ae/model/content/1.0"
 * ASPECT_DEPARTMENT_ROOT = cud:departmentRoot
 * LIFECYCLE_SUFFIXES     = _Draft, _Review, _Published, _Archive
 *
 * NOTES
 * -----
 * - Idempotent: ensureFolder/ensureGroup reuse existing artifacts, so a
 *   re-run will not duplicate folders or groups.
 * - The recursion guard relies on the lifecycle suffixes; folders whose name
 *   ends with one of them are ignored by the behaviour.
 *
 * @see org.alfresco.repo.node.NodeServicePolicies.OnCreateNodePolicy
 * @see org.alfresco.repo.policy.JavaBehaviour
 * =============================================================================
 */
public class CudAutoDepartmentBehaviour implements NodeServicePolicies.OnCreateNodePolicy {

    private static final Log logger = LogFactory.getLog(CudAutoDepartmentBehaviour.class);

    public static final String CUD_MODEL_URI = "http://www.cud.ac.ae/model/content/1.0";
    public static final QName ASPECT_DEPARTMENT_ROOT =
            QName.createQName(CUD_MODEL_URI, "departmentRoot");

    private static final String[] LIFECYCLE_SUFFIXES = {
            "_Draft", "_Review", "_Published", "_Archive"
    };

    private PolicyComponent policyComponent;
    private NodeService nodeService;
    private FileFolderService fileFolderService;
    private AuthorityService authorityService;
    private PermissionService permissionService;

    public void init() {
        policyComponent.bindClassBehaviour(
                NodeServicePolicies.OnCreateNodePolicy.QNAME,
                ContentModel.TYPE_FOLDER,
                new JavaBehaviour(this, "onCreateNode",
                        Behaviour.NotificationFrequency.TRANSACTION_COMMIT));
        logger.info("CUD Auto-Department behaviour registered");
    }

    @Override
    public void onCreateNode(ChildAssociationRef childAssocRef) {
        final NodeRef deptFolder = childAssocRef.getChildRef();
        final NodeRef parent = childAssocRef.getParentRef();

        if (!nodeService.exists(deptFolder) || !nodeService.exists(parent)) {
            return;
        }

        // Only direct children of a CUD department root
        if (!nodeService.hasAspect(parent, ASPECT_DEPARTMENT_ROOT)) {
            return;
        }

        // Only standard folders (and subtypes of cm:folder if you prefer)
        if (!ContentModel.TYPE_FOLDER.equals(nodeService.getType(deptFolder))) {
            return;
        }

        String deptName = (String) nodeService.getProperty(deptFolder, ContentModel.PROP_NAME);
        if (deptName == null || deptName.trim().isEmpty()) {
            return;
        }

        // Ignore lifecycle folders we create (prevents recursion)
        for (String suffix : LIFECYCLE_SUFFIXES) {
            if (deptName.endsWith(suffix)) {
                return;
            }
        }

        logger.info("CUD auto-department: department folder detected → " + deptName);

        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Void>() {
            @Override
            public Void doWork() {
                try {
                    
                    permissionService.setInheritParentPermissions(deptFolder, false);
                    logger.info("Inherit parent permissions DISABLED on department root: " + deptFolder);

                    createDepartmentStructure(deptFolder, deptName);
                    removeCreateAndUploadOnRoot(deptFolder);
                } catch (Exception e) {
                    logger.error("CUD auto-department failed for: " + deptName, e);
                }
                return null;
            }
        }, AuthenticationUtil.getSystemUserName());
    }
    
    private void createDepartmentStructure(NodeRef deptFolder, String deptName) {
        String safeName = deptName.replaceAll("[^a-zA-Z0-9]", "_");

        NodeRef draft     = ensureFolder(deptFolder, deptName + "_Draft");
        NodeRef review    = ensureFolder(deptFolder, deptName + "_Review");
        NodeRef published = ensureFolder(deptFolder, deptName + "_Published");
        NodeRef archive   = ensureFolder(deptFolder, deptName + "_Archive");

        String groupContributors = ensureGroup(safeName + "_Contributors", deptName + " Contributors");
        String groupReviewers    = ensureGroup(safeName + "_Reviewers",    deptName + " Reviewers");
        String groupManagers     = ensureGroup(safeName + "_Managers",     deptName + " Managers");
        String groupReaders      = ensureGroup(safeName + "_Readers",      deptName + " Readers");

        // Apply all the created permissions to the department (parent) folder first,
        // looping over all of the created groups
        String[] deptGroups = { groupContributors, groupReviewers, groupManagers, groupReaders };
        String[][] deptPermEntries = new String[deptGroups.length][];
        for (int i = 0; i < deptGroups.length; i++) {
            deptPermEntries[i] = perm(deptGroups[i], "Collaborator");
        }
        applyPermissions(deptFolder, false, deptPermEntries);

        applyPermissions(draft, false,
                perm(groupContributors, "Collaborator"),
                perm(groupReviewers,    PermissionService.CONSUMER),
                perm(groupManagers,     PermissionService.CONSUMER),
                perm(groupReaders,      PermissionService.CONSUMER));

        applyPermissions(review, false,
                perm(groupReviewers,    "Collaborator"),
                perm(groupManagers,     "Collaborator"),
                perm(groupContributors, PermissionService.CONSUMER),
                perm(groupReaders,      PermissionService.CONSUMER));

        applyPermissions(published, false,
                perm(groupContributors, PermissionService.CONSUMER),
                perm(groupReviewers,    PermissionService.CONSUMER),
                perm(groupReaders,      PermissionService.CONSUMER),
                perm(groupManagers,     "Collaborator"));

        applyPermissions(archive, false,
                perm(groupContributors, PermissionService.CONSUMER),
                perm(groupReviewers,    PermissionService.CONSUMER),
                perm(groupReaders,      PermissionService.CONSUMER),
                perm(groupManagers,     PermissionService.COORDINATOR));

        for (NodeRef f : new NodeRef[]{ draft, review, published, archive }) {
            if (!nodeService.hasAspect(f, ContentModel.ASPECT_VERSIONABLE)) {
                nodeService.addAspect(f, ContentModel.ASPECT_VERSIONABLE, null);
            }
        }

        logger.info("CUD auto-department finished for: " + deptName
                + " | groups: " + groupContributors + ", " + groupReviewers
                + ", " + groupManagers + ", " + groupReaders);
    }

    /**
     * Explicitly removes CreateChildren / AddChildren (and therefore Upload)
     * from the department root folder.
     * <p>
     * Sub-folders created by the space template keep their normal permissions
     * and can still accept content.
     */
    private void removeCreateAndUploadOnRoot(NodeRef deptName) {
        
        // Deny CreateChildren for every authority that currently has any permission
        // on the node (except System / Admin which are handled separately).
        for (AccessPermission ap : permissionService.getAllSetPermissions(deptName)) {
            String authority = ap.getAuthority();

            // Never touch the special system authorities
            if (PermissionService.ALL_AUTHORITIES.equals(authority)
                    || PermissionService.OWNER_AUTHORITY.equals(authority)
                    || "ROLE_ADMINISTRATOR".equals(authority)
                    || "GROUP_ALFRESCO_ADMINISTRATORS".equals(authority)) {
                continue;
            }

            // Explicitly deny the ability to create children / upload
            permissionService.setPermission(deptName, authority,
                    PermissionService.CREATE_CHILDREN, false);
            permissionService.setPermission(deptName, authority,
                    PermissionService.ADD_CHILDREN, false);
            // Also deny Write if you want a pure "read-only root"
            permissionService.setPermission(deptName, authority,
                     PermissionService.WRITE, false);
        }

        logger.info("Create / Upload disabled on department root " + deptName);
    }

    private NodeRef ensureFolder(NodeRef parent, String name) {
        NodeRef existing = fileFolderService.searchSimple(parent, name);
        if (existing != null) {
            return existing;
        }
        FileInfo info = fileFolderService.create(parent, name, ContentModel.TYPE_FOLDER);
        NodeRef folder = info.getNodeRef();
        Map<QName, Serializable> props = new HashMap<QName, Serializable>();
        props.put(ContentModel.PROP_TITLE, name);
        nodeService.addProperties(folder, props);
        return folder;
    }

    /** @return full authority name e.g. GROUP_President_Contributors */
    private String ensureGroup(String shortName, String displayName) {
        String fullName = authorityService.getName(AuthorityType.GROUP, shortName);
        if (!authorityService.authorityExists(fullName)) {
            fullName = authorityService.createAuthority(
                    AuthorityType.GROUP,
                    shortName,
                    displayName,
                    authorityService.getDefaultZones());
            logger.info("Created group: " + fullName);
        }
        return fullName;
    }

    private void applyPermissions(NodeRef folder, boolean inherit, String[]... entries) {
        permissionService.setInheritParentPermissions(folder, inherit);
        for (String[] e : entries) {
            permissionService.setPermission(folder, e[0], e[1], true);
        }
    }

    private static String[] perm(String authority, String permission) {
        return new String[]{ authority, permission };
    }

    // Spring setters
    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }
    public void setFileFolderService(FileFolderService fileFolderService) {
        this.fileFolderService = fileFolderService;
    }
    public void setAuthorityService(AuthorityService authorityService) {
        this.authorityService = authorityService;
    }
    public void setPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }
}