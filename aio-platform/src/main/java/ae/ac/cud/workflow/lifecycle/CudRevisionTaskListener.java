package ae.ac.cud.workflow.lifecycle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.engine.impl.context.Context;
import org.alfresco.model.ContentModel;
import org.alfresco.model.ForumModel;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.repo.workflow.activiti.ActivitiConstants;
import org.alfresco.repo.workflow.activiti.ActivitiScriptNode;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.lock.LockService;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.model.FileNotFoundException;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * TaskListener that handles the "Submit for Revision" workflow.
 *
 * This listener uses the same technique as CudDocumentLifecycleTaskListener:
 *   - Implements TaskListener (not JavaDelegate)
 *   - Resolves services via ServiceRegistry fallback
 *   - Extracts package documents via WorkflowModel.ASSOC_PACKAGE_CONTAINS
 *
 * For every document in the workflow package that currently sits in a
 * {Dept}_Published folder (locked), this listener:
 *
 *   1. Creates a COPY in {Dept}_Archive to preserve the published version
 *   2. MOVES the original from {Dept}_Published back to {Dept}_Draft
 *   3. Unlocks the document so the author can revise it
 *   4. Adds a comment: "Sent back for revision – new version requested"
 *   5. Stamps lifecycle metadata as DRAFT with history entry
 *
 * BPMN usage (same technique as cud-document-lifecycle.bpmn20.xml, but fully
 * automatic — only the "create" event is used; no manual completion):
 *   <userTask id="revisionTask" name="Send Back for Revision"
 *             activiti:formKey="cudwf:revisionTask">
 *       <extensionElements>
 *           <activiti:taskListener event="create"
 *                                  activiti:class="ae.ac.cud.workflow.lifecycle.CudRevisionTaskListener" />
 *       </extensionElements>
 *   </userTask>
 */
public class CudRevisionTaskListener implements TaskListener {

    private static final Logger LOG =
            LoggerFactory.getLogger(CudRevisionTaskListener.class);

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
    private static final String ARCHIVE_SUFFIX = "_Archive";

    private static final String REVISION_COMMENT =
            "This document has been sent back for revision. "
            + "A new version is requested. "
            + "The previously published copy has been archived.";

    /** Forum model namespace URI (fm:). */
    private static final String FORUMS_MODEL_URI =
            "http://www.alfresco.org/model/forum/1.0";

    private static final String MIMETYPE_TEXT_PLAIN = "text/plain";

    private NodeService nodeService;
    private FileFolderService fileFolderService;
    private LockService lockService;
    private ContentService contentService;

    @Override
    public void notify(DelegateTask delegateTask) {

        // Resolve services via ServiceRegistry fallback (same technique as lifecycle listener)
        if (this.nodeService == null || this.fileFolderService == null
                || this.lockService == null || this.contentService == null) {
            ProcessEngineConfigurationImpl config = Context.getProcessEngineConfiguration();
            if (config != null && config.getBeans() != null) {
                Object serviceRegistryObj = config.getBeans().get(ActivitiConstants.SERVICE_REGISTRY_BEAN_KEY);
                if (serviceRegistryObj == null) {
                    serviceRegistryObj = config.getBeans().get("ServiceRegistry");
                }

                ServiceRegistry registry = null;
                if (serviceRegistryObj instanceof ServiceRegistry) {
                    registry = (ServiceRegistry) serviceRegistryObj;
                } else if (serviceRegistryObj instanceof ApplicationContext) {
                    registry = (ServiceRegistry) ((ApplicationContext) serviceRegistryObj)
                            .getBean(ServiceRegistry.SERVICE_REGISTRY);
                }

                if (registry != null) {
                    if (this.nodeService == null) this.nodeService = registry.getNodeService();
                    if (this.fileFolderService == null) this.fileFolderService = registry.getFileFolderService();
                    if (this.lockService == null) this.lockService = registry.getLockService();
                    if (this.contentService == null) this.contentService = registry.getContentService();
                }
            }
        }

        String event = delegateTask.getEventName();

        LOG.info("[CUD Revision TaskListener] >>> notify() invoked | taskId='{}' | taskName='{}' | event='{}' | processInstanceId='{}' | executionId='{}'",
                delegateTask.getId(),
                delegateTask.getName(),
                event,
                delegateTask.getProcessInstanceId(),
                delegateTask.getExecutionId());

        if ("create".equals(event)) {
            LOG.info("[CUD Revision TaskListener] event='create' — performing revision logic");

            // Perform the revision logic on all documents in the package
            sendBackForRevision(delegateTask);

            // Auto-complete the task so the workflow proceeds straight to the end
            // event. The revision is fully automatic — there is no manual task for
            // anyone to complete.
            autoCompleteTask(delegateTask);
        } else {
            LOG.info("[CUD Revision TaskListener] event='{}' — not handled by this listener", event);
        }

        LOG.info("[CUD Revision TaskListener] <<< notify() finished for taskId='{}' event='{}'",
                delegateTask.getId(), event);
    }

    /**
     * Completes the current task programmatically so the workflow moves on to
     * the end event immediately. This makes the revision workflow fully
     * automatic (no manual task completion required).
     */
    private void autoCompleteTask(DelegateTask delegateTask) {
        try {
            ProcessEngineConfigurationImpl config = Context.getProcessEngineConfiguration();
            if (config != null && config.getTaskService() != null) {
                config.getTaskService().complete(delegateTask.getId());
                LOG.info("[CUD Revision TaskListener] auto-completed task '{}' — workflow proceeding to end",
                        delegateTask.getId());
            } else {
                LOG.warn("[CUD Revision TaskListener] could not auto-complete task '{}' — TaskService unavailable",
                        delegateTask.getId());
            }
        } catch (Exception e) {
            LOG.warn("[CUD Revision TaskListener] failed to auto-complete task '{}': {}",
                    delegateTask.getId(), e.getMessage());
        }
    }

    /**
     * Iterates over all documents in the workflow package and sends each
     * back for revision (archive copy + move to draft + unlock + comment).
     */
    private void sendBackForRevision(DelegateTask delegateTask) {
        // 1. Resolve package variable (same technique as lifecycle listener)
        Object pkg = delegateTask.getVariable("bpm_package");
        if (pkg == null && delegateTask.getExecution() != null) {
            pkg = delegateTask.getExecution().getVariable("bpm_package");
        }

        if (pkg == null) {
            LOG.warn("[CUD Revision TaskListener] bpm_package is NULL");
            return;
        }

        // 2. Extract the package container NodeRef
        NodeRef packageNodeRef = null;
        if (pkg instanceof ActivitiScriptNode) {
            packageNodeRef = ((ActivitiScriptNode) pkg).getNodeRef();
        } else if (pkg instanceof NodeRef) {
            packageNodeRef = (NodeRef) pkg;
        }

        if (packageNodeRef == null) {
            LOG.warn("[CUD Revision TaskListener] Could not resolve NodeRef for bpm_package");
            return;
        }

        // 3. Get all document nodes inside the workflow package
        List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(
            packageNodeRef,
            WorkflowModel.ASSOC_PACKAGE_CONTAINS,
            RegexQNamePattern.MATCH_ALL
        );

        if (childAssocs == null || childAssocs.isEmpty()) {
            LOG.warn("[CUD Revision TaskListener] Workflow package contains 0 items");
            return;
        }

        // 4. Process each document in the package
        for (ChildAssociationRef assoc : childAssocs) {
            NodeRef docRef = assoc.getChildRef();
            try {
                processDocumentForRevision(docRef, delegateTask);
            } catch (Exception e) {
                LOG.error("[CUD Revision TaskListener] Failed to process document for revision: {}", docRef, e);
            }
        }
    }

    /**
     * Core revision logic for a single document:
     * archive a copy, move the original back to Draft, unlock, comment, and stamp metadata.
     */
    private void processDocumentForRevision(NodeRef doc, DelegateTask delegateTask) {

        LOG.info("[CUD Revision TaskListener] processDocumentForRevision() START | nodeRef={}", doc);

        FileInfo info = fileFolderService.getFileInfo(doc);
        if (info == null) {
            LOG.warn("[CUD Revision TaskListener] fileFolderService.getFileInfo({}) returned NULL – skipping", doc);
            return;
        }
        if (!nodeService.exists(doc)) {
            LOG.warn("[CUD Revision TaskListener] nodeService.exists({}) returned FALSE – skipping", doc);
            return;
        }

        LOG.info("[CUD Revision TaskListener] document name='{}' | nodeRef={}", info.getName(), doc);

        NodeRef currentFolderRef = nodeService.getPrimaryParent(doc).getParentRef();
        String currentFolderName = (String)
                nodeService.getProperty(currentFolderRef, ContentModel.PROP_NAME);

        LOG.info("[CUD Revision TaskListener] currentFolder='{}' (NodeRef={})",
                currentFolderName, currentFolderRef);

        // expect the original to sit in "{Dept}_Published"
        if (!currentFolderName.endsWith(PUBLISHED_SUFFIX)) {
            LOG.warn("[CUD Revision TaskListener] document '{}' is NOT in a _Published folder (parent='{}') – skipping",
                    info.getName(), currentFolderName);
            return;
        }

        String deptBase = currentFolderName.substring(
                0, currentFolderName.length() - PUBLISHED_SUFFIX.length());
        NodeRef deptRoot = nodeService.getPrimaryParent(currentFolderRef).getParentRef();
        String deptRootName = (String) nodeService.getProperty(deptRoot, ContentModel.PROP_NAME);

        String origName = info.getName();

        LOG.info("[CUD Revision TaskListener] deptBase='{}' | deptRoot='{}' (NodeRef={})",
                deptBase, deptRootName, deptRoot);

        // ── Step 1: Copy to Archive ──────────────────────────────────────
        String archiveFolderName = deptBase + ARCHIVE_SUFFIX;
        NodeRef archiveFolder = fileFolderService.searchSimple(deptRoot, archiveFolderName);
        if (archiveFolder == null) {
            LOG.error("[CUD Revision TaskListener] archive folder '{}' NOT FOUND under department root '{}' – skipping archive copy",
                    archiveFolderName, deptRootName);
        } else {
            String archiveName = buildCopyName(origName, archiveFolder);
            try {
                FileInfo archiveCopy = fileFolderService.copy(doc, archiveFolder, archiveName);
                NodeRef archiveCopyRef = archiveCopy.getNodeRef();
                LOG.info("[CUD Revision TaskListener] archived copy '{}' -> '{}/{}'",
                        origName, archiveFolderName, archiveName);

                // stamp the archive copy with ARCHIVED status
                if (!nodeService.hasAspect(archiveCopyRef, ASPECT_LIFECYCLE)) {
                    nodeService.addAspect(archiveCopyRef, ASPECT_LIFECYCLE, null);
                }
                Map<QName, Serializable> archiveProps = new HashMap<QName, Serializable>();
                archiveProps.put(PROP_STATUS, "ARCHIVED");
                archiveProps.put(PROP_CHANGED_AT, new Date());
                nodeService.addProperties(archiveCopyRef, archiveProps);

                appendHistory(archiveCopyRef,
                        "ARCHIVED (sent back for revision – preserved copy of '" + origName + "')",
                        archiveFolderName, delegateTask);
            } catch (FileNotFoundException e) {
                LOG.error("[CUD Revision TaskListener] failed to copy '{}' to archive folder '{}': {}",
                        origName, archiveFolderName, e.getMessage(), e);
            }
        }

        // ── Step 2: Move to Draft ────────────────────────────────────────
        String draftFolderName = deptBase + DRAFT_SUFFIX;
        NodeRef draftFolder = fileFolderService.searchSimple(deptRoot, draftFolderName);
        if (draftFolder == null) {
            LOG.error("[CUD Revision TaskListener] draft folder '{}' NOT FOUND under department root '{}' – cannot move document",
                    draftFolderName, deptRootName);
            return;
        }

        try {
            fileFolderService.move(doc, draftFolder, null);
            LOG.info("[CUD Revision TaskListener] moved '{}' from '{}' to '{}/{}'",
                    origName, currentFolderName, draftFolderName, origName);
        } catch (FileNotFoundException e) {
            LOG.error("[CUD Revision TaskListener] failed to move '{}' to draft folder '{}': {}",
                    origName, draftFolderName, e.getMessage(), e);
            return;
        }

        // ── Step 3: Unlock the document ──────────────────────────────────
        try {
            if (lockService.isLocked(doc)) {
                lockService.unlock(doc);
                LOG.info("[CUD Revision TaskListener] unlocked '{}' for revision", origName);
            } else {
                LOG.info("[CUD Revision TaskListener] document '{}' is not locked – no unlock needed", origName);
            }
        } catch (Exception e) {
            LOG.warn("[CUD Revision TaskListener] could not unlock '{}': {}", origName, e.getMessage());
        }

        // ── Step 4: Stamp lifecycle metadata as DRAFT ────────────────────
        if (!nodeService.hasAspect(doc, ASPECT_LIFECYCLE)) {
            nodeService.addAspect(doc, ASPECT_LIFECYCLE, null);
        }
        Map<QName, Serializable> props = new HashMap<QName, Serializable>();
        props.put(PROP_STATUS, "DRAFT");
        props.put(PROP_CHANGED_AT, new Date());
        nodeService.addProperties(doc, props);
        LOG.info("[CUD Revision TaskListener] updated lifecycleStatus='DRAFT' on '{}'", origName);

        appendHistory(doc,
                "DRAFT (sent back for revision from '" + currentFolderName + "')",
                draftFolderName, delegateTask);

        // ── Step 5: Add revision comment ─────────────────────────────────
        addRevisionComment(doc, delegateTask);

        LOG.info("[CUD Revision TaskListener] processDocumentForRevision() END | document '{}' successfully sent back for revision "
                + "(archived in '{}', moved to '{}', unlocked, commented)",
                origName, archiveFolderName, draftFolderName);
    }

    /**
     * Builds a non-colliding copy name in the target folder.
     * Appends "_archived" suffix; falls back to timestamp if name exists.
     */
    private String buildCopyName(String origName, NodeRef targetFolder) {
        String baseName = origName;
        String extension = "";
        int dot = origName.lastIndexOf('.');
        if (dot > 0) {
            baseName = origName.substring(0, dot);
            extension = origName.substring(dot);
        }
        String copyName = baseName + "_archived" + extension;
        if (fileFolderService.searchSimple(targetFolder, copyName) != null) {
            copyName = baseName + "_archived_" + System.currentTimeMillis() + extension;
        }
        return copyName;
    }

    /**
     * Adds a comment to the document indicating it was sent back for revision.
     *
     * Alfresco comments are stored as fm:post nodes under an fm:discussion
     * container attached to the node via the fm:discussable aspect.
     */
    private void addRevisionComment(NodeRef doc, DelegateTask delegateTask) {
        try {
            // 1. ensure the node is discussable
            if (!nodeService.hasAspect(doc, ForumModel.ASPECT_DISCUSSABLE)) {
                nodeService.addAspect(doc, ForumModel.ASPECT_DISCUSSABLE, null);
            }

            // 2. find (or create) the discussion container
            NodeRef discussionRef = null;
            List<ChildAssociationRef> discussions = nodeService.getChildAssocs(
                    doc, ForumModel.ASSOC_DISCUSSION, RegexQNamePattern.MATCH_ALL);
            if (discussions != null && !discussions.isEmpty()) {
                discussionRef = discussions.get(0).getChildRef();
            } else {
                String discName = "discussion";
                discussionRef = nodeService.createNode(
                        doc,
                        ForumModel.ASSOC_DISCUSSION,
                        QName.createQName(FORUMS_MODEL_URI, discName),
                        ForumModel.TYPE_FORUM).getChildRef();
            }

            // 3. create the comment post node
            String postName = "post_" + System.currentTimeMillis();
            NodeRef postRef = nodeService.createNode(
                    discussionRef,
                    ContentModel.ASSOC_CHILDREN,
                    QName.createQName(FORUMS_MODEL_URI, postName),
                    ForumModel.TYPE_POST).getChildRef();

            nodeService.setProperty(postRef, ContentModel.PROP_NAME, postName);
            nodeService.setProperty(postRef, ContentModel.PROP_TITLE, "Sent back for revision");

            // 4. write the comment content
            String commentContent = REVISION_COMMENT
                    + " Workflow instance: " + delegateTask.getProcessInstanceId()
                    + " | Date: " + new Date();
            if (contentService != null) {
                ContentWriter writer = contentService.getWriter(
                        postRef, ContentModel.PROP_CONTENT, true);
                writer.setMimetype(MIMETYPE_TEXT_PLAIN);
                writer.putContent(commentContent);
            }

            LOG.info("[CUD Revision TaskListener] added revision comment to document {}", doc);
        } catch (Exception e) {
            LOG.warn("[CUD Revision TaskListener] failed to add comment to document {}: {}",
                    doc, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void appendHistory(NodeRef doc, String state, String folderName,
                               DelegateTask delegateTask) {
        List<Serializable> history;
        Object existing = nodeService.getProperty(doc, PROP_HISTORY);
        if (existing instanceof List) {
            history = new ArrayList<Serializable>((List<Serializable>) existing);
        } else {
            history = new ArrayList<Serializable>();
        }
        String entry = new Date() + " | " + state + " | placed in " + folderName
                + " | by workflow instance " + delegateTask.getProcessInstanceId();
        history.add(entry);
        nodeService.setProperty(doc, PROP_HISTORY, (Serializable) history);
        LOG.debug("[CUD Revision TaskListener] appended history entry: '{}'", entry);
    }

    // ------------------------------------------------------------------
    // Spring injection (optional — ServiceRegistry fallback is primary)
    // ------------------------------------------------------------------
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setFileFolderService(FileFolderService fileFolderService) {
        this.fileFolderService = fileFolderService;
    }

    public void setLockService(LockService lockService) {
        this.lockService = lockService;
    }

    public void setContentService(ContentService contentService) {
        this.contentService = contentService;
    }
}