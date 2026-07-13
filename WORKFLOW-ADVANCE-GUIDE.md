# Alfresco Workflow Customization — Advanced Guide

This document builds on the patterns established in `WORKFLOW-SAMPLE-GUIDE.md` and presents a **strategic architecture** for building a production-grade multi-stage review and approval workflow. It covers the design decisions, component breakdown, and implementation strategy for a workflow that supports parallel reviews, conditional routing, escalation timers, email notifications, and audit logging.

---

## 1. Strategic Overview

### 1.1 Business Scenario

A document requires **multi-stage approval** before it can be published:

1. **Peer Review** — Assigned to a specific reviewer selected on the start form.
2. **Manager Approval** — Assigned to a specific manager selected on the start form.
3. **Final Sign-Off** — Assigned to a designated senior reviewer selected on the start form. Once approved, the document is published and becomes an un-editable final copy.
4. **Auto-Escalation** — (Optional) If any stage is not completed within a configurable time limit, the task escalates to the next-level approver.
5. **Notification** — Email and in-app notifications at each stage transition.

### 1.2 Design Goals

| Goal | Approach |
|---|---|
| **Reusability** | Define workflow as a BPMN 2.0 process with configurable parameters (escalation timeout, reviewer pool) |
| **Separation of concerns** | Keep BPMN for flow logic, Java delegates for business logic, Spring beans for service injection |
| **Testability** | Each Java delegate should be unit-testable with mocked `DelegateExecution` |
| **Observability** | Log key events via SLF4J; store audit trail as workflow variables |
| **Extensibility** | New stages can be added by extending the BPMN and adding new delegates without modifying existing ones |

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     BPMN 2.0 Process Definition                  │
│                    (advance-review-process.bpmn20.xml)           │
│                                                                  │
│  Start → Peer Review → Manager Approval →                       │
│  Final Sign-Off → Notification Service → End                    │
│                                                                  │
│  Start form captures all three reviewers:                        │
│    • acmewadv:peerReviewer — Peer Reviewer                       │
│    • acmewadv:managerReviewer — Manager Reviewer                 │
│    • acmewadv:seniorReviewer — Senior / Final Reviewer           │
│  Each user task has:                                             │
│    • activiti:taskListener (pre/post)                            │
│    • activiti:executionListener (start/end)                      │
│    • Boundary timer event for escalation                         │
└─────────────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  Java Delegates  │  │ Task Listeners  │  │ExecutionListeners│
│  (business logic)│  │ (UI hooks)      │  │(audit/logging)   │
└─────────────────┘  └─────────────────┘  └─────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Service Layer                           │
│  • WorkflowNotificationService (email + in-app)                  │
│  • PeopleService (resolve manager, group members)                │
│  • NodeService (access document properties)                      │
│  • AuthorityService (role/group resolution)                      │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Component Breakdown

### 3.1 BPMN Process Definition

**File:** `aio-platform/src/main/resources/alfresco/module/aio-platform/workflow/advance-review-process.bpmn20.xml`

**Process ID:** `advance-review-process`

**Key BPMN Elements:**

| Element | Type | Purpose |
|---|---|---|
| `start` | `startEvent` | Entry point; uses a custom start form `acmewadv:advanceReviewStart` that captures all three reviewers |
| `peerReviewTask` | `userTask` | Single-instance task assigned via `acmewadv:peerReviewer` from start form |
| `managerApprovalTask` | `userTask` | Sequential task assigned via `acmewadv:managerReviewer` from start form |
| `finalSignOffTask` | `userTask` | Sequential task assigned via `acmewadv:seniorReviewer` from start form; approval publishes the document as an un-editable final copy |
| `escalationTimer` | `boundaryEvent` (timer) | Fires if a task is not completed within the configured duration |
| `escalationTask` | `userTask` | Re-assigns the task to the next-level approver |
| `notificationService` | `serviceTask` | Invokes `NotificationDelegate` to send email + in-app notification |
| `auditService` | `serviceTask` | Invokes `AuditDelegate` to log the final decision to the audit trail |
| `exclusiveGateway` | `gateway` | Routes based on approval decision (approved / rejected / needs-changes) |

**BPMN Skeleton:**

```xml
<process id="advance-review-process" name="Advanced Review and Approval">

    <startEvent id="start" activiti:formKey="acmewadv:advanceReviewStart"/>
    <sequenceFlow id="flow1" sourceRef="start" targetRef="peerReviewTask"/>

    <!-- Peer review — assigned via acmewadv:peerReviewer from start form -->
    <userTask id="peerReviewTask" name="Peer Review"
              activiti:formKey="acmewadv:advanceReviewTask"
              activiti:taskListener="${advanceReviewTaskListener}">
        <humanPerformer>
            <resourceAssignmentExpression>
                <formalExpression>${acmewadv_peerReviewer.properties.userName}</formalExpression>
            </resourceAssignmentExpression>
        </humanPerformer>
    </userTask>
    <sequenceFlow id="flow2" sourceRef="peerReviewTask" targetRef="managerApprovalTask"/>

    <!-- Manager approval — assigned via acmewadv:managerReviewer from start form -->
    <userTask id="managerApprovalTask" name="Manager Approval"
              activiti:formKey="acmewadv:advanceReviewTask"
              activiti:taskListener="${advanceReviewTaskListener}">
        <humanPerformer>
            <resourceAssignmentExpression>
                <formalExpression>${acmewadv_managerReviewer.properties.userName}</formalExpression>
            </resourceAssignmentExpression>
        </humanPerformer>
    </userTask>
    <sequenceFlow id="flow3" sourceRef="managerApprovalTask" targetRef="finalSignOffTask"/>

    <!-- Final sign-off — assigned via acmewadv:seniorReviewer from start form -->
    <userTask id="finalSignOffTask" name="Final Sign-Off"
              activiti:formKey="acmewadv:advanceReviewTask"
              activiti:taskListener="${advanceReviewTaskListener}">
        <humanPerformer>
            <resourceAssignmentExpression>
                <formalExpression>${acmewadv_seniorReviewer.properties.userName}</formalExpression>
            </resourceAssignmentExpression>
        </humanPerformer>
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
```

### 3.2 Custom Workflow Content Model

**File:** `aio-platform/src/main/resources/alfresco/module/aio-platform/model/advance-workflow-model.xml`

**Purpose:** Define custom types and aspects for the advanced workflow's start form and task forms. The start form type `acmewadv:advanceReviewStart` defines three `d:noderef` properties — one for each reviewer — so the workflow initiator can assign all three reviewers directly on the start form.

```xml
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
                <property name="acmewadv:peerReviewer">
                    <title>Peer Reviewer</title>
                    <type>d:noderef</type>
                    <mandatory>true</mandatory>
                </property>
                <property name="acmewadv:managerReviewer">
                    <title>Manager Reviewer</title>
                    <type>d:noderef</type>
                    <mandatory>true</mandatory>
                </property>
                <property name="acmewadv:seniorReviewer">
                    <title>Senior Reviewer</title>
                    <type>d:noderef</type>
                    <mandatory>true</mandatory>
                </property>
            </properties>
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

**How the three reviewers are captured and used:**

1. The start form type `acmewadv:advanceReviewStart` defines three `d:noderef` properties:
   - `acmewadv:peerReviewer` — assigned to the first (peer review) stage
   - `acmewadv:managerReviewer` — assigned to the second (manager approval) stage
   - `acmewadv:seniorReviewer` — assigned to the third (final sign-off) stage

   Each property renders the standard Alfresco authority/person selector control in the Share UI, identical to how `bpm:assignee` works.

2. When the workflow starts, the persons selected in each authority control are stored as workflow variables with the colon `:` replaced by underscore `_`:
   - `acmewadv:peerReviewer` → `acmewadv_peerReviewer`
   - `acmewadv:managerReviewer` → `acmewadv_managerReviewer`
   - `acmewadv:seniorReviewer` → `acmewadv_seniorReviewer`

3. The BPMN references these variables in each task assignment:
   - `${acmewadv_peerReviewer.properties.userName}` — Peer Review
   - `${acmewadv_managerReviewer.properties.userName}` — Manager Approval
   - `${acmewadv_seniorReviewer.properties.userName}` — Final Sign-Off

4. The Share form configuration (see `share-config-custom.xml`) maps the authority control to each field, ensuring the person selectors render properly on the start form.

This pattern gives the workflow initiator full control over who reviews at each stage — all from a single start form.

### 3.3 Java Delegates

All delegates live under `aio-platform/src/main/java/ae/ac/cud/workflow/advance/`.

#### 3.3.1 `NotificationDelegate.java`

**Purpose:** Sends email and in-app notifications when the workflow reaches the notification service task.

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

#### 3.3.2 `AuditDelegate.java`

**Purpose:** Logs the final approval decision to an audit trail (workflow variable + external audit log).

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

#### 3.3.3 `AdvanceReviewTaskListener.java`

**Purpose:** Task listener that fires on task creation and completion to log transitions and send notifications.

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

### 3.4 Service Context Configuration

**File:** `aio-platform/src/main/resources/alfresco/module/aio-platform/context/service-context.xml`

Add the following beans:

```xml
<!-- Advanced workflow delegates -->
<bean id="advanceNotificationDelegate"
      class="ae.ac.cud.workflow.advance.NotificationDelegate" />

<bean id="advanceAuditDelegate"
      class="ae.ac.cud.workflow.advance.AuditDelegate" />

<bean id="advanceReviewTaskListener"
      class="ae.ac.cud.workflow.advance.AdvanceReviewTaskListener" />
```

### 3.5 Bootstrap Configuration

**File:** `aio-platform/src/main/resources/alfresco/module/aio-platform/context/bootstrap-context.xml`

Add a new `<props>` block inside `workflowDeployer.workflowDefinitions`:

```xml
<props>
    <prop key="engineId">activiti</prop>
    <prop key="location">alfresco/module/${project.artifactId}/workflow/advance-review-process.bpmn20.xml</prop>
    <prop key="mimetype">text/xml</prop>
</props>
```

And add the messages label:

```xml
<value>alfresco/module/${project.artifactId}/messages/advance-workflow-messages</value>
```

### 3.6 i18n Messages

**File:** `aio-platform/src/main/resources/alfresco/module/aio-platform/messages/advance-workflow-messages.properties`

```properties
advance-review-process.workflow.title=Advanced Review & Approval
advance-review-process.workflow.description=Multi-stage review with peer review, manager approval, and final sign-off (publishes document as an un-editable final copy)
```

---

## 4. Strategic Implementation Roadmap

### Phase 1 — Foundation (Week 1)

| Step | Task | Files |
|---|---|---|
| 1 | Define the BPMN process with all user tasks and gateways | `advance-review-process.bpmn20.xml` |
| 2 | Define the custom content model with start task and task types | `advance-workflow-model.xml` |
| 3 | Create i18n message properties | `advance-workflow-messages.properties` |
| 4 | Register the model and workflow in bootstrap context | `bootstrap-context.xml` |
| 5 | Build and deploy; verify the workflow appears in Share UI | — |

### Phase 2 — Delegates & Listeners (Week 2)

| Step | Task | Files |
|---|---|---|
| 1 | Implement `NotificationDelegate` (logging only initially) | `NotificationDelegate.java` |
| 2 | Implement `AuditDelegate` | `AuditDelegate.java` |
| 3 | Implement `AdvanceReviewTaskListener` | `AdvanceReviewTaskListener.java` |
| 4 | Register all beans in `service-context.xml` | `service-context.xml` |
| 5 | Update BPMN to reference delegates via `activiti:class` and `activiti:taskListener` | `advance-review-process.bpmn20.xml` |
| 6 | Build, deploy, and test end-to-end | — |

### Phase 3 — Escalation & Timers (Week 3)

| Step | Task | Files |
|---|---|---|
| 1 | Add boundary timer events to each user task in the BPMN | `advance-review-process.bpmn20.xml` |
| 2 | Implement `EscalationDelegate` to re-assign tasks | `EscalationDelegate.java` |
| 3 | Configure escalation timeout via workflow variable (`acmewadv:escalationTimeout`) | — |
| 4 | Test timer-triggered escalation | — |

### Phase 4 — Email & In-App Notifications (Week 4)

| Step | Task | Files |
|---|---|---|
| 1 | Inject `org.alfresco.repo.workflow.WorkflowNotificationService` into `NotificationDelegate` | `NotificationDelegate.java` |
| 2 | Switch from `activiti:class` to `activiti:delegateExpression` for DI support | `advance-review-process.bpmn20.xml` |
| 3 | Configure email templates for each notification type | `extension/templates/` |
| 4 | Test email delivery and in-app notification display | — |

### Phase 5 — Audit & Reporting (Week 5)

| Step | Task | Files |
|---|---|---|
| 1 | Extend `AuditDelegate` to write to an external audit store (database or file) | `AuditDelegate.java` |
| 2 | Add workflow variables to capture timestamps, assignees, and decisions at each stage | — |
| 3 | Create a REST API or Web Script to expose audit data | `webscript-context.xml` + custom Web Script |
| 4 | Build a Share dashboard extension to display workflow metrics | Share module |

---

## 5. Key Design Decisions

### 5.1 Multi-Instance vs. Single-Instance Tasks

| Pattern | When to Use |
|---|---|
| **Multi-instance (parallel)** | Peer review where multiple reviewers must act independently |
| **Multi-instance (sequential)** | Chain of approval where each approver sees the previous decision |
| **Single-instance** | Manager approval or final sign-off (publishes document as an un-editable final copy) where only one person acts |

### 5.2 Task Assignment Strategies

| Strategy | BPMN Expression | Use Case |
|---|---|---|
| Custom person selector | `${acmewadv_peerReviewer.properties.userName}` | Assign peer review to a person selected via the start form |
| Custom person selector | `${acmewadv_managerReviewer.properties.userName}` | Assign manager approval to a person selected via the start form |
| Custom person selector | `${acmewadv_seniorReviewer.properties.userName}` | Assign final sign-off to a person selected via the start form |

### 5.3 Expression Language Best Practices

- Custom properties of type `d:noderef` on the start form are stored as workflow variables with the colon `:` replaced by underscore `_`. For example:
  - `acmewadv:peerReviewer` → `acmewadv_peerReviewer`
  - `acmewadv:managerReviewer` → `acmewadv_managerReviewer`
  - `acmewadv:seniorReviewer` → `acmewadv_seniorReviewer`
- Use `${acmewadv_peerReviewer.properties.userName}`, `${acmewadv_managerReviewer.properties.userName}`, and `${acmewadv_seniorReviewer.properties.userName}` to resolve the selected person's username and assign each task.
- When reading person node references in Java delegates, use `Object.toString()` instead of a direct `(String)` cast, because Alfresco stores them as `ActivitiScriptNode`.
- For conditional routing, use simple boolean variables (e.g., `${approved == true}`) rather than complex expressions in the BPMN.

### 5.4 Logging Configuration

Add the following to `dev-log4j2.properties` to see delegate output:

```properties
logger.ae-ac-cud-workflow-advance.name=ae.ac.cud.workflow.advance
logger.ae-ac-cud-workflow-advance.level=info
```

---

## 6. Testing Strategy

### 6.1 Unit Testing Delegates

```java
import org.activiti.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

public class NotificationDelegateTest {

    @Test
    public void testExecute() throws Exception {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("approved")).thenReturn("true");
        when(execution.getVariable("initiator")).thenReturn("admin");
        when(execution.getVariable("bpm_workflowDescription")).thenReturn("Test workflow");
        when(execution.getProcessInstanceId()).thenReturn("12345");

        NotificationDelegate delegate = new NotificationDelegate();
        delegate.execute(execution);

        verify(execution).setVariable(eq("advanceNotification"), anyString());
    }
}
```

### 6.2 Integration Testing

Use the Alfresco integration test framework to start a process instance via the REST API and verify:

- Task is created and assigned to the correct user
- Task completion triggers the next stage
- Service tasks execute without errors
- Workflow variables are set correctly

---

## 7. File Reference Table

| # | File | Path (from project root) | Role |
|---|---|---|---|
| 1 | **advance-review-process.bpmn20.xml** | `aio-platform/src/main/resources/alfresco/module/aio-platform/workflow/advance-review-process.bpmn20.xml` | BPMN 2.0 process definition — advanced review |
| 2 | **advance-workflow-model.xml** | `aio-platform/src/main/resources/alfresco/module/aio-platform/model/advance-workflow-model.xml` | Custom types and aspects for the advanced workflow |
| 3 | **advance-workflow-messages.properties** | `aio-platform/src/main/resources/alfresco/module/aio-platform/messages/advance-workflow-messages.properties` | i18n labels for the advanced workflow |
| 4 | **NotificationDelegate.java** | `aio-platform/src/main/java/ae/ac/cud/workflow/advance/NotificationDelegate.java` | Sends email and in-app notifications |
| 5 | **AuditDelegate.java** | `aio-platform/src/main/java/ae/ac/cud/workflow/advance/AuditDelegate.java` | Logs audit trail for the final decision |
| 6 | **AdvanceReviewTaskListener.java** | `aio-platform/src/main/java/ae/ac/cud/workflow/advance/AdvanceReviewTaskListener.java` | Task lifecycle listener (create, assign, complete) |
| 7 | **EscalationDelegate.java** | `aio-platform/src/main/java/ae/ac/cud/workflow/advance/EscalationDelegate.java` | Handles timer-triggered task escalation |

---

## 8. Quick-Start Checklist

- [ ] **1. BPMN** — Create `workflow/advance-review-process.bpmn20.xml` with the full process definition
- [ ] **2. Model** — Create `model/advance-workflow-model.xml` with custom start and task types
- [ ] **3. Messages** — Create `messages/advance-workflow-messages.properties`
- [ ] **4. Bootstrap** — Register the new model and workflow in `bootstrap-context.xml`
- [ ] **5. Delegates** — Create `NotificationDelegate.java`, `AuditDelegate.java`, `AdvanceReviewTaskListener.java`
- [ ] **6. Service Context** — Register all delegates as beans in `service-context.xml`
- [ ] **7. Logging** — Add logger configuration in `dev-log4j2.properties`
- [ ] **8. Build & Deploy** — Run `mvn clean package` and restart ACS
- [ ] **9. Test** — Start a workflow instance via Share UI or REST API and verify each stage

---

*This guide provides a strategic architecture for building a production-grade multi-stage review workflow. Each phase builds on the previous one, allowing incremental development and testing.*