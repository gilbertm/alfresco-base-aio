# Alfresco Share Header Customization (share-header.get.js)

## Overview

This document outlines the custom Aikau JavaScript controller extension used to modify the global navigation header in Alfresco Share.

* The primary objective of this script is to remove specific top-level navigation menus (Sites, Tasks, and People) to streamline the user interface and enforce access control.
* It utilizes the official Aikau `widgetUtils` module to traverse and modify the JSON model that renders the Share header.

The customization is applied **only to non-admin users** through a Surf extension module evaluator, so administrators retain the full navigation while regular users see a simplified header. The same module also hides the **My Sites dashlet** on the user dashboard.

---

## Table of Contents

1. [File Location](#file-location)
2. [Source Code](#source-code)
3. [How the Override Works](#how-the-override-works)
4. [Extension Module Descriptor](#extension-module-descriptor)
5. [Removed Menu Items](#removed-menu-items)
6. [Rebuild and Verify](#rebuild-and-verify)
7. [Troubleshooting](#troubleshooting)
8. [File Checklist](#file-checklist)

---

## File Location

To apply this override, the file must be placed in your Share module extension path. For a domain like `our-domain.org`, the standard deployment path is:
`alfresco/web-extension/site-webscripts/org/our-domain/header/share-header.get.js`.

In this project the controller lives under the custom `ae.ac.cud` package root, which is mapped onto the standard Share header package by the extension module (see [How the Override Works](#how-the-override-works)):

```
aio-share/src/main/resources/alfresco/web-extension/
└── site-webscripts/
    └── ae/
        └── ac/
            └── cud/
                └── header/
                    └── share-header.get.js
```

The original controller that gets replaced is shipped inside the Share WAR at:

```
org/alfresco/share/header/share-header.get.js
```

---

## Source Code

```javascript
// Official Aikau header customization pattern
// Removes the My Sites menu item for non-admins (the extension module already restricts this file)

// Log user details to server log (catalina.out / share.log)
logger.log("=== share-header.get.js is executing ===");
logger.log("Current user: " + (user ? user.userName : "null"));

// Check whether the Sites menu exists
var sitesMenu = widgetUtils.findObject(model.jsonModel, "id", "HEADER_SITES_MENU");
logger.log("HEADER_SITES_MENU found: " + (sitesMenu != null));

// Global Removal: Deletes these items for ALL users navigating the header
widgetUtils.deleteObjectFromArray(model.jsonModel, "id", "HEADER_SITES_MENU");
widgetUtils.deleteObjectFromArray(model.jsonModel, "id", "HEADER_TASKS");
widgetUtils.deleteObjectFromArray(model.jsonModel, "id", "HEADER_PEOPLE");

logger.log("HEADER_SITES_MENU deleted");

// Conditional Check: Non-Admin specific logic
if (user && user.userName && !user.isAdmin)
{
   // Remove the top-level "My Sites" entry if present
   widgetUtils.deleteObjectFromArray(model.jsonModel, "id", "HEADER_SITES_MENU");
   widgetUtils.deleteObjectFromArray(model.jsonModel, "id", "HEADER_TASKS");
   widgetUtils.deleteObjectFromArray(model.jsonModel, "id", "HEADER_PEOPLE");

   // Also clean the Sites menu itself (common related items)
   // Note: Since HEADER_SITES_MENU was deleted above, this will return null,
   // but is kept here as a fallback in case global deletion is removed.
   var sitesMenu = widgetUtils.findObject(model.jsonModel, "id", "HEADER_SITES_MENU");
   if (sitesMenu != null)
   {
      // Hide site finder / create site / useful group when desired
      sitesMenu.config.showSiteFinder   = false;
      sitesMenu.config.showCreateSite   = false;
      sitesMenu.config.showUsefulGroup  = false;
      // Optionally keep recent sites / favourites – comment out if you want them gone too
      // sitesMenu.config.showRecentSites = false;
      // sitesMenu.config.showFavourites  = false;
   }
}
```

### Key APIs Used

| API | Purpose |
|-----|---------|
| `widgetUtils.findObject(jsonModel, key, value)` | Recursively searches the widget JSON model and returns the first object whose `key` equals `value` (or `null`) |
| `widgetUtils.deleteObjectFromArray(jsonModel, key, value)` | Finds and removes the matching object from the model so the widget is never rendered |
| `model.jsonModel` | The server-side Aikau widget model that the Share header web script renders |
| `logger.log(...)` | Writes to the Share application log (`share.log` / `catalina.out`) for verification |
| `user` | Surf root-scoped object exposing `userName`, `isAdmin`, etc. for conditional logic |

---

## How the Override Works

This project does **not** override the header by placing a file directly over `org/alfresco/share/header/`. Instead it uses a **Surf extension module** with a `<customizations>` package-root mapping:

```
┌──────────────────────────────────────────────────────────────────┐
│  Request: any Share page renders the header                      │
│                                                                  │
│  1. Surf resolves the web script controller for                  │
│     org.alfresco.share.header → share-header.get.js              │
│                                                                  │
│  2. Deployed extension module is evaluated:                      │
│     "AIOS - Hide My Sites for non-admins"                        │
│     └─ group.module.evaluator                                    │
│        groups=GROUP_ALFRESCO_ADMINISTRATORS, negate=true         │
│        → module active ONLY for non-admin users                  │
│                                                                  │
│  3. Customization mapping applies:                               │
│     targetPackageRoot: org.alfresco.share.header                 │
│     sourcePackageRoot: ae.ac.cud.header                          │
│     → ae/ac/cud/header/share-header.get.js runs instead          │
│                                                                  │
│  4. Controller edits model.jsonModel with widgetUtils:           │
│     deletes HEADER_SITES_MENU, HEADER_TASKS, HEADER_PEOPLE       │
│                                                                  │
│  5. Aikau renders the header without those widgets               │
└──────────────────────────────────────────────────────────────────┘
```

Because the module evaluator gates the whole module, the custom controller only executes for non-admins — administrators continue to load the original, unmodified `share-header.get.js`.

---

## Extension Module Descriptor

File:

```
aio-share/src/main/resources/alfresco/web-extension/site-data/extensions/
└── aio-share-hide-link-and-dashlet-for-sites-navigation-extension.xml
```

```xml
<extension>
   <modules>
      <!-- Module that applies only when the current user is NOT an admin -->
      <module>
         <id>AIOS - Hide My Sites for non-admins</id>
         <version>1.0</version>
         <auto-deploy>true</auto-deploy>

         <!-- Official group.module.evaluator – negate so it runs for non-admins -->
         <evaluator type="group.module.evaluator">
            <params>
               <groups>GROUP_ALFRESCO_ADMINISTRATORS</groups>
               <negate>true</negate>
            </params>
         </evaluator>

         <!-- Aikau header customization (navigation item) -->
         <customizations>
            <customization>
               <targetPackageRoot>org.alfresco.share.header</targetPackageRoot>
               <sourcePackageRoot>ae.ac.cud.header</sourcePackageRoot>
            </customization>
            <!-- Classic dashlet controllers (My Sites dashlet + related) -->
            <customization>
               <targetPackageRoot>org.alfresco.components.dashlets</targetPackageRoot>
               <sourcePackageRoot>ae.ac.cud.dashlets</sourcePackageRoot>
            </customization>
         </customizations>

         <!-- Hide the My Sites dashlet on user dashboard -->
         <components>
            <component>
               <region-id>component-1-1</region-id>          
               <source-id>user/{userid}/dashboard</source-id>
               <scope>page</scope>
               <sub-components>
                  <sub-component id="default">
                     <evaluations>
                        <evaluation id="hide-my-sites-dashlet">
                           <render>false</render>
                        </evaluation>
                     </evaluations>
                  </sub-component>
               </sub-components>
            </component>
         </components>
      </module>
    </modules>
</extension>
```

### Element Reference

| Element | Description |
|---------|-------------|
| `<auto-deploy>true</auto-deploy>` | Module is deployed automatically on startup — no manual activation in the Module Deployment console required |
| `<evaluator type="group.module.evaluator">` | Restricts the module to users based on group membership |
| `<groups>GROUP_ALFRESCO_ADMINISTRATORS</groups>` | The admin group to check membership against |
| `<negate>true</negate>` | Inverts the match — the module applies to users who are **not** in the admin group |
| `<targetPackageRoot>` | The original Share web script package to override |
| `<sourcePackageRoot>` | The custom package whose web scripts replace the target's |
| `<components>` + `<render>false</render>` | Hides the My Sites dashlet (region `component-1-1`) on the user dashboard |

> **Note:** The `ae.ac.cud.dashlets` source package is declared for future dashlet controller overrides. If no files exist under `ae/ac/cud/dashlets/`, the mapping is harmless — the dashlet itself is already hidden via the `<components>` block.

---

## Removed Menu Items

| Widget ID | Header Item | Effect |
|-----------|-------------|--------|
| `HEADER_SITES_MENU` | **Sites** menu (My Sites, site finder, create site) | Removed from the header |
| `HEADER_TASKS` | **Tasks** link | Removed from the header |
| `HEADER_PEOPLE` | **People** link | Removed from the header |

These IDs correspond to widgets defined in the original `share-header.get.js` model inside the Share WAR. To discover other removable IDs, inspect the header model — for example, temporarily add:

```javascript
logger.log(jsonUtils.toJSONString(model.jsonModel));
```

or browse `alfresco/site-webscripts/org/alfresco/share/header/share-header.get.js` inside the Share WAR.

---

## Rebuild and Verify

```bash
./run.sh reload_share
```

This rebuilds the `aio-share` JAR and restarts the Share container so the extension module and controller are picked up.

**Verification:**

1. Log in as a **regular (non-admin) user**:
   - The **Sites**, **Tasks**, and **People** items are gone from the top navigation.
   - The My Sites dashlet is not rendered on the user dashboard.
2. Log in as an **administrator**:
   - All standard navigation items are still present (the module evaluator excludes admins).
3. Check the Share log (`share.log` / `catalina.out`) for:

   ```
   === share-header.get.js is executing ===
   Current user: <username>
   HEADER_SITES_MENU found: true
   HEADER_SITES_MENU deleted
   ```

   These lines confirm the custom controller ran. They should only appear for non-admin users.

---

## Troubleshooting

| Issue | Possible Cause | Solution |
|-------|---------------|----------|
| Menus still visible for non-admins | Extension module not deployed | Check **Admin Tools → Module Deployment** for "AIOS - Hide My Sites for non-admins". With `auto-deploy=true` it should be active; otherwise rebuild with `./run.sh reload_share` |
| Menus disappear for admins too | Evaluator misconfigured | Verify `<negate>true</negate>` and the exact group name `GROUP_ALFRESCO_ADMINISTRATORS` in the extension XML |
| Controller never executes (no log lines) | Wrong package root mapping | `<sourcePackageRoot>` must match the folder path under `site-webscripts/` (`ae/ac/cud/header` ↔ `ae.ac.cud.header`), and the file must be named `share-header.get.js` |
| Controller runs but items remain | Wrong widget ID | Confirm the IDs (`HEADER_SITES_MENU`, `HEADER_TASKS`, `HEADER_PEOPLE`) exist in your Share version's header model |
| My Sites dashlet still shown | Component binding mismatch | Verify `region-id` / `source-id` in the `<components>` block match the dashboard page, or remove the dashlet via the dashboard customization UI |
| Error "widgetUtils is not defined" | Script resolved outside the header web script context | `widgetUtils` and `model` are bound by the Share header web script — the file must be resolved as the `share-header.get.js` controller via the package-root mapping |

---

## File Checklist

| # | File | Purpose |
|---|------|---------|
| 1 | `aio-share/.../site-webscripts/ae/ac/cud/header/share-header.get.js` | Custom header controller — removes Sites/Tasks/People |
| 2 | `aio-share/.../site-data/extensions/aio-share-hide-link-and-dashlet-for-sites-navigation-extension.xml` | Extension module: non-admin evaluator, package-root mapping, dashlet hiding |

### Project Structure

```
aio-share/src/main/resources/alfresco/web-extension/
├── site-data/
│   └── extensions/
│       └── aio-share-hide-link-and-dashlet-for-sites-navigation-extension.xml
└── site-webscripts/
    └── ae/
        └── ac/
            └── cud/
                └── header/
                    └── share-header.get.js
```

After verifying both files, rebuild and redeploy:

```bash
./run.sh reload_share

# or for a full wipe and rebuild:
./run.sh stop
./run.sh purge
./run.sh build_start
```

Then log in as a non-admin user to confirm the Sites, Tasks, and People navigation items are hidden.