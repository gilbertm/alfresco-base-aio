# Custom Theme Head Override Guide

## Overview

This guide documents how to dynamically change the **browser tab title** and **favicon** based on the active Share theme in an Alfresco SDK All-In-One (AIO) project. Two separate approaches are used because Alfresco renders the favicon and title through different mechanisms.

| Element | Mechanism | Approach |
|---------|-----------|----------|
| **Favicon** | FreeMarker `@markup` in `resources.get.html.ftl` | Full webscript override |
| **Title** | `page.title` message key in `slingshot` bundle | i18n message override |

---

## Table of Contents

1. [Favicon — Dynamic per Theme via Webscript Override](#1-favicon--dynamic-per-theme-via-webscript-override)
2. [Title — Dynamic per Theme via Message Key Override](#2-title--dynamic-per-theme-via-message-key-override)
3. [How It All Fits Together](#3-how-it-all-fits-together)
4. [Why Some Approaches Failed](#4-why-some-approaches-failed)
5. [File Checklist](#5-file-checklist)

---

## 1. Favicon — Dynamic per Theme via Webscript Override

### Why a full Webscript Override?

The original Share `resources.get.html.ftl` renders favicon links inside a `<@markup id="favicons">` block. A Surf extension module (`.head.ftl` file) using `@markup` with `action="replace"` is **not supported** — the `replace` action only works for extension modules targeting the base model, but Surf processes them differently. The cleanest approach is a **full webscript override** placed at the same path, which Surf automatically picks up and uses instead of the original.

### Step 1.1: Create the Override Template

Place a copy of the original `resources.get.html.ftl` at the extension path, modifying only the favicon links:

```
[project]-share/src/main/resources/alfresco/web-extension/
└── site-webscripts/
    └── org/
        └── alfresco/
            └── components/
                └── head/
                    └── resources.get.html.ftl
```

The file must include **all** original `@markup` blocks (`yui`, `alfrescoConstants`, `alfrescoResources`, `shareConstants`, `shareResources`, `resources`) to ensure the page loads all CSS/JS correctly. Only the `favicons` block is modified:

```ftl
<#include "../component.head.inc">
<#--
   RESOURCES
   Customized: Dynamic favicon per theme
-->
<@markup id="favicons">
   <!-- Icons - Dynamic per theme -->
   <link rel="shortcut icon" href="${url.context}/res/themes/${theme}/images/${msg(theme + '.favicon')!msg('CUDCustomTheme.favicon')}" type="image/vnd.microsoft.icon" />
   <link rel="icon" href="${url.context}/res/themes/${theme}/images/${msg(theme + '.favicon')!msg('CUDCustomTheme.favicon')}" type="image/vnd.microsoft.icon" />
</@markup>

<@markup id="yui">
   <!-- ... ALL original YUI resource links ... -->
</@>
<!-- ... ALL other original @markup blocks unchanged ... -->
<@markup id="resources">
   <#-- Use this "markup id" to add in a extension's resources -->
</@>
```

**Key points:**

- `${theme}` is a Surf root-scoped variable set in `component.head.inc` — it contains the active theme ID (e.g., `"CUDCustomTheme"`)
- `${msg(theme + '.favicon')}` dynamically queries the i18n bundle for a key like `CUDCustomTheme.favicon`
- `!msg('CUDCustomTheme.favicon')` is a fallback in case no theme-specific favicon is configured

### Step 1.2: Define the Favicon Message Key

```
[project]-share/src/main/resources/alfresco/web-extension/messages/[project]-share.properties
```

```properties
CUDCustomTheme.favicon=cud-favicon.ico
```

### Step 1.3: Place the Favicon File

```
[project]-share/src/main/resources/META-INF/resources/themes/[theme-id]/images/
└── [favicon-filename].ico
```

Example:
```
aio-share/src/main/resources/META-INF/resources/themes/CUDCustomTheme/images/
└── cud-favicon.ico
```

### How the URL Resolves

When `theme` = `"CUDCustomTheme"` and `CUDCustomTheme.favicon` = `"cud-favicon.ico"`:

```
${url.context}/res/themes/${theme}/images/${msg(theme + '.favicon')}
→ /share/res/themes/CUDCustomTheme/images/cud-favicon.ico
```

---

## 2. Title — Dynamic per Theme via Message Key Override

### Why a Message Key Override?

Alfresco Aikau generates the browser tab title server-side using the `page.title` message key from the `slingshot` resource bundle:

```
page.title=Alfresco &raquo; {0}
```

Where `{0}` is replaced at runtime with the page-specific label (e.g., "Dashboard", "Admin Tools").

By overriding this key in the module's i18n properties file, we replace `"Alfresco"` with custom branding **without any JavaScript or template changes**. The override is applied globally — all pages get the custom prefix regardless of user navigation.

### Step 2.1: Override the page.title Message Key

```
[project]-share/src/main/resources/alfresco/web-extension/messages/[project]-share.properties
```

```properties
# Override page title prefix - replaces "Alfresco" with custom branding
page.title=CUD Custom Theme - Canadian University Dubai &raquo; {0}
```

**How it works:**

- The message bundle is registered in Spring via `ResourceBundleBootstrapComponent` in the `slingshot-application-context.xml`
- Surf resolves `msg("page.title")` from the bundle, finds our override, and uses it
- The `{0}` placeholder is filled by Aikau with the page name
- Result: `<title>CUD Custom Theme - Canadian University Dubai » Admin Tools</title>`

### Step 2.2: Ensure the Resource Bundle is Registered

```
[project]-share/src/main/resources/alfresco/web-extension/[project]-slingshot-application-context.xml
```

```xml
<bean id="ae.ac.cud.aio-share.resources"
      class="org.springframework.extensions.surf.util.ResourceBundleBootstrapComponent">
   <property name="resourceBundles">
      <list>
         <value>alfresco.web-extension.messages.aio-share</value>
      </list>
   </property>
</bean>
```

The bundle path `alfresco.web-extension.messages.aio-share` maps to `alfresco/web-extension/messages/aio-share.properties`.

---

## 3. How It All Fits Together

```
┌─────────────────────────────────────────────────────────────────┐
│                      Alfresco Share                              │
│                                                                  │
│  Surf Framework                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  component.head.inc                                       │   │
│  │  └─ Sets ${theme} = "CUDCustomTheme"                      │   │
│  │                                                           │   │
│  │  resources.get.html.ftl  ←── WEBSCRIPT OVERRIDE           │   │
│  │  ┌──────────────────────────────────────────────────┐    │   │
│  │  │  @markup id="favicons"                           │    │   │
│  │  │  └─ ${msg(theme + '.favicon')}                   │    │   │
│  │  │     → CUDCustomTheme.favicon = "cud-favicon.ico" │    │   │
│  │  │     → /res/themes/CUDCustomTheme/images/...      │    │   │
│  │  └──────────────────────────────────────────────────┘    │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  Aikau Page Framework                                            │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  msg("page.title")                                       │   │
│  │  → aio-share.properties:                                 │   │
│  │    page.title=CUD Custom Theme... &raquo; {0}             │   │
│  │  → renders <title>CUD Custom Theme... &raquo; Dashboard  │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### Theme Asset Resolution Chain

```
1. Surf resolves active ${theme} = "CUDCustomTheme"
2. FreeMarker calls msg("CUDCustomTheme.favicon")
3. Message bundle returns "cud-favicon.ico"
4. Final HTML: href="/share/res/themes/CUDCustomTheme/images/cud-favicon.ico"

1. Aikau calls msg("page.title", [pageLabel])
2. Message bundle returns "CUD Custom Theme... » {0}"
3. Aikau substitutes {0} = "Admin Tools"
4. Final HTML: <title>CUD Custom Theme - Canadian University Dubai » Admin Tools</title>
```

---

## 4. Why Some Approaches Failed

### ❌ `@markup` with `action="replace"` in a webscript override

**Attempted:** Create a partial template at `resources.get.head.ftl` using:
```ftl
<@markup id="favicons" action="replace" target="favicons" scope="global">
```

**Error:**
```
WARN: The 'replace' action was attempted to used when defining the base model
```

**Why:** The `replace` action is valid only in **extension module** templates that modify already-defined regions. When placed in a webscript override file, Surf treats it as a new base model definition, where `replace` is not allowed. The correct fix was to do a full webscript override with the modified content inline.

### ❌ Partial webscript override (only favicon block)

**Attempted:** Place a `resources.get.html.ftl` at the extension path containing only the favicon block.

**Why:** Surf replaces the **entire** template output with the override file. Since the override only contained favicon links, all CSS/JS resources were lost, breaking the page. The fix was to include the complete original template with all `@markup` blocks.

### ❌ JavaScript DOM manipulation for title

**Attempted:** Use `MutationObserver` or `DOMContentLoaded` to replace `"Alfresco"` in `document.title`.

**Why:** While functional, this is unnecessary. The title is generated server-side by Aikau using a standard message key. Overriding the key is simpler, faster (no JS execution), and works for all pages including those without JavaScript.

---

## 5. File Checklist

| # | File | Purpose |
|---|------|---------|
| 1 | `.../site-webscripts/.../head/resources.get.html.ftl` | Full webscript override — dynamic favicon |
| 2 | `.../messages/[project]-share.properties` | Message keys for favicon filename AND page.title override |
| 3 | `.../META-INF/resources/themes/[theme-id]/images/[favicon].ico` | Favicon asset file |
| 4 | `.../slingshot-application-context.xml` | Spring bean registering the resource bundle |

### Project Structure (CUDCustomTheme example)

```
aio-share/src/main/resources/
├── alfresco/
│   └── web-extension/
│       ├── aio-share-slingshot-application-context.xml
│       ├── messages/
│       │   └── aio-share.properties          ← page.title + favicon keys
│       └── site-webscripts/
│           └── org/alfresco/components/head/
│               └── resources.get.html.ftl    ← full webscript override
└── META-INF/
    └── resources/
        └── themes/
            └── CUDCustomTheme/
                └── images/
                    └── cud-favicon.ico       ← favicon asset