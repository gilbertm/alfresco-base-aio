# Alfresco Share Custom Theme Guide

## Overview

This guide explains how to create a custom theme in an **Alfresco SDK All-In-One (AIO) project** and make it selectable in the **Share Administration Console** (Admin Tools → Application → Theme) and **Site Theme Management** (Customize Site → Theme).

The approach uses a **Share JAR module** (the `*-share` sub-project) to package the theme resources and descriptor. No AMP is required.

The implementation described here is based on the **CUD Custom Theme** (`CUDCustomTheme`) included in this project, which was created by extracting the built-in **LightTheme** from the Share WAR and renaming it.

---

## Table of Contents

1. [Theme Structure](#1-theme-structure)
2. [Choosing an Approach](#2-choosing-an-approach)
3. [Approach A — Build from Scratch](#3-approach-a--build-from-scratch)
   - [Step 1: Create Theme Resources](#31-step-1-create-the-theme-resources)
   - [Step 2: Create Theme Descriptor XML](#32-step-2-create-the-theme-descriptor-xml)
   - [Step 3: Add i18n Translation Label](#33-step-3-add-the-i18n-translation-label)
   - [Step 4: Register in share-config-custom.xml](#34-step-4-optional-register-in-share-config-customxml)
   - [Step 5: Rebuild and Verify](#35-step-5-rebuild-and-verify)
4. [Approach B — Extract LightTheme from JAR & Rename](#4-approach-b--extract-lighttheme-from-jar--rename)
   - [Step 1: Determine Alfresco Version](#41-step-1-determine-alfresco-version)
   - [Step 2: Locate or Pull the Share Image](#42-step-2-locate-or-pull-the-share-image)
   - [Step 3: Extract LightTheme from the JAR/WAR](#43-step-3-extract-lighttheme-from-the-jarwar)
   - [Step 4: Copy into Project and Rename Folder](#44-step-4-copy-into-project-and-rename-folder)
   - [Step 5: Rename Identifiers Inside CSS Files](#45-step-5-rename-identifiers-inside-css-files)
   - [Step 6: Remove Old Custom Theme Resources](#46-step-6-remove-old-custom-theme-resources)
   - [Step 7: Create/Update Theme Descriptor XML](#47-step-7-createupdate-theme-descriptor-xml)
   - [Step 8: Update i18n Properties](#48-step-8-update-i18n-properties)
   - [Step 9: Update share-config-custom.xml](#49-step-9-update-share-config-custom-xml)
   - [Step 10: Update Docker share-config-custom.xml](#410-step-10-update-docker-share-config-customxml)
   - [Step 11: Update Any Other References](#411-step-11-update-any-other-references)
   - [Step 12: Rebuild and Verify](#412-step-12-rebuild-and-verify)
5. [Complete Working Example: CUDCustomTheme](#5-complete-working-example-cudcustomtheme)
6. [Troubleshooting](#6-troubleshooting)
7. [Quick Reference: File Checklist](#7-quick-reference-file-checklist)

---

## 1. Theme Structure

A custom theme consists of two main parts inside your `[project]-share` sub-project:

### Part A — Theme Resource Files (CSS, Images, YUI Skin)

These are packaged under `META-INF/resources/themes/` so they become accessible from the Share webapp's classpath at runtime.

```
[project]-share/src/main/resources/META-INF/resources/themes/
└── [theme-id]/
    ├── images/
    │   └── logo.png                (Custom logo / icon images)
    ├── presentation.css            (Core structural CSS rules)
    └── yui/
        └── assets/
            ├── skin.css            (YUI component rules)
            └── *.png / *.gif       (YUI sprite/asset images)
```

### Part B — Theme Descriptor XML (Required)

This file is what makes the theme **discoverable** by Share's theme management system. Without this file, the theme will **not** appear in the Admin Console or Site Theme dropdown, even if the CSS files are present.

```
[project]-share/src/main/resources/alfresco/web-extension/
└── site-data/
    └── themes/
        └── [theme-id].xml          (Theme descriptor)
```

### Part C — i18n Properties File

The theme title displayed in the Admin Console is loaded from a properties file via a Spring resource bundle.

```
[project]-share/src/main/resources/alfresco/web-extension/
└── messages/
    └── [project]-share.properties  (i18n labels)
```

The resource bundle must be registered in the Spring application context.

```
[project]-share/src/main/resources/alfresco/web-extension/
└── [project]-slingshot-application-context.xml   (Spring context)
```

---

## 2. Choosing an Approach

| Criterion | Approach A (Build from Scratch) | Approach B (Extract LightTheme) |
|-----------|-------------------------------|----------------------------------|
| Effort | High — write all CSS manually | Low — rename existing theme |
| Completeness | Manual — easy to miss components | Complete — all YUI assets included |
| Customisability | Full control over every rule | Modify the extracted CSS as needed |
| Best for | Simple tweaks (logo, colours) | Full white-label or brand theme |

This project uses **Approach B**. The `CUDCustomTheme` was created by extracting the built-in **LightTheme** from the Share WAR.

---

## 3. Approach A — Build from Scratch

### 3.1 Step 1: Create the Theme Resources

#### 3.1.1 Create the directory structure

```bash
mkdir -p [project]-share/src/main/resources/META-INF/resources/themes/[theme-id]/images
mkdir -p [project]-share/src/main/resources/META-INF/resources/themes/[theme-id]/yui/assets
```

#### 3.1.2 Create `presentation.css`

This is the main stylesheet that overrides Alfresco Share's default styles. All CSS selectors must be prefixed with `.alfresco-share` to scope them to the Share application, and `!important` is required to override Share's built-in styles.

```css
/**
 * [Theme Name] — presentation.css
 * Core structural theme rules
 *
 * Brand Colors:
 *   Primary:   #003366   (Navy)
 *   Accent:    #c8102e   (Red)
 *   Highlight: #f5a623   (Gold)
 */

/* ---- Global ---- */
.alfresco-share {
    font-family: "Segoe UI", Arial, sans-serif !important;
    color: #333333 !important;
    background-color: #f5f5f5 !important;
}

a {
    color: #003366 !important;
}

a:hover {
    color: #c8102e !important;
    text-decoration: underline !important;
}

/* ---- Header ---- */
.alfresco-share .alfresco-header-Header {
    background-color: #003366 !important;
    border-bottom: 3px solid #c8102e !important;
    height: 48px !important;
}

.alfresco-share .alfresco-header-Logo {
    background: url("images/logo.png") no-repeat left center !important;
    background-size: contain !important;
    width: 200px !important;
    height: 40px !important;
    margin: 4px 0 !important;
}

/* Hide the default flower logo image */
.alfresco-share .alfresco-header-Logo img {
    display: none !important;
}

.alfresco-share .alfresco-header-Title {
    color: #ffffff !important;
    font-size: 16px !important;
    font-weight: 600 !important;
    line-height: 48px !important;
}

/* Header bar links (user menu, etc.) */
.alfresco-share .alfresco-header-Header .header-bar-link {
    color: #ffffff !important;
    font-size: 12px !important;
    line-height: 48px !important;
}

.alfresco-share .alfresco-header-Header .header-bar-link:hover {
    color: #f5a623 !important;
    text-decoration: none !important;
}

/* ---- Navigation ---- */
.alfresco-share .alf-menu-bar {
    background-color: #002244 !important;
}

.alfresco-share .alf-menu-bar a {
    color: #ffffff !important;
}

.alfresco-share .site-navigation {
    background-color: #002244 !important;
    border-bottom: 2px solid #c8102e !important;
}

.alfresco-share .site-navigation a {
    color: #ffffff !important;
    padding: 8px 16px !important;
}

.alfresco-share .site-navigation a:hover {
    background-color: #003366 !important;
    color: #f5a623 !important;
}

.alfresco-share .site-navigation a.selected {
    background-color: #003366 !important;
    border-bottom: 2px solid #c8102e !important;
}

/* Breadcrumb */
.alfresco-share .breadcrumb {
    color: #003366 !important;
    padding: 6px 12px !important;
    background-color: #ffffff !important;
    border-bottom: 1px solid #e0e0e0 !important;
}

/* ---- Site Page Header ---- */
.alfresco-share .site-page-header {
    background-color: #003366 !important;
    color: #ffffff !important;
    padding: 10px 16px !important;
    border-bottom: 2px solid #c8102e !important;
}

.alfresco-share .site-page-header .site-page-header-title {
    color: #ffffff !important;
    font-size: 18px !important;
    font-weight: 600 !important;
}

/* ---- Footer ---- */
.alfresco-share .alfresco-footer-Footer {
    background-color: #003366 !important;
    color: #ffffff !important;
    border-top: 3px solid #c8102e !important;
    padding: 12px 20px !important;
    font-size: 12px !important;
}

.alfresco-share .alfresco-footer-Copyright {
    color: #cccccc !important;
    font-size: 11px !important;
}

.alfresco-share .alfresco-footer-Footer a {
    color: #f5a623 !important;
}

/* ---- Buttons ---- */
.alfresco-share .alfresco-button-Button,
.alfresco-share .button,
.alfresco-share input[type="submit"],
.alfresco-share input[type="button"] {
    background-color: #c8102e !important;
    border: 1px solid #a00d25 !important;
    color: #ffffff !important;
    padding: 6px 16px !important;
    font-weight: 600 !important;
    border-radius: 3px !important;
    cursor: pointer !important;
}

.alfresco-share .alfresco-button-Button:hover,
.alfresco-share .button:hover {
    background-color: #e01234 !important;
    border-color: #c8102e !important;
}

/* Secondary (outline) buttons */
.alfresco-share .alfresco-button-Button--secondary,
.alfresco-share .button-secondary {
    background-color: #ffffff !important;
    border: 1px solid #003366 !important;
    color: #003366 !important;
}

.alfresco-share .alfresco-button-Button--secondary:hover {
    background-color: #e8f0f8 !important;
}

/* Disabled buttons */
.alfresco-share .alfresco-button-Button[disabled],
.alfresco-share .button-disabled {
    background-color: #e0e0e0 !important;
    border-color: #cccccc !important;
    color: #999999 !important;
    cursor: not-allowed !important;
}

/* ---- Dashlets / Dashboard ---- */
.alfresco-share .alfresco-dashlets-Dashlet {
    border: 1px solid #d0d0d0 !important;
    border-radius: 3px !important;
    background-color: #ffffff !important;
    margin-bottom: 12px !important;
}

.alfresco-share .dashlet-title {
    background-color: #003366 !important;
    color: #ffffff !important;
    padding: 8px 12px !important;
    font-size: 14px !important;
    font-weight: 600 !important;
    border-bottom: 2px solid #c8102e !important;
}

.alfresco-share .dashlet-body {
    padding: 12px !important;
}

.alfresco-share .dashlet a {
    color: #003366 !important;
}

.alfresco-share .dashlet a:hover {
    color: #c8102e !important;
}

/* ---- Document Library ---- */
.alfresco-share .document-list .doclist-header {
    background-color: #003366 !important;
    color: #ffffff !important;
    border-bottom: 2px solid #c8102e !important;
}

.alfresco-share .document-list .doclist-header th {
    padding: 8px 10px !important;
    font-weight: 600 !important;
    font-size: 12px !important;
}

.alfresco-share .document-list .doclist-row:hover {
    background-color: #e8f0f8 !important;
}

/* ---- Data Tables ---- */
.alfresco-share .dataTable th {
    background-color: #003366 !important;
    color: #ffffff !important;
    border-color: #002244 !important;
}

.alfresco-share .dataTable tr:hover td {
    background-color: #e8f0f8 !important;
}

/* ---- Pagination ---- */
.alfresco-share .pagination .currentPage {
    background-color: #003366 !important;
    color: #ffffff !important;
    border-color: #003366 !important;
}

/* ---- Tabs ---- */
.alfresco-share .yui-navset .yui-nav .selected a {
    background-color: #003366 !important;
    color: #ffffff !important;
}

/* ---- Dialogs / Modals ---- */
.alfresco-share .dialog .dialog-header {
    background-color: #003366 !important;
    color: #ffffff !important;
    padding: 10px 16px !important;
    font-weight: 600 !important;
    border-bottom: 2px solid #c8102e !important;
}

/* ---- Forms ---- */
.alfresco-share .form-set .form-set-title {
    background-color: #003366 !important;
    color: #ffffff !important;
    padding: 8px 12px !important;
    font-weight: 600 !important;
}

.alfresco-share input[type="text"]:focus,
.alfresco-share textarea:focus {
    border-color: #003366 !important;
    outline: none !important;
}

/* ---- Tags ---- */
.alfresco-share .tag {
    background-color: #003366 !important;
    color: #ffffff !important;
}

.alfresco-share .tag:hover {
    background-color: #c8102e !important;
}

/* ---- Status Messages ---- */
.alfresco-share .status-message {
    background-color: #e8f0f8 !important;
    border-color: #003366 !important;
}

.alfresco-share .status-message.error {
    background-color: #fbe9e7 !important;
    border-color: #c8102e !important;
    color: #b71c1c !important;
}

.alfresco-share .status-message.warning {
    background-color: #fff8e1 !important;
    border-color: #f5a623 !important;
    color: #e65100 !important;
}

.alfresco-share .status-message.info {
    background-color: #e3f2fd !important;
    border-color: #003366 !important;
    color: #003366 !important;
}

/* ---- Toolbar ---- */
.alfresco-share .toolbar {
    background-color: #003366 !important;
}

.alfresco-share .toolbar a {
    color: #ffffff !important;
}

/* ---- Admin Console ---- */
.alfresco-share .admin-console .heading {
    background-color: #003366 !important;
    color: #ffffff !important;
    border-bottom: 3px solid #c8102e !important;
    padding: 8px 12px !important;
}

/* ---- Login Page ---- */
.alfresco-share .theme-company-logo {
    background: url("images/logo.png") no-repeat center center !important;
    background-size: contain !important;
    height: 80px !important;
    margin-bottom: 20px !important;
}

/* ---- Misc ---- */
.alfresco-share .separator {
    border-color: #003366 !important;
}

.alfresco-share .loading {
    background-color: #ffffff !important;
    border-color: #003366 !important;
    color: #003366 !important;
}

.alfresco-share .progress-bar .progress {
    background-color: #003366 !important;
}
```

> **Important Rules:**
> - Always use `.alfresco-share` prefix to scope rules to the Share application
> - Always use `!important` to override Share's built-in styles
> - Reference images with relative paths from the CSS file (e.g., `url("images/logo.png")`)

#### 3.1.3 Create `yui/assets/skin.css`

YUI (Yahoo User Interface) components in Share include dialogs, menus, data tables, tab views, progress bars, and calendars. This file provides styling for those components.

```css
/**
 * [Theme Name] — YUI Skin CSS
 * YUI component styling
 *
 * Brand Colors:
 *   Primary:   #003366   (Navy)
 *   Accent:    #c8102e   (Red)
 */

/* ---- YUI Data Table ---- */
.alfresco-share .yui-dt th {
    background-color: #003366 !important;
    color: #ffffff !important;
    border-color: #002244 !important;
}

.alfresco-share .yui-dt tr.yui-dt-even td {
    background-color: #f9f9f9 !important;
}

.alfresco-share .yui-dt tr.yui-dt-odd td {
    background-color: #ffffff !important;
}

.alfresco-share .yui-dt tr.yui-dt-even td:hover,
.alfresco-share .yui-dt tr.yui-dt-odd td:hover {
    background-color: #e8f0f8 !important;
}

/* ---- YUI Menu ---- */
.alfresco-share .yuimenu .bd {
    background-color: #ffffff !important;
    border: 1px solid #d0d0d0 !important;
}

.alfresco-share .yuimenu a {
    color: #333333 !important;
}

.alfresco-share .yuimenu a:hover {
    background-color: #003366 !important;
    color: #ffffff !important;
}

/* ---- YUI Panel / Dialog ---- */
.alfresco-share .yui-panel {
    background-color: #ffffff !important;
    border: 1px solid #003366 !important;
}

.alfresco-share .yui-panel .hd {
    background-color: #003366 !important;
    color: #ffffff !important;
    padding: 8px 12px !important;
    font-weight: 600 !important;
}

.alfresco-share .yui-panel .bd {
    padding: 12px !important;
    background-color: #ffffff !important;
}

.alfresco-share .yui-panel .ft {
    background-color: #f5f5f5 !important;
    border-top: 1px solid #e0e0e0 !important;
    padding: 8px 12px !important;
}

/* ---- YUI Tab View ---- */
.alfresco-share .yui-navset .yui-nav a {
    background-color: #e0e0e0 !important;
    color: #333333 !important;
}

.alfresco-share .yui-navset .yui-nav .selected a {
    background-color: #003366 !important;
    color: #ffffff !important;
}

/* ---- YUI Button ---- */
.alfresco-share .yuibutton button,
.alfresco-share .yuibutton input[type="button"],
.alfresco-share .yuibutton input[type="submit"] {
    background-color: #c8102e !important;
    border: 1px solid #a00d25 !important;
    color: #ffffff !important;
}

.alfresco-share .yuibutton button:hover,
.alfresco-share .yuibutton input[type="button"]:hover {
    background-color: #e01234 !important;
}

/* ---- YUI Calendar ---- */
.alfresco-share .yui-calendar .calheader {
    background-color: #003366 !important;
    color: #ffffff !important;
}

.alfresco-share .yui-calendar .calheader a {
    color: #ffffff !important;
}

/* ---- YUI Progress Bar ---- */
.alfresco-share .yui-pbar .yui-pbar-bar {
    background-color: #003366 !important;
}

/* ---- YUI Overlay ---- */
.alfresco-share .yui-overlay {
    background-color: #ffffff !important;
    border: 1px solid #003366 !important;
}
```

#### 3.1.4 Add images

Place custom images (logo, icons, backgrounds) in the `images/` directory. Reference them in CSS using **relative paths** from the CSS file's location, e.g.:

```css
.alfresco-share .alfresco-header-Logo {
    background: url("images/logo.png") no-repeat left center !important;
}
```

---

### 3.2 Step 2: Create the Theme Descriptor XML

This is the **critical file** that makes your theme selectable in the Admin Console. Without it, the theme will not appear even if all CSS files are present.

Create the file at:

```
[project]-share/src/main/resources/alfresco/web-extension/site-data/themes/[theme-id].xml
```

```xml
<?xml version='1.0' encoding='UTF-8'?>
<theme>
   <title>[Theme Title Fallback]</title>
   <title-id>theme.[theme-id]</title-id>
   <css-tokens>
      <token>/themes/[theme-id]/presentation.css</token>
   </css-tokens>
</theme>
```

**Element Reference**

| Element | Description |
|---------|-------------|
| `<title>` | Fallback display name — used if the i18n label is not found |
| `<title-id>` | i18n key that maps to a message in the properties file (e.g., `theme.CUDCustomTheme`) |
| `<css-tokens>` | Container for one or more `<token>` child elements |
| `<token>` | Path to a CSS file, relative to the Share webapp root (e.g., `/themes/CUDCustomTheme/presentation.css`) |

> **Important:** The `<css-tokens>` **must** use `<token>` child elements as shown above. A simple string value like `<css-tokens>/themes/.../presentation.css</css-tokens>` will not work in all versions of Alfresco Share.

---

### 3.3 Step 3: Add the i18n Translation Label

The `<title-id>` element in the theme descriptor references an i18n key. This key must exist in a properties file that is loaded by Share's Spring resource bundle.

#### 3.3.1 Add the theme label to the properties file

Open or create:

```
[project]-share/src/main/resources/alfresco/web-extension/messages/[project]-share.properties
```

Add:

```properties
theme.[theme-id]=[Theme Display Name]
```

For example:

```properties
theme.CUDCustomTheme=CUD Custom Theme (Canadian University Dubai)
```

#### 3.3.2 Register the resource bundle in Spring context

Open the Share extension's Spring context file:

```
[project]-share/src/main/resources/alfresco/web-extension/[project]-slingshot-application-context.xml
```

Add a resource bundle bean (if not already present):

```xml
<bean id="com.[yourcompany].[project].resources"
      class="org.springframework.extensions.surf.util.ResourceBundleBootstrapComponent">
   <property name="resourceBundles">
      <list>
         <value>alfresco.web-extension.messages.[project]-share</value>
      </list>
   </property>
</bean>
```

The value `alfresco.web-extension.messages.[project]-share` maps to the file at `alfresco/web-extension/messages/[project]-share.properties`. The dots replace slashes and the `.properties` extension is omitted.

---

### 3.4 Step 4: (Optional) Register in share-config-custom.xml

The theme descriptor XML (Step 2) is the **primary** mechanism for registering a theme in the Admin Console. However, you may also want to add the theme to the `Themes` configuration in `share-config-custom.xml` if you need to:

- Set the theme as the default for all sites
- Define the theme in the JAR's `META-INF/share-config-custom.xml` for additional visibility

#### In the JAR module's `META-INF/share-config-custom.xml`

```xml
<config evaluator="string-compare" condition="Themes">
    <themes>
        <theme>
            <id>[theme-id]</id>
            <title>[Theme Title]</title>
            <title-id>theme.[theme-id]</title-id>
            <css-tokens>
                <token>/themes/[theme-id]/presentation.css</token>
            </css-tokens>
        </theme>
    </themes>
</config>

<!-- Set as default for all sites (optional) -->
<config evaluator="string-compare" condition="Site" replace="true">
    <theme>[theme-id]</theme>
</config>
```

#### If using Docker, update the runtime config

```
[project]-share-docker/src/main/docker/share-config-custom.xml
```

This file is mounted into the Share container at `tomcat/shared/classes/alfresco/web-extension/share-config-custom.xml`. It serves as the authoritative environment-specific configuration.

```xml
<config evaluator="string-compare" condition="Themes">
    <themes>
        <theme>
            <id>[theme-id]</id>
            <title>[Theme Title]</title>
            <title-id>theme.[theme-id]</title-id>
            <css-tokens>
                <token>/themes/[theme-id]/presentation.css</token>
            </css-tokens>
        </theme>
    </themes>
</config>
```

---

### 3.5 Step 5: Rebuild and Verify

```bash
./run.sh reload_share
```

This command:
1. Kills the running Share container
2. Runs `mvn clean package -pl [project]-share,[project]-share-docker`
3. Rebuilds the Docker image with the updated JAR containing the theme resources, descriptor, and i18n
4. Starts the new Share container

**Verification:**
1. Log into Alfresco Share as an **Administrator**
2. Navigate to **Admin Tools → Application → Theme**
3. Your custom theme will appear in the dropdown list
4. Select it and click **Apply**

---

## 4. Approach B — Extract LightTheme from JAR & Rename

This is the approach used to create the **CUDCustomTheme** in this project. It extracts the built-in LightTheme from the Alfresco Share JAR/WAR, copies it into the project, renames all identifiers, and registers it.

### 4.1 Step 1: Determine Alfresco Version

From your project's `pom.xml`:

```xml
<alfresco.share.version>26.1.0.45</alfresco.share.version>
```

The relevant JAR is `share-{version}-classes.jar` (or `share.war`) in your local Maven repository or Docker image.

### 4.2 Step 2: Locate or Pull the Share Image

The theme CSS/images are inside the Share webapp (not in the `-classes.jar`). They can be extracted from a running Docker container, or from the Share Docker image.

**Option A — From Docker image:**

```bash
# Pull the Share image matching your version
docker pull alfresco/alfresco-share:26.1.0

# Create a temporary container
docker create --name share-temp alfresco/alfresco-share:26.1.0

# Copy the exploded webapp to /tmp
docker cp share-temp:/usr/local/tomcat/webapps /tmp/share-webapps

# Clean up
docker rm -f share-temp
```

The LightTheme is now at `/tmp/share-webapps/share/themes/lightTheme/`.

**Option B — From Maven repository (if the WAR exists):**

```bash
# Find the WAR/JAR
find ~/.m2/repository/org/alfresco/share -name "share*.war" 2>/dev/null
find ~/.m2/repository/org/alfresco/share -name "share*.jar" 2>/dev/null

# Extract the theme
unzip ~/.m2/repository/org/alfresco/share/.../share-26.1.0.45.war \
  "themes/lightTheme/*"

# or from classes JAR (may not contain CSS resources)
unzip ~/.m2/repository/org/alfresco/share/.../share-26.1.0.45-classes.jar \
  "META-INF/resources/themes/lightTheme/*"
```

### 4.3 Step 3: Extract LightTheme from the JAR/WAR

Verify the theme structure:

```bash
ls -la /tmp/share-webapps/share/themes/lightTheme/
# Output should show:
#   images/
#   presentation.css
#   yui/
```

The LightTheme contains:
- `presentation.css` — Core structural CSS (~37KB, comprehensive styles)
- `yui/assets/skin.css` — YUI component skin (~30KB, ~633 `.yui-skin-lightTheme` selectors)
- `images/` — All theme images (app-logo, sprites, icons, etc.)
- `yui/assets/*.png|*.gif` — YUI sprite/asset images

### 4.4 Step 4: Copy into Project and Rename Folder

```bash
# Copy the LightTheme into your project as your new theme name
cp -r /tmp/share-webapps/share/themes/lightTheme \
  alfresco-custom-cud-project-share/src/main/resources/META-INF/resources/themes/CUDCustomTheme
```

Now the folder structure is:

```
alfresco-custom-cud-project-share/src/main/resources/META-INF/resources/themes/CUDCustomTheme/
├── images/          (17 files: alfresco.svg, app-logo.png, sprite.png, etc.)
├── presentation.css
└── yui/assets/
    ├── skin.css
    └── (35 image files: sprite.png, asc.gif, bg-h.gif, etc.)
```

### 4.5 Step 5: Rename Identifiers Inside CSS Files

The LightTheme uses `yui-skin-lightTheme` as its YUI CSS class selector name throughout both `presentation.css` and `yui/assets/skin.css`. These must be renamed to your theme name.

**Important:** Only rename the **theme identifier** (e.g., `lightTheme`, `LightTheme`) — NOT general CSS property values like `light`, `lighter`, `lightgray`, etc.

```bash
cd alfresco-custom-cud-project-share/src/main/resources/META-INF/resources/themes/CUDCustomTheme

# Replace the YUI skin class name in presentation.css
sed -i 's/yui-skin-lightTheme/yui-skin-CUDCustomTheme/g' presentation.css

# Replace the YUI skin class name in skin.css
sed -i 's/yui-skin-lightTheme/yui-skin-CUDCustomTheme/g' yui/assets/skin.css
```

Verify no stale references remain:

```bash
grep -rn "lightTheme\|LightTheme\|light-theme" presentation.css yui/assets/skin.css
# Should output nothing (exit code 1)
```

### 4.6 Step 6: Remove Old Custom Theme Resources

If replacing an existing custom theme, delete its CSS/images:

```bash
rm -rf alfresco-custom-cud-project-share/src/main/resources/META-INF/resources/themes/old-theme-id
```

Remove any empty old theme JS directories:

```bash
rmdir alfresco-custom-cud-project-share/src/main/resources/META-INF/resources/alfresco-custom-cud-project-share/js/old-theme-id/ 2>/dev/null
```

### 4.7 Step 7: Create/Update Theme Descriptor XML

Create or update:

```
alfresco-custom-cud-project-share/src/main/resources/alfresco/web-extension/site-data/themes/CUDCustomTheme.xml
```

```xml
<?xml version='1.0' encoding='UTF-8'?>
<theme>
   <title>CUD Custom Theme</title>
   <title-id>theme.CUDCustomTheme</title-id>
   <css-tokens>
      <!-- Base layout and structural styles (from LightTheme) -->
      <token>/themes/CUDCustomTheme/presentation.css</token>
      <!-- YUI skin overrides (from LightTheme) -->
      <token>/themes/CUDCustomTheme/yui/assets/skin.css</token>
   </css-tokens>
</theme>
```

**Note:** Unlike the old cud-theme which had individual component CSS files (buttons.css, header.css, etc.), the LightTheme combines everything into `presentation.css` and `skin.css`, so only those two token entries are needed.

### 4.8 Step 8: Update i18n Properties

In `alfresco-custom-cud-project-share/src/main/resources/alfresco/web-extension/messages/alfresco-custom-cud-project-share.properties`:

```properties
# Before (if old theme existed):
# theme.cud-theme=CUD Theme (Canadian University Dubai)

# After:
theme.CUDCustomTheme=CUD Custom Theme (Canadian University Dubai)
```

### 4.9 Step 9: Update share-config-custom.xml

In `alfresco-custom-cud-project-share/src/main/resources/META-INF/share-config-custom.xml`:

```xml
<!-- Custom theme registration -->
<config evaluator="string-compare" condition="Themes">
    <themes>
        <theme>
            <id>CUDCustomTheme</id>
            <title>CUD Custom Theme</title>
            <title-id>theme.CUDCustomTheme</title-id>
            <css-tokens>
                <token>/themes/CUDCustomTheme/presentation.css</token>
                <token>/themes/CUDCustomTheme/yui/assets/skin.css</token>
            </css-tokens>
        </theme>
    </themes>
</config>

<!-- Make CUDCustomTheme the default for all sites -->
<config evaluator="string-compare" condition="Site" replace="true">
    <theme>CUDCustomTheme</theme>
</config>
```

### 4.10 Step 10: Update Docker share-config-custom.xml

In `alfresco-custom-cud-project-share-docker/src/main/docker/share-config-custom.xml`:

```xml
<config evaluator="string-compare" condition="Themes">
    <themes>
        <theme>
            <id>CUDCustomTheme</id>
            <title>CUD Custom Theme</title>
            <title-id>theme.CUDCustomTheme</title-id>
            <css-tokens>
                <token>/themes/CUDCustomTheme/presentation.css</token>
                <token>/themes/CUDCustomTheme/yui/assets/skin.css</token>
            </css-tokens>
        </theme>
    </themes>
</config>

<config evaluator="string-compare" condition="WebFramework">
    <web-framework>
        <!-- Set CUDCustomTheme as the default theme for the whole Share app -->
        <defaults>
            <theme>CUDCustomTheme</theme>
        </defaults>
        ...
    </web-framework>
</config>
```

### 4.11 Step 11: Update Any Other References

Search the entire project for old theme references and update them:

```bash
grep -rn "old-theme-id" alfresco-custom-cud-project-share/src/ alfresco-custom-cud-project-share-docker/src/
```

Common places where old theme references may appear:
- Login page templates (`login.get.html.ftl`) — logo image paths
- Any custom FreeMarker templates referencing `/themes/old-theme-id/...`

Example fix:
```html
<!-- Before: -->
<img src="${url.context}/res/themes/cud-theme/images/logo.png" />

<!-- After: -->
<img src="${url.context}/res/themes/CUDCustomTheme/images/logo.png" />
```

### 4.12 Step 12: Rebuild and Verify

```bash
./run.sh reload_share
```

**Verification checks:**
1. Share container starts without errors
2. Navigate to **Admin Tools → Application → Theme** — "CUD Custom Theme (Canadian University Dubai)" appears
3. Select it and apply — the full LightTheme styling with your renamed theme is active
4. In browser DevTools → Network tab — verify CSS files load from:
   - `/share/themes/CUDCustomTheme/presentation.css`
   - `/share/themes/CUDCustomTheme/yui/assets/skin.css`
5. Check a Site → **Customize Site → Theme** — theme is selectable

---

## 5. Complete Working Example: CUDCustomTheme

The **CUD Custom Theme** (`CUDCustomTheme`) is a fully working theme implemented in this project. It was created by extracting the built-in **LightTheme** from the Share WAR and renaming it.

### Directory Structure

```
alfresco-custom-cud-project-share/src/main/resources/
├── META-INF/
│   ├── share-config-custom.xml              (Theme registration + default)
│   └── resources/themes/CUDCustomTheme/
│       ├── images/
│       │   ├── app-logo-48.png              (Share app icon)
│       │   ├── app-logo.png                 (Share app logo)
│       │   ├── alfresco.svg                 (Vector logo)
│       │   ├── sprite.png                   (UI sprite)
│       │   └── ... (13 more image files)
│       ├── presentation.css                 (Core structural CSS from LightTheme)
│       └── yui/assets/
│           ├── skin.css                     (YUI component skin from LightTheme)
│           └── *.png / *.gif                (35 YUI asset files)
│
└── alfresco/web-extension/
    ├── site-data/themes/
    │   └── CUDCustomTheme.xml          ★ Theme descriptor (REQUIRED)
    ├── messages/
    │   └── alfresco-custom-cud-project-share.properties  (i18n labels)
    └── alfresco-custom-cud-project-share-slingshot-application-context.xml (Spring context)
```

### File: `site-data/themes/CUDCustomTheme.xml`

```xml
<?xml version='1.0' encoding='UTF-8'?>
<theme>
   <title>CUD Custom Theme</title>
   <title-id>theme.CUDCustomTheme</title-id>
   <css-tokens>
      <!-- Base layout and structural styles (from LightTheme) -->
      <token>/themes/CUDCustomTheme/presentation.css</token>
      <!-- YUI skin overrides (from LightTheme) -->
      <token>/themes/CUDCustomTheme/yui/assets/skin.css</token>
   </css-tokens>
</theme>
```

### File: `messages/alfresco-custom-cud-project-share.properties`

```properties
theme.CUDCustomTheme=CUD Custom Theme (Canadian University Dubai)
```

### File: `alfresco-custom-cud-project-share-slingshot-application-context.xml`

```xml
<bean id="com.canadianuniversitydubai.alfresco-custom-cud-project-share.resources"
      class="org.springframework.extensions.surf.util.ResourceBundleBootstrapComponent">
   <property name="resourceBundles">
      <list>
         <value>alfresco.web-extension.messages.alfresco-custom-cud-project-share</value>
      </list>
   </property>
</bean>
```

### File: `META-INF/share-config-custom.xml` (Themes section)

```xml
<config evaluator="string-compare" condition="Themes">
    <themes>
        <theme>
            <id>CUDCustomTheme</id>
            <title>CUD Custom Theme</title>
            <title-id>theme.CUDCustomTheme</title-id>
            <css-tokens>
                <token>/themes/CUDCustomTheme/presentation.css</token>
                <token>/themes/CUDCustomTheme/yui/assets/skin.css</token>
            </css-tokens>
        </theme>
    </themes>
</config>

<config evaluator="string-compare" condition="Site" replace="true">
    <theme>CUDCustomTheme</theme>
</config>
```

### File: `alfresco-custom-cud-project-share-docker/src/main/docker/share-config-custom.xml` (Themes section)

```xml
<config evaluator="string-compare" condition="Themes">
    <themes>
        <theme>
            <id>CUDCustomTheme</id>
            <title>CUD Custom Theme</title>
            <title-id>theme.CUDCustomTheme</title-id>
            <css-tokens>
                <token>/themes/CUDCustomTheme/presentation.css</token>
                <token>/themes/CUDCustomTheme/yui/assets/skin.css</token>
            </css-tokens>
        </theme>
    </themes>
</config>
```

---

## 6. Troubleshooting

### Theme not appearing in Admin Console

| Possible Cause | Solution |
|---------------|----------|
| Missing theme descriptor XML | Create the file at `alfresco/web-extension/site-data/themes/[theme-id].xml` — this is **required** |
| Invalid XML in theme descriptor | Ensure valid XML with `<theme>`, `<title>`, `<title-id>`, `<css-tokens>`, and `<token>` elements |
| i18n label not found | Add `theme.[theme-id]=[Title]` to the properties file and verify the resource bundle is registered in Spring context |
| Incorrect `<title-id>` value | The value must match exactly (e.g., `theme.CUDCustomTheme` in XML must match `theme.CUDCustomTheme` in properties) |
| JAR not rebuilt | Run `./run.sh reload_share` to rebuild and redeploy |
| Docker container not restarted | The `reload_share` command handles this |

### CSS not being applied

| Possible Cause | Solution |
|---------------|----------|
| Missing `!important` | Add `!important` to all CSS rules to override Share's defaults |
| Missing `.alfresco-share` prefix | Prefix all selectors with `.alfresco-share` to scope them correctly |
| CSS file not loading | Open browser DevTools → Network tab and check if the CSS file URL returns 200 |
| Incorrect `<token>` path | The path should be `/themes/[theme-id]/presentation.css` (relative to Share webapp root) |
| Stale `yui-skin-lightTheme` class | After extracting LightTheme, you **must** rename `yui-skin-lightTheme` to `yui-skin-[YourThemeName]` in both CSS files |

### Image not loading

| Possible Cause | Solution |
|---------------|----------|
| Wrong relative path | Images in CSS use paths relative to the CSS file's location: `url("images/logo.png")` |
| Image not in JAR | Ensure the image file exists at `META-INF/resources/themes/[theme-id]/images/` |
| Image not rebuilt | Rebuild the JAR with `./run.sh reload_share` |

### i18n label not found

| Possible Cause | Solution |
|---------------|----------|
| Missing resource bundle bean | Add the `ResourceBundleBootstrapComponent` bean to the Spring context XML |
| Wrong property value path | The `resourceBundles` list value must be `alfresco.web-extension.messages.[project]-share` (dots replace slashes) |
| Missing or malformed key | Ensure the key `theme.[theme-id]` exists in the properties file with no typos |

### LightTheme extraction issues

| Issue | Solution |
|-------|----------|
| `-classes.jar` doesn't contain CSS/images | The `-classes.jar` only has compiled classes. CSS/images are in the full `share.war` or Docker image |
| `jar` command not found | Install JDK: `sudo apt-get install openjdk-17-jdk-headless`, or use `unzip` instead |
| `unzip` not found | Install: `sudo apt-get install -y unzip` |
| Docker image not available locally | Pull the matching version: `docker pull alfresco/alfresco-share:{version}` |
| Theme works locally but not after rebuild | Run a full purge and rebuild: `./run.sh stop && ./run.sh purge && ./run.sh build_start` |

---

## 7. Quick Reference: File Checklist

Before rebuilding, ensure all of the following files exist with correct content:

```
[project]-share/src/main/resources/
├── META-INF/resources/themes/[theme-id]/
│   ├── images/logo.png
│   ├── presentation.css
│   └── yui/assets/skin.css
├── alfresco/web-extension/
│   ├── site-data/themes/[theme-id].xml          ← REQUIRED
│   ├── messages/[project]-share.properties       ← Must contain "theme.[theme-id]=..."
│   └── [project]-slingshot-application-context.xml ← Must have resource bundle bean
└── META-INF/share-config-custom.xml               ← Optional (for site default)
```

After verifying all files, run:

```bash
./run.sh reload_share

# or for a full wipe and rebuild:
./run.sh stop
./run.sh purge
./run.sh build_start
```

Then log in as admin and navigate to **Admin Tools → Application → Theme** to select your custom theme.