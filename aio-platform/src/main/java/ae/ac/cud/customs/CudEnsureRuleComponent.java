package ae.ac.cud.customs;

import java.util.List;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.module.AbstractModuleComponent;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ActionService;
import org.alfresco.service.cmr.action.CompositeAction;
import org.alfresco.repo.nodelocator.CompanyHomeNodeLocator;
import org.alfresco.repo.nodelocator.NodeLocatorService;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.rule.Rule;
import org.alfresco.service.cmr.rule.RuleService;
import org.alfresco.service.cmr.rule.RuleType;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.ISO9075;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Module component that ensures the "CUD Auto-Create Department Structure"
 * inbound rule exists on the CUD Document Management space template.
 *
 * IMPORTANT: this component runs during repository bootstrap, therefore it
 * must NOT use SearchService (SOLR queries are rejected while bootstrapping).
 * All node lookups are done through the database only, via NodeLocatorService
 * and NodeService child-association traversal.
 */
public class CudEnsureRuleComponent extends AbstractModuleComponent {

    private static final Log logger = LogFactory.getLog(CudEnsureRuleComponent.class);

    private static final String RULE_TITLE = "CUD Auto-Create Department Structure";

    private NodeLocatorService nodeLocatorService;
    private NodeService nodeService;
    private RuleService ruleService;
    private ActionService actionService;

    @Override
    protected void executeInternal() throws Throwable {
        logger.info("=== CUD Ensure Rule component starting ===");
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Void>() {
            @Override
            public Void doWork() throws Exception {
                ensureRule();
                return null;
            }
        }, AuthenticationUtil.getAdminUserName());
        logger.info("=== CUD Ensure Rule component finished ===");
    }

    private void ensureRule() {
        // Locate Company Home through the node locator (database only)
        NodeRef companyHome = nodeLocatorService.getNode(CompanyHomeNodeLocator.NAME, null, null);
        if (companyHome == null) {
            logger.warn("Company Home not found – skipping CUD rule creation");
            return;
        }

        // Data Dictionary
        NodeRef dictionary = findChild(companyHome, "Data Dictionary",
                qname(NamespaceService.APP_MODEL_1_0_URI, "dictionary"),
                qname(NamespaceService.CONTENT_MODEL_1_0_URI, ISO9075.encode("Data Dictionary")));
        if (dictionary == null) {
            logger.warn("Data Dictionary not found – skipping CUD rule creation");
            return;
        }

        // Space Templates -> CUD Document Management
        NodeRef spaceTemplates = findChild(dictionary, "Space Templates",
                qname(NamespaceService.APP_MODEL_1_0_URI, "space_templates"),
                qname(NamespaceService.CONTENT_MODEL_1_0_URI, ISO9075.encode("Space Templates")));
        NodeRef template = findChild(spaceTemplates, "CUD Document Management System",
                qname(NamespaceService.CONTENT_MODEL_1_0_URI, ISO9075.encode("CUD Document Management System")));
        if (template == null) {
            logger.warn("CUD Space Template not found – skipping rule creation");
            return;
        }

        // Scripts -> cud-auto-department.js
        NodeRef scripts = findChild(dictionary, "Scripts",
                qname(NamespaceService.APP_MODEL_1_0_URI, "scripts"),
                qname(NamespaceService.CONTENT_MODEL_1_0_URI, ISO9075.encode("Scripts")));
        NodeRef script = findChild(scripts, "cud-auto-department.js",
                qname(NamespaceService.CONTENT_MODEL_1_0_URI, ISO9075.encode("cud-auto-department.js")));
        if (script == null) {
            logger.warn("cud-auto-department.js not found – skipping rule creation");
            return;
        }

        // Already exists?
        List<Rule> existing = ruleService.getRules(template);
        for (Rule r : existing) {
            if (RULE_TITLE.equals(r.getTitle())) {
                logger.info("CUD rule already present – nothing to do");
                return;
            }
        }

        // Create rule
        Rule rule = new Rule();
        rule.setRuleType(RuleType.INBOUND);
        rule.setTitle(RULE_TITLE);
        rule.setDescription("Creates Draft/Review/Published/Archive folders, groups and permissions");
        rule.applyToChildren(false);
        rule.setExecuteAsynchronously(true);

        Action scriptAction = actionService.createAction("script");
        scriptAction.setParameterValue("script-ref", script);

        CompositeAction composite = actionService.createCompositeAction();
        composite.addAction(scriptAction);

        rule.setAction(composite);
        ruleService.saveRule(template, rule);

        logger.info("CUD Auto-Create Department Structure rule created successfully");
    }

    /**
     * Finds a child node without using the search index. Tries the given
     * child-association QNames first (locale independent) and falls back to a
     * cm:name match.
     */
    private NodeRef findChild(NodeRef parent, String displayName, QName... candidateQNames) {
        if (parent == null) {
            return null;
        }
        if (candidateQNames != null) {
            for (QName childQName : candidateQNames) {
                List<ChildAssociationRef> assocs =
                        nodeService.getChildAssocs(parent, ContentModel.ASSOC_CONTAINS, childQName);
                if (!assocs.isEmpty()) {
                    return assocs.get(0).getChildRef();
                }
            }
        }
        return nodeService.getChildByName(parent, ContentModel.ASSOC_CONTAINS, displayName);
    }

    private static QName qname(String namespaceUri, String localName) {
        return QName.createQName(namespaceUri, localName);
    }

    // Spring setters
    public void setNodeLocatorService(NodeLocatorService nodeLocatorService) {
        this.nodeLocatorService = nodeLocatorService;
    }
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }
    public void setRuleService(RuleService ruleService) {
        this.ruleService = ruleService;
    }
    public void setActionService(ActionService actionService) {
        this.actionService = actionService;
    }
}