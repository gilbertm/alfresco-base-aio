# User Seed API — Implementation Guide

> **Affected / Needed Files:**
>
> | # | File | Path (from project root) | Role |
> |---|---|---|---|
> | 1 | `UserSeedPost.java` | `aio-platform/src/main/java/ae/ac/cud/customs/` | Java controller — reads bundled Excel from classpath, creates Person + Authentication for each user |
> | 2 | `users-seed.post.desc.xml` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/customs/` | Descriptor for `POST /custom/seed-users` — auth: user |
> | 3 | `users-seed.post.json.ftl` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/customs/` | FreeMarker template — renders `${result}` |
> | 4 | `alfresco_seed_data.xlsx` | `aio-platform/src/main/resources/ae/ac/cud/customs/` | Bundled Excel seed file — classpath resource at `ae/ac/cud/customs/`, protected from Maven resource filtering by POM excludes |
> | 5 | `webscript-context.xml` | `aio-platform/src/main/resources/alfresco/module/aio-platform/context/` | Spring bean for `UserSeedPost` — injects PersonService, MutableAuthenticationService |
> | 6 | `pom.xml` | `aio-platform/` | POM resource filtering — excludes `*.xlsx` from `${property}` substitution to prevent binary corruption |
> | 7 | `cud-customs-api.yaml` | `aio-platform-docker/src/main/docker/` | Swagger 2.0 spec for the "CUD Customs" collection in api-explorer |
> | 8 | `Dockerfile` | `aio-platform-docker/src/main/docker/` | Copies YAML into api-explorer + injects Swagger UI entry |

---

This document describes the design, implementation, and deployment of a **batch user registration endpoint** that reads user data from a **bundled Excel spreadsheet** shipped inside the AMP module and creates corresponding Alfresco Person and Authentication entries — no external file upload required.

---

## 1. Strategic Overview

### 1.1 Business Scenario

An administrator needs to register a large number of users quickly. Instead of creating each user individually through the Alfresco Admin Console or the People API, the administrator simply calls the seed endpoint. The endpoint reads a **bundled Excel file** (`alfresco_seed_data.xlsx`) from the classpath and processes each row:

1. **Validates** — Checks that required columns (firstName, lastName, email, password) are present
2. **Deduplicates** — Skips users that already exist (matched by email/username)
3. **Creates Person** — Registers the user via `PersonService.createPerson()`
4. **Sets Authentication** — Creates login credentials via `MutableAuthenticationService`
5. **Reports** — Returns a per-row result indicating created, skipped, or error status

### 1.2 Design Goals

| Goal | Approach |
|---|---|
| **Batch efficiency** | Single HTTP request processes the bundled Excel file (up to 1000 rows per batch) |
| **Idempotence** | Existing users (matched by email) are skipped — safe to run multiple times |
| **Security** | Endpoint requires `authentication="user"` — admin credentials needed |
| **Zero-config** | No external file upload — seed data ships inside the AMP |
| **Discoverability** | Custom Swagger YAML injected into the api-explorer under "CUD Customs API" |
| **Error resilience** | Per-row error handling — one bad row doesn't stop the entire batch |

---

## 2. High-Level Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                    Declarative Web Script Framework                 │
│                                                                     │
│  ┌──────────────────────────────┐    ┌────────────────────────────┐ │
│  │ Simple POST (no body)        │───▶│ UserSeedPost.java          │ │
│  │ POST /custom/seed-users      │    │ (DeclarativeWebScript)     │ │
│  │ auth: user                   │    │                            │ │
│  └──────────────────────────────┘    └──────┬─────────────────────┘ │
│                                              │                       │
│                    ┌─────────────────────────┼─────────────────────┐ │
│                    │                         ▼                     │ │
│                    │  ┌──────────────────────────────────────────┐ │ │
│                    │  │ Classpath Resource (alfresco_seed_data   │ │ │
│                    │  │ .xlsx) loaded via getClass()             │ │ │
│                    │  │ .getResourceAsStream()                   │ │ │
│                    │  └───────────────────┬──────────────────────┘ │ │
│                    │                      │ row-by-row loop        │ │
│                    │           ┌──────────┴──────────┐             │ │
│                    │           ▼                     ▼             │ │
│                    │  ┌──────────────────┐  ┌───────────────────┐  │ │
│                    │  │ PersonService    │  │ MutableAuth       │  │ │
│                    │  │ .createPerson()  │  │ .createAuth()     │  │ │
│                    │  └──────────────────┘  └───────────────────┘  │ │
│                    │           │                     │              │ │
│                    │           ▼                     ▼              │ │
│                    │  ┌──────────────────────────────────────────┐ │ │
│                    │  │ JSON Response: { created, skipped,       │ │ │
│                    │  │   errors, users: [...] }                 │ │ │
│                    │  └──────────────────────────────────────────┘ │ │
│                    └───────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────┘
```

### 2.1 Excel Format

The Excel file must have a **header row** with column names (case-insensitive, whitespace-trimmed):

| Column | Required | Maps to | Notes |
|--------|----------|---------|-------|
| `firstName` | Yes | `cm:firstName` | User's given name |
| `lastName` | Yes | `cm:lastName` | User's family name |
| `email` | Yes | Username + `cm:email` | Used as the Alfresco username |
| `password` | Yes | Authentication | Plain-text password stored as Alfresco authentication |
| `organization` | No | `cm:organization` | Company/organization name |
| `jobTitle` | No | `cm:jobtitle` | Job title |
| `location` | No | `cm:location` | Physical location/office |
| `telephone` | No | `cm:telephone` | Phone number |

---

## 3. Component Breakdown

### 3.1 Java Controller

**File:** `aio-platform/src/main/java/ae/ac/cud/customs/UserSeedPost.java`

Extends `DeclarativeWebScript` directly (not the `HelloUniverseBase` abstract class — it needs different services: PersonService, MutableAuthenticationService).

**Injected services:** `NodeService`, `PersonService`, `MutableAuthenticationService`, `NamespaceService`

**Seed file location:** `ae/ac/cud/customs/alfresco_seed_data.xlsx` on the classpath — stored under `src/main/resources/` at the same package path as the controller class.

**Loading mechanism:**
```java
InputStream is = getClass().getResourceAsStream(SEED_FILENAME);   // SEED_FILENAME = "alfresco_seed_data.xlsx"
```
`Class.getResourceAsStream(name)` resolves relative to the class's own package. Since `UserSeedPost` is in `ae.ac.cud.customs`, this resolves to `ae/ac/cud/customs/alfresco_seed_data.xlsx` on the classpath. At build time, both the compiled `.class` file and the resource file end up in `target/classes/ae/ac/cud/customs/` — the `.class` from the Java source tree and the `.xlsx` from the resources tree. The class-relative resolution works regardless of which source root the file originates from.

**Processing flow:**

1. `getClass().getResourceAsStream(SEED_FILENAME)` — loads the bundled Excel from the classpath
2. `WorkbookFactory.create(is)` — opens the Excel input stream
3. `sheet.getRow(0)` — reads header row to build a column index map
4. Validates that all 4 required columns (`firstname`, `lastname`, `email`, `password`) exist in the header (case-insensitive)
5. Loops through data rows (row 1 to `sheet.getLastRowNum()`)
6. For each row:
   - `extractUser()` — reads cell values using the column map
   - `authenticationService.authenticationExists()` — checks for duplicates
   - `personService.createPerson()` — creates the Person node with properties
   - `authenticationService.createAuthentication()` — sets the password
   - `authenticationService.setAuthenticationEnabled()` — enables the account
7. Returns a JSON response with per-row status and summary counts

**Batch limit:** 1000 users per request. Processing stops if this limit is reached.

### 3.2 Web Script Descriptor

**File:** `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/customs/users-seed.post.desc.xml`

| Property | Value |
|----------|-------|
| URL | `/custom/seed-users` |
| Method | POST |
| Authentication | `user` (requires admin credentials) |
| Format | JSON |

### 3.3 Spring Context

**File:** `aio-platform/src/main/resources/alfresco/module/aio-platform/context/webscript-context.xml`

The `UserSeedPost` bean is registered with direct property injection:

```xml
<bean id="webscript.alfresco.customs.users-seed.post"
      class="ae.ac.cud.customs.UserSeedPost">
    <property name="nodeService" ref="NodeService" />
    <property name="personService" ref="PersonService" />
    <property name="authenticationService" ref="AuthenticationService" />
    <property name="namespaceService" ref="NamespaceService" />
</bean>
```

### 3.4 Bundled Seed File & POM Resource Filtering

**File:** `aio-platform/src/main/resources/ae/ac/cud/customs/alfresco_seed_data.xlsx`

The Excel spreadsheet is a classpath resource in the `ae/ac/cud/customs/` package path under `src/main/resources/`. The controller loads it with class-relative resolution (`getClass().getResourceAsStream()`), which finds it at the same classpath location regardless of which source root it lives in.

#### 3.4.1 Directory Tree

```
aio-platform/src/main/
├── java/
│   └── ae/
│       └── ac/
│           └── cud/
│               └── customs/
│                   └── UserSeedPost.java              ← controller
└── resources/
    ├── ae/
    │   └── ac/
    │       └── cud/
    │           └── customs/
    │               └── alfresco_seed_data.xlsx        ← ✅ seed file (classpath: ae/ac/cud/customs/)
    └── alfresco/
        ├── extension/
        │   └── templates/
        │       └── webscripts/
        │           └── alfresco/
        │               └── customs/                   ← web script descriptors (NOT for seed data)
        │                   ├── users-seed.post.desc.xml
        │                   └── users-seed.post.json.ftl
        └── module/
            └── aio-platform/
                └── context/
                    └── webscript-context.xml          ← Spring bean registration
```

#### 3.4.2 Why `.xlsx` Files Need Special Handling

Maven's `maven-resources-plugin` applies `${property}` substitution (resource filtering) to files under `src/main/resources/` by default. Since `.xlsx` files are actually ZIP archives (OOXML format), the binary byte sequences that happen to match `${...}` patterns will be replaced with property values, corrupting the ZIP structure. Apache POI's `WorkbookFactory` will then reject the file with:

```
org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException:
  unsupported file type: UNKNOWN
```

#### 3.4.3 The Solution: POM Resource Exclusions

The `aio-platform/pom.xml` uses `combine.self="override"` to replace the parent POM's resource configuration with two resource blocks:

```xml
<resources combine.self="override">
    <!-- All resources EXCEPT .xlsx → filtered (property substitution enabled) -->
    <resource>
        <directory>src/main/resources</directory>
        <filtering>true</filtering>
        <excludes>
            <exclude>**/*.xlsx</exclude>
        </excludes>
    </resource>
    <!-- .xlsx files ONLY → NO filtering (binary-safe) -->
    <resource>
        <directory>src/main/resources</directory>
        <filtering>false</filtering>
        <includes>
            <include>**/*.xlsx</include>
        </includes>
    </resource>
</resources>
```

| Aspect | First block | Second block |
|--------|-------------|--------------|
| Scope | All files except `.xlsx` | `.xlsx` files only |
| Filtering | `true` — `${property}` substitution applied | `false` — bytes copied as-is |
| Purpose | Normal resource processing (property placeholders work) | Preserve binary zip structure intact |

The `combine.self="override"` attribute is **critical** — without it, Maven merges child `<resources>` with the parent POM's `<resources>`, and the parent's unfiltered resource block would still apply to `.xlsx` files.

#### 3.4.4 To Change or Add Seed Data

1. Replace `alfresco_seed_data.xlsx` at:
   ```
   aio-platform/src/main/resources/ae/ac/cud/customs/alfresco_seed_data.xlsx
   ```
   with your new Excel file (keeping the same filename), **OR**

2. Add a new `.xlsx` file anywhere under `src/main/resources/` and update the `SEED_FILENAME` constant and classpath resolution path in `UserSeedPost.java`

3. Run `mvn clean package -DskipTests` — the `.xlsx` will be copied without filtering

4. Redeploy

> **⚠️ Caution:** Passwords in the seed file are stored as plain-text Alfresco authentication credentials. Never commit the `.xlsx` to version control if it contains real production credentials. Use a placeholder file in the repo and inject a real seed file at build/deploy time via CI/CD.

> **Note:** The seed file is not placed under `alfresco/extension/templates/webscripts/` because that directory tree is resolvable by the web script engine URL mechanism. Placing it in a Java-package-structured path under `src/main/resources/` keeps it inaccessible via direct URL while remaining accessible on the classpath.

### 3.5 Api-Explorer Integration

**File:** `aio-platform-docker/src/main/docker/cud-customs-api.yaml`

The **CUD Customs** collection in the api-explorer documents the seed endpoint — no file upload parameter is needed since the seed data is bundled.

**Dockerfile injection (lines 24-32):** Copies the YAML spec into the api-explorer definitions directory and injects a Swagger UI menu entry via `sed`:

```dockerfile
COPY hellouniverse-api.yaml cud-customs-api.yaml $TOMCAT_DIR/webapps/api-explorer/definitions/
RUN sed -i 's/{url: "definitions\/alfresco-scim-v2.yaml",  name: "SCIM 2.0 API"}/{url: "definitions\/alfresco-scim-v2.yaml",  name: "SCIM 2.0 API"},/' $TOMCAT_DIR/webapps/api-explorer/index.html && \
    sed -i '/SCIM 2.0 API"},/a\        {url: "definitions/cud-customs-api.yaml",    name: "CUD Customs API"},'   $TOMCAT_DIR/webapps/api-explorer/index.html && \
    sed -i '/CUD Customs API"},/a\        {url: "definitions/hellouniverse-api.yaml",  name: "HelloUniverse API"},' $TOMCAT_DIR/webapps/api-explorer/index.html
```

---

## 4. Endpoint Reference

### 4.1 POST /custom/seed-users

**URL:** `/alfresco/service/custom/seed-users`  
**Method:** `POST`  
**Authentication:** HTTP Basic (user role)

**Request:** No body required — the seed file is read from the classpath.

**Response (200):**
```json
{
  "total_rows": 3,
  "created": 2,
  "skipped": 1,
  "errors": 0,
  "users": [
    {
      "row": 2,
      "status": "created",
      "username": "gilbert.martinez@cud.ac.ae",
      "nodeRef": "workspace://SpacesStore/uuid-here"
    },
    {
      "row": 3,
      "status": "skipped",
      "username": "aisha.khan@cud.ac.ae",
      "reason": "User already exists"
    },
    {
      "row": 4,
      "status": "created",
      "username": "john.smith@cud.ac.ae",
      "nodeRef": "workspace://SpacesStore/uuid-here"
    }
  ]
}
```

**Response (400) — Missing required column:**
```json
{
  "error": "Missing required column: email",
  "expected_columns": ["firstname", "lastname", "email", "password"],
  "found_columns": ["firstname", "lastname", "password"]
}
```

### 4.2 curl Example

```bash
curl -u admin:admin -X POST "http://localhost:8080/alfresco/service/custom/seed-users"
```

---

## 5. Deployment

### 5.1 Build

```bash
mvn clean package -DskipTests
```

### 5.2 Start

```bash
docker compose -f target/classes/docker/docker-compose.yml up --build -d aio-acs
```

### 5.3 Verify

```bash
# Check the seed endpoint is registered
curl -s -u admin:admin "http://localhost:8080/alfresco/service/index/uri/custom/seed-users"
```

---

## 6. Troubleshooting

### 6.1 "Bundled seed file not found in package"

**Cause:** The `alfresco_seed_data.xlsx` file is missing from the classpath at the expected package path (`ae/ac/cud/customs/`).

**Fix:** Verify the file exists at:
```
aio-platform/src/main/resources/ae/ac/cud/customs/alfresco_seed_data.xlsx
```
and rebuild with `mvn clean package -DskipTests`. Also verify that `pom.xml` has the `combine.self="override"` resource blocks with the `.xlsx` exclusion (see Section 3.4.3).

### 6.2 "unsupported file type: UNKNOWN"

**Cause:** The `.xlsx` file was corrupted by Maven resource filtering — `${property}` substitution has overwritten binary ZIP bytes.

**Root cause:** Either:
- The `pom.xml` is missing the `combine.self="override"` resource blocks that exclude `.xlsx` from filtering, or
- The `combine.self="override"` is present but the parent POM's resource configuration is not properly overridden (e.g., the attribute is on the wrong element)

**Fix:** Ensure `aio-platform/pom.xml` contains the resource configuration shown in Section 3.4.3 with `combine.self="override"` on the `<resources>` element. This creates two resource blocks:
1. One that filters everything **except** `.xlsx` (filtering = true, excludes = `**/*.xlsx`)
2. One that handles only `.xlsx` with filtering disabled (filtering = false, includes = `**/*.xlsx`)

Without `combine.self="override"`, Maven merges child and parent `<resources>` lists, and the parent's unfiltered block may not exclude `.xlsx` files.

### 6.3 "Missing required column"

**Cause:** Excel header row doesn't contain all required columns: `firstName`, `lastName`, `email`, `password`.

**Fix:** 
- Column names are case-insensitive and whitespace-trimmed (e.g., `firstName`, `FirstName`, and `FIRSTNAME` all work)
- Check the response's `found_columns` field to see what was detected
- If a column like `email` is present but empty at runtime, ensure the header cell actually contains text (not a formula evaluating to blank)

### 6.4 Users not being created

**Cause:** The authenticated user may not have the correct permissions, or `PersonService` / `AuthenticationService` beans may not be available.

**Fix:** Check ACS logs:
```bash
docker logs docker-aio-acs-1 2>&1 | grep -i "SeedUser\|PersonService\|Authentication"