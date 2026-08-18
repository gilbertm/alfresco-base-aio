package ae.ac.cud.workflow.lifecycle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.workflow.activiti.ActivitiScriptNode;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.model.FileNotFoundException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "Submit for revision" path.
 *
 * For every document in the workflow package that currently sits in a
 * {Dept}_Published folder, this delegate creates a COPY of the content
 * inside {Dept}_Draft so the author can revise it, while the published
 * original stays untouched (still locked, still PUBLISHED).
 *
 * The copy receives:
 *   - cud:documentLifecycle aspect, status DRAFT
 *   - cm:title copied from the original
 *   - name suffix "_rev" to avoid collisions while the original lives on
 *
 * BPMN usage:
 *   <serviceTask id="copyToDraft" name="Copy to Draft"
 *       activiti:delegateExpression="${cudRevisionDelegate}"/>
 */
public class CudRevisionDelegate implements JavaDelegate {

    private static final Logger LOG =
            LoggerFactory.getLogger(CudRevisionDelegate.class);

    private static final String CUD_URI = "http://www.cud.ac.ae/model/content/1.0";
    private static final QName ASPECT_LIFECYCLE =
            QName.createQName(CUD_URI, "documentLifecycle");
    private static final QName PROP_STATUS =
            QName.createQName(CUD_URI, "lifecycleStatus");
    private static final QName PROP_CHANGED_AT =
            QName.createQName(CUD_URI, "statusChangedAt");
    private static final QName PROP_HISTORY =
            QName.createQName(CUD_URI, "statusHistory");

    private static final String PUBLISHED_SUFFIX = "_Published";
    private static final String DRAFT_SUFFIX = "_Draft";

    private NodeService nodeService;
    private FileFolderService fileFolderService;

    @Override
    public void execute(DelegateExecution execution) {

        Object pkg = execution.getVariable("bpm_package");
        if (!(pkg instanceof List)) {
            LOG.warn("CUD revision: bpm_package missing – nothing to copy");
            return;
        }

        for (Object item : (List<?>) pkg) {
            if (!(item instanceof ActivitiScriptNode)) {
                continue;
            }
            NodeRef doc = ((ActivitiScriptNode) item).getNodeRef();
            copyToDraft(doc, execution);
        }
    }

    private void copyToDraft(NodeRef doc, DelegateExecution execution) {
        FileInfo info = fileFolderService.getFileInfo(doc);
        if (info == null || !nodeService.exists(doc)) {
            LOG.warn("CUD revision: payload node no longer exists – skipping");
            return;
        }

        NodeRef currentFolderRef = nodeService.getPrimaryParent(doc).getParentRef();
        String currentFolderName = (String)
                nodeService.getProperty(currentFolderRef, ContentModel.PROP_NAME);

        // expect the original to sit in "{Dept}_Published"
        if (!currentFolderName.endsWith(PUBLISHED_SUFFIX)) {
            LOG.warn("CUD revision: document '{}' is not in a _Published folder (parent='{}') – skipping",
                    info.getName(), currentFolderName);
            return;
        }

        String deptBase = currentFolderName.substring(
                0, currentFolderName.length() - PUBLISHED_SUFFIX.length());

        NodeRef deptRoot = nodeService.getPrimaryParent(currentFolderRef).getParentRef();
        String draftFolderName = deptBase + DRAFT_SUFFIX;
        NodeRef draftFolder = fileFolderService.searchSimple(deptRoot, draftFolderName);
        if (draftFolder == null) {
            LOG.error("CUD revision: draft folder '{}' not found under department root – skipping",
                    draftFolderName);
            return;
        }

        // build a non-colliding revision name: name + "_rev" (keeps extension)
        String origName = info.getName();
        String baseName = origName;
        String extension = "";
        int dot = origName.lastIndexOf('.');
        if (dot > 0) {
            baseName = origName.substring(0, dot);
            extension = origName.substring(dot);
        }
        String copyName = baseName + "_rev" + extension;
        if (fileFolderService.searchSimple(draftFolder, copyName) != null) {
            copyName = baseName + "_rev_" + System.currentTimeMillis() + extension;
        }

        // copy content into draft folder (keeps the original untouched)
        FileInfo copy;
        try {
            copy = fileFolderService.copy(doc, draftFolder, copyName);
        } catch (FileNotFoundException e) {
            LOG.error("CUD revision: failed to copy '{}' to '{}': {}",
                    origName, draftFolderName, e.getMessage());
            return;
        }
        NodeRef copyRef = copy.getNodeRef();
        LOG.info("CUD revision: copied '{}' -> '{}/{}'",
                origName, draftFolderName, copyName);

        // stamp the copy as a fresh draft
        if (!nodeService.hasAspect(copyRef, ASPECT_LIFECYCLE)) {
            nodeService.addAspect(copyRef, ASPECT_LIFECYCLE, null);
        }
        Map<QName, Serializable> props = new HashMap<QName, Serializable>();
        props.put(PROP_STATUS, "DRAFT");
        props.put(PROP_CHANGED_AT, new Date());
        nodeService.addProperties(copyRef, props);

        appendHistory(copyRef, "DRAFT (revision copy of '" + origName + "')",
                draftFolderName, execution);
    }

    @SuppressWarnings("unchecked")
    private void appendHistory(NodeRef doc, String state, String folderName,
                               DelegateExecution execution) {
        List<Serializable> history;
        Object existing = nodeService.getProperty(doc, PROP_HISTORY);
        if (existing instanceof List) {
            history = new ArrayList<Serializable>((List<Serializable>) existing);
        } else {
            history = new ArrayList<Serializable>();
        }
        history.add(new Date() + " | " + state + " | placed in " + folderName
                + " | by workflow " + execution.getProcessDefinitionId());
        nodeService.setProperty(doc, PROP_HISTORY, (Serializable) history);
    }

    // ------------------------------------------------------------------
    // Spring injection
    // ------------------------------------------------------------------
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setFileFolderService(FileFolderService fileFolderService) {
        this.fileFolderService = fileFolderService;
    }
}