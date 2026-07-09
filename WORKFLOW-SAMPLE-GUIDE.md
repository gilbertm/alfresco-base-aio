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
│   └── sample-process.bpmn20.xml               # BPMN 2.0 process definition (Activiti engine)
└── messages/
    └── workflow-messages.properties            # i18n labels for the workflow
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
<beans>
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
- Deploys the BPMN 2.0 workflow definition to the Activiti engine.
- Associates i18n message bundles with both the models and the workflow.

**Structure:**
```xml
<beans>
    <!-- 1. Model Registration -->
    <bean id="aio-platform.dictionaryBootstrap" parent="dictionaryModelBootstrap" depends-on="dictionaryBootstrap">
        <property name="models">
            <list>
                <value>alfresco/module/${project.artifactId}/model/content-model.xml</value>
                <value>alfresco/module/${project.artifactId}/model/workflow-model.xml</value>
            </list>
        </property>
        <property name="labels">
            <list>
                <value>alfresco/module/${project.artifactId}/messages/content-model</value>
            </list>
        </property>
    </bean>

    <!-- 2. Workflow Deployment -->
    <bean id="ae.ac.cud.sampleprocess.workflowBootstrap" parent="workflowDeployer">
        <property name="workflowDefinitions">
            <list>
                <props>
                    <prop key="engineId">activiti</prop>
                    <prop key="location">alfresco/module/${project.artifactId}/workflow/sample-process.bpmn20.xml</prop>
                    <prop key="mimetype">text/xml</prop>
                </props>
            </list>
        </property>
        <property name="labels">
            <list>
                <value>alfresco/module/${project.artifactId}/messages/workflow-messages</value>
            </list>
        </property>
    </bean>
</beans>
```

**Key Details:**

| Bean ID | Parent Bean | Role |
|---|---|---|
| `aio-platform.dictionaryBootstrap` | `dictionaryModelBootstrap` | Registers content-model.xml and workflow-model.xml in the Alfresco dictionary |
| `ae.ac.cud.sampleprocess.workflowBootstrap` | `workflowDeployer` | Deploys the BPMN file to the Activiti engine on startup |

**Connectivity:**
- `dictionaryBootstrap` references:
  - `model/content-model.xml` — Registers content types/aspects used by the workflow (e.g., `wf:submitAdhocTask`, `wf:adhocTask`)
  - `model/workflow-model.xml` — Registers workflow-specific custom types, aspects, and properties
  - `messages/content-model` — i18n labels for the content model
- `workflowDeployer` references:
  - `workflow/sample-process.bpmn20.xml` — The BPMN process definition to deploy
  - `messages/workflow-messages` — i18n labels for the workflow title and description

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

### 2.4 `sample-process.bpmn20.xml` (BPMN 2.0 Process Definition)

**Path:** `aio-platform/src/main/resources/alfresco/module/aio-platform/workflow/sample-process.bpmn20.xml`

**Purpose:**
- Defines the actual workflow process using BPMN 2.0 standard notation.
- Executed by the Activiti workflow engine embedded in Alfresco.
- Specifies the process flow: start event → user task(s) → end event.

**Structure:**
```xml
<definitions xmlns:activiti="http://activiti.org/bpmn" ...>
    <process id="my-process">
        <startEvent id="start" activiti:formKey="wf:submitAdhocTask"/>
        <sequenceFlow id="flow1" sourceRef="start" targetRef="someTask"/>

        <userTask id="someTask" name="Activiti is awesome!" activiti:formKey="wf:adhocTask">
            <humanPerformer>
                <resourceAssignmentExpression>
                    <formalExpression>${bpm_assignee.properties.userName}</formalExpression>
                </resourceAssignmentExpression>
            </humanPerformer>
        </userTask>
        <sequenceFlow id="flow2" sourceRef="someTask" targetRef="end"/>

        <endEvent id="end"/>
    </process>
</definitions>
```

**Key Elements:**

| Element | ID | Role |
|---|---|---|
| `process` | `my-process` | Unique process identifier; used as the workflow definition key in Alfresco |
| `startEvent` | `start` | Entry point; `activiti:formKey="wf:submitAdhocTask"` references a standard Alfresco start task form |
| `userTask` | `someTask` | Human task; `activiti:formKey="wf:adhocTask"` references a standard Alfresco ad-hoc task form |
| `humanPerformer` | — | Assigns the task to the workflow initiator via `${bpm_assignee.properties.userName}` |
| `endEvent` | `end` | Terminates the process |

**Connectivity:**
- Deployed by `workflowDeployer` in `bootstrap-context.xml` (via `engineId=activiti`, `location=.../sample-process.bpmn20.xml`)
- Process ID `my-process` is used as the key for i18n messages: `my-process.workflow.title`, `my-process.workflow.description`
- `activiti:formKey` values (`wf:submitAdhocTask`, `wf:adhocTask`) reference form types defined in Alfresco's standard content model (`content-model.xml` registered by the sibling `dictionaryBootstrap` bean)
- Task assignment expression `${bpm_assignee.properties.userName}` references the `bpm:assignee` aspect from the imported BPM model

---

### 2.5 `workflow-messages.properties` (i18n Labels)

**Path:** `aio-platform/src/main/resources/alfresco/module/aio-platform/messages/workflow-messages.properties`

**Purpose:**
- Provides human-readable titles and descriptions for the workflow in the Alfresco Share UI (e.g., "Start Workflow" dropdown, task list).
- Follows the naming convention: `{process-id}.workflow.title` and `{process-id}.workflow.description`.

**Structure:**
```properties
my-process.workflow.title=My Process
my-process.workflow.description=This is my custom process
```

**Key Details:**

| Property | Value | Used In |
|---|---|---|
| `my-process.workflow.title` | `My Process` | Share UI when selecting a workflow to start |
| `my-process.workflow.description` | `This is my custom process` | Share UI tooltip/description for the workflow |

**Connectivity:**
- Referenced by `workflowDeployer.labels` in `bootstrap-context.xml`
- The prefix `my-process` must match the `process.id` attribute in `sample-process.bpmn20.xml`
- Localized variants can be added (e.g., `workflow-messages_de.properties` for German)

---

### 2.6 `service-context.xml` (Custom Service Beans)

**Path:** `aio-platform/src/main/resources/alfresco/module/aio-platform/context/service-context.xml`

**Purpose:**
- Placeholder for custom Java beans used by the workflow, such as:
  - **JavaDelegate** implementations (called by `activiti:class` in BPMN service tasks)
  - **TaskListener** implementations (called by `activiti:taskListener` in BPMN user tasks)
  - **ExecutionListener** implementations (called by `activiti:executionListener` on any BPMN element)

**Current State:**
```xml
<beans>
    <!-- Empty bean definitions — add your service beans here -->
</beans>
```

**Connectivity:**
- Imported by `module-context.xml` after `bootstrap-context.xml` (ensures models are registered before instantiation)
- Beans defined here can be referenced from the BPMN 2.0 file using `activiti:delegateExpression="${myBean}"` or `activiti:expression="${myBean.doSomething()}"`

---

## 3. Data Flow Summary

The following diagram shows how a workflow request flows through the system:

```
User starts workflow in Share UI
        │
        ▼
Alfresco Workflow Engine (Activiti)
        │
        ├── Loads process definition from:
        │   sample-process.bpmn20.xml (deployed at startup)
        │
        ├── Resolves form keys against:
        │   content-model.xml (registered via dictionaryBootstrap)
        │
        ├── Displays workflow labels from:
        │   workflow-messages.properties (loaded via workflowDeployer.labels)
        │
        └── If custom Java code is needed:
            service-context.xml → JavaDelegate / TaskListener beans
```

---

## 4. Sequence of Operations at Startup

| Step | File | What Happens |
|---|---|---|
| 1 | `module-context.xml` | Alfresco loads the module's Spring context; imports `bootstrap-context.xml` first |
| 2 | `bootstrap-context.xml` — `dictionaryBootstrap` | Registers `content-model.xml` and `workflow-model.xml` in the dictionary |
| 3 | `bootstrap-context.xml` — `workflowDeployer` | Deploys `sample-process.bpmn20.xml` to Activiti engine; loads `workflow-messages.properties` |
| 4 | `module-context.xml` | Imports `service-context.xml` (custom beans are now safe to instantiate) |
| 5 | Share UI | Workflow "My Process" is visible in the Start Workflow dialog |

---

## 5. Adding More Complexity — Extension Points

To extend this sample into a more complex workflow, modify or add to these files:

| What You Want to Do | File(s) to Modify/Create |
|---|---|
| Add more tasks, gateways, or service tasks | `workflow/sample-process.bpmn20.xml` (or create a new `.bpmn20.xml` + register in `bootstrap-context.xml`) |
| Add custom workflow properties (e.g., priority, department) | `model/workflow-model.xml` (add `<type>` or `<aspect>` definitions) |
| Add custom start task forms or task forms | `content-model.xml` (add new `wf:` types) + create FreeMarker form templates |
| Add Java delegate logic (service tasks) | `service-context.xml` (register bean) + implement `org.activiti.engine.delegate.JavaDelegate` |
| Add task/execution listeners | `service-context.xml` (register bean) + implement listener interface + reference in BPMN |
| Add multi-language support | `workflow-messages.properties` (create locale-specific variants: `_de.properties`, `_fr.properties`, etc.) |
| Add a new workflow definition | Copy `sample-process.bpmn20.xml` + add a new `<props>` block in `workflowDeployer` bean + add new messages |

---

## 6. Deployment Notes

- All files under `aio-platform/src/main/resources/` are packaged into the JAR (or AMP) and placed on the classpath.
- The `${project.artifactId}` Maven variable is replaced during the `resources` phase by Maven filtering. Ensure the `pom.xml` has `<filtering>true</filtering>` on the resources directory.
- After modifying any workflow file, rebuild the module with `mvn clean install -pl aio-platform -am` and restart Alfresco.
- Workflow changes (especially BPMN model changes) require a fresh deployment — existing running process instances of the old definition are unaffected, but new instances will use the updated definition.

---

## 7. File Reference Table

| # | File | Relative Path (from `aio-platform/src/main/resources/alfresco/module/aio-platform/`) | Role |
|---|---|---|---|
| 1 | **module-context.xml** | `module-context.xml` | Spring entry point; imports all context files |
| 2 | **bootstrap-context.xml** | `context/bootstrap-context.xml` | Registers models and deploys workflow definition |
| 3 | **workflow-model.xml** | `model/workflow-model.xml` | Custom workflow types, aspects, properties |
| 4 | **sample-process.bpmn20.xml** | `workflow/sample-process.bpmn20.xml` | BPMN 2.0 process definition |
| 5 | **workflow-messages.properties** | `messages/workflow-messages.properties` | i18n labels for the workflow |
| 6 | **service-context.xml** | `context/service-context.xml` | Custom Java delegate and listener beans |
| 7 | **content-model.xml** (shared) | `model/content-model.xml` | Standard content model defining form types like `wf:submitAdhocTask`, `wf:adhocTask` |
| 8 | **content-model.properties** (shared) | `messages/content-model.properties` | i18n labels for the content model |

---

*Generated from the aio-platform sample workflow. Use this guide as a reference for building more complex Alfresco workflow customizations.*