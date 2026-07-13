package ae.ac.cud.workflow.advance;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuditDelegate implements JavaDelegate {

    private static final Logger LOG = LoggerFactory.getLogger(AuditDelegate.class);

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String approved = (String) execution.getVariable("approved");
        String auditTrail = String.format(
                "[AUDIT] Process '%s' completed with decision '%s' at %s",
                execution.getProcessInstanceId(),
                approved,
                new java.util.Date()
        );

        LOG.info(auditTrail);
        execution.setVariable("auditTrail", auditTrail);
    }
}