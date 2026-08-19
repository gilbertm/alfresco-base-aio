package ae.ac.cud.workflow.lifecycle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.workflow.activiti.ActivitiScriptNode;
import org.alfresco.service.cmr.lock.LockService;
import org.alfresco.service.cmr.lock.LockType;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.model.FileNotFoundException;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TaskListener that handles document lifecycle transitions.
 *
 * This listener consolidates the folder-moving logic that was previously
 * spread across serviceTasks (moveToReview, moveToPublished, moveBackToDraft)
 * into task lifecycle events:
 *
 *   event="create"  — moves documents from {Dept}_Draft to {Dept}_Review
 *   event="complete" — reads cudwf_approved and moves documents to
 *                      {Dept}_Published (approve + lock) or {Dept}_Draft (reject)
 *
 * Folder resolution (no hard-coded paths):
 *   current folder "President_Review" -> dept base "President"
 *   target folder  = sibling "President" + suffix, e.g. "President_Published"
 *
 * When the target state is PUBLISHED the document is additionally locked
 * (owner lock) so nobody except the owner/unlock-capable users can edit it;
 * the {Dept}_Published folder permissions already limit editing to Managers.
 *
 * BPMN usage:
 *   <userTask id="reviewTask" name="Review & Approve"
 *             activiti:formKey="cudwf:reviewTask"
 *             activiti:taskListener="${cudDocumentLifecycleTaskListener}">
 *       <extensionElements>
 *           <activiti:taskListener event="create"
 *                                  activiti:class="ae.ac.cud.workflow.lifecycle.CudDocumentLifecycleTaskListener" />
 *       </extensionElements>
 *   </userTask>
 */
public class CudDocumentLifecycleTaskListener implements TaskListener {

    private static final Logger LOG =
            LoggerFactory.getLogger(CudDocumentLifecycleTaskListener.class);

    private static final String CUD_URI = "http://www.cud.ac.ae/model/content/1.0";
    private static final QName ASPECT_LIFECYCLE =
            QName.createQName(CUD_URI, "documentLifecycle");
    private static final QName PROP_STATUS =
            QName.createQName(CUD_URI, "lifecycleStatus");
    private static final QName PROP_CHANGED_AT =
            QName.createQName(CUD_URI, "statusChangedAt");
    private static final QName PROP_HISTORY =
            QName.createQName(CUD_URI, "statusHistory");

    /** state -> folder suffix; must match CudAutoDepartmentBehaviour */
    private static final Map<String, String> STATE_TO_SUFFIX = new HashMap<String, String>();
    static {
        STATE_TO_SUFFIX.put("DRAFT", "_Draft");
        STATE_TO_SUFFIX.put("REVIEW", "_Review");
        STATE_TO_SUFFIX.put("PUBLISHED", "_Published");
        STATE_TO_SUFFIX.put("ARCHIVED", "_Archive");
    }
    private static final String[] SUFFIXES =
            { "_Draft", "_Review", "_Published", "_Archive" };

    public static final String STATE_REVIEW = "REVIEW";
    public static final String STATE_PUBLISHED = "PUBLISHED";
    public static final String STATE_DRAFT = "DRAFT";

    private NodeService nodeService;
    private FileFolderService fileFolderService;
    private LockService lockService;

    @Override
    public void notify(DelegateTask delegateTask) {
        String event = delegateTask.getEventName();

        LOG.info("[CUD Lifecycle TaskListener] >>> notify() invoked | taskId='{}' | taskName='{}' | event='{}' | processInstanceId='{}' | executionId='{}'",
                delegateTask.getId(),
                delegateTask.getName(),
                event,
                delegateTask.getProcessInstanceId(),
                delegateTask.getExecutionId());

        if (LOG.isDebugEnabled()) {
            LOG.debug("[CUD Lifecycle TaskListener] All task variables at event '{}':", event);
            try {
                for (String varName : delegateTask.getVariablesLocal().keySet()) {
                    Object val = delegateTask.getVariableLocal(varName);
                    LOG.debug("  local var '{}' = {} (type={})", varName, val,
                            val != null ? val.getClass().getName() : "null");
                }
            } catch (Exception e) {
                LOG.debug("  (could not enumerate local variables: {})", e.getMessage());
            }
            try {
                for (String varName : delegateTask.getVariables().keySet()) {
                    Object val = delegateTask.getVariable(varName);
                    LOG.debug("  process var '{}' = {} (type={})", varName, val,
                            val != null ? val.getClass().getName() : "null");
                }
            } catch (Exception e) {
                LOG.debug("  (could not enumerate process variables: {})", e.getMessage());
            }
        }

        if ("create".equals(event)) {
            LOG.info("[CUD Lifecycle TaskListener] event='create' — will move documents to REVIEW state");

            // Task created — move documents into the REVIEW folder
            transitionDocuments(STATE_REVIEW, delegateTask);

            // Assign to the workflow initiator
            Object initiator = delegateTask.getVariable("initiator");
            LOG.info("[CUD Lifecycle TaskListener] initiator variable = {} (type={})",
                    initiator, initiator != null ? initiator.getClass().getName() : "null");
            if (initiator != null) {
                String userName = resolveUserName(initiator);
                if (userName != null && !userName.isEmpty()) {
                    delegateTask.setAssignee(userName);
                    LOG.info("[CUD Lifecycle TaskListener] assigned review task to '{}'", userName);
                } else {
                    LOG.warn("[CUD Lifecycle TaskListener] resolved initiator userName is null/empty – task not assigned");
                }
            } else {
                LOG.warn("[CUD Lifecycle TaskListener] 'initiator' variable is null – task not assigned");
            }
        } else if ("complete".equals(event)) {
            // Task completed — decide target state based on approval
            Object approved = delegateTask.getVariable("cudwf_approved");
            LOG.info("[CUD Lifecycle TaskListener] event='complete' — cudwf_approved={} (type={})",
                    approved, approved != null ? approved.getClass().getName() : "null");

            boolean isApproved = Boolean.TRUE.equals(approved)
                    || "true".equalsIgnoreCase(String.valueOf(approved));

            String targetState = isApproved ? STATE_PUBLISHED : STATE_DRAFT;
            LOG.info("[CUD Lifecycle TaskListener] task completed, approved={} -> targetState={}",
                    isApproved, targetState);

            transitionDocuments(targetState, delegateTask);
        } else {
            LOG.info("[CUD Lifecycle TaskListener] event='{}' — not handled by this listener (only 'create' and 'complete' are processed)", event);
        }

        LOG.info("[CUD Lifecycle TaskListener] <<< notify() finished for taskId='{}' event='{}'",
                delegateTask.getId(), event);
    }

    private void transitionDocuments(String state, DelegateTask delegateTask) {
        LOG.info("[CUD Lifecycle TaskListener] transitionDocuments() called | targetState='{}'", state);

        // Log ALL workflow fields/variables for full traceability
        logWorkflowFields(delegateTask);

        String suffix = STATE_TO_SUFFIX.get(state);
        if (suffix == null) {
            LOG.error("[CUD Lifecycle TaskListener] Unknown lifecycle state '{}' – throwing exception", state);
            throw new IllegalArgumentException("Unknown lifecycle state: " + state);
        }
        LOG.info("[CUD Lifecycle TaskListener] resolved folder suffix='{}' for state='{}'", suffix, state);

        Object pkg = delegateTask.getVariable("bpm_package");
        LOG.info("[CUD Lifecycle TaskListener] bpm_package variable = {} | type={}",
                pkg, pkg != null ? pkg.getClass().getName() : "null");

        if (pkg == null) {
            LOG.warn("[CUD Lifecycle TaskListener] bpm_package is NULL – no documents to move. "
                    + "Check that the start task sets bpm_package correctly.");
            return;
        }

        if (!(pkg instanceof List)) {
            LOG.warn("[CUD Lifecycle TaskListener] bpm_package is NOT a List (actual type: {}). "
                    + "Cannot iterate – nothing to move.", pkg.getClass().getName());
            return;
        }

        List<?> pkgList = (List<?>) pkg;
        LOG.info("[CUD Lifecycle TaskListener] bpm_package contains {} item(s)", pkgList.size());

        if (pkgList.isEmpty()) {
            LOG.warn("[CUD Lifecycle TaskListener] bpm_package is an EMPTY list – no documents to move.");
            return;
        }

        int movedCount = 0;
        int skippedCount = 0;

        for (int i = 0; i < pkgList.size(); i++) {
            Object item = pkgList.get(i);
            LOG.info("[CUD Lifecycle TaskListener] processing bpm_package item[{}] = {} | type={}",
                    i, item, item != null ? item.getClass().getName() : "null");

            if (!(item instanceof ActivitiScriptNode)) {
                LOG.warn("[CUD Lifecycle TaskListener] bpm_package item[{}] is NOT an ActivitiScriptNode "
                        + "(actual type: {}) – skipping", i,
                        item != null ? item.getClass().getName() : "null");
                skippedCount++;
                continue;
            }

            NodeRef doc = ((ActivitiScriptNode) item).getNodeRef();
            LOG.info("[CUD Lifecycle TaskListener] bpm_package item[{}] resolved to NodeRef={}", i, doc);

            try {
                moveDocument(doc, state, suffix, delegateTask);
                movedCount++;
            } catch (Exception e) {
                LOG.error("[CUD Lifecycle TaskListener] EXCEPTION while moving document NodeRef={}: {} – {}",
                        doc, e.getClass().getName(), e.getMessage(), e);
                skippedCount++;
            }
        }

        LOG.info("[CUD Lifecycle TaskListener] transitionDocuments() summary: processed={}, moved={}, skipped/failed={}",
                pkgList.size(), movedCount, skippedCount);
    }

    private void moveDocument(NodeRef doc, String state, String suffix,
                              DelegateTask delegateTask) {

        LOG.info("[CUD Lifecycle TaskListener] moveDocument() START | nodeRef={} | targetState='{}' | suffix='{}'",
                doc, state, suffix);

        FileInfo info = fileFolderService.getFileInfo(doc);
        if (info == null) {
            LOG.warn("[CUD Lifecycle TaskListener] moveDocument: fileFolderService.getFileInfo({}) returned NULL – node may not exist or is not a file/folder. Skipping.", doc);
            return;
        }
        if (!nodeService.exists(doc)) {
            LOG.warn("[CUD Lifecycle TaskListener] moveDocument: nodeService.exists({}) returned FALSE – node no longer exists. Skipping.", doc);
            return;
        }

        LOG.info("[CUD Lifecycle TaskListener] moveDocument: document name='{}' | nodeRef={} | isFolder={}",
                info.getName(), doc, info.isFolder());

        // Log full file information: name, version, size, mimetype, audit, etc.
        logDocumentDetails(doc, info);

        NodeRef currentFolderRef = nodeService.getPrimaryParent(doc).getParentRef();
        String currentFolderName = (String)
                nodeService.getProperty(currentFolderRef, ContentModel.PROP_NAME);

        LOG.info("[CUD Lifecycle TaskListener] moveDocument: currentFolder='{}' (NodeRef={})",
                currentFolderName, currentFolderRef);

        // 1. derive department base name, e.g. "President_Review" -> "President"
        String deptBase = null;
        for (String s : SUFFIXES) {
            if (currentFolderName.endsWith(s)) {
                deptBase = currentFolderName.substring(
                        0, currentFolderName.length() - s.length());
                LOG.info("[CUD Lifecycle TaskListener] moveDocument: matched suffix '{}' in folder name '{}' -> deptBase='{}'",
                        s, currentFolderName, deptBase);
                break;
            }
        }
        if (deptBase == null) {
            LOG.warn("[CUD Lifecycle TaskListener] moveDocument: document '{}' (NodeRef={}) is NOT inside a recognized lifecycle folder. "
                    + "Current parent folder='{}'. Expected suffixes: _Draft, _Review, _Published, _Archive. Skipping.",
                    info.getName(), doc, currentFolderName);
            return;
        }

        // 2. resolve target sibling folder under the department root
        NodeRef deptRoot = nodeService.getPrimaryParent(currentFolderRef).getParentRef();
        String deptRootName = (String) nodeService.getProperty(deptRoot, ContentModel.PROP_NAME);
        String targetName = deptBase + suffix;

        LOG.info("[CUD Lifecycle TaskListener] moveDocument: deptRoot='{}' (NodeRef={}) | searching for target folder='{}'",
                deptRootName, deptRoot, targetName);

        NodeRef targetFolder = fileFolderService.searchSimple(deptRoot, targetName);

        if (targetFolder == null) {
            LOG.error("[CUD Lifecycle TaskListener] moveDocument: target lifecycle folder '{}' NOT FOUND under department root '{}' (NodeRef={}). "
                    + "Ensure the folder exists. Skipping move for document '{}'.",
                    targetName, deptRootName, deptRoot, info.getName());
            return;
        }

        LOG.info("[CUD Lifecycle TaskListener] moveDocument: target folder '{}' found (NodeRef={})",
                targetName, targetFolder);

        // 3. move (no-op if already there); unlock first if a revision path brings it back
        if (!targetFolder.equals(currentFolderRef)) {
            LOG.info("[CUD Lifecycle TaskListener] moveDocument: moving document '{}' from '{}' to '{}'",
                    info.getName(), currentFolderName, targetName);
            try {
                fileFolderService.move(doc, targetFolder, null);
                LOG.info("[CUD Lifecycle TaskListener] moveDocument: SUCCESS – moved '{}' : '{}' -> '{}'",
                        info.getName(), currentFolderName, targetName);
            } catch (FileNotFoundException e) {
                LOG.error("[CUD Lifecycle TaskListener] moveDocument: FileNotFoundException while moving '{}' to '{}': {} – {}",
                        info.getName(), targetName, e.getClass().getName(), e.getMessage(), e);
                return;
            } catch (Exception e) {
                LOG.error("[CUD Lifecycle TaskListener] moveDocument: UNEXPECTED EXCEPTION while moving '{}' to '{}': {} – {}",
                        info.getName(), targetName, e.getClass().getName(), e.getMessage(), e);
                return;
            }
        } else {
            LOG.info("[CUD Lifecycle TaskListener] moveDocument: document '{}' is ALREADY in target folder '{}' – no move needed",
                    info.getName(), targetName);
        }

        // 4. stamp lifecycle metadata
        LOG.debug("[CUD Lifecycle TaskListener] moveDocument: stamping lifecycle metadata on '{}' (state='{}')",
                info.getName(), state);
        if (!nodeService.hasAspect(doc, ASPECT_LIFECYCLE)) {
            nodeService.addAspect(doc, ASPECT_LIFECYCLE, null);
            LOG.debug("[CUD Lifecycle TaskListener] moveDocument: added lifecycle aspect to '{}'", info.getName());
        }
        Map<QName, Serializable> props = new HashMap<QName, Serializable>();
        props.put(PROP_STATUS, state);
        props.put(PROP_CHANGED_AT, new Date());
        nodeService.addProperties(doc, props);
        LOG.info("[CUD Lifecycle TaskListener] moveDocument: updated lifecycleStatus='{}' and statusChangedAt on '{}'",
                state, info.getName());

        // audit trail on the node itself
        appendHistory(doc, state, targetName, delegateTask);

        // 5. publish lockdown / revision unlock
        if (STATE_PUBLISHED.equals(state)) {
            if (!lockService.isLocked(doc)) {
                // WRITE_LOCK = owner lock: only the owner (and unlock-capable users) can modify
                lockService.lock(doc, LockType.WRITE_LOCK);
                LOG.info("[CUD Lifecycle TaskListener] moveDocument: locked '{}' (owner WRITE_LOCK) – read-only for everyone except owner",
                        info.getName());
            } else {
                LOG.info("[CUD Lifecycle TaskListener] moveDocument: document '{}' is already locked – skipping lock",
                        info.getName());
            }
        } else {
            // moving back to DRAFT/REVIEW releases any owner lock
            if (lockService.isLocked(doc)) {
                lockService.unlock(doc);
                LOG.info("[CUD Lifecycle TaskListener] moveDocument: unlocked '{}' (state={})", info.getName(), state);
            } else {
                LOG.debug("[CUD Lifecycle TaskListener] moveDocument: document '{}' is not locked – no unlock needed",
                        info.getName());
            }
        }

        LOG.info("[CUD Lifecycle TaskListener] moveDocument() END | nodeRef={} | finalState='{}' | folder='{}'",
                doc, state, targetName);
    }

    @SuppressWarnings("unchecked")
    private void appendHistory(NodeRef doc, String state, String folderName,
                               DelegateTask delegateTask) {
        LOG.debug("[CUD Lifecycle TaskListener] appendHistory() for nodeRef={} | state='{}' | folder='{}'",
                doc, state, folderName);

        List<Serializable> history;
        Object existing = nodeService.getProperty(doc, PROP_HISTORY);
        if (existing instanceof List) {
            history = new ArrayList<Serializable>((List<Serializable>) existing);
            LOG.debug("[CUD Lifecycle TaskListener] appendHistory: existing history has {} entries", history.size());
        } else {
            history = new ArrayList<Serializable>();
            LOG.debug("[CUD Lifecycle TaskListener] appendHistory: no existing history – starting new list");
        }
        String entry = new Date() + " | " + state + " | moved to " + folderName
                + " | by workflow instance " + delegateTask.getProcessInstanceId();
        history.add(entry);
        nodeService.setProperty(doc, PROP_HISTORY, (Serializable) history);
        LOG.debug("[CUD Lifecycle TaskListener] appendHistory: appended entry: '{}'", entry);
    }

    /**
     * Logs all workflow fields: task metadata plus every process and local
     * workflow variable (expanding lists and ActivitiScriptNode items).
     */
    private void logWorkflowFields(DelegateTask delegateTask) {
        LOG.info("[CUD Lifecycle TaskListener] === Workflow fields dump ===");
        try {
            LOG.info("  taskId='{}' | taskName='{}' | taskDescription='{}' | assignee='{}' | owner='{}' | priority={} | dueDate={}",
                    delegateTask.getId(), delegateTask.getName(), delegateTask.getDescription(),
                    delegateTask.getAssignee(), delegateTask.getOwner(),
                    delegateTask.getPriority(), delegateTask.getDueDate());
            LOG.info("  processInstanceId='{}' | executionId='{}' | processDefinitionId='{}' | createTime={}",
                    delegateTask.getProcessInstanceId(), delegateTask.getExecutionId(),
                    delegateTask.getProcessDefinitionId(), delegateTask.getCreateTime());
        } catch (Exception e) {
            LOG.warn("  could not read task metadata: {}", e.getMessage());
        }

        // Process-level workflow variables (the form fields submitted by users)
        try {
            Map<String, Object> processVars = delegateTask.getVariables();
            LOG.info("  Process variables count = {}",
                    processVars != null ? processVars.size() : 0);
            if (processVars != null) {
                for (Map.Entry<String, Object> entry : processVars.entrySet()) {
                    logWorkflowVariable("process", entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            LOG.warn("  could not enumerate process variables: {}", e.getMessage());
        }

        // Task-local variables
        try {
            Map<String, Object> localVars = delegateTask.getVariablesLocal();
            LOG.info("  Local variables count = {}",
                    localVars != null ? localVars.size() : 0);
            if (localVars != null) {
                for (Map.Entry<String, Object> entry : localVars.entrySet()) {
                    logWorkflowVariable("local", entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            LOG.warn("  could not enumerate local variables: {}", e.getMessage());
        }
        LOG.info("[CUD Lifecycle TaskListener] === end workflow fields dump ===");
    }

    private void logWorkflowVariable(String scope, String name, Object val) {
        if (val == null) {
            LOG.info("  [{}] var '{}' = null", scope, name);
            return;
        }
        if (val instanceof List) {
            List<?> list = (List<?>) val;
            LOG.info("  [{}] var '{}' = List with {} item(s) (type={})",
                    scope, name, list.size(), val.getClass().getName());
            for (int i = 0; i < list.size(); i++) {
                LOG.info("    [{}] var '{}'[{}] = {}", scope, name, i,
                        describeValue(list.get(i)));
            }
        } else {
            LOG.info("  [{}] var '{}' = {} (type={})",
                    scope, name, describeValue(val), val.getClass().getName());
        }
    }

    /**
     * Produces a readable description of a workflow variable value,
     * resolving ActivitiScriptNode items to their NodeRef + file name.
     */
    private String describeValue(Object val) {
        if (val == null) {
            return "null";
        }
        if (val instanceof ActivitiScriptNode) {
            try {
                NodeRef ref = ((ActivitiScriptNode) val).getNodeRef();
                FileInfo fi = fileFolderService.getFileInfo(ref);
                return "ActivitiScriptNode nodeRef=" + ref
                        + (fi != null ? " name='" + fi.getName() + "'" : "");
            } catch (Exception e) {
                return "ActivitiScriptNode (could not resolve: " + e.getMessage() + ")";
            }
        }
        return String.valueOf(val);
    }

    /**
     * Logs detailed file information for a document node:
     * file name, version label, file size, mimetype, creator/modifier
     * audit data, title/description, lock status, and aspects.
     */
    private void logDocumentDetails(NodeRef doc, FileInfo info) {
        LOG.info("[CUD Lifecycle TaskListener] === Document details for nodeRef={} ===", doc);
        LOG.info("  fileName='{}'", info.getName());
        LOG.info("  nodeRef={}", doc);
        LOG.info("  isFolder={}", info.isFolder());

        // content info: mimetype + size
        try {
            ContentData contentData = info.getContentData();
            if (contentData != null) {
                long size = contentData.getSize();
                LOG.info("  mimeType='{}' | encoding='{}' | contentSize={} bytes ({})",
                        contentData.getMimetype(), contentData.getEncoding(),
                        size, humanReadableSize(size));
            } else {
                LOG.info("  contentData=null (folder or no content)");
            }
        } catch (Exception e) {
            LOG.warn("  could not read content data: {}", e.getMessage());
        }

        // version
        try {
            Object versionLabel = nodeService.getProperty(doc, ContentModel.PROP_VERSION_LABEL);
            boolean versionable = nodeService.hasAspect(doc, ContentModel.ASPECT_VERSIONABLE);
            LOG.info("  versionLabel='{}' | hasVersionableAspect={}", versionLabel, versionable);
        } catch (Exception e) {
            LOG.warn("  could not read version info: {}", e.getMessage());
        }

        // audit properties
        try {
            LOG.info("  creator='{}' | created={} | modifier='{}' | modified={}",
                    nodeService.getProperty(doc, ContentModel.PROP_CREATOR),
                    nodeService.getProperty(doc, ContentModel.PROP_CREATED),
                    nodeService.getProperty(doc, ContentModel.PROP_MODIFIER),
                    nodeService.getProperty(doc, ContentModel.PROP_MODIFIED));
        } catch (Exception e) {
            LOG.warn("  could not read audit properties: {}", e.getMessage());
        }

        // title / description
        try {
            LOG.info("  title='{}' | description='{}'",
                    nodeService.getProperty(doc, ContentModel.PROP_TITLE),
                    nodeService.getProperty(doc, ContentModel.PROP_DESCRIPTION));
        } catch (Exception e) {
            LOG.warn("  could not read title/description: {}", e.getMessage());
        }

        // lock status
        try {
            boolean locked = lockService.isLocked(doc);
            LOG.info("  isLocked={} | lockType={}",
                    locked, locked ? lockService.getLockType(doc) : "N/A");
        } catch (Exception e) {
            LOG.warn("  could not read lock info: {}", e.getMessage());
        }

        // aspects
        try {
            LOG.info("  aspects={}", nodeService.getAspects(doc));
        } catch (Exception e) {
            LOG.warn("  could not read aspects: {}", e.getMessage());
        }

        // current location
        try {
            NodeRef parentRef = nodeService.getPrimaryParent(doc).getParentRef();
            String parentName = (String)
                    nodeService.getProperty(parentRef, ContentModel.PROP_NAME);
            LOG.info("  currentLocation: parentFolder='{}' (nodeRef={})",
                    parentName, parentRef);
        } catch (Exception e) {
            LOG.warn("  could not resolve parent folder: {}", e.getMessage());
        }
        LOG.info("[CUD Lifecycle TaskListener] === end document details ===");
    }

    private String humanReadableSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MB", mb);
        }
        return String.format("%.1f GB", mb / 1024.0);
    }

    /**
     * Resolves the username from an initiator variable.
     * The initiator may be an ActivitiScriptNode or a plain username string.
     */
    private String resolveUserName(Object initiator) {
        LOG.debug("[CUD Lifecycle TaskListener] resolveUserName() | initiator={} | type={}",
                initiator, initiator != null ? initiator.getClass().getName() : "null");

        if (initiator instanceof String) {
            LOG.debug("[CUD Lifecycle TaskListener] resolveUserName: initiator is a plain String -> '{}'", initiator);
            return (String) initiator;
        }
        try {
            Object properties = initiator.getClass().getMethod("getProperties").invoke(initiator);
            Object userName = properties.getClass().getMethod("get", Object.class)
                    .invoke(properties, "userName");
            if (userName != null) {
                LOG.debug("[CUD Lifecycle TaskListener] resolveUserName: resolved userName='{}' via getProperties()", userName);
                return userName.toString();
            }
            LOG.warn("[CUD Lifecycle TaskListener] resolveUserName: getProperties().get('userName') returned null");
        } catch (Exception e) {
            LOG.warn("[CUD Lifecycle TaskListener] resolveUserName: could not resolve initiator userName via reflection: {} – {}",
                    e.getClass().getName(), e.getMessage());
        }
        LOG.debug("[CUD Lifecycle TaskListener] resolveUserName: falling back to toString() -> '{}'", initiator);
        return initiator.toString();
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

    public void setLockService(LockService lockService) {
        this.lockService = lockService;
    }
}