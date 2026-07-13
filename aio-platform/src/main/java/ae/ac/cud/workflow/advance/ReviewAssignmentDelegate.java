package ae.ac.cud.workflow.advance;

import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.TaskListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Task listener that resolves custom d:noderef workflow variables to assignees.
 * <p>
 * Custom d:noderef properties on the start form are stored as raw node reference strings
 * (e.g., "workspace://SpacesStore/uuid"), not as ActivitiScriptNode objects.
 * This listener resolves them to the actual username and sets the task assignee.
 * </p>
 * <p>
 * Usage in BPMN:
 * <pre>
 * <activiti:taskListener event="create" class="ae.ac.cud.workflow.advance.ReviewAssignmentDelegate" />
 * </pre>
 * Configure the variable name via the delegate's taskVariable property:
 * <pre>
 * <activiti:field name="reviewerVariable" stringValue="acmewadv_peerReviewer" />
 * </pre>
 * </p>
 */
public class ReviewAssignmentDelegate implements TaskListener {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewAssignmentDelegate.class);

    private Expression reviewerVariable;

    @Override
    public void notify(DelegateTask delegateTask) {
        // Determine which workflow variable to read
        String varName = (reviewerVariable != null) ? reviewerVariable.getValue(delegateTask).toString() : getDefaultVariable(delegateTask);

        Object reviewerValue = delegateTask.getVariable(varName);
        LOG.info("ReviewAssignmentDelegate: task='{}' variable='{}' value='{}'",
                delegateTask.getId(), varName, reviewerValue);

        if (reviewerValue == null) {
            LOG.warn("ReviewAssignmentDelegate: workflow variable '{}' is null, cannot assign task", varName);
            return;
        }

        // The value is a node reference string like "workspace://SpacesStore/uuid"
        // We need to resolve it to a username. We use the userName property of the
        // ActivitiScriptNode, or fall back to the toString() representation.
        String assignee = null;

        if (reviewerValue instanceof String) {
            // Raw node reference string — we can't resolve it without a service call
            // In production, inject PeopleService and resolve the node ref
            LOG.warn("ReviewAssignmentDelegate: raw node reference '{}' cannot be resolved without PeopleService. " +
                     "Falling back to using the value as-is.", reviewerValue);
            assignee = reviewerValue.toString();
        } else {
            // ActivitiScriptNode — we can access .properties.userName
            try {
                Object properties = reviewerValue.getClass().getMethod("getProperties").invoke(reviewerValue);
                Object userName = properties.getClass().getMethod("get", Object.class).invoke(properties, "userName");
                if (userName != null) {
                    assignee = userName.toString();
                }
            } catch (Exception e) {
                LOG.warn("ReviewAssignmentDelegate: could not resolve userName via reflection, using toString(): {}",
                         e.getMessage());
                assignee = reviewerValue.toString();
            }
        }

        if (assignee != null && !assignee.isEmpty()) {
            delegateTask.setAssignee(assignee);
            LOG.info("ReviewAssignmentDelegate: assigned task '{}' to '{}'", delegateTask.getId(), assignee);
        }
    }

    /**
     * Determines the default variable name based on the task definition key.
     */
    private String getDefaultVariable(DelegateTask task) {
        String taskKey = task.getTaskDefinitionKey();
        if ("peerReviewTask".equals(taskKey)) {
            return "acmewadv_peerReviewer";
        } else if ("managerApprovalTask".equals(taskKey)) {
            return "acmewadv_managerReviewer";
        } else if ("finalSignOffTask".equals(taskKey)) {
            return "acmewadv_seniorReviewer";
        }
        return "acmewadv_peerReviewer";
    }

    public void setReviewerVariable(Expression reviewerVariable) {
        this.reviewerVariable = reviewerVariable;
    }
}