# Alfresco Workflow Customization — Advanced Guide

> **Affected / Needed Files:**
>
> | # | File | Path (from project root) | Role |
> |---|---|---|---|
> | 1 | `advance-review-process.bpmn20.xml` | `aio-platform/src/main/resources/alfresco/module/aio-platform/workflow/` | BPMN 2.0 process definition |
> | 2 | `advance-workflow-model.xml` | `aio-platform/src/main/resources/alfresco/module/aio-platform/model/` | Custom content model (types & associations) |
> | 3 | `ReviewAssignmentDelegate.java` | `aio-platform/src/main/java/ae/ac/cud/workflow/advance/` | Task listener — resolves reviewer variable → assignee |
> | 4 | `AdvanceReviewTaskListener.java` | `aio-platform/src/main/java/ae/ac/cud/workflow/advance/` | Task listener — captures approval decision on complete |
> | 5 | `NotificationDelegate.java` | `aio-platform/src/main/java/ae/ac/cud/workflow/advance/` | Service task — sends notifications |
> | 6 | `AuditDelegate.java` | `aio-platform/src/main/java/ae/ac/cud/workflow/advance/` | Service task — logs audit trail |
> | 7 | `service-context.xml` | `aio-platform/src/main/resources/alfresco/module/aio-platform/context/` | Spring bean definitions for all delegates |
> | 8 | `bootstrap-context.xml` | `aio-platform/src/main/resources/alfresco/module/aio-platform/context/` | Model + workflow registration |
> | 9 | `share-config-custom.xml` | `aio-share/src/main/resources/META-INF/` | Share UI form configuration (start form + task form) |
> | 10 | `advance-workflow-messages.properties` | `aio-platform/src/main/resources/alfresco/module/aio-platform/messages/` | i18n labels |

---

This document builds on the patterns established in `WORKFLOW-SAMPLE-GUIDE.md` and presents a **strategic architecture** for building a production-grade multi-stage review and approval workflow. It covers the design decisions, component breakdown, and implementation strategy for a workflow that supports sequential reviews, conditional routing, escalation timers, email notifications, and audit logging.

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
─────┌──────────────────────────────────────────────────────────────────────┐
│                     BPMN 2.0 Process Definition                           │
│                    (advance-review-process.bpmn20.xml)                    │
│                                                                           │
│  Start → Peer Review → Manager Approval →                                 │
│  Final Sign-Off → Notification Service → Audit Service → End              │
│                                                                           │
│  Start form captures all three reviewers via associations:                │
│    • acmewadv:peerReviewer — Peer Reviewer (cm:person)                    │
│    • acmewadv:managerReviewer — Manager Reviewer (cm:person)              │
│    • acmewadv:seniorReviewer — Senior / Final Reviewer (cm:person)        │
│  Each user task has:                                                      │
│    • activiti:taskListener (create event → ReviewAssignmentDelegate)      │
│    • activiti:taskListener (complete event → AdvanceReviewTaskListener)   │
│    • Boundary timer event for escalation (future phase)                   │
└───────────────────────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌──────────────────────┐  ┌─────────────────┐  ┌──────────────────────┐
│  Java Delegates      │  │ Task Listeners  │  │ExecutionListeners    │
│  (business logic)    │  │ (UI hooks)      │  │(audit/logging)       │
└──────────────────────┘  └─────────────────┘  └──────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Service Layer                         │
│  • WorkflowNotificationService (email + in-app)                 │
│  • PeopleService (resolve manager, group members)               │
│  • NodeService (access document properties)                     │
│  • AuthorityService (role/group resolution)                     │
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
| `peerReviewTask` | `userTask` | Single-instance task assigned via `ReviewAssignmentDelegate` reading `acmewadv_peerReviewer` |
| `managerApprovalTask` | `userTask` | Sequential task assigned via `ReviewAssignmentDelegate` reading `acmewadv_managerReviewer` |
| `finalSignOffTask` | `userTask` | Sequential task assigned via `ReviewAssignmentDelegate` reading `acmewadv_seniorReviewer`; approval publishes the document as an un-editable final copy |
| `exclusiveGateway` | `gateway` | Routes based on approval decision (`approved == true` / `approved == false`) |
| `notificationService` | `serviceTask` | Invokes `NotificationDelegate` to send email + in-app notification |
| `auditService` | `serviceTask` | Invokes `AuditDelegate` to log the final decision to the audit trail |

**Why `ReviewAssignmentDelegate` instead of EL expressions?**

Custom `d:noderef` associations on the start form are stored as raw node reference strings (e.g., `workspace://SpacesStore/uuid`), not as `ActivitiScriptNode` objects. This means EL expressions like `${acmewadv_peerReviewer.properties.userName}` do **not** resolve. The `ReviewAssignmentDelegate` task listener reads the variable and programmatically sets the assignee on each user task.

**Actual BPMN structure (abbreviated):**

```xml
<process id="advance-review-process" name="Advanced Review and Approval">

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
```

### 3.2 Custom Workflow Content Model

**File:** `aio-platform/src/main/resources/alfresco/module/aio-platform/model/advance-workflow-model.xml`

**Purpose:** Define custom types and associations for the advanced workflow's start form and task forms.

The start form type `acmewadv:advanceReviewStart` defines three **associations** (not properties) targeting `cm:person` — one for each reviewer — so the workflow initiator can assign all three reviewers directly on the start form. Associations are used instead of `d:noderef` properties because they render the standard Alfresco authority/person selector control in the Share UI via the `association.ftl` control template.

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
        <!-- Start form type: captures all three reviewers + deadline + escalation timeout -->
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

        <!-- Task form type: captures review comment + approval decision -->
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

1. The start form type `acmewadv:advanceReviewStart` defines three **associations** targeting `cm:person`:
   - `acmewadv:peerReviewer` — assigned to the first (peer review) stage
   - `acmewadv:managerReviewer` — assigned to the second (manager approval) stage
   - `acmewadv:seniorReviewer` — assigned to the third (final sign-off) stage

   Each association renders the standard Alfresco authority/person selector control in the Share UI via the `association.ftl` control template (configured in `share-config-custom.xml`).

2. When the workflow starts, the persons selected in each authority control are stored as workflow variables with the colon `:` replaced by underscore `_`:
   - `acmewadv:peerReviewer` → `acmewadv_peerReviewer`
   - `acmewadv:managerReviewer` → `acmewadv_managerReviewer`
   - `acmewadv:seniorReviewer` → `acmewadv_seniorReviewer`

3. The values are stored as **raw node reference strings** (e.g., `workspace://SpacesStore/uuid`), not as `ActivitiScriptNode` objects. Therefore, EL expressions like `${acmewadv_peerReviewer.properties.userName}` do **not** resolve.

4. Instead, each user task uses a `ReviewAssignmentDelegate` task listener (on the `create` event) that reads the appropriate workflow variable and programmatically resolves the assignee. The delegate determines which variable to read based on the task definition key:
   - `peerReviewTask` → reads `acmewadv_peerReviewer`
   - `managerApprovalTask` → reads `acmewadv_managerReviewer`
   - `finalSignOffTask` → reads `acmewadv_seniorReviewer`

5. The Share form configuration (see `share-config-custom.xml`) maps the `association.ftl` control to each association field, ensuring the person selectors render properly on the start form.

### 3.3 Share Form Configuration

**File:** `aio-share/src/main/resources/META-INF/share-config-custom.xml`

The Share UI requires form configurations for both the start form and the task form. These are defined in `share-config-custom.xml` using `<config>` blocks keyed by the form key.

**Start form (`acmewadv:advanceReviewStart`):**

```xml
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

**Task form (`acmewadv:advanceReviewTask`):**

```xml
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
```

**Key point:** The reviewer fields use `association.ftl` (not `authority.ftl`) because they are defined as **associations** targeting `cm:person` in the content model, not as `d:noderef` properties.

### 3.4 Java Delegates & Task Listeners

All delegates live under `aio-platform/src/main/java/ae/ac/cud/workflow/advance/`.

#### 3.4.1 `ReviewAssignmentDelegate.java` (Task Listener)

**Purpose:** Resolves custom `d:noderef` workflow variables (stored as raw node reference strings) to actual usernames and sets the task assignee. This is the **critical** delegate that makes task assignment work — without it, tasks would have no assignee because EL expressions like `${acmewadv_peerReviewer.properties.userName}` cannot resolve raw node reference strings.

**How it works:**
1. Fires on the `create` event of each user task
2. Determines which workflow variable to read based on the task definition key (`peerReviewTask` → `acmewadv_peerReviewer`, `managerApprovalTask` → `acmewadv_managerReviewer`, `finalSignOffTask` → `acmewadv_seniorReviewer`)
3. Reads the variable value (a node reference string like `workspace://SpacesStore/uuid`)
4. Resolves it to a username (via reflection if it's an `ActivitiScriptNode`, or falls back to `toString()`)
5. Calls `delegateTask.setAssignee(assignee)` to assign the task

```java
package ae.ac.cud.workflow.advance;

import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.TaskListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Task listener that resolves custom d:noderef workflow variables to assignees.
 *
 * Custom d:noderef properties on the start form are stored as raw node reference strings
 * (e.g., "workspace://SpacesStore/uuid"), not as ActivitiScriptNode objects.
 * This listener resolves them to the actual username and sets the task assignee.
 *
 * Usage in BPMN:
 *   <activiti:taskListener event="create"
 *       class="ae.ac.cud.workflow.advance.ReviewAssignmentDelegate" />
 */
public class ReviewAssignmentDelegate implements TaskListener {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewAssignmentDelegate.class);

    private Expression reviewerVariable;

    @Override
    public void notify(DelegateTask delegateTask) {
        String varName = (reviewerVariable != null)
                ? reviewerVariable.getValue(delegateTask).toString()
                : getDefaultVariable(delegateTask);

        Object reviewerValue = delegateTask.getVariable(varName);
        LOG.info("ReviewAssignmentDelegate: task='{}' variable='{}' value='{}'",
                delegateTask.getId(), varName, reviewerValue);

        if (reviewerValue == null) {
            LOG.warn("ReviewAssignmentDelegate: workflow variable '{}' is null, cannot assign task", varName);
            return;
        }

        String assignee = null;

        if (reviewerValue instanceof String) {
            // Raw node reference string — in production, inject PeopleService to resolve
            LOG.warn("ReviewAssignmentDelegate: raw node reference '{}' cannot be resolved without PeopleService. " +
                     "Falling back to using the value as-is.", reviewerValue);
            assignee = reviewerValue.toString();
        } else {
            // ActivitiScriptNode — access .properties.userName via reflection
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

#### 3.4.2 `AdvanceReviewTaskListener.java` (Task Listener)

**Purpose:** Task listener that fires on task creation and completion to log transitions and capture the approval decision. On the `complete` event, it reads the `acmewadv_approved` task variable and promotes it to a process-level `approved` variable so the gateway can evaluate it.

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

#### 3.4.3 `NotificationDelegate.java` (Service Task)

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

#### 3.4.4 `AuditDelegate.java` (Service Task)

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

### 3.5 Service Context Configuration

**File:** `aio-platform/src/main/resources/alfresco/module/aio-platform/context/service-context.xml`

All delegates and task listeners must be registered as Spring beans:

```xml
<!-- Advanced workflow delegates -->
<bean id="advanceNotificationDelegate"
      class="ae.ac.cud.workflow.advance.NotificationDelegate" />

<bean id="advanceAuditDelegate"
      class="ae.ac.cud.workflow.advance.AuditDelegate" />

<bean id="advanceReviewTaskListener"
      class="ae.ac.cud.workflow.advance.AdvanceReviewTaskListener" />

<bean id="reviewAssignmentDelegate"
      class="ae.ac.cud.workflow.advance.ReviewAssignmentDelegate" />
```

**Note:** The BPMN references `AdvanceReviewTaskListener` via `activiti:taskListener="${advanceReviewTaskListener}"` (delegate expression), while `ReviewAssignmentDelegate`, `NotificationDelegate`, and `AuditDelegate` are referenced via `activiti:class` (fully qualified class name). The delegate expression approach requires the bean to be registered in Spring; the `activiti:class` approach instantiates the class directly.

### 3.6 Bootstrap Configuration

**File:** `aio-platform/src/main/resources/alfresco/module/aio-platform/context/bootstrap-context.xml`

The content model is registered in the `dictionaryBootstrap` bean, and the workflow definition is registered in the `workflowDeployer` bean alongside other workflows:

**Model registration (inside `dictionaryBootstrap`):**
```xml
<value>alfresco/module/${project.artifactId}/model/advance-workflow-model.xml</value>
```

**Workflow registration (inside `workflowDeployer` → `workflowDefinitions` list):**
```xml
<props>
    <prop key="engineId">activiti</prop>
    <prop key="location">alfresco/module/${project.artifactId}/workflow/advance-review-process.bpmn20.xml</prop>
    <prop key="mimetype">text/xml</prop>
</props>
```

**Messages label registration (inside `workflowDeployer` → `labels` list):**
```xml
<value>alfresco/module/${project.artifactId}/messages/advance-workflow-messages</value>
```

### 3.7 i18n Messages

**File:** `aio-platform/src/main/resources/alfresco/module/aio-platform/messages/advance-workflow-messages.properties`

```properties
advance-review-process.workflow.title=Advanced Review & Approval
advance-review-process.workflow.description=Multi-stage review with parallel peer review, manager approval, and final sign-off (publishes document as an un-editable final copy)
acmewadv_seniorReviewTask.bpm_assignee.title=Senior Reviewer
```

---

## 4. Variable Mapping (Start Form → BPMN → Java Delegate)

| Start Form Association | Workflow Variable | Task (`definitionKey`) | Delegate Resolves To |
|---|---|---|---|
| `acmewadv:peerReviewer` | `acmewadv_peerReviewer` | `peerReviewTask` | Assignee for Peer Review |
| `acmewadv:managerReviewer` | `acmewadv_managerReviewer` | `managerApprovalTask` | Assignee for Manager Approval |
| `acmewadv:seniorReviewer` | `acmewadv_seniorReviewer` | `finalSignOffTask` | Assignee for Final Sign-Off |

**Note:** Colon `:` in model names is replaced by underscore `_` in workflow variables.

---

## 5. Workflow Stages (End-to-End Flow)

```
Start Form (select 3 reviewers via association.ftl controls)
    ↓
Peer Review (userTask: peerReviewTask)
    │  ReviewAssignmentDelegate reads acmewadv_peerReviewer → sets assignee
    │  AdvanceReviewTaskListener captures acmewadv_approved → process variable "approved"
    ↓
Manager Approval (userTask: managerApprovalTask)
    │  ReviewAssignmentDelegate reads acmewadv_managerReviewer → sets assignee
    │  AdvanceReviewTaskListener captures acmewadv_approved → process variable "approved"
    ↓
Final Sign-Off (userTask: finalSignOffTask)
    │  ReviewAssignmentDelegate reads acmewadv_seniorReviewer → sets assignee
    │  AdvanceReviewTaskListener captures acmewadv_approved → process variable "approved"
    ↓
Gateway Decision (${approved == true} / ${approved == false})
    ↓
Notification Service (serviceTask: NotificationDelegate)
    ↓
Audit Service (serviceTask: AuditDelegate)
    ↓
End
```

---

## 6. Key Design Decisions

### 6.1 Associations vs. Properties for Reviewer Selection

The three reviewers (`peerReviewer`, `managerReviewer`, `seniorReviewer`) are defined as **associations** targeting `cm:person` rather than `d:noderef` properties. This is because:

- Associations targeting `cm:person` render the standard Alfresco authority/person picker control via `association.ftl` in Share
- `d:noderef` properties would require a custom control or the `authority.ftl` control, which has different behavior
- Associations enforce referential integrity (the target must be a `cm:person` node)

### 6.2 Task Assignment Strategy: `ReviewAssignmentDelegate` vs. EL Expressions

| Approach | How It Works | Limitation |
|---|---|---|
| **EL Expression** (e.g., `${acmewadv_peerReviewer.properties.userName}`) | Resolves the variable as an `ActivitiScriptNode` and accesses its `.properties.userName` | Only works if the variable is stored as an `ActivitiScriptNode` object. Custom `d:noderef` associations are stored as raw node reference strings, so this fails. |
| **`ReviewAssignmentDelegate`** (task listener on `create` event) | Reads the raw variable, resolves it to a username programmatically, and calls `setAssignee()` | Requires a Java delegate, but works reliably regardless of how the variable is stored. |

**This workflow uses `ReviewAssignmentDelegate`** because the custom associations are stored as raw node reference strings.

### 6.3 Expression Language Best Practices

- Custom associations of type `cm:person` on the start form are stored as workflow variables with the colon `:` replaced by underscore `_`. For example:
  - `acmewadv:peerReviewer` → `acmewadv_peerReviewer`
  - `acmewadv:managerReviewer` → `acmewadv_managerReviewer`
  - `acmewadv:seniorReviewer` → `acmewadv_seniorReviewer`
- These variables contain **raw node reference strings** (e.g., `workspace://SpacesStore/uuid`), not `ActivitiScriptNode` objects. Do not attempt to use `.properties.userName` on them in EL expressions.
- Use `ReviewAssignmentDelegate` (or a similar task listener) to resolve the assignee programmatically.
- For conditional routing, use simple boolean variables (e.g., `${approved == true}`) rather than complex expressions in the BPMN.
- The `AdvanceReviewTaskListener` promotes the task-local `acmewadv_approved` variable to a process-level `approved` variable on task completion, making it available to the gateway.

### 6.4 Logging Configuration

Add the following to `dev-log4j2.properties` to see delegate output:

```properties
logger.ae-ac-cud-workflow.name=ae.ac.cud.workflow
logger.ae-ac-cud-workflow.level=info
```

---

## 7. Testing Strategy

### 7.1 Unit Testing Delegates

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

### 7.2 Integration Testing

Use the Alfresco integration test framework to start a process instance via the REST API and verify:

- Task is created and assigned to the correct user (via `ReviewAssignmentDelegate`)
- Task completion triggers the next stage
- `AdvanceReviewTaskListener` promotes `acmewadv_approved` to process-level `approved`
- Service tasks (`NotificationDelegate`, `AuditDelegate`) execute without errors
- Workflow variables are set correctly

---

## 8. Quick-Start Checklist

- [ ] **1. Model** — Create `model/advance-workflow-model.xml` with custom start type (associations for reviewers) and task type (review comment + approved boolean)
- [ ] **2. BPMN** — Create `workflow/advance-review-process.bpmn20.xml` with three user tasks, exclusive gateway, and two service tasks
- [ ] **3. Messages** — Create `messages/advance-workflow-messages.properties` with workflow title, description, and task labels
- [ ] **4. Bootstrap** — Register the new model in `dictionaryBootstrap` and the workflow + messages in `workflowDeployer` inside `bootstrap-context.xml`
- [ ] **5. Delegates** — Create `ReviewAssignmentDelegate.java`, `AdvanceReviewTaskListener.java`, `NotificationDelegate.java`, `AuditDelegate.java`
- [ ] **6. Service Context** — Register all four delegates as beans in `service-context.xml`
- [ ] **7. Share Forms** — Add `<config>` blocks for `acmewadv:advanceReviewStart` (start form) and `acmewadv:advanceReviewTask` (task form) in `share-config-custom.xml`
- [ ] **8. Logging** — Add logger configuration in `dev-log4j2.properties`
- [ ] **9. Build & Deploy** — Run `mvn clean package -DskipTests`, rebuild Docker images, restart containers
- [ ] **10. Test** — Start a workflow instance via Share UI and verify each stage assigns correctly and transitions properly

---

## 9. Build & Deploy

```bash
# Build all modules
mvn clean package -DskipTests

# Rebuild Docker images
docker compose -f docker/docker-compose.yml build aio-acs aio-share

# Restart containers
docker compose -f docker/docker-compose.yml up -d

# Check logs for workflow deployment
docker logs docker-aio-acs-1 --tail=50 | grep -E "workflow|advance|model"
```

---

*This guide provides a strategic architecture for building a production-grade multi-stage review workflow. Each phase builds on the previous one, allowing incremental development and testing.*