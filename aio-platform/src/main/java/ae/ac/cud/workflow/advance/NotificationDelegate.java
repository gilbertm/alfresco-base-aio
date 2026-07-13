package ae.ac.cud.workflow.advance;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotificationDelegate implements JavaDelegate {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationDelegate.class);

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String approved = (String) execution.getVariable("approved");
        String initiator = (String) execution.getVariable("initiator");
        String workflowDescription = (String) execution.getVariable("bpm_workflowDescription");

        String message = String.format(
                "[ADVANCE WORKFLOW] Document review completed. Decision: '%s'. " +
                "Initiator: '%s'. Workflow: '%s' (id=%s)",
                approved != null ? approved : "unknown",
                initiator != null ? initiator : "unknown",
                workflowDescription != null ? workflowDescription : "no description",
                execution.getProcessInstanceId()
        );

        LOG.info(message);

        // In production: inject WorkflowNotificationService and call sendNotification()
        execution.setVariable("advanceNotification", message);
    }
}