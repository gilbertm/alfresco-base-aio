# Workflow Advance: Move Files — CUD Document Lifecycle

This document describes the **working** CUD Document Lifecycle workflow implementation, which automatically **moves documents between lifecycle folders** (`{Dept}_Draft` → `{Dept}_Review` → `{Dept}_Published` / back to `{Dept}_Draft`) as the workflow progresses.

The folder-move + lock/unlock logic is consolidated into a single **Activiti TaskListener** (`CudDocumentLifecycleTaskListener`) attached to the review user task's `create` and `complete` events.

---

## 1. Workflow Path

```
                          ┌──────────────────────────────────────────────────┐
                          │          AUTHOR (document owner)                 │
                          │  uploads document into {Dept}_Draft folder       │
                          └───────────────────────┬──────────────────────────┘
                                                  │
                                                  ▼
                    ┌─────────────────────────────────────────────────────────┐
                    │   START WORKFLOW: "CUD Document Lifecycle"              │
                    │   formKey: cudwf:submitForReviewStart                   │
                    │   fields: bpm:packageItems, cudwf:submitComment         │
                    └───────────────────────┬─────────────────────────────────┘
                                            │
                                            ▼
        ┌───────────────────────────────────────────────────────────────────────────┐
        │  USER TASK: "Review & Approve" (id=customReviewTask)                      │
        │  formKey: cudwf:reviewTask                                                │
        │                                                                           │
        │  ┌─ TaskListener event="create" ───────────────────────────────────────┐  │
        │  │  1. Move document(s) from {Dept}_Draft → {Dept}_Review              │  │
        │  │  2. Stamp cud:lifecycleStatus = REVIEW + statusChangedAt + history  │  │
        │  │  3. Unlock document if locked                                       │  │
        │  │  4. Assign task to workflow initiator                               │  │
        │  └─────────────────────────────────────────────────────────────────────┘  │
        │                                                                           │
        │  Reviewer completes task, sets cudwf_approved = true / false              │
        │                                                                           │
        │  ┌─ TaskListener event="complete" ─────────────────────────────────────┐  │
        │  │  reads cudwf_approved:                                              │  │
        │  │                                                                     │  │
        │  │  APPROVED (true):                                                   │  │
        │  │    → Move document {Dept}_Review → {Dept}_Published                 │  │
        │  │    → Stamp cud:lifecycleStatus = PUBLISHED                          │  │
        │  │    → Lock document (WRITE_LOCK / owner lock)                        │  │
        │  │                                                                     │  │
        │  │  REJECTED (false):                                                  │  │
        │  │    → Move document {Dept}_Review → {Dept}_Draft                     │  │
        │  │    → Stamp cud:lifecycleStatus = DRAFT                              │  │
        │  │    → Unlock document if locked                                      │  │
        │  └─────────────────────────────────────────────────────────────────────┘  │
        └───────────────────────┬───────────────────────────────────────────────────┘
                                │
                                ▼
                        ┌───────────────┐
                        │   END EVENT   │
                        └───────────────┘
```

### Step-by-step flow

| Step | Actor | Action | Document Location | Status |
|------|-------|--------|-------------------|--------|
| 1 | Author | Uploads document into `{Dept}_Draft` | `{Dept}_Draft` | DRAFT |
| 2 | Author | Starts **CUD Document Lifecycle** workflow on the document | `{Dept}_Draft` | DRAFT |
| 3 | TaskListener (`create`) | Moves document to Review folder; assigns task to initiator | `{Dept}_Review` | REVIEW |
| 4a | Reviewer | Completes task with **Approved = true** | `{Dept}_Published` | PUBLISHED (locked) |
| 4b | Reviewer | Completes task with **Approved = false** | `{Dept}_Draft` | DRAFT (unlocked) |

### Lifecycle states ↔ folder suffixes

| Lifecycle State | Folder Suffix | Example Folder | Lock Behavior |
|-----------------|---------------|----------------|---------------|
| `DRAFT` | `_Draft` | `President_Draft` | Unlocked |
| `REVIEW` | `_Review` | `President_Review` | Unlocked |
| `PUBLISHED` | `_Published` | `President_Published` | **WRITE_LOCK (owner lock)** |
| `ARCHIVED` | `_Archive` | `President_Archive` | Unlocked |

---

## 2. Primary Files (What Makes It Work)

| # | File | Role |
|---|------|------|
| 1 | `aio-platform/src/main/resources/alfresco/module/cud-document-management-v2/workflow/cud-document-lifecycle.bpmn20.xml` | BPMN 2.0 process definition; attaches the TaskListener to the review task's `create` and `complete` events |
| 2 | `aio-platform/src/main/java/ae/ac/cud/workflow/lifecycle/CudDocumentLifecycleTaskListener.java` | Java `TaskListener` implementing all folder-move, lock/unlock, and metadata-stamping logic |
| 3 | `aio-platform/src/main/resources/alfresco/module/cud-document-management-v2/context/service-context.xml` | Spring bean registration for the listener + dictionary model bootstrap |
| 4 | `aio-platform/src/main/resources/alfresco/module/cud-document-management-v2/context/bootstrap-context.xml` | `workflowDeployer` bean that registers the BPMN definitions with the Activiti engine |
| 5 | `aio-platform/src/main/resources/alfresco/module/cud-document-management-v2/model/cud-workflow-model.xml` | Workflow task types: `cudwf:submitForReviewStart`, `cudwf:reviewTask` |
| 6 | `aio-platform/src/main/resources/alfresco/module/cud-document-management-v2/model/cud-model.xml` | Content model: `cud:documentLifecycle` aspect with `cud:lifecycleStatus`, `cud:statusChangedAt`, `cud:statusHistory` |
| 7 | `aio-share/src/main/resources/META-INF/share-config-custom.xml` | Share form configuration for the start form (`cudwf:submitForReviewStart`) |
| 8 | `aio-platform/src/main/resources/alfresco/module/cud-document-management-v2/workflow/cud-workflow-messages.properties` | Workflow/task labels and descriptions shown in Share |

---

## 3. BPMN Process Definition

**File:** `workflow/cud-document-lifecycle.bpmn20.xml`

Process id: `cud-document-lifecycle`

```
[startEvent] ──flow-start-to-task──► [userTask: customReviewTask] ──flow-task-to-end──► [endEvent]
```

The single user task carries **two TaskListener registrations** (same class, different events):

```xml
<userTask id="customReviewTask" name="Review & Approve"
            activiti:formKey="cudwf:reviewTask">
   <extensionElements>
      <activiti:taskListener event="create"
                             activiti:class="ae.ac.cud.workflow.lifecycle.CudDocumentLifecycleTaskListener" />
      <activiti:taskListener event="complete"
                             activiti:class="ae.ac.cud.workflow.lifecycle.CudDocumentLifecycleTaskListener" />
   </extensionElements>
</userTask>
```

| BPMN Element | Id | Form Key | Purpose |
|--------------|----|----------|---------|
| Start Event | `start` | `cudwf:submitForReviewStart` | Author submits document(s) from `{Dept}_Draft` |
| User Task | `customReviewTask` | `cudwf:reviewTask` | Approve (publish) or reject (back to draft) |
| End Event | `end` | — | Workflow completion |

> **Note:** The BPMN uses `activiti:class`, meaning Activiti instantiates the listener via reflection on every event. This is why the listener resolves Alfresco services lazily at runtime (see §5.1) rather than relying solely on Spring setter injection.

---

## 4. Spring Wiring

**File:** `context/service-context.xml`

### 4.1 Dictionary model bootstrap

```xml
<bean id="cud.v2.dictionaryBootstrap"
      parent="dictionaryModelBootstrap"
      depends-on="dictionaryBootstrap">
   <property name="models">
      <list>
         <value>alfresco/module/cud-document-management-v2/model/cud-model.xml</value>
         <value>alfresco/module/cud-document-management-v2/model/cud-workflow-model.xml</value>
      </list>
   </property>
</bean>
```

### 4.2 TaskListener bean

```xml
<bean id="cudDocumentLifecycleTaskListener"
      class="ae.ac.cud.workflow.lifecycle.CudDocumentLifecycleTaskListener">
   <property name="nodeService" ref="NodeService" />
   <property name="fileFolderService" ref="FileFolderService" />
   <property name="lockService" ref="LockService" />
</bean>
```

### 4.3 Workflow deployment

**File:** `context/bootstrap-context.xml`

```xml
<bean id="cud.v2.workflowBootstrap" parent="workflowDeployer">
   <property name="workflowDefinitions">
      <list>
         <props>
            <prop key="engineId">activiti</prop>
            <prop key="location">alfresco/module/cud-document-management-v2/workflow/cud-document-lifecycle.bpmn20.xml</prop>
            <prop key="mimetype">text/xml</prop>
         </props>
         <!-- cud-document-revision.bpmn20.xml also deployed here -->
      </list>
   </property>
   <property name="labels">
      <list>
         <value>alfresco/module/cud-document-management-v2/workflow/cud-workflow-messages</value>
      </list>
   </property>
</bean>
```

---

## 5. TaskListener Implementation

**File:** `CudDocumentLifecycleTaskListener.java`
**Class:** `ae.ac.cud.workflow.lifecycle.CudDocumentLifecycleTaskListener implements org.activiti.engine.delegate.TaskListener`

### 5.1 Service resolution (why it works with `activiti:class`)

Because `activiti:class` creates a **new instance per event** (Spring setters are not applied to those instances), the `notify()` method lazily obtains services from the Activiti engine's bean map on first use:

```java
ProcessEngineConfigurationImpl config = Context.getProcessEngineConfiguration();
Object serviceRegistryObj = config.getBeans().get(ActivitiConstants.SERVICE_REGISTRY_BEAN_KEY);
// fallback: config.getBeans().get("ServiceRegistry")
ServiceRegistry registry = (ServiceRegistry) serviceRegistryObj;
this.nodeService      = registry.getNodeService();
this.fileFolderService = registry.getFileFolderService();
this.lockService       = registry.getLockService();
```

This runtime lookup is the key mechanism that makes the file-moving logic execute correctly inside the workflow engine.

### 5.2 Event handling in `notify(DelegateTask)`

| Event | Action |
|-------|--------|
| `create` | `transitionDocuments(STATE_REVIEW, task)` — moves documents to `{Dept}_Review`; then resolves the `initiator` variable (ActivitiScriptNode or String) and calls `delegateTask.setAssignee(userName)` |
| `complete` | Reads `cudwf_approved` task variable. `true` → `transitionDocuments(STATE_PUBLISHED, task)`; `false`/null → `transitionDocuments(STATE_DRAFT, task)` |

### 5.3 Document transition logic (`transitionDocuments`)

1. Resolves the `bpm_package` process variable (workflow's document package).
2. Extracts the package container `NodeRef` (handles `ActivitiScriptNode` or raw `NodeRef`).
3. Gets all child documents via `nodeService.getChildAssocs(packageNodeRef, WorkflowModel.ASSOC_PACKAGE_CONTAINS, MATCH_ALL)`.
4. Moves each document via `moveDocument(...)`.

### 5.4 Folder resolution algorithm (`moveDocument`)

No hard-coded paths — the target folder is **derived from the document's current location**:

1. Get the document's current parent folder name (e.g. `President_Draft`).
2. Strip a recognized lifecycle suffix (`_Draft`, `_Review`, `_Published`, `_Archive`) to obtain the department base name (e.g. `President`).
3. The department root is the **parent of the current folder** (e.g. the `President` folder containing all lifecycle subfolders).
4. Target folder = `deptBase + targetSuffix` (e.g. `President_Published`), located via `fileFolderService.searchSimple(deptRoot, targetName)`.
5. If the document is not already there: `fileFolderService.move(doc, targetFolder, null)`.
6. If the target folder does not exist, the move is skipped and an error is logged (folder structure is normally auto-created by `CudAutoDepartmentBehaviour` when department folders are created from the space template).

```
Department Root (e.g. "President")
├── President_Draft       ◄── DRAFT documents
├── President_Review      ◄── REVIEW documents
├── President_Published   ◄── PUBLISHED documents (locked)
└── President_Archive     ◄── ARCHIVED documents
```

### 5.5 Metadata stamping

After each move the listener:

- Adds aspect `cud:documentLifecycle` if not present.
- Sets `cud:lifecycleStatus` = target state (`DRAFT` | `REVIEW` | `PUBLISHED`).
- Sets `cud:statusChangedAt` = current timestamp.
- Appends an audit entry to `cud:statusHistory`:
  `{date} | {STATE} | moved to {FolderName} | by workflow instance {processInstanceId}`

### 5.6 Locking behavior

| Target state | Lock action |
|--------------|-------------|
| `PUBLISHED` | `lockService.lock(doc, LockType.WRITE_LOCK)` — owner lock; document is read-only for everyone except the owner / users with unlock rights |
| `DRAFT` / `REVIEW` | `lockService.unlock(doc)` if locked — releases any owner lock (e.g. when a revision brings a published doc back into the cycle) |

---

## 6. Content Model (Lifecycle Aspect)

**File:** `model/cud-model.xml` — namespace `http://www.cud.ac.ae/model/content/1.0` (prefix `cud`)

### Aspect: `cud:documentLifecycle`

| Property | Type | Description |
|----------|------|-------------|
| `cud:lifecycleStatus` | `d:text` (default `DRAFT`) | Constrained by `cud:lifecycleStatusList`: `DRAFT`, `REVIEW`, `PUBLISHED`, `ARCHIVED` |
| `cud:statusChangedAt` | `d:datetime` | Timestamp of the last status transition |
| `cud:statusHistory` | `d:text` (multiple) | Append-only audit trail of transitions |

The aspect also defines `cud:departmentRoot` (placed on the space-template root; used by `CudAutoDepartmentBehaviour` to auto-create the Draft/Review/Published/Archive structure for department folders).

---

## 7. Workflow Task Model

**File:** `model/cud-workflow-model.xml` — namespace `http://www.cud.ac.ae/model/workflow/1.0` (prefix `cudwf`)

| Type | Parent | Properties |
|------|--------|------------|
| `cudwf:submitForReviewStart` | `bpm:startTask` | `cudwf:submitComment` (mltext) |
| `cudwf:reviewTask` | `bpm:workflowTask` | `cudwf:approved` (boolean, mandatory, default `false`), `cudwf:reviewComment` (mltext) |
| `cudwf:submitForRevisionStart` | `bpm:startTask` | `cudwf:revisionComment` (mltext) — used by the companion revision workflow |

---

## 8. Share Form Configuration

**File:** `aio-share/src/main/resources/META-INF/share-config-custom.xml`

Start form for submitting documents for review:

```xml
<config evaluator="string-compare" condition="cudwf:submitForReviewStart">
    <forms>
        <form>
            <field-visibility>
                <show id="bpm:packageItems" />
                <show id="cudwf:submitComment" />
            </field-visibility>
        </form>
    </forms>
</config>
```

- `bpm:packageItems` — the items picker that attaches document(s) to the workflow package (`bpm_package`).
- The review task form (`cudwf:reviewTask`) renders its model-defined fields (`cudwf:approved`, `cudwf:reviewComment`) using Alfresco's default workflow task form rendering.

---

## 9. Key Workflow Variables

| Variable | Type | Set by | Used by |
|----------|------|--------|---------|
| `bpm_package` | NodeRef / ActivitiScriptNode | Alfresco workflow engine (from `bpm:packageItems`) | TaskListener — locates all attached documents |
| `initiator` | ActivitiScriptNode / String | Workflow engine at start | TaskListener (`create`) — assigns the review task |
| `cudwf_approved` | Boolean | Reviewer via task form (`cudwf:approved`) | TaskListener (`complete`) — decides publish vs reject |

---

## 10. Companion Workflow: Revision

**File:** `workflow/cud-document-revision.bpmn20.xml` (process id `cud-document-revision`)

Handles the PUBLISHED → DRAFT cycle without touching the locked original:

```
[startEvent: cudwf:submitForRevisionStart] ──► [serviceTask: copyToDraft ${cudRevisionDelegate}] ──► [endEvent]
```

- Started from the `{Dept}_Published` folder by owner/manager.
- **Copies** the published document into `{Dept}_Draft` (name + `_rev`) so it can be revised and re-submitted through the lifecycle workflow.
- The published original remains in `{Dept}_Published`, locked, with status `PUBLISHED`.

---

## 11. Module Context Loading Order

```
module-context.xml
├── imports context/bootstrap-context.xml
│   ├── cud.v2.bootstrap.spaceTemplate   (ImporterModuleComponent — space template)
│   └── cud.v2.workflowBootstrap         (workflowDeployer — BPMN + labels)
└── imports context/service-context.xml
    ├── cud.v2.dictionaryBootstrap       (cud-model.xml + cud-workflow-model.xml)
    ├── cudDocumentLifecycleTaskListener (TaskListener bean)
    ├── cudRevisionDelegate              (revision copy delegate)
    └── cud.v2.autoDepartmentBehaviour   (auto-creates lifecycle folder structure)
```

---

## 12. Summary

The workflow works because:

1. **BPMN attaches one listener class to both task events** (`create` and `complete`) — no separate service tasks or gateway routing needed.
2. **The listener self-resolves Alfresco services** from the Activiti engine's bean map at runtime, so it works even when instantiated via `activiti:class`.
3. **Folder targets are derived dynamically** from the document's current folder name suffix — no hard-coded repository paths.
4. **Documents travel with their status**: the physical folder move is always paired with `cud:lifecycleStatus` metadata + audit history on the node itself.
5. **Publishing locks the document** (`WRITE_LOCK`), enforcing read-only access until a revision workflow brings a copy back to Draft.