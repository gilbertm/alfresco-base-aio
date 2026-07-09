# Alfresco Workflow Customization — Sample Guide

This document describes the files that constitute the sample workflow in the `aio-platform` module, their exact paths, their structures, and how they connect to one another. Use this as a base reference for building more complex workflows.

---

## 1. Project Structure Overview

All workflow-related files reside under the module's classpath root:

```
aio-platform/src/main/resources/alfresco/module/aio-platform/
├── module-context.xml                          # Module entry point (Spring imports)
├── context/
│   ├── bootstrap-context.xml                   # Bootstrapping: model registration & workflow deployment
│   └── service-context.xml                     # Custom service beans (e.g. Java delegates, listeners)
├── model/
│   └── workflow-model.xml                      # Custom workflow content model (types, aspects, properties)
├── workflow/
│   ├── sample-process.bpmn20.xml               # BPMN 2.0 process definition (Activiti engine) — sample
│   └── review-process.bpmn20.xml               # BPMN 2.0 process definition — custom review with JavaDelegate
└── messages/
    ├── workflow-messages.properties            # i18n labels for the sample workflow (my-process)
    └── review-workflow-messages.properties     # i18n labels for the review workflow (review-process)
```

The Java source for the custom delegate lives under `src/main/java`:

```
aio-platform/src/main/java/ae/ac/cud/workflow/
└── ReviewNotificationDelegate.java            # JavaDelegate that logs a "file reviewed" notification
```

The `bootstrap-context.xml` also references the content model definition and its messages:

```
aio-platform/src/main/resources/alfresco/module/aio-platform/
├── model/
│   └── content-model.xml                       # (Referenced alongside workflow-model.xml for shared model)
└── messages/
    └── content-model.properties                # (Referenced alongside workflow-messages.properties)
```

---

## 2. File-by-File Description & Connectivity

### 2.1 `module-context.xml` (Module Entry Point)

**Path:** `aio-platform/src/main/resources/alfresco/module/aio-platform/module-context.xml`

**Purpose:**
- This is the main Spring context file for the module. Alfresco's module system loads this file automatically from the module's classpath.
- It acts as an orchestrator that imports all sub-context files in the correct order.

**Structure:**
```xml
<?xml version='1.0' encoding='UTF-8'?>
<!--
	Licensed to the Apache Software Foundation (ASF) under one or more
	contributor license agreements. 
	The ASF licenses this file to You under the Apache License, Version 2.0
	(the "License"); you may not use this file except in compliance with
	the License.  You may obtain a copy of the License at
	
	http://www.apache.org/licenses/LICENSE-2.0
	
	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
	
-->

<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
          http://www.springframework.org/schema/beans/spring-beans-3.0.xsd">

	<!-- This is filtered by Maven at build time, so that module name is single sourced. -->
	<!-- Note. The bootstrap-context.xml file has to be loaded first.
				Otherwise your custom models are not yet loaded when your service beans are instantiated and you
				cannot for example register policies on them. -->
	<import resource="classpath:alfresco/module/${project.artifactId}/context/bootstrap-context.xml" />
    <import resource="classpath:alfresco/module/${project.artifactId}/context/service-context.xml" />
	<import resource="classpath:alfresco/module/${project.artifactId}/context/webscript-context.xml" />

</beans>
```

**Key Details:**
| Attribute | Value |
|---|---|
| Load order | First (before any other module context file) |
| Maven filtering | `${project.artifactId}` is replaced at build time with `aio-platform` |
| Import order | `bootstrap-context.xml` **must** be imported first so that models and workflows are registered before any service beans that depend on them |

**Connectivity:**
- Imports **bootstrap-context.xml** → triggers model registration and workflow deployment
- Imports **service-context.xml** → loads custom service beans (delegates, listeners)
- Imports **webscript-context.xml** → loads any custom webscripts (not workflow-specific but part of the module)

---

### 2.2 `bootstrap-context.xml` (Bootstrap & Workflow Deployment)

**Path:** `aio-platform/src/main/resources/alfresco/module/aio-platform/context/bootstrap-context.xml`

**Purpose:**
- Registers the custom content models (both content model and workflow model) with the Alfresco dictionary.
- Deploys the BPMN 2.0 workflow definition(s) to the Activiti engine.
- Associates i18n message bundles with both the models and the workflow(s).

**Structure:**
```xml
<?xml version='1.0' encoding='UTF-8'?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
          http://www.springframework.org/schema/beans/spring-beans-3.0.xsd">

    <!-- The bootstrap-context.xml file is used for patch definitions, importers, 
		 workflow, and loading custom content models.  -->

    <!-- Registration of new models -->
    <bean id="aio-platform.dictionaryBootstrap" parent="dictionaryModelBootstrap" depends-on="dictionaryBootstrap">
        <property name="models">
            <list>
                <value>alfresco/module/${project.artifactId}/model/content-model.xml</value>
                <value>alfresco/module/${project.artifactId}/model/workflow-model.xml</value>
            </list>
        </property>
        <property name="labels">
            <list>
                <!-- Bootstrap Resource Bundles for the content model types, aspects, properties etc -->
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
            </list>
        </property>
        <property name="labels">
            <list>
                <value>alfresco/module/${project.artifactId}/messages/workflow-messages</value>
                <value>alfresco/module/${project.artifactId}/messages/review-workflow-messages</value>
            </list>
        </property>
    </bean>
</beans>
```

**Key Details:**

| Bean ID | Parent Bean | Role |
|---|---|---|
| `aio-platform.dictionaryBootstrap` | `dictionaryModelBootstrap` | Registers content-model.xml and workflow-model.xml in the Alfresco dictionary |
| `ae.ac.cud.sampleprocess.workflowBootstrap` | `workflowDeployer` | Deploys all BPMN file(s) to the Activiti engine on startup |

**Connectivity:**
- `dictionaryBootstrap` references:
  - `model/content-model.xml` — Registers content types/aspects used by the workflow (e.g., `wf:submitAdhocTask`, `wf:adhocTask`)
  - `model/workflow-model.xml` — Registers workflow-specific custom types, aspects, and properties
  - `messages/content-model` — i18n labels for the content model
- `workflowDeployer` references:
  - `workflow/sample-process.bpmn20.xml` — The first BPMN process definition to deploy
  - `workflow/review-process.bpmn20.xml` — A second BPMN process definition (custom review workflow)
  - `messages/workflow-messages` — i18n labels for the sample workflow
  - `messages/review-workflow-messages` — i18n labels for the review workflow

> **Important:** The `bootstrap-context.xml` must be imported **before** `service-context.xml` (done in `module-context.xml`). If custom models are not loaded first, service beans that rely on those model types (e.g., policy registrations) will fail at startup.

---

### 2.3 `workflow-model.xml` (Custom Workflow Content Model)

**Path:** `aio-platform/src/main/resources/alfresco/module/aio-platform/model/workflow-model.xml`

**Purpose:**
- Defines a custom namespace and model for workflow-specific types, aspects, and properties.
- Extends the standard Alfresco BPM model (`bpm:`) with custom requirements.

**Structure:**
```xml
<model name="acmew:workflowModel" xmlns="http://www.alfresco.org/model/dictionary/1.0">
    <description>Sample Workflow Model</description>
    <author>My Name</author>
    <version>1.0</version>

    <imports>
        <import uri="http://www.alfresco.org/model/dictionary/1.0" prefix="d"/>
        <import uri="http://www.alfresco.org/model/content/1.0" prefix="cm"/>
        <import uri="http://www.alfresco.org/model/bpm/1.0" prefix="bpm"/>
    </imports>

    <namespaces>
        <namespace uri="http://www.acme.org/model/workflow/1.0" prefix="acmew"/>
    </namespaces>
</model>
```

**Key Details:**

| Element | Value | Purpose |
|---|---|---|
| Model name | `acmew:workflowModel` | Unique identifier for the model |
| Namespace prefix | `acmew` | Shorthand used in forms, types, and BPMN definitions |
| Namespace URI | `http://www.acme.org/model/workflow/1.0` | Full URI matching the namespace |
| Imports | `d`, `cm`, `bpm` | Allows use of `d:text`, `cm:person`, `bpm:assignee`, etc. |

**Connectivity:**
- Loaded by `dictionaryBootstrap` in `bootstrap-context.xml`
- Imports the BPM model (`bpm:`) to reuse workflow standard types like `bpm:assignee`, `bpm:package`, `bpm:workflowTask`
- Defines custom types that can be used as `activiti:formKey` values in the BPMN file (e.g., `acmew:customTask`)
- Any custom properties defined here can be referenced in the BPMN file's form keys or task forms

---

### 2.4 BPMN 2.0 Process Definitions

Two process definitions are deployed side-by-side. The first is a basic sample; the second demonstrates a **service task** invoking a **custom JavaDelegate**.

#### 2.4.1 `sample-process.bpmn20.xml` (Sample)

**Path:** `aio-platform/src/main/resources/alfresco/module/aio-platform/workflow/sample-process.bpmn20.xml`

**Purpose:** A minimal workflow (start → user task → end) that verifies the deployment pipeline works.

**Process ID:** `my-process`

```xml
<process id="my-process">
    <startEvent id="start" activiti:formKey="wf:submitAdhocTask"/>
    <sequenceFlow id="flow1" sourceRef="start" targetRef="someTask"/>
    <userTask id="someTask" name="Activiti is awesome!" activiti:formKey="wf:adhocTask">
        <humanPerformer>
            <resourceAssignmentExpression>
                <formalExpression>${initiator.properties.userName}</formalExpression>
            </resourceAssignmentExpression>
        </humanPerformer>
    </userTask>
    <sequenceFlow id="flow2" sourceRef="someTask" targetRef="end"/>
    <endEvent id="end"/>
</process>
```

#### 2.4.2 `review-process.bpmn20.xml` (Custom Review with JavaDelegate)

**Path:** `aio-platform/src/main/resources/alfresco/module/aio-platform/workflow/review-process.bpmn20.xml`

**Purpose:**
- Demonstrates a full custom workflow with a **service task** that invokes a custom **JavaDelegate** class.
- Flow: start event → review user task → notification service task → end event.

**Process ID:** `review-process`

```xml
<process id="review-process">
    <startEvent id="start" activiti:formKey="wf:submitAdhocTask"/>
    <sequenceFlow id="flow1" sourceRef="start" targetRef="reviewTask"/>

    <userTask id="reviewTask" name="Review Document" activiti:formKey="wf:adhocTask">
        <humanPerformer>
            <resourceAssignmentExpression>
                <formalExpression>${initiator.properties.userName}</formalExpression>
            </resourceAssignmentExpression>
        </humanPerformer>
    </userTask>
    <sequenceFlow id="flow2" sourceRef="reviewTask" targetRef="notificationServiceTask"/>

    <serviceTask id="notificationServiceTask"
                 name="Send Review Notification"
                 activiti:class="ae.ac.cud.workflow.ReviewNotificationDelegate"/>
    <sequenceFlow id="flow3" sourceRef="notificationServiceTask" targetRef="end"/>

    <endEvent id="end"/>
</process>
```

**Key Elements:**

| Element | ID | Role |
|---|---|---|
| `process` | `review-process` | Unique process identifier; used as the workflow definition key in Alfresco |
| `startEvent` | `start` | Entry point; `activiti:formKey="wf:submitAdhocTask"` references a standard Alfresco start task form |
| `userTask` | `reviewTask` | Human review task; `activiti:formKey="wf:adhocTask"` references a standard Alfresco ad-hoc task form |
| `humanPerformer` | — | Assigns the task to the workflow initiator via `${initiator.properties.userName}` |
| `serviceTask` | `notificationServiceTask` | Custom service task that runs `ReviewNotificationDelegate` (JavaDelegate) |
| `endEvent` | `end` | Terminates the process |

**How the service task connects to Java code:**
The `<serviceTask>` uses `activiti:class="ae.ac.cud.workflow.ReviewNotificationDelegate"`. When the Activiti engine reaches this node, it:
1. Instantiates `ReviewNotificationDelegate` (the class must be on the classpath).
2. Calls its `execute(DelegateExecution)` method.
3. The method logs a notification message and stores a workflow variable.

Alternatively, you can use `activiti:delegateExpression="${reviewNotificationDelegate}"` instead of `activiti:class`, which lets you inject Alfresco services (e.g., `NodeService`, `WorkflowNotificationService`) into the delegate via Spring DI. In that case, the bean must be defined in `service-context.xml` (see section 2.6).

---

### 2.5 Properties Files (i18n Labels)

#### 2.5.1 `workflow-messages.properties`

**Path:** `aio-platform/src/main/resources/alfresco/module/aio-platform/messages/workflow-messages.properties`

Labels for the sample `my-process` workflow:
```properties
my-process.workflow.title=My Process
my-process.workflow.description=This is my custom process
```

#### 2.5.2 `review-workflow-messages.properties`

**Path:** `aio-platform/src/main/resources/alfresco/module/aio-platform/messages/review-workflow-messages.properties`

Labels for the custom `review-process` workflow:
```properties
review-process.workflow.title=Review Document
review-process.workflow.description=Review a document and receive a notification when review is complete
```

**Connectivity:**
- Each `.properties` file is referenced by a `<value>` inside `workflowDeployer.labels` in `bootstrap-context.xml`
- The prefix (e.g., `review-process`) must match the `process.id` attribute in the corresponding BPMN file
- Localized variants can be added (e.g., `review-workflow-messages_de.properties` for German)

---

### 2.6 `service-context.xml` (Custom Service Beans)

**Path:** `aio-platform/src/main/resources/alfresco/module/aio-platform/context/service-context.xml`

**Purpose:**
- Provides Spring bean definitions for custom Java classes used by the workflow, such as:
  - **JavaDelegate** implementations (called by `activiti:class` or `activiti:delegateExpression` in BPMN service tasks)
  - **TaskListener** implementations (called by `activiti:taskListener` in BPMN user tasks)
  - **ExecutionListener** implementations (called by `activiti:executionListener` on any BPMN element)

**Current State:**
```xml
<?xml version='1.0' encoding='UTF-8'?>
<!--
    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
          http://www.springframework.org/schema/beans/spring-beans-3.0.xsd">

    <!--
        Custom Java delegate for the review workflow.
        Called by the service task in review-process.bpmn20.xml via activiti:class.
        To use dependency injection, switch to activiti:delegateExpression="${reviewNotificationDelegate}"
        in the BPMN and remove the class attribute.
    -->
    <bean id="reviewNotificationDelegate" class="ae.ac.cud.workflow.ReviewNotificationDelegate" />

</beans>
```

**Connectivity:**
- Imported by `module-context.xml` after `bootstrap-context.xml` (ensures models are registered before instantiation)
- The `reviewNotificationDelegate` bean is declared here but the BPMN currently uses `activiti:class` (direct class instantiation). To use Spring DI, change `activiti:class` to `activiti:delegateExpression="${reviewNotificationDelegate}"` in the BPMN file
- Additional beans (task listeners, execution listeners, other delegates) would also be defined here

---

### 2.7 Custom Java Class — `ReviewNotificationDelegate.java`

**Path:** `aio-platform/src/main/java/ae/ac/cud/workflow/ReviewNotificationDelegate.java`

**Purpose:**
- A `JavaDelegate` that is executed automatically when the service task in the review workflow runs.
- Logs a notification message to the Alfresco log indicating that the file has been reviewed.
- In a production implementation, this could be extended to send an email, trigger an Alfresco notification, or push a message to the Share UI.

**Structure:**
```java
package ae.ac.cud.workflow;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        LOG.info(message);

        // Store a workflow variable so a web script or Share extension
        // can pick it up and display it to the user.
        execution.setVariable("reviewNotification", message);
    }
}
```

**Connectivity:**
- Referenced by `activiti:class="ae.ac.cud.workflow.ReviewNotificationDelegate"` in `review-process.bpmn20.xml`
- Also registered as a Spring bean `reviewNotificationDelegate` in `service-context.xml` (for future use with `delegateExpression`)
- The class is compiled by Maven and packaged into the `aio-platform` JAR
- Access to Alfresco services (e.g., `WorkflowNotificationService`, `NodeService`) can be added via Spring injection if switched to `delegateExpression`

---

## 3. Data Flow Summary

The following diagram shows how a workflow request flows through the system:

```
User starts "Review Document" workflow in Share UI
        │
        ▼
Alfresco Workflow Engine (Activiti)
        │
        ├── Loads process definition from:
        │   review-process.bpmn20.xml (deployed at startup)
        │
        ├── Resolves form keys against:
        │   content-model.xml (registered via dictionaryBootstrap)
        │
        ├── Displays workflow labels from:
        │   review-workflow-messages.properties (loaded via workflowDeployer.labels)
        │
        ├── Step 1: User completes "Review Document" task
        │   (assigned via ${initiator.properties.userName})
        │
        ├── Step 2: Activiti engine reaches the service task:
        │   ┌─────────────────────────────────────────────────────┐
        │   │  notificationServiceTask                            │
        │   │  activiti:class="ae.ac.cud.workflow.                │
        │   │    ReviewNotificationDelegate"                      │
        │   │                                                     │
        │   │  → instantiates ReviewNotificationDelegate          │
        │   │  → calls execute(DelegateExecution)                 │
        │   │  → LOG.info( "[WORKFLOW NOTIFICATION] The file     │
        │   │             has been reviewed by '...'" )           │
        │   └─────────────────────────────────────────────────────┘
        │
        └── Process ends
```

---

## 4. Sequence of Operations at Startup

| Step | File | What Happens |
|---|---|---|
| 1 | `module-context.xml` | Alfresco loads the module's Spring context; imports `bootstrap-context.xml` first |
| 2 | `bootstrap-context.xml` — `dictionaryBootstrap` | Registers `content-model.xml` and `workflow-model.xml` in the dictionary |
| 3 | `bootstrap-context.xml` — `workflowDeployer` | Deploys all BPMN files (`sample-process.bpmn20.xml`, `review-process.bpmn20.xml`) to Activiti engine; loads all message bundles |
| 4 | `module-context.xml` | Imports `service-context.xml` (custom beans are now safe to instantiate) |
| 5 | Share UI | Both workflows are visible in the Start Workflow dialog: "My Process" and "Review Document" |

---

## 5. Adding More Complexity — Extension Points

To extend this review workflow into a more complex workflow, modify or add to these files:

| What You Want to Do | File(s) to Modify/Create |
|---|---|
| Add more tasks, gateways, or service tasks | `workflow/review-process.bpmn20.xml` (or create a new `.bpmn20.xml` + register in `bootstrap-context.xml`) |
| Add custom workflow properties (e.g., priority, department) | `model/workflow-model.xml` (add `<type>` or `<aspect>` definitions) |
| Add custom start task forms or task forms | `content-model.xml` (add new `wf:` types) + create FreeMarker form templates |
| Add Java delegate logic (service tasks) | `service-context.xml` (register bean) + implement `org.activiti.engine.delegate.JavaDelegate` |
| Add task/execution listeners | `service-context.xml` (register bean) + implement listener interface + reference in BPMN |
| Add multi-language support | Create locale-specific `.properties` variants (e.g., `review-workflow-messages_de.properties`) |
| Add a new workflow definition | Copy `review-process.bpmn20.xml` + add a new `<props>` block in `workflowDeployer` bean + add new messages |
| Inject Alfresco services into delegate | Switch from `activiti:class` to `activiti:delegateExpression="${reviewNotificationDelegate}"` in BPMN, use `@Autowired` in the Java class |

---

## 6. Deployment Notes

- All files under `aio-platform/src/main/resources/` are packaged into the JAR (or AMP) and placed on the classpath.
- Java source files under `aio-platform/src/main/java/` are compiled by Maven and packaged into the same JAR.
- The `${project.artifactId}` Maven variable is replaced during the `resources` phase by Maven filtering. Ensure the `pom.xml` has `<filtering>true</filtering>` on the resources directory.
- After modifying any workflow file or Java class, rebuild the module with `mvn clean install -pl aio-platform -am` and restart Alfresco.
- Workflow changes (especially BPMN model changes) require a fresh deployment — existing running process instances of the old definition are unaffected, but new instances will use the updated definition.

---

## 7. File Reference Table

| # | File | Path (from project root) | Role |
|---|---|---|---|
| 1 | **module-context.xml** | `aio-platform/src/main/resources/alfresco/module/aio-platform/module-context.xml` | Spring entry point; imports all context files |
| 2 | **bootstrap-context.xml** | `aio-platform/src/main/resources/alfresco/module/aio-platform/context/bootstrap-context.xml` | Registers models and deploys workflow definitions |
| 3 | **workflow-model.xml** | `aio-platform/src/main/resources/alfresco/module/aio-platform/model/workflow-model.xml` | Custom workflow types, aspects, properties |
| 4a | **sample-process.bpmn20.xml** | `aio-platform/src/main/resources/alfresco/module/aio-platform/workflow/sample-process.bpmn20.xml` | BPMN 2.0 process definition — sample |
| 4b | **review-process.bpmn20.xml** | `aio-platform/src/main/resources/alfresco/module/aio-platform/workflow/review-process.bpmn20.xml` | BPMN 2.0 process definition — custom review with service task |
| 5a | **workflow-messages.properties** | `aio-platform/src/main/resources/alfresco/module/aio-platform/messages/workflow-messages.properties` | i18n labels for the sample workflow |
| 5b | **review-workflow-messages.properties** | `aio-platform/src/main/resources/alfresco/module/aio-platform/messages/review-workflow-messages.properties` | i18n labels for the review workflow |
| 6 | **service-context.xml** | `aio-platform/src/main/resources/alfresco/module/aio-platform/context/service-context.xml` | Custom Java delegate and listener beans |
| 7 | **ReviewNotificationDelegate.java** | `aio-platform/src/main/java/ae/ac/cud/workflow/ReviewNotificationDelegate.java` | JavaDelegate that logs a "file reviewed" notification |
| 8 | **content-model.xml** (shared) | `aio-platform/src/main/resources/alfresco/module/aio-platform/model/content-model.xml` | Content model defining form types like `wf:submitAdhocTask`, `wf:adhocTask` |
| 9 | **content-model.properties** (shared) | `aio-platform/src/main/resources/alfresco/module/aio-platform/messages/content-model.properties` | i18n labels for the content model |

---

## 8. Quick-Start Checklist for a New Custom Workflow

Follow these steps to create a new custom workflow from scratch using the patterns above:

- [ ] **1. BPMN** — Create `workflow/new-process.bpmn20.xml` with a unique `process id`
- [ ] **2. Messages** — Create `messages/new-workflow-messages.properties` with `{process-id}.workflow.title` and `.description`
- [ ] **3. Bootstrap** — Add a new `<props>` block inside `workflowDeployer.workflowDefinitions` in `bootstrap-context.xml`, and add the new messages label
- [ ] **4. Java Delegate (if needed)** — Create a class implementing `JavaDelegate` under `src/main/java/.../workflow/`
- [ ] **5. Service Context (if needed)** — Register the JavaDelegate as a bean in `service-context.xml`
- [ ] **6. BPMN Service Task (if using delegate)** — Add `<serviceTask activiti:class="..."/>` or `activiti:delegateExpression="${...}"` in the BPMN
- [ ] **7. Build & Deploy** — Run `mvn clean install -pl aio-platform -am` and restart Alfresco

---

*Generated from the aio-platform sample and review workflows. Use this guide as a reference for building more complex Alfresco workflow customizations.*