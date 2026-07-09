package ae.ac.cud.workflow;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaDelegate that logs a notification message when a review task is completed.
 * <p>
 * This demonstrates how custom Java code can be plugged into a BPMN workflow
 * via {@code activiti:class} in the process definition.
 * <p>
 * To extend this to a real popup or in-app notification:
 * <ul>
 *   <li>Inject Alfresco's {@code org.alfresco.repo.workflow.WorkflowNotificationService}
 *       and call {@code sendNotification(...)}</li>
 *   <li>Or write a custom Web Script and invoke it from the client side</li>
 * </ul>
 */
public class ReviewNotificationDelegate implements JavaDelegate {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewNotificationDelegate.class);

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        // Retrieve workflow variables (set by the user task form)
        // bpm_assignee may be an ActivitiScriptNode (wrapping a NodeRef) rather than a plain String
        Object reviewerObj = execution.getVariable("bpm_assignee");
        String reviewer = reviewerObj != null ? reviewerObj.toString() : null;
        String workflowDescription = (String) execution.getVariable("bpm_workflowDescription");
        String message = String.format(
                "[WORKFLOW NOTIFICATION] The file has been reviewed by '%s'. Workflow: '%s' (id=%s)",
                reviewer != null ? reviewer : "unknown",
                workflowDescription != null ? workflowDescription : "no description",
                execution.getProcessInstanceId()
        );

        // Log the notification — in a real implementation this would trigger
        // an Alfresco notification, email, or client-side popup.
        LOG.info(message);

        // Optionally store a workflow variable so a web script or Share extension
        // can pick it up and display it to the user.
        execution.setVariable("reviewNotification", message);
    }
}