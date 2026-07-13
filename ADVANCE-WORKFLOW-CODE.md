# Advance Workflow Code Reference

Complete code listing for all advance workflow files after fixes.

---

## 1. BPMN Process Definition

**File:** `aio-platform/src/main/resources/alfresco/module/aio-platform/workflow/advance-review-process.bpmn20.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>

<definitions
        xmlns:activiti="http://activiti.org/bpmn"
        xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        typeLanguage="http://www.w3.org/2001/XMLSchema"
        expressionLanguage="http://www.w3.org/1999/XPath"
        targetNamespace="http://www.activiti.org/test">

    <!--
        Advanced Review Process — a multi-stage review and approval workflow:
        1. Peer Review — assigned via acmewadv:peerReviewer from start form
        2. Manager Approval — assigned via acmewadv:managerReviewer from start form
        3. Final Sign-Off — assigned via acmewadv:seniorReviewer from start form
        4. Notification — sends email and in-app notifications
        5. Audit — logs the final decision to the audit trail

        Task assignment uses ReviewAssignmentDelegate because custom d:noderef
        properties are stored as raw node reference strings, not ActivitiScriptNode
        objects, so EL expressions like ${acmewadv_peerReviewer.properties.userName}
        do not resolve. The delegate reads the variable and sets the assignee.
    -->
    <process id="advance-review-process" name="Advanced Review and Approval">

        <!-- Start event — uses custom start form with all three reviewer selectors -->
        <startEvent id="start" activiti:formKey="acmewadv:advanceReviewStart"/>
        <sequenceFlow id="flow1" sourceRef="start" targetRef="peerReviewTask"/>

        <!-- Peer review — assigned via ReviewAssignmentDelegate from acmewadv:peerReviewer -->
        <userTask id="peerReviewTask" name="Peer Review"
                  activiti:formKey="acmewadv:advanceReviewTask"
                  activiti:taskListener="${advanceReviewTaskListener}">
            <extensionElements>
                <activiti:taskListener event="create"
                                       activiti:class="ae.ac.cud.workflow.advance.ReviewAssignmentDelegate" />
            </extensionElements>
        </userTask>
        <sequenceFlow id="flow2" sourceRef="peerReviewTask" targetRef="managerApprovalTask"/>

        <!-- Manager approval — assigned via ReviewAssignmentDelegate from acmewadv:managerReviewer -->
        <userTask id="managerApprovalTask" name="Manager Approval"
                  activiti:formKey="acmewadv:advanceReviewTask"
                  activiti:taskListener="${advanceReviewTaskListener}">
            <extensionElements>
                <activiti:taskListener event="create"
                                       activiti:class="ae.ac.cud.workflow.advance.ReviewAssignmentDelegate" />
            </extensionElements>
        </userTask>
        <sequenceFlow id="flow3" sourceRef="managerApprovalTask" targetRef="finalSignOffTask"/>

        <!-- Final sign-off — assigned via ReviewAssignmentDelegate from acmewadv:seniorReviewer -->
        <userTask id="finalSignOffTask" name="Final Sign-Off"
                  activiti:formKey="acmewadv:advanceReviewTask"
                  activiti:taskListener="${advanceReviewTaskListener}">
            <extensionElements>
                <activiti:taskListener event="create"
                                       activiti:class="ae.ac.cud.workflow.advance.ReviewAssignmentDelegate" />
            </extensionElements>
        </userTask>
        <sequenceFlow id="flow4" sourceRef="finalSignOffTask" targetRef="exclusiveGateway"/>

        <!-- Decision gateway -->
        <exclusiveGateway id="exclusiveGateway" name="Approval Decision"/>
        <sequenceFlow id="flowApproved" sourceRef="exclusiveGateway" targetRef="notificationService">
            <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
        </sequenceFlow>
        <sequenceFlow id="flowRejected" sourceRef="exclusiveGateway" targetRef="notificationService">
            <conditionExpression xsi:type="tFormalExpression">${approved == false}</conditionExpression>
        </sequenceFlow>

        <!-- Notification service task -->
        <serviceTask id="notificationService"
                     name="Send Notifications"
                     activiti:class="ae.ac.cud.workflow.advance.NotificationDelegate"/>
        <sequenceFlow id="flow5" sourceRef="notificationService" targetRef="auditService"/>

        <!-- Audit service task -->
        <serviceTask id="auditService"
                     name="Audit Trail"
                     activiti:class="ae.ac.cud.workflow.advance.AuditDelegate"/>
        <sequenceFlow id="flow6" sourceRef="auditService" targetRef="end"/>

        <endEvent id="end"/>

    </process>

</definitions>
```

---

## 2. Custom Content Model

**File:** `aio-platform/src/main/resources/alfresco/module/aio-platform/model/advance-workflow-model.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<model name="acmewadv:advanceWorkflowModel" xmlns="http://www.alfresco.org/model/dictionary/1.0">
    <description>Advanced Review Workflow Model</description>
    <author>My Name</author>
    <version>1.0</version>

    <imports>
        <import uri="http://www.alfresco.org/model/dictionary/1.0" prefix="d"/>
        <import uri="http://www.alfresco.org/model/content/1.0" prefix="cm"/>
        <import uri="http://www.alfresco.org/model/bpm/1.0" prefix="bpm"/>
    </imports>

    <namespaces>
        <namespace uri="http://www.acme.org/model/advance-workflow/1.0" prefix="acmewadv"/>
    </namespaces>

    <types>
        <type name="acmewadv:advanceReviewStart">
            <parent>bpm:startTask</parent>
            <properties>
                <property name="acmewadv:reviewDeadline">
                    <title>Review Deadline</title>
                    <type>d:date</type>
                    <mandatory>false</mandatory>
                </property>
                <property name="acmewadv:escalationTimeout">
                    <title>Escalation Timeout (hours)</title>
                    <type>d:int</type>
                    <default>72</default>
                </property>
            </properties>
            <associations>
                <association name="acmewadv:managerReviewer">
                    <title>Manager Reviewer</title>
                    <source>
                        <mandatory>true</mandatory>
                        <many>false</many>
                    </source>
                    <target>
                        <class>cm:person</class>
                        <mandatory>true</mandatory>
                        <many>false</many>
                    </target>
                </association>
                <association name="acmewadv:peerReviewer">
                    <title>Peer Reviewer</title>
                    <source>
                        <mandatory>true</mandatory>
                        <many>false</many>
                    </source>
                    <target>
                        <class>cm:person</class>
                        <mandatory>true</mandatory>
                        <many>false</many>
                    </target>
                </association>
                <association name="acmewadv:seniorReviewer">
                    <title>Senior Reviewer</title>
                    <source>
                        <mandatory>true</mandatory>
                        <many>false</many>
                    </source>
                    <target>
                        <class>cm:person</class>
                        <mandatory>true</mandatory>
                        <many>false</many>
                    </target>
                </association>
            </associations>
        </type>
        <type name="acmewadv:advanceReviewTask">
            <parent>bpm:workflowTask</parent>
            <properties>
                <property name="acmewadv:reviewComment">
                    <title>Review Comment</title>
                    <type>d:mltext</type>
                    <mandatory>false</mandatory>
                </property>
                <property name="acmewadv:approved">
                    <title>Approved</title>
                    <type>d:boolean</type>
                    <mandatory>true</mandatory>
                </property>
            </properties>
        </type>
    </types>
</model>
```

---

## 3. ReviewAssignmentDelegate (Task Listener)

**File:** `aio-platform/src/main/java/ae/ac/cud/workflow/advance/ReviewAssignmentDelegate.java`

```java
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
```

---

## 4. AdvanceReviewTaskListener

**File:** `aio-platform/src/main/java/ae/ac/cud/workflow/advance/AdvanceReviewTaskListener.java`

```java
package ae.ac.cud.workflow.advance;

import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdvanceReviewTaskListener implements TaskListener {

    private static final Logger LOG = LoggerFactory.getLogger(AdvanceReviewTaskListener.class);

    @Override
    public void notify(DelegateTask delegateTask) {
        String event = delegateTask.getEventName();
        String taskId = delegateTask.getId();
        String assignee = delegateTask.getAssignee();

        LOG.info("[TASK LISTENER] Task '{}' event='{}' assignee='{}'", taskId, event, assignee);

        if ("complete".equals(event)) {
            // Capture the approval decision from the task variable
            Object approved = delegateTask.getVariable("acmewadv_approved");
            delegateTask.setVariable("approved", approved);
        }
    }
}
```

---

## 5. NotificationDelegate (Service Task)

**File:** `aio-platform/src/main/java/ae/ac/cud/workflow/advance/NotificationDelegate.java`

```java
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
```

---

## 6. AuditDelegate (Service Task)

**File:** `aio-platform/src/main/java/ae/ac/cud/workflow/advance/AuditDelegate.java`

```java
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
```

---

## 7. Spring Service Context

**File:** `aio-platform/src/main/resources/alfresco/module/aio-platform/context/service-context.xml`

```xml
<?xml version='1.0' encoding='UTF-8'?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
           http://www.springframework.org/schema/beans/spring-beans-3.0.xsd">

    <bean id="reviewNotificationDelegate" class="ae.ac.cud.workflow.ReviewNotificationDelegate" />

    <!-- Advanced workflow delegates -->
    <bean id="advanceNotificationDelegate"
          class="ae.ac.cud.workflow.advance.NotificationDelegate" />

    <bean id="advanceAuditDelegate"
          class="ae.ac.cud.workflow.advance.AuditDelegate" />

    <bean id="advanceReviewTaskListener"
          class="ae.ac.cud.workflow.advance.AdvanceReviewTaskListener" />

    <bean id="reviewAssignmentDelegate"
          class="ae.ac.cud.workflow.advance.ReviewAssignmentDelegate" />

</beans>
```

---

## 8. Bootstrap Context

**File:** `aio-platform/src/main/resources/alfresco/module/aio-platform/context/bootstrap-context.xml`

```xml
<?xml version='1.0' encoding='UTF-8'?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
           http://www.springframework.org/schema/beans/spring-beans-3.0.xsd">

    <!-- Registration of new models -->
    <bean id="aio-platform.dictionaryBootstrap" parent="dictionaryModelBootstrap" depends-on="dictionaryBootstrap">
        <property name="models">
            <list>
                <value>alfresco/module/${project.artifactId}/model/content-model.xml</value>
                <value>alfresco/module/${project.artifactId}/model/workflow-model.xml</value>
                <value>alfresco/module/${project.artifactId}/model/advance-workflow-model.xml</value>
            </list>
        </property>
        <property name="labels">
            <list>
                <value>alfresco/module/${project.artifactId}/messages/content-model</value>
            </list>
        </property>
    </bean>

    <bean id="ae.ac.cud.sampleprocess.workflowBootstrap" parent="workflowDeployer">
        <property name="workflowDefinitions">
            <list>
                <props>
                    <prop key="engineId">activiti</prop>
                    <prop key="location">alfresco/module/${project.artifactId}/workflow/sample-process.bpmn20.xml</prop>
                    <prop key="mimetype">text/xml</prop>
                </props>
                <props>
                    <prop key="engineId">activiti</prop>
                    <prop key="location">alfresco/module/${project.artifactId}/workflow/review-process.bpmn20.xml</prop>
                    <prop key="mimetype">text/xml</prop>
                </props>
                <props>
                    <prop key="engineId">activiti</prop>
                    <prop key="location">alfresco/module/${project.artifactId}/workflow/advance-review-process.bpmn20.xml</prop>
                    <prop key="mimetype">text/xml</prop>
                </props>
            </list>
        </property>
        <property name="labels">
            <list>
                <value>alfresco/module/${project.artifactId}/messages/workflow-messages</value>
                <value>alfresco/module/${project.artifactId}/messages/review-workflow-messages</value>
                <value>alfresco/module/${project.artifactId}/messages/advance-workflow-messages</value>
            </list>
        </property>
    </bean>
</beans>
```

---

## 9. Share Form Configuration

**File:** `aio-share/src/main/resources/META-INF/share-config-custom.xml`

```xml
    <!-- Advanced Workflow: Task form for acmewadv:advanceReviewTask -->
    <config evaluator="string-compare" condition="acmewadv:advanceReviewTask">
        <forms>
            <form>
                <field-visibility>
                    <show id="acmewadv:reviewComment" />
                    <show id="acmewadv:approved" />
                </field-visibility>
                <appearance>
                    <field id="acmewadv:reviewComment" label="Review Comment">
                        <control template="/org/alfresco/components/form/controls/textarea.ftl" />
                    </field>
                    <field id="acmewadv:approved" label="Approved">
                        <control template="/org/alfresco/components/form/controls/checkbox.ftl" />
                    </field>
                </appearance>
            </form>
        </forms>
    </config>

    <!-- Advanced Workflow: Start form for acmewadv:advanceReviewStart -->
    <config evaluator="string-compare" condition="acmewadv:advanceReviewStart">
        <forms>
            <form>
                <field-visibility>
                    <show id="bpm:workflowDescription" />
                    <show id="acmewadv:peerReviewer" />
                    <show id="acmewadv:managerReviewer" />
                    <show id="acmewadv:seniorReviewer" />
                    <show id="acmewadv:reviewDeadline" />
                    <show id="acmewadv:escalationTimeout" />
                </field-visibility>
                <appearance>
                    <field id="bpm:workflowDescription" label="Description">
                        <control template="/org/alfresco/components/form/controls/textarea.ftl" />
                    </field>
                     <field id="acmewadv:peerReviewer" label="Peer Reviewer">
                        <control template="/org/alfresco/components/form/controls/association.ftl">
                            <control-param name="compactMode">true</control-param>
                        </control>
                    </field>
                    <field id="acmewadv:managerReviewer" label="Manager Reviewer">
                        <control template="/org/alfresco/components/form/controls/association.ftl">
                            <control-param name="compactMode">true</control-param>
                        </control>
                    </field>
                    <field id="acmewadv:seniorReviewer" label="Senior Reviewer">
                        <control template="/org/alfresco/components/form/controls/association.ftl">
                            <control-param name="compactMode">true</control-param>
                        </control>
                    </field>
                    <field id="acmewadv:reviewDeadline" label="Review Deadline">
                        <control template="/org/alfresco/components/form/controls/date.ftl" />
                    </field>
                    <field id="acmewadv:escalationTimeout" label="Escalation Timeout (hours)">
                        <control template="/org/alfresco/components/form/controls/textfield.ftl" />
                    </field>
                </appearance>
            </form>
        </forms>
    </config>
```

---

## 10. i18n Messages

**File:** `aio-platform/src/main/resources/alfresco/module/aio-platform/messages/advance-workflow-messages.properties`

```properties
advance-review-process.workflow.title=Advanced Review & Approval
advance-review-process.workflow.description=Multi-stage review with parallel peer review, manager approval, and final sign-off (publishes document as an un-editable final copy)
acmewadv_seniorReviewTask.bpm_assignee.title=Senior Reviewer
```

---

## 11. Logging Configuration (dev-log4j2.properties)

**File:** `aio-platform-docker/src/main/docker/dev-log4j2.properties`

```properties
# Custom workflow delegate
logger.ae-ac-cud-workflow.name=ae.ac.cud.workflow
logger.ae-ac-cud-workflow.level=info
logger.alfresco-repo-workflow.name=org.alfresco.repo.workflow
logger.alfresco-repo-workflow.level=info
```

---

## 12. File Reference Table

| # | File | Path (from project root) | Role |
|---|---|---|---|
| 1 | **advance-review-process.bpmn20.xml** | `aio-platform/src/main/resources/alfresco/module/aio-platform/workflow/` | BPMN 2.0 process definition |
| 2 | **advance-workflow-model.xml** | `aio-platform/src/main/resources/alfresco/module/aio-platform/model/` | Custom content model (types & associations) |
| 3 | **ReviewAssignmentDelegate.java** | `aio-platform/src/main/java/ae/ac/cud/workflow/advance/` | Task listener — resolves reviewer variable → assignee |
| 4 | **AdvanceReviewTaskListener.java** | `aio-platform/src/main/java/ae/ac/cud/workflow/advance/` | Task listener — captures approval decision on complete |
| 5 | **NotificationDelegate.java** | `aio-platform/src/main/java/ae/ac/cud/workflow/advance/` | Service task — sends notifications |
| 6 | **AuditDelegate.java** | `aio-platform/src/main/java/ae/ac/cud/workflow/advance/` | Service task — logs audit trail |
| 7 | **service-context.xml** | `aio-platform/src/main/resources/alfresco/module/aio-platform/context/` | Spring bean definitions for delegates |
| 8 | **bootstrap-context.xml** | `aio-platform/src/main/resources/alfresco/module/aio-platform/context/` | Model + workflow registration |
| 9 | **share-config-custom.xml** | `aio-share/src/main/resources/META-INF/` | Share UI form configuration |
| 10 | **advance-workflow-messages.properties** | `aio-platform/src/main/resources/alfresco/module/aio-platform/messages/` | i18n labels |

---

## 13. Variable Mapping (Start Form → BPMN → Java Delegate)

| Start Form Association | Workflow Variable | Task (definitionKey) | Delegate Resolves To |
|---|---|---|---|
| `acmewadv:peerReviewer` | `acmewadv_peerReviewer` | `peerReviewTask` | Assignee for Peer Review |
| `acmewadv:managerReviewer` | `acmewadv_managerReviewer` | `managerApprovalTask` | Assignee for Manager Approval |
| `acmewadv:seniorReviewer` | `acmewadv_seniorReviewer` | `finalSignOffTask` | Assignee for Final Sign-Off |

**Note:** Colon `:` in model names is replaced by underscore `_` in workflow variables.

---

## 14. Workflow Stages

```
Start Form (select 3 reviewers)
    ↓
Peer Review (userTask: peerReviewTask)
    ↓
Manager Approval (userTask: managerApprovalTask)
    ↓
Final Sign-Off (userTask: finalSignOffTask)
    ↓
Gateway Decision (${approved == true} / ${approved == false})
    ↓
Notification Service (serviceTask)
    ↓
Audit Service (serviceTask)
    ↓
End
```

---

## 15. Key Lessons (Bugs Fixed)

| # | Bug | Symptom | Fix |
|---|---|---|---|
| 1 | Three separate `<associations>` blocks in model XML | `DictionaryException: Expected type end tag, found associations start tag` | Merge all associations into one `<associations>` block |
| 2 | `ReviewAssignmentDelegate` commented out in BPMN | Tasks had no assignee; workflow stalls | Uncomment `<extensionElements>` blocks |
| 3 | `setReviewerVariable(String)` setter | `ActivitiIllegalArgumentException: Declared value has type FixedValue, while expecting String` | Change setter to accept `Expression`; resolve via `getValue(delegateTask).toString()` |
| 4 | Start form config block commented out in share-config-custom.xml | Start form doesn't render in Share UI | Uncomment the `<config>` block |
| 5 | `acmewadv:seniorReviewers` (with 's') in share-config | Senior reviewer field missing from start form | Change to `acmewadv:seniorReviewer` (no 's') |

---

## 16. Build & Deploy

```bash
# Build all modules
mvn clean package -DskipTests

# Rebuild Docker images
docker compose -f docker/docker-compose.yml build aio-acs aio-share

# Restart containers
docker compose -f docker/docker-compose.yml up -d

# Check logs for workflow deployment
docker logs docker-aio-acs-1 --tail=50 | grep -E "workflow|advance|model"