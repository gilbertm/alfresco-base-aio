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

        LOG.info("[CUD Lifecycle TaskListener] task='{}' event='{}'",
                delegateTask.getId(), event);

        if ("create".equals(event)) {
            // Task created — move documents into the REVIEW folder
            transitionDocuments(STATE_REVIEW, delegateTask);

            // Assign to the workflow initiator
            Object initiator = delegateTask.getVariable("initiator");
            if (initiator != null) {
                String userName = resolveUserName(initiator);
                if (userName != null && !userName.isEmpty()) {
                    delegateTask.setAssignee(userName);
                    LOG.info("[CUD Lifecycle TaskListener] assigned review task to '{}'", userName);
                }
            }
        } else if ("complete".equals(event)) {
            // Task completed — decide target state based on approval
            Object approved = delegateTask.getVariable("cudwf_approved");
            boolean isApproved = Boolean.TRUE.equals(approved)
                    || "true".equalsIgnoreCase(String.valueOf(approved));

            String targetState = isApproved ? STATE_PUBLISHED : STATE_DRAFT;
            LOG.info("[CUD Lifecycle TaskListener] task completed, approved={} -> targetState={}",
                    isApproved, targetState);

            transitionDocuments(targetState, delegateTask);
        }
    }

    // ------------------------------------------------------------------
    // Document transition logic (same as CudDocumentTransitionDelegate)
    // ------------------------------------------------------------------

    private void transitionDocuments(String state, DelegateTask delegateTask) {
        String suffix = STATE_TO_SUFFIX.get(state);
        if (suffix == null) {
            throw new IllegalArgumentException("Unknown lifecycle state: " + state);
        }

        Object pkg = delegateTask.getVariable("bpm_package");
        if (!(pkg instanceof List)) {
            LOG.warn("CUD transition: bpm_package missing – nothing to move");
            return;
        }

        for (Object item : (List<?>) pkg) {
            if (!(item instanceof ActivitiScriptNode)) {
                continue;
            }
            NodeRef doc = ((ActivitiScriptNode) item).getNodeRef();
            moveDocument(doc, state, suffix, delegateTask);
        }
    }

    private void moveDocument(NodeRef doc, String state, String suffix,
                              DelegateTask delegateTask) {

        FileInfo info = fileFolderService.getFileInfo(doc);
        if (info == null || !nodeService.exists(doc)) {
            LOG.warn("CUD transition: payload node no longer exists – skipping");
            return;
        }

        NodeRef currentFolderRef = nodeService.getPrimaryParent(doc).getParentRef();
        String currentFolderName = (String)
                nodeService.getProperty(currentFolderRef, ContentModel.PROP_NAME);

        // 1. derive department base name, e.g. "President_Review" -> "President"
        String deptBase = null;
        for (String s : SUFFIXES) {
            if (currentFolderName.endsWith(s)) {
                deptBase = currentFolderName.substring(
                        0, currentFolderName.length() - s.length());
                break;
            }
        }
        if (deptBase == null) {
            LOG.warn("CUD transition: document '{}' is not inside a lifecycle folder (parent='{}') – skipping",
                    info.getName(), currentFolderName);
            return;
        }

        // 2. resolve target sibling folder under the department root
        NodeRef deptRoot = nodeService.getPrimaryParent(currentFolderRef).getParentRef();
        String targetName = deptBase + suffix;
        NodeRef targetFolder = fileFolderService.searchSimple(deptRoot, targetName);
        if (targetFolder == null) {
            LOG.error("CUD transition: target lifecycle folder '{}' not found under department root – skipping",
                    targetName);
            return;
        }

        // 3. move (no-op if already there); unlock first if a revision path brings it back
        if (!targetFolder.equals(currentFolderRef)) {
            try {
                fileFolderService.move(doc, targetFolder, null);
            } catch (FileNotFoundException e) {
                LOG.error("CUD transition: failed to move '{}' to '{}': {}",
                        info.getName(), targetName, e.getMessage());
                return;
            }
            LOG.info("CUD transition: moved '{}' : {} -> {}",
                    info.getName(), currentFolderName, targetName);
        }

        // 4. stamp lifecycle metadata
        if (!nodeService.hasAspect(doc, ASPECT_LIFECYCLE)) {
            nodeService.addAspect(doc, ASPECT_LIFECYCLE, null);
        }
        Map<QName, Serializable> props = new HashMap<QName, Serializable>();
        props.put(PROP_STATUS, state);
        props.put(PROP_CHANGED_AT, new Date());
        nodeService.addProperties(doc, props);

        // audit trail on the node itself
        appendHistory(doc, state, targetName, delegateTask);

        // 5. publish lockdown / revision unlock
        if (STATE_PUBLISHED.equals(state)) {
            if (!lockService.isLocked(doc)) {
                // WRITE_LOCK = owner lock: only the owner (and unlock-capable users) can modify
                lockService.lock(doc, LockType.WRITE_LOCK);
                LOG.info("CUD transition: locked '{}' (owner lock) – read-only for everyone except owner",
                        info.getName());
            }
        } else {
            // moving back to DRAFT/REVIEW releases any owner lock
            if (lockService.isLocked(doc)) {
                lockService.unlock(doc);
                LOG.info("CUD transition: unlocked '{}'", info.getName());
            }
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
        history.add(new Date() + " | " + state + " | moved to " + folderName
                + " | by workflow instance " + delegateTask.getProcessInstanceId());
        nodeService.setProperty(doc, PROP_HISTORY, (Serializable) history);
    }

    /**
     * Resolves the username from an initiator variable.
     * The initiator may be an ActivitiScriptNode or a plain username string.
     */
    private String resolveUserName(Object initiator) {
        if (initiator instanceof String) {
            return (String) initiator;
        }
        try {
            Object properties = initiator.getClass().getMethod("getProperties").invoke(initiator);
            Object userName = properties.getClass().getMethod("get", Object.class)
                    .invoke(properties, "userName");
            if (userName != null) {
                return userName.toString();
            }
        } catch (Exception e) {
            LOG.warn("CUD Lifecycle TaskListener: could not resolve initiator userName via reflection: {}",
                    e.getMessage());
        }
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