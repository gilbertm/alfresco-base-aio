# AIO Platform — API Reference

Base URL: `http://localhost:8080` · Authentication: HTTP Basic (user role)

---

## Affected Files

These are the files involved in creating the HelloUniverse CRUD API. Each file is listed with its full project path and purpose.

| # | File | Path (from project root) | Role |
|---|------|--------------------------|------|
| | **Content Model** | | |
| 1 | `hellouniverse-model.xml` | `aio-platform/src/main/resources/alfresco/module/aio-platform/model/` | Defines type `acme:universeRecord` with properties: planetName, galaxy, distanceFromEarth, habitable |
| 2 | `hellouniverse-model.properties` | `aio-platform/src/main/resources/alfresco/module/aio-platform/messages/` | i18n labels for the content model type and properties |
| | **Java Controllers** | | |
| 3 | `HelloUniverseBase.java` | `aio-platform/src/main/java/ae/ac/cud/tutorials/` | Abstract base class — injects NodeService, NamespaceService, SearchService; provides shared helpers (resolveNodeRef, nodeToJson, applyProperties, readJsonBody) |
| 4 | `HelloUniverseList.java` | `aio-platform/src/main/java/ae/ac/cud/tutorials/` | List handler — FTS query for all `acme:universeRecord` nodes |
| 5 | `HelloUniverseGet.java` | `aio-platform/src/main/java/ae/ac/cud/tutorials/` | Get by ID handler |
| 6 | `HelloUniverseCreate.java` | `aio-platform/src/main/java/ae/ac/cud/tutorials/` | Create handler — creates `acme:universeRecord` under root node |
| 7 | `HelloUniverseUpdate.java` | `aio-platform/src/main/java/ae/ac/cud/tutorials/` | Update handler — applies partial property updates |
| 8 | `HelloUniverseDelete.java` | `aio-platform/src/main/java/ae/ac/cud/tutorials/` | Delete handler — calls nodeService.deleteNode |
| | **Web Script Descriptors** | | |
| 9 | `hellouniverse-list.get.desc.xml` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/tutorials/` | Descriptor for `GET /sample/hellouniverse` (list) |
| 10 | `hellouniverse.get.desc.xml` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/tutorials/` | Descriptor for `GET /sample/hellouniverse/node?id={id}` (get by ID) |
| 11 | `hellouniverse.post.desc.xml` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/tutorials/` | Descriptor for `POST /sample/hellouniverse` (create) |
| 12 | `hellouniverse.put.desc.xml` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/tutorials/` | Descriptor for `PUT /sample/hellouniverse/node?id={id}` (update) |
| 13 | `hellouniverse.delete.desc.xml` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/tutorials/` | Descriptor for `DELETE /sample/hellouniverse/node?id={id}` (delete) |
| | **FreeMarker Templates** | | |
| 14 | `hellouniverse-list.get.json.ftl` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/tutorials/` | Renders `${result}` for list endpoint |
| 15 | `hellouniverse.get.json.ftl` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/tutorials/` | Renders `${result}` for get/update/delete endpoints |
| 16 | `hellouniverse.post.json.ftl` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/tutorials/` | Renders `${result}` for create endpoint |
| 17 | `hellouniverse.put.json.ftl` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/tutorials/` | Renders `${result}` for update endpoint |
| 18 | `hellouniverse.delete.json.ftl` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/tutorials/` | Renders `${result}` for delete endpoint |
| | **Spring Context Configuration** | | |
| 19 | `webscript-context.xml` | `aio-platform/src/main/resources/alfresco/module/aio-platform/context/` | Bean definitions — base bean with service injection + 5 webscript beans |
| 20 | `bootstrap-context.xml` | `aio-platform/src/main/resources/alfresco/module/aio-platform/context/` | Registers `hellouniverse-model.xml` and `hellouniverse-model.properties` |

---

## Endpoint Summary

| Method | URL | Description |
|--------|-----|-------------|
| `GET` | `/alfresco/service/sample/hellouniverse` | List all universe records |
| `GET` | `/alfresco/service/sample/hellouniverse/node?id={uuid}` | Get a single record by node UUID |
| `POST` | `/alfresco/service/sample/hellouniverse` | Create a new universe record |
| `PUT` | `/alfresco/service/sample/hellouniverse/node?id={uuid}` | Update a universe record |
| `DELETE` | `/alfresco/service/sample/hellouniverse/node?id={uuid}` | Delete a universe record |
| `GET` | `/alfresco/service/sample/helloworld` | Sample HelloWorld web script |
| `GET` | `/alfresco/s/api/workflow-definitions` | List deployed workflow definitions |
| `POST` | `/alfresco/s/api/workflows` | Start a workflow instance |
| `GET` | `/alfresco/s/api/workflows` | List workflow instances |
| `GET` | `/alfresco/s/api/task-instances` | List task instances |
| `PUT` | `/alfresco/s/api/task-instances/{id}` | Update/complete a task instance |

---

## 1. HelloUniverse CRUD API

Custom type `acme:universeRecord` extends `cm:content` with persistent storage via NodeService.

| Property | Type | Required |
|----------|------|----------|
| `planetName` | `d:text` | Yes |
| `galaxy` | `d:text` | No |
| `distanceFromEarth` | `d:double` | No |
| `habitable` | `d:boolean` | No, defaults to `false` |

### 1.1 List all records

```bash
curl -u admin:admin "http://localhost:8080/alfresco/service/sample/hellouniverse"
```

**Response:**
```json
{
  "count": 1,
  "items": [
    {
      "id": "c0a8...uuid",
      "nodeRef": "workspace://SpacesStore/c0a8...uuid",
      "name": "Earth.txt",
      "planetName": "Earth",
      "galaxy": "Milky Way",
      "distanceFromEarth": 0.0,
      "habitable": true
    }
  ]
}
```

### 1.2 Get a record by ID

```bash
curl -u admin:admin "http://localhost:8080/alfresco/service/sample/hellouniverse/node?id={node-uuid}"
```

**Response:** Single JSON object (same shape as items above).

### 1.3 Create a record

```bash
curl -u admin:admin -X POST "http://localhost:8080/alfresco/service/sample/hellouniverse" \
  -H "Content-Type: application/json" \
  -d '{
    "planetName": "Kepler-442b",
    "galaxy": "Milky Way",
    "distanceFromEarth": 1206.0,
    "habitable": true
  }'
```

**Response:**
```json
{
  "id": "new-node-uuid",
  "nodeRef": "workspace://SpacesStore/new-node-uuid",
  "name": "Kepler-442b.txt",
  "planetName": "Kepler-442b",
  "galaxy": "Milky Way",
  "distanceFromEarth": 1206.0,
  "habitable": true,
  "created": true
}
```

### 1.4 Update a record

```bash
curl -u admin:admin -X PUT "http://localhost:8080/alfresco/service/sample/hellouniverse/node?id={node-uuid}" \
  -H "Content-Type: application/json" \
  -d '{
    "distanceFromEarth": 1205.5,
    "habitable": false
  }'
```

**Response:** Updated JSON object with new property values.

### 1.5 Delete a record

```bash
curl -u admin:admin -X DELETE "http://localhost:8080/alfresco/service/sample/hellouniverse/node?id={node-uuid}"
```

**Response:**
```json
{
  "deleted": true,
  "id": "node-uuid"
}
```

---

## 2. HelloWorld Web Script

```bash
curl -u admin:admin "http://localhost:8080/alfresco/service/sample/helloworld"
# Response: Message: 'Hello from JS!' ''
```

---

## 3. Workflow APIs

Three custom workflow definitions are deployed:

| Process ID | Name | Stages |
|------------|------|--------|
| `my-process` | My Process | start → user task → end |
| `review-process` | Review Document | start → review task → notification → end |
| `advance-review-process` | Advanced Review & Approval | peer review → manager approval → final sign-off → notification → audit |

### 3.1 List workflow definitions

```bash
curl -u admin:admin "http://localhost:8080/alfresco/s/api/workflow-definitions"
```

### 3.2 Start a workflow

```bash
# review-process
curl -u admin:admin -X POST "http://localhost:8080/alfresco/s/api/workflows" \
  -H "Content-Type: application/json" \
  -d '{
    "processDefinitionKey": "review-process",
    "items": ["workspace://SpacesStore/{node-uuid}"],
    "bpm_workflowDescription": "Please review this document"
  }'

# advance-review-process with reviewers (node references to cm:person)
curl -u admin:admin -X POST "http://localhost:8080/alfresco/s/api/workflows" \
  -H "Content-Type: application/json" \
  -d '{
    "processDefinitionKey": "advance-review-process",
    "items": ["workspace://SpacesStore/{node-uuid}"],
    "bpm_workflowDescription": "Multi-stage review for Q4 report",
    "acmewadv_peerReviewer": "workspace://SpacesStore/{peer-user-node-uuid}",
    "acmewadv_managerReviewer": "workspace://SpacesStore/{manager-user-node-uuid}",
    "acmewadv_seniorReviewer": "workspace://SpacesStore/{senior-user-node-uuid}"
  }'
```

### 3.3 List task instances

```bash
# Active tasks for a user
curl -u admin:admin "http://localhost:8080/alfresco/s/api/task-instances?authority=admin&state=active"

# Tasks for a specific workflow instance
curl -u admin:admin "http://localhost:8080/alfresco/s/api/task-instances?workflowInstanceId={workflowId}"
```

### 3.4 Complete a task

```bash
curl -u admin:admin -X PUT "http://localhost:8080/alfresco/s/api/task-instances/{taskId}" \
  -H "Content-Type: application/json" \
  -d '{
    "state": "completed",
    "acmewadv_approved": true,
    "acmewadv_reviewComment": "Looks good, approved."
  }'
```

### 3.5 List workflow instances

```bash
curl -u admin:admin "http://localhost:8080/alfresco/s/api/workflows?state=active"
curl -u admin:admin "http://localhost:8080/alfresco/s/api/workflows?state=completed"
curl -u admin:admin "http://localhost:8080/alfresco/s/api/workflows?processDefinitionKey=advance-review-process"
```

### 3.6 Advance workflow variables

| Start-form association | Workflow variable | Task | Assignee resolved by |
|---|---|---|---|
| `acmewadv:peerReviewer` | `acmewadv_peerReviewer` | `peerReviewTask` | `ReviewAssignmentDelegate` |
| `acmewadv:managerReviewer` | `acmewadv_managerReviewer` | `managerApprovalTask` | `ReviewAssignmentDelegate` |
| `acmewadv:seniorReviewer` | `acmewadv_seniorReviewer` | `finalSignOffTask` | `ReviewAssignmentDelegate` |

Colons in model names become underscores in workflow variables. Association values are raw node references (e.g., `workspace://SpacesStore/uuid`).

---

## 4. Custom Theme — `CUDCustomTheme`

Registered as a Share theme, selectable via **Admin Tools → Application → Theme**.

Theme files served from `META-INF/resources/themes/CUDCustomTheme/`:
- `presentation.css` — structural overrides
- `yui/assets/skin.css` — YUI component skinning
- `images/` — logo, sprites, icons