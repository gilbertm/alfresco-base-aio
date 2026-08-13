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
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * OnCreateNode behaviour for cm:folder.
 *
 * When a folder is created directly under a node that has aspect cud:departmentRoot
 * (CUD space template root or any space created from it), this creates:
 *   - {name}_Draft / _Review / _Published / _Archive
 *   - four groups (Contributors, Reviewers, Managers, Readers)
 *   - permissions matching the original JavaScript rule
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
                    createDepartmentStructure(deptFolder, deptName);
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