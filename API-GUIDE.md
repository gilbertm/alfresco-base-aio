# HelloUniverse CRUD API — Implementation Guide

> **Affected / Needed Files:**
>
> | # | File | Path (from project root) | Role |
> |---|---|---|---|
> | | **Content Model** | | |
> | 1 | `content-model.xml` | `aio-platform/src/main/resources/alfresco/module/aio-platform/model/` | Defines namespace `http://www.acme.org/model/aio/1.0` plus type `acme:universeRecord` with properties: planetName, galaxy, distanceFromEarth, habitable |
> | 2 | `hellouniverse-model.properties` | `aio-platform/src/main/resources/alfresco/module/aio-platform/messages/` | i18n labels for the content model type and properties |
> | | **Java Controllers** | | |
> | 3 | `HelloUniverseBase.java` | `aio-platform/src/main/java/ae/ac/cud/tutorials/` | Abstract base class — injects NodeService, NamespaceService, SearchService; provides shared helpers (resolveNodeRef, nodeToJson, applyProperties, readJsonBody) |
> | 4 | `HelloUniverseList.java` | `aio-platform/src/main/java/ae/ac/cud/tutorials/` | List handler — FTS query for all `acme:universeRecord` nodes |
> | 5 | `HelloUniverseGet.java` | `aio-platform/src/main/java/ae/ac/cud/tutorials/` | Get by ID handler |
> | 6 | `HelloUniverseCreate.java` | `aio-platform/src/main/java/ae/ac/cud/tutorials/` | Create handler — creates `acme:universeRecord` under Company Home |
> | 7 | `HelloUniverseUpdate.java` | `aio-platform/src/main/java/ae/ac/cud/tutorials/` | Update handler — applies partial property updates |
> | 8 | `HelloUniverseDelete.java` | `aio-platform/src/main/java/ae/ac/cud/tutorials/` | Delete handler — calls nodeService.deleteNode |
> | | **Web Script Descriptors** | | |
> | 9 | `hellouniverse-list.get.desc.xml` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/tutorials/` | Descriptor for `GET /sample/hellouniverse` (list) — auth: user |
> | 10 | `hellouniverse.get.desc.xml` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/tutorials/` | Descriptor for `GET /sample/hellouniverse/node?id={id}` (get by ID) — auth: user |
> | 11 | `hellouniverse.post.desc.xml` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/tutorials/` | Descriptor for `POST /sample/hellouniverse` (create) — auth: user |
> | 12 | `hellouniverse.put.desc.xml` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/tutorials/` | Descriptor for `PUT /sample/hellouniverse/node?id={id}` (update) — auth: user |
> | 13 | `hellouniverse.delete.desc.xml` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/tutorials/` | Descriptor for `DELETE /sample/hellouniverse/node?id={id}` (delete) — auth: user |
> | | **FreeMarker Templates** | | |
> | 14 | `hellouniverse-list.get.json.ftl` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/tutorials/` | Renders `${result}` for list endpoint |
> | 15 | `hellouniverse.get.json.ftl` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/tutorials/` | Renders `${result}` for get/update/delete endpoints |
> | 16 | `hellouniverse.post.json.ftl` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/tutorials/` | Renders `${result}` for create endpoint |
> | 17 | `hellouniverse.put.json.ftl` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/tutorials/` | Renders `${result}` for update endpoint |
> | 18 | `hellouniverse.delete.json.ftl` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/tutorials/` | Renders `${result}` for delete endpoint |
> | | **Spring Context Configuration** | | |
> | 19 | `webscript-context.xml` | `aio-platform/src/main/resources/alfresco/module/aio-platform/context/` | Bean definitions — base bean with service injection + 5 webscript beans |
> | 20 | `bootstrap-context.xml` | `aio-platform/src/main/resources/alfresco/module/aio-platform/context/` | Registers `content-model.xml` and `hellouniverse-model.properties` |
> | | **Api-Explorer Integration** | | |
> | 21 | `hellouniverse-api.yaml` | `aio-platform-docker/src/main/docker/` | Swagger 2.0 spec injected into api-explorer at `definitions/` |
> | 22 | `Dockerfile` | `aio-platform-docker/src/main/docker/` | Copies YAML into api-explorer + injects Swagger UI entry |

---

This document describes the design, implementation, and deployment of a custom CRUD REST API for the `acme:universeRecord` content type. The API follows the Alfresco **Declarative Web Script** pattern (V0 framework) and is documented in the api-explorer via a custom Swagger 2.0 specification.

---

## 1. Strategic Overview

### 1.1 Business Scenario

A set of REST endpoints to manage planetary/universe records stored as Alfresco content nodes:

1. **List** — Retrieve all `acme:universeRecord` nodes with pagination support.
2. **Get** — Fetch a single record by its node UUID.
3. **Create** — Create a new record under Company Home with validated properties.
4. **Update** — Partially update an existing record's properties.
5. **Delete** — Permanently remove a record by its node UUID.

### 1.2 Design Goals

| Goal | Approach |
|---|---|
| **Separation of Concerns** | Java controllers handle business logic; FreeMarker templates handle rendering; XML descriptors define URL routing and authentication |
| **Reusability** | Abstract base class (`HelloUniverseBase`) provides shared helpers and service injection for all 5 controllers |
| **Security** | All endpoints require `authentication="user"` — no anonymous or guest access |
| **Discoverability** | Custom Swagger YAML injected into the api-explorer so endpoints appear alongside Core/Search/Model APIs |
| **Consistency** | All responses are JSON; all errors return structured error objects with status codes |

---

## 2. High-Level Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                    Declarative Web Script Framework                 │
│                                                                     │
│  ┌─────────────────────┐     ┌──────────────────────────────┐       │
│  │ Desc XML (routing)  │────▶│ Java Controller (logic)      │       │
│  │ auth: user           │     │ HelloUniverseBase + impl     │       │
│  │ url: /sample/...     │     │                              │       │
│  └─────────────────────┘     └───────────┬──────────────────┘       │
│                                          │                           │
│                                          ▼                           │
│                              ┌──────────────────────────────┐       │
│                              │ FTL Template (rendering)     │       │
│                              │ ${result} → JSON output      │       │
│                              └──────────────────────────────┘       │
│                                                                     │
│  Spring Beans (webscript-context.xml)                               │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐       │
│  │ List       │ │ Get        │ │ Create     │ │ Update    │        │
│  │ Bean       │ │ Bean       │ │ Bean       │ │ Bean       │        │
│  └────────────┘ └────────────┘ └────────────┘ └────────────┘       │
│                         ┌────────────┐                               │
│                         │ Delete     │                               │
│                         │ Bean       │                               │
│                         └────────────┘                               │
└────────────────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌────────────────────────────────────────────────────────────────────┐
│                    Spring Service Layer                             │
│  • NodeService — CRUD operations on nodes                          │
│  • SearchService — FTS queries for listing                         │
│  • NamespaceService — QName resolution for custom properties       │
└────────────────────────────────────────────────────────────────────┘
```

### 2.1 Content Model

The `acme:universeRecord` type is defined inside `content-model.xml` (merged from the former standalone `hellouniverse-model.xml` to avoid namespace conflicts in ACS 26.x):

```xml
<model name="acme:contentModel" xmlns="http://www.alfresco.org/model/dictionary/1.0">
    <namespaces>
        <namespace uri="http://www.acme.org/model/aio/1.0" prefix="acme"/>
    </namespaces>
    <types>
        <type name="acme:universeRecord">
            <parent>cm:content</parent>
            <properties>
                <property name="acme:planetName">
                    <type>d:text</type>
                    <mandatory>true</mandatory>
                </property>
                <property name="acme:galaxy">
                    <type>d:text</type>
                </property>
                <property name="acme:distanceFromEarth">
                    <type>d:double</type>
                </property>
                <property name="acme:habitable">
                    <type>d:boolean</type>
                    <default>false</default>
                </property>
            </properties>
        </type>
    </types>
</model>
```

**Namespace Note:** The URI `http://www.acme.org/model/aio/1.0` was chosen over the original `http://www.acme.org/model/content/1.0` because ACS 26.x internally registers the `/content/1.0` path, causing a `NamespaceException`. All Java controllers reference this URI via the `NAMESPACE` constant in `HelloUniverseBase.java`.

---

## 3. Component Breakdown

### 3.1 Abstract Base Class

**File:** `aio-platform/src/main/java/ae/ac/cud/tutorials/HelloUniverseBase.java`

Extends `DeclarativeWebScript` and provides:

| Method | Purpose |
|---|---|
| `resolveNodeRef(String id)` | Resolves a node UUID or full NodeRef string, throws 400/404 on invalid inputs |
| `nodeToJson(NodeRef)` | Reads all `acme:universeRecord` properties and serializes to JSON |
| `applyProperties(NodeRef, JSONObject)` | Applies partial property updates from a JSON body |
| `readJsonBody(WebScriptRequest)` | Parses the incoming request body as JSON |

Injected services: `NodeService`, `NamespaceService`, `SearchService`.

### 3.2 Web Script Controllers

Each controller extends `HelloUniverseBase` and implements `executeImpl`:

| Controller | Bean ID | Method | URL Pattern | Description |
|---|---|---|---|---|
| `HelloUniverseList` | `webscript.alfresco.tutorials.hellouniverse-list.get` | GET | `/sample/hellouniverse` | FTS query for `{acme}universeRecord` type |
| `HelloUniverseGet` | `webscript.alfresco.tutorials.hellouniverse.get` | GET | `/sample/hellouniverse/node?id=` | Resolves node by ID |
| `HelloUniverseCreate` | `webscript.alfresco.tutorials.hellouniverse.post` | POST | `/sample/hellouniverse` | Creates under Company Home |
| `HelloUniverseUpdate` | `webscript.alfresco.tutorials.hellouniverse.put` | PUT | `/sample/hellouniverse/node?id=` | Partial property update |
| `HelloUniverseDelete` | `webscript.alfresco.tutorials.hellouniverse.delete` | DELETE | `/sample/hellouniverse/node?id=` | Calls `nodeService.deleteNode` |

### 3.3 FreeMarker Templates

All templates are a single line: `${result}`. This enables the Alfresco web script framework to render the JSON string set by the Java controller into the model.

### 3.4 Spring Context

**File:** `aio-platform/src/main/resources/alfresco/module/aio-platform/context/webscript-context.xml`

Defines one abstract parent bean (`webscript.alfresco.tutorials.hellouniverse.base`) with all service injections, plus 5 concrete beans for each endpoint.

**File:** `aio-platform/src/main/resources/alfresco/module/aio-platform/context/bootstrap-context.xml`

Registers `content-model.xml` (which now contains all `acme:*` types including `universeRecord`) and `hellouniverse-model.properties` for i18n labels.

---

## 4. Api-Explorer Integration

### 4.1 Swagger Specification

**File:** `aio-platform-docker/src/main/docker/hellouniverse-api.yaml`

A Swagger 2.0 YAML file that documents all 5 endpoints with:

- `basePath: /alfresco/service`
- `securityDefinitions: basicAuth (HTTP Basic)`
- Per-operation `security` blocks requiring authentication
- Full request/response schemas for `UniverseRecord`, `UniverseRecordInput`, `UniverseRecordList`, and `DeleteResponse`
- `401` and `404` error responses documented per operation

### 4.2 Dockerfile Injection

**File:** `aio-platform-docker/src/main/docker/Dockerfile`

Two build steps wire the Swagger spec into the api-explorer:

1. `COPY hellouniverse-api.yaml $TOMCAT_DIR/webapps/api-explorer/definitions/` — copies the YAML file
2. `RUN sed ...` — injects the HelloUniverse API entry into the Swagger UI's `urls` array in `index.html`, adding a comma to the previous last entry (SCIM 2.0 API) to maintain valid JavaScript syntax

### 4.3 Accessing the Api-Explorer

1. Navigate to `http://localhost:8080/api-explorer/`
2. Select **"HelloUniverse API"** from the dropdown at the top
3. Click the green **"Authorize"** button
4. Enter credentials (e.g., `admin` / `admin`)
5. Expand any endpoint, click **"Try it out"**, then **"Execute"**

---

## 5. Endpoint Reference

**Base URL:** `http://localhost:8080` · **Authentication:** HTTP Basic (user role)

### 5.1 Property Reference

| Property | Type | Required |
|----------|------|----------|
| `planetName` | `d:text` | Yes |
| `galaxy` | `d:text` | No |
| `distanceFromEarth` | `d:double` | No |
| `habitable` | `d:boolean` | No, defaults to `false` |

### 5.2 Endpoint Summary

| Method | URL | Description |
|--------|-----|-------------|
| `GET` | `/alfresco/service/sample/hellouniverse` | List all universe records |
| `POST` | `/alfresco/service/sample/hellouniverse` | Create a new universe record |
| `GET` | `/alfresco/service/sample/hellouniverse/node?id={uuid}` | Get a single record by node UUID |
| `PUT` | `/alfresco/service/sample/hellouniverse/node?id={uuid}` | Update a universe record |
| `DELETE` | `/alfresco/service/sample/hellouniverse/node?id={uuid}` | Delete a universe record |

### 5.3 List All Records

```bash
curl -u admin:admin "http://localhost:8080/alfresco/service/sample/hellouniverse"
```

**Response (200):**
```json
{
  "count": 2,
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

**Response (401):** Returned when no valid credentials are provided.

### 5.4 Get a Record by ID

```bash
curl -u admin:admin "http://localhost:8080/alfresco/service/sample/hellouniverse/node?id={node-uuid}"
```

**Response (200):** Single JSON object in the same shape as an `items` entry.

**Response (404):** Returned when the node UUID does not exist.

### 5.5 Create a Record

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

**Response (200):**
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

**Response (400):** Returned when `planetName` is missing or empty.

### 5.6 Update a Record

```bash
curl -u admin:admin -X PUT "http://localhost:8080/alfresco/service/sample/hellouniverse/node?id={node-uuid}" \
  -H "Content-Type: application/json" \
  -d '{
    "distanceFromEarth": 1205.5,
    "habitable": false
  }'
```

**Response (200):** Updated JSON object with new property values. Only fields present in the body are modified; omitted properties retain their current values.

### 5.7 Delete a Record

```bash
curl -u admin:admin -X DELETE "http://localhost:8080/alfresco/service/sample/hellouniverse/node?id={node-uuid}"
```

**Response (200):**
```json
{
  "deleted": true,
  "id": "node-uuid"
}
```

---

## 6. Deployment

### 6.1 Build

```bash
# Full project build (platform JAR + Docker images)
mvn clean package -DskipTests
```

### 6.2 Start Services

```bash
# Start all containers with fresh builds
docker compose -f target/classes/docker/docker-compose.yml up --build -d

# Start only ACS (after code changes)
docker compose -f target/classes/docker/docker-compose.yml up --build -d aio-acs

# View logs
docker compose -f target/classes/docker/docker-compose.yml logs -f
```

### 6.3 Quick Start (via run.sh)

```bash
./run.sh build_start    # Build, start, and tail logs
./run.sh reload_acs     # Rebuild ACS after code changes
./run.sh stop           # Stop all containers
```

### 6.4 Verification

```bash
# Check ACS context initialization (should have no errors)
docker logs docker-aio-acs-1 2>&1 | grep "Context initialization failed"
# Expected: no output (clean startup)

# Check web script index (all 5 endpoints should appear)
curl -s -u admin:admin "http://localhost:8080/alfresco/service/index/uri/sample/hellouniverse"
```

---

## 7. Troubleshooting

### 7.1 Namespace Conflicts (ACS 26.x)

**Symptom:** `org.springframework.beans.factory.BeanCreationException: URI http://www.acme.org/model/content/1.0 has already been defined`

**Cause:** ACS 26.x internally registers certain namespace URIs. Using a URI that ends with `/content/1.0` causes a collision.

**Fix:** Changed namespace URI to `http://www.acme.org/model/aio/1.0` in `content-model.xml` and updated the `NAMESPACE` constant in `HelloUniverseBase.java`.

### 7.2 SOLR ModelTracker 404 Errors

**Symptom:** `org.alfresco.error.AlfrescoRuntimeException: 06210000 api/solr/modelsdiff return status:404`

**Cause:** When ACS context initialization fails (see 7.1), the SOLR V1 API endpoints are never deployed, so every SOLR tracker call returns 404.

**Fix:** Resolve the namespace conflict. Once ACS starts cleanly, SOLR connects successfully.

### 7.3 Api-Explorer Blank Page

**Symptom:** `http://localhost:8080/api-explorer/` loads a blank page.

**Cause:** The `sed` command that injects the HelloUniverse API entry into `index.html` must add a comma to the previous last entry (SCIM 2.0 API) before inserting the new line — otherwise the JavaScript `urls` array has a syntax error (missing comma between array items).

**Fix:** The Dockerfile now applies two `sed` commands: first adding a comma to the SCIM line, then inserting the HelloUniverse line.