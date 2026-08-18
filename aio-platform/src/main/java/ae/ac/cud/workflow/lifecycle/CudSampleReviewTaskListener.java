package ae.ac.cud.workflow.advance;

import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CudSampleReviewTaskListener implements TaskListener {

    private static final Logger LOG = LoggerFactory.getLogger(CudSampleReviewTaskListener.class);

    @Override
    public void notify(DelegateTask delegateTask) {
        String event = delegateTask.getEventName();
        String taskId = delegateTask.getId();
        String assignee = delegateTask.getAssignee();

        LOG.info("[TASK LISTENER] Task '{}' event='{}' assignee='{}'", taskId, event, assignee);

        if ("complete".equals(event)) {
            // Capture the approval decision from the task variable
            // Object approved = delegateTask.getVariable("acmewadv_approved");
            // delegateTask.setVariable("approved", approved);
        }
    }
}