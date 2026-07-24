# User Seed API — Implementation Guide

> **Affected / Needed Files:**
>
> | # | File | Path (from project root) | Role |
> |---|---|---|---|
> | 1 | `UserSeedPost.java` | `aio-platform/src/main/java/ae/ac/cud/customs/` | Java controller — reads bundled Excel from classpath, creates Person + Authentication for each user |
> | 2 | `users-seed.post.desc.xml` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/customs/` | Descriptor for `POST /custom/seed-users` — auth: user |
> | 3 | `users-seed.post.json.ftl` | `aio-platform/src/main/resources/alfresco/extension/templates/webscripts/alfresco/customs/` | FreeMarker template — renders `${result}` |
> | 4 | `alfresco_seed_data.xlsx` | `aio-platform/src/main/java/ae/ac/cud/customs/` | Bundled Excel seed file — lives alongside UserSeedPost.java in the Java source tree, immune to Maven resource filtering |
> | 5 | `webscript-context.xml` | `aio-platform/src/main/resources/alfresco/module/aio-platform/context/` | Spring bean for `UserSeedPost` — injects PersonService, MutableAuthenticationService |
> | 6 | `cud-customs-api.yaml` | `aio-platform-docker/src/main/docker/` | Swagger 2.0 spec for the "CUD Customs" collection in api-explorer |
> | 7 | `Dockerfile` | `aio-platform-docker/src/main/docker/` | Copies YAML into api-explorer + injects Swagger UI entry |

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
│                    │  │ .xlsx) loaded via getResourceAsStream()  │ │ │
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

The Excel file must have a **header row** with column names (case-insensitive):

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

Extends `DeclarativeWebScript` directly (not the `HelloUniverseBase` abstract class — it needs different services).

**Injected services:** `NodeService`, `PersonService`, `MutableAuthenticationService`, `NamespaceService`

**Seed file location:** `ae/ac/cud/customs/alfresco_seed_data.xlsx` on the classpath — placed in the same Java package as `UserSeedPost.java`. Maven's compiler plugin copies non-`.java` files from `src/main/java` to `target/classes` **without resource filtering**, so the binary ZIP/OOXML structure is guaranteed intact.

**Loading mechanism:**
```java
InputStream is = getClass().getResourceAsStream("alfresco_seed_data.xlsx");
```
`Class.getResourceAsStream(name)` resolves relative to the class's own package. Since `UserSeedPost` is in `ae.ac.cud.customs`, this resolves to `ae/ac/cud/customs/alfresco_seed_data.xlsx` on the classpath. No classloader-relative path (which maps to `src/main/resources/`) is needed — this sidesteps Maven resource filtering entirely.

**Processing flow:**

1. `getClass().getClassLoader().getResourceAsStream(SEED_FILE_CLASSPATH)` — loads the bundled Excel from the classpath
2. `WorkbookFactory.create(is)` — opens the Excel input stream
3. `sheet.getRow(0)` — reads header row to build a column index map
4. Validates that all 4 required columns exist in the header
5. Loops through data rows (row 1 to `sheet.getLastRowNum()`)
6. For each row:
   - `extractUser()` — reads cell values using the column map
   - `authenticationService.authenticationExists()` — checks for duplicates
   - `personService.createPerson()` — creates the Person node
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

The `UserSeedPost` bean is registered with direct property injection (not using the `HelloUniverseBase` abstract parent):

```xml
<bean id="webscript.alfresco.customs.users-seed.post"
      class="ae.ac.cud.customs.UserSeedPost">
    <property name="nodeService" ref="NodeService" />
    <property name="personService" ref="PersonService" />
    <property name="authenticationService" ref="AuthenticationService" />
    <property name="namespaceService" ref="NamespaceService" />
</bean>
```

### 3.4 Bundled Seed File

**File:** `aio-platform/src/main/java/ae/ac/cud/customs/alfresco_seed_data.xlsx`

The Excel spreadsheet lives in the same Java package as `UserSeedPost.java`. This location is chosen deliberately:

1. **Security:** The file is not in `src/main/resources/`, so no web script URL pattern can resolve to it.
2. **Integrity:** Maven's `maven-compiler-plugin` copies non-`.java` files from `src/main/java` to `target/classes` **without resource filtering**. Unlike files in `src/main/resources/`—which are subject to `${property}` substitution that corrupts binary ZIP/OOXML structures—the `.xlsx` byte stream arrives in the JAR exactly as authored.

#### 3.4.1 Seed File Placement Guide

**Directory tree:**

```
aio-platform/src/main/
├── java/
│   └── ae/
│       └── ac/
│           └── cud/
│               └── customs/
│                   ├── UserSeedPost.java                ← controller
│                   └── alfresco_seed_data.xlsx          ← ✅ seed file (same package)
└── resources/
    └── alfresco/
        ├── extension/
        │   └── templates/
        │       └── webscripts/
        │           └── alfresco/
        │               └── customs/                     ← ⚠️ DO NOT put seed files here
        │                   ├── users-seed.post.desc.xml     (web script engine resolves this dir)
        │                   ├── users-seed.post.json.ftl
        │                   └── sample-users.xlsx            (packaged but unsafe)
        └── module/
            └── aio-platform/
                └── context/
                    └── webscript-context.xml            ← Spring bean registration
```

**Comparison: `src/main/resources/` vs `src/main/java/`**

| Aspect | `src/main/resources/` | `src/main/java/` |
|--------|----------------------|-------------------|
| Handled by | `maven-resources-plugin` | `maven-compiler-plugin` |
| Filtering | `<filtering>true</filtering>` applies `${prop}` substitution (corrupts binary files like `.xlsx`) | No filtering ever — bytes copied as-is |
| Web-accessible | Files under `alfresco/extension/templates/webscripts/` are resolvable via web script engine URLs | **Never** — Java package paths are not web-addressable |
| Classpath resolution | `getClassLoader().getResourceAsStream("alfresco/...")` | `getClass().getResourceAsStream("filename.xlsx")` (class-relative) |

**Why `src/main/java/` is the correct location:**

The `"unsupported file type: UNKNOWN"` error occurs because Maven's resource plugin treats `.xlsx` as text and replaces `${...}` byte sequences with property values. This corrupts the ZIP file structure, and Apache POI's `WorkbookFactory` cannot recognize the file signature. By placing the `.xlsx` in `src/main/java/`, it is copied by the compiler plugin which never filters files.

**How the Java code resolves it:**

```java
// UserSeedPost.java — class-relative resolution
private static final String SEED_FILENAME = "alfresco_seed_data.xlsx";

// At runtime:
InputStream is = getClass().getResourceAsStream(SEED_FILENAME);
//  getClass()             = ae.ac.cud.customs.UserSeedPost
//  getResourceAsStream()  = resolves relative to the class's package:
//                           ae/ac/cud/customs/alfresco_seed_data.xlsx
//
//  Maven copies this from:
//    src/main/java/ae/ac/cud/customs/alfresco_seed_data.xlsx
//  to:
//    target/classes/ae/ac/cud/customs/alfresco_seed_data.xlsx  (unfiltered!)
```

**To change or add seed data:**

1. Replace `alfresco_seed_data.xlsx` in the Java package directory with your new Excel file (keeping the same filename), **OR**
2. Add a new `.xlsx` file to the package and update the `SEED_FILENAME` constant in `UserSeedPost.java`
3. Run `mvn clean package -DskipTests` — the compiler plugin copies non-`.java` files without filtering
4. Redeploy

> **⚠️ Caution:** Passwords in the seed file are stored as plain-text Alfresco authentication credentials. Never commit the `.xlsx` to version control if it contains real production credentials. Use a placeholder file in the repo and inject a real seed file at build/deploy time via CI/CD.

### 3.5 Api-Explorer Integration

**File:** `aio-platform-docker/src/main/docker/cud-customs-api.yaml`

The **CUD Customs** collection in the api-explorer documents the seed endpoint — no file upload parameter is needed since the seed data is bundled.

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
  "expected_columns": ["firstName", "lastName", "email", "password"],
  "found_columns": ["firstName", "lastName", "password"]
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

### 6.1 "Bundled seed file not found"

**Cause:** The `alfresco_seed_data.xlsx` file is missing from the Java package directory or was excluded by the compiler.

**Fix:** Verify the file exists at:
```
aio-platform/src/main/java/ae/ac/cud/customs/alfresco_seed_data.xlsx
```
and rebuild with `mvn clean package -DskipTests`.

### 6.1a "unsupported file type: UNKNOWN"

**Cause:** The `.xlsx` file was corrupted by Maven resource filtering (property substitution on binary ZIP bytes).

**Fix:** Ensure the file lives in `src/main/java/` (the compiler copies it without filtering), not in `src/main/resources/`. The `UserSeedPost` class uses `getClass().getResourceAsStream()` (class-relative resolution) which loads from the package directory. If for any reason the file must stay in `src/main/resources/`, add this to `aio-platform/pom.xml`:

```xml
<build>
    <resources combine.self="override">
        <resource>
            <directory>src/main/resources</directory>
            <filtering>true</filtering>
            <excludes><exclude>**/*.xlsx</exclude></excludes>
        </resource>
        <resource>
            <directory>src/main/resources</directory>
            <filtering>false</filtering>
            <includes><include>**/*.xlsx</include></includes>
        </resource>
    </resources>
</build>
```

The `combine.self="override"` attribute is critical — without it, Maven merges child `<resources>` with the parent's, and the parent's unfiltered block still applies.

### 6.2 "Missing required column"

**Cause:** Excel header row doesn't contain all required columns (firstName, lastName, email, password).

**Fix:** 
- Column names are case-insensitive but must match exactly (e.g., `firstname`, `FirstName`, and `FIRSTNAME` all work)
- Spaces in headers are NOT trimmed — use exactly `firstName`, not `first name`
- Check the response's `found_columns` field to see what was detected

### 6.3 Users not being created

**Cause:** The `admin` user may not have the correct permissions, or `PersonService` / `AuthenticationService` beans may not be available.

**Fix:** Check ACS logs:
```bash
docker logs docker-aio-acs-1 2>&1 | grep -i "SeedUser\|PersonService\|Authentication"