
# CUD Document Management – Space Template Guide

**Alfresco SDK 4.11 AIO – Platform Module**

This guide describes how to bootstrap a complete **Space Template** named **CUD Document Management**, modelled after the classic out-of-the-box *Software Engineering Project* template.

---

## 1. Goal

Create a reusable folder structure under:

```
Repository → Data Dictionary → Space Templates → CUD Document Management
```

Users will then be able to use:

**Create → Create folder from template → CUD Document Management**

---

## 2. Recommended Structure

```
CUD Document Management/
├── Documentation/
│   ├── Drafts/
│   ├── Pending Approval/
│   ├── Published/
│   └── Samples/
├── Discussions/
├── UI Design/
├── Presentations/
└── Quality Assurance/
```

---

## 3. Project Layout (SDK 4.11 AIO)

Place the files in the **platform** module:

```
aio-platform/   (or your-platform-module-name)
└── src/main/resources/alfresco/module/<module.id>/
    ├── module.properties
    ├── context/
    │   └── bootstrap-context.xml
    └── bootstrap/
        └── cud-document-management-space-template.xml
```

> Replace `<module.id>` with the exact value from your `module.properties` (example: `aio-platform`).

---

## 4. Space Template XML

**File:**
`.../bootstrap/cud-document-management-space-template.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<view:view xmlns:view="http://www.alfresco.org/view/repository/1.0"
           xmlns:cm="http://www.alfresco.org/model/content/1.0"
           xmlns:app="http://www.alfresco.org/model/application/1.0">

   <cm:folder view:childName="cm:CUD Document Management">
      <app:uifacets/>
      <cm:name>CUD Document Management</cm:name>
      <cm:title>CUD Document Management</cm:title>
      <cm:description>Space template similar to Software Engineering Project</cm:description>
      <app:icon>space-icon-default</app:icon>

      <cm:contains>

         <!-- Documentation -->
         <cm:folder view:childName="cm:Documentation">
            <app:uifacets/>
            <cm:name>Documentation</cm:name>
            <app:icon>space-icon-default</app:icon>
            <cm:contains>
               <cm:folder view:childName="cm:Drafts">
                  <app:uifacets/>
                  <cm:name>Drafts</cm:name>
                  <app:icon>space-icon-default</app:icon>
               </cm:folder>
               <cm:folder view:childName="cm:Pending Approval">
                  <app:uifacets/>
                  <cm:name>Pending Approval</cm:name>
                  <app:icon>space-icon-default</app:icon>
               </cm:folder>
               <cm:folder view:childName="cm:Published">
                  <app:uifacets/>
                  <cm:name>Published</cm:name>
                  <app:icon>space-icon-default</app:icon>
               </cm:folder>
               <cm:folder view:childName="cm:Samples">
                  <app:uifacets/>
                  <cm:name>Samples</cm:name>
                  <app:icon>space-icon-doc</app:icon>
               </cm:folder>
            </cm:contains>
         </cm:folder>

         <!-- Discussions -->
         <cm:folder view:childName="cm:Discussions">
            <app:uifacets/>
            <cm:name>Discussions</cm:name>
            <app:icon>space-icon-default</app:icon>
         </cm:folder>

         <!-- UI Design -->
         <cm:folder view:childName="cm:UI Design">
            <app:uifacets/>
            <cm:name>UI Design</cm:name>
            <app:icon>space-icon-default</app:icon>
         </cm:folder>

         <!-- Presentations -->
         <cm:folder view:childName="cm:Presentations">
            <app:uifacets/>
            <cm:name>Presentations</cm:name>
            <app:icon>space-icon-default</app:icon>
         </cm:folder>

         <!-- Quality Assurance -->
         <cm:folder view:childName="cm:Quality Assurance">
            <app:uifacets/>
            <cm:name>Quality Assurance</cm:name>
            <app:icon>space-icon-default</app:icon>
         </cm:folder>

      </cm:contains>
   </cm:folder>

</view:view>
```

---

## 5. Bootstrap Configuration (Recommended – GenericBootstrapPatch)

**File:**
`.../context/bootstrap-context.xml`

```xml
<?xml version='1.0' encoding='UTF-8'?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
                           http://www.springframework.org/schema/beans/spring-beans.xsd">

    <!-- Force bootstrap of CUD Document Management space template -->
    <bean id="patch.cudDocumentManagementSpaceTemplate"
          class="org.alfresco.repo.admin.patch.impl.GenericBootstrapPatch"
          parent="basePatch">

        <property name="id">
            <value>patch.cudDocumentManagementSpaceTemplate</value>
        </property>
        <property name="description">
            <value>patch.cudDocumentManagementSpaceTemplate.description</value>
        </property>
        <property name="fixesFromSchema">
            <value>0</value>
        </property>
        <property name="fixesToSchema">
            <value>${version.schema}</value>
        </property>
        <property name="targetSchema">
            <value>100000</value>
        </property>

        <!-- Force execution even if previously applied -->
        <property name="force" value="true"/>

        <property name="importerBootstrap">
            <ref bean="spacesBootstrap"/>
        </property>

        <property name="bootstrapView">
            <props>
                <prop key="path">/${spaces.company_home.childname}/${spaces.dictionary.childname}/${spaces.templates.childname}</prop>
                <prop key="location">alfresco/module/${project.artifactId}/bootstrap/cud-document-management-space-template.xml</prop>
                <prop key="encoding">UTF-8</prop>
            </props>
        </property>
    </bean>

</beans>
```

> **Note:** `${project.artifactId}` is automatically replaced by Maven.
> If you prefer a hardcoded path, replace it with your actual module folder name.

---

## 6. Import the Context

Make sure `module-context.xml` (or the main context file of the platform module) imports the bootstrap context:

```xml
<import resource="classpath:alfresco/module/${project.artifactId}/context/bootstrap-context.xml"/>
```

---

## 7. Clean Restart (Required)

Because patches and module components often run only once, perform a clean start during development:

```bash
./run.sh stop

# Remove the persistent volume (name may vary)
docker volume ls | grep -i alf
docker volume rm <alf_data-volume-name>

./run.sh build_start
```

---

## 8. Verification

After Alfresco starts:

1. Go to **Repository → Data Dictionary → Space Templates**
2. Confirm that the folder **CUD Document Management** exists with the expected sub-folders
3. Test creation:
   In any Document Library → **Create → Create folder from template → CUD Document Management**

---

## 9. Optional Improvements

- Add rules (versioning, notifications, simple workflow) on Drafts / Pending Approval / Published
- Add sample documents inside the `Samples` folder
- Localise titles/descriptions via message bundles
- Change `force` back to `false` (or remove the property) for production
- Protect the template with permissions so only administrators can modify it

---

## 10. Troubleshooting Checklist

| Issue                  | Check                                                                    |
| ---------------------- | ------------------------------------------------------------------------ |
| Template not appearing | Module ID matches, context is imported, clean volume used                |
| Patch not running      | Look for`patch.cudDocumentManagementSpaceTemplate` in `alfresco.log` |
| Wrong location         | Confirm path tokens:`company_home` → `dictionary` → `templates`  |
| Module not loading     | Verify`module.properties` and that the JAR is present in the container |

---

**Document version:** 1.0
**Compatible with:** Alfresco SDK 4.11 / AIO platform module
**Based on:** Successful bootstrap using `GenericBootstrapPatch` (August 2026)
