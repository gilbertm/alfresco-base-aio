# Custom Theme — Header Navigation: Hiding Sites & Repository Widgets

## Overview

This guide documents how to hide the **"Sites"** and **"Repository"** header navigation widgets in Alfresco Share, conditionally scoped to the **CUDCustomTheme** and **non-admin users**.

---

## Problem

The out-of-the-box Alfresco Share header includes navigation items for **Sites** and **Repository**:

- `#HEADER_SITES_MENU` — dropdown for site navigation and creation
- `#HEADER_REPOSITORY` — link to the document library root

For some deployments, these widgets are not needed by end users and should be hidden. The removal should:
- Only apply when the **CUDCustomTheme** is active
- **Not** apply to admin users (admins should see all navigation)

---

## Approach: CSS Override in `resources.get.html.ftl`

The Alfresco Share header in this environment uses the **Aikau framework** (Dojo-based widgets). The classic `share-config-custom.xml` `<config condition="Header">` approach does **not** work for Aikau headers — CSS is the reliable method.

### File

```
aio-share/src/main/resources/alfresco/web-extension/site-webscripts/org/alfresco/components/head/resources.get.html.ftl
```

### Implementation

Inside the `<@markup id="resources">` block, add an inline `<style>` block with a conditional FreeMarker check:

```html
<@markup id="resources">
   <#-- Use this "markup id" to add in a extension's resources -->
   <#if theme = 'CUDCustomTheme' && user.name != "admin">
      <style type="text/css">
         #HEADER_SITES_MENU { display: none !important; }
         #HEADER_REPOSITORY { display: none !important; }
      </style>
   </#if>
</@>
```

### How It Works

| Component | Purpose |
|---|---|
| `theme = 'CUDCustomTheme'` | Only injects CSS when the active theme is CUDCustomTheme |
| `user.name != "admin"` | Excludes the admin user — admin sees the full header |
| `#HEADER_SITES_MENU` | Targets the Aikau Dojo widget for the Sites dropdown |
| `#HEADER_REPOSITORY` | Targets the Aikau Dojo widget for the Repository link |
| `!important` | Ensures CSS override takes precedence over Dojo/Dijit inline styles |

### Behavior Matrix

| Theme | User | Sites Widget | Repository Widget |
|---|---|---|---|
| CUDCustomTheme | Non-admin | Hidden | Hidden |
| CUDCustomTheme | Admin (username = "admin") | Visible | Visible |
| default (or any other) | Any user | Visible | Visible |

---

## Alternative Approaches Considered

### 1. `share-config-custom.xml` Header Config (❌ Did NOT work)

```xml
<config evaluator="string-compare" condition="Header" replace="true">
    <items>
        <item type="link" id="Sites" visible="false"/>
        <item type="link" id="Repository" visible="false"/>
    </items>
</config>
```

**Why it failed:** This config only applies to the **classic Surf header**, not the **Aikau (Dojo-based) header** rendered in this Alfresco instance.

### 2. `!user.admin` / `!user.isAdmin` FreeMarker check (❌ Did NOT work)

```ftl
<#if theme = 'CUDCustomTheme' && !user.admin>
<#if theme = 'CUDCustomTheme' && !user.isAdmin>
```

**Why it failed:** The `ScriptUser.isAdmin()` boolean getter is not reliably exposed through FreeMarker bean introspection in this webscript context. The `user.admin` property evaluates to `false` even for admin users.

### 3. CSS in `presentation.css` (⚠️ Works, but lacks conditionality)

Adding to `presentation.css` works, but cannot conditionally check theme or user — it would affect all themes and all users.

---

## Related Files

- `aio-share/src/main/resources/alfresco/web-extension/site-webscripts/org/alfresco/components/head/resources.get.html.ftl` — Head resources webscript (where the CSS override lives)
- `aio-share/src/main/resources/META-INF/share-config-custom.xml` — Share configuration (theme registration, but header config doesn't work for Aikau)
- `aio-share/src/main/resources/META-INF/resources/themes/CUDCustomTheme/presentation.css` — Custom theme stylesheet