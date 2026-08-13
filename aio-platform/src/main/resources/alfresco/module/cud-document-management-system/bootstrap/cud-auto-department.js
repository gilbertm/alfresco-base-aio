// cud-auto-department.js
// Triggered by rule on "CUD Document Management" Space Template root
// when a new Folder is created inside it.

(function () {
    if (!document || !document.isContainer) {
        logger.warn("CUD: not a folder – aborting");
        return;
    }

    var deptName = document.name;                                 // e.g. "President"
    var safeName = deptName.replace(/[^a-zA-Z0-9]/g, "_");        // safe group short-name

    logger.log("=== CUD auto-department starting for: " + deptName + " ===");

    // ------------------------------------------------------------------
    // Helper: create folder if it does not exist
    // ------------------------------------------------------------------
    function ensureFolder(parent, name) {
        var f = parent.childByNamePath(name);
        if (f == null) {
            f = parent.createFolder(name);
            f.properties["cm:title"] = name;
            f.save();
            logger.log("Created folder: " + name);
        }
        return f;
    }

    // ------------------------------------------------------------------
    // Helper: create group if it does not exist (idempotent)
    // ------------------------------------------------------------------
    function ensureGroup(shortName, displayName) {
        var fullId = "GROUP_" + shortName;
        var g = groups.getGroup(fullId);
        if (g == null) {
            // createRootGroup(shortName, displayName)
            groups.createRootGroup(shortName, displayName);
            g = groups.getGroup(fullId);
            logger.log("Created group: " + fullId + " (" + displayName + ")");
        } else {
            logger.log("Group already exists: " + fullId);
        }
        return fullId;   // always return the authority name to use in setPermission
    }

    // ------------------------------------------------------------------
    // 1. Create the four lifecycle folders
    // ------------------------------------------------------------------
    var draft     = ensureFolder(document, deptName + "_Draft");
    var review    = ensureFolder(document, deptName + "_Review");
    var published = ensureFolder(document, deptName + "_Published");
    var archive   = ensureFolder(document, deptName + "_Archive");

    // ------------------------------------------------------------------
    // 2. Create the four groups (exactly as requested)
    // ------------------------------------------------------------------
    var groupContributors = ensureGroup(safeName + "_Contributors", deptName + " Contributors");
    var groupReviewers    = ensureGroup(safeName + "_Reviewers",    deptName + " Reviewers");
    var groupManagers     = ensureGroup(safeName + "_Managers",     deptName + " Managers");
    var groupReaders      = ensureGroup(safeName + "_Readers",      deptName + " Readers");

    // ------------------------------------------------------------------
    // 3. Apply permissions
    // ------------------------------------------------------------------
    // Draft
    draft.setInheritsPermissions(false);
    draft.setPermission("Collaborator", groupContributors);
    draft.setPermission("Consumer",     groupReviewers);
    draft.setPermission("Consumer",     groupManagers);
    draft.setPermission("Consumer",     groupReaders);
    draft.save();

    // Review
    review.setInheritsPermissions(false);
    review.setPermission("Collaborator", groupReviewers);
    review.setPermission("Collaborator", groupManagers);
    review.setPermission("Consumer",     groupContributors);
    review.setPermission("Consumer",     groupReaders);
    review.save();

    // Published
    published.setInheritsPermissions(false);
    published.setPermission("Consumer",     groupContributors);
    published.setPermission("Consumer",     groupReviewers);
    published.setPermission("Consumer",     groupReaders);
    published.setPermission("Collaborator", groupManagers);   // managers can request revision
    published.save();

    // Archive
    archive.setInheritsPermissions(false);
    archive.setPermission("Consumer",    groupContributors);
    archive.setPermission("Consumer",    groupReviewers);
    archive.setPermission("Consumer",    groupReaders);
    archive.setPermission("Coordinator", groupManagers);
    archive.save();

    // ------------------------------------------------------------------
    // 4. Optional – make everything versionable
    // ------------------------------------------------------------------
    [draft, review, published, archive].forEach(function (f) {
        if (!f.hasAspect("cm:versionable")) {
            f.addAspect("cm:versionable");
        }
    });

    logger.log("=== CUD auto-department finished for: " + deptName + " ===");
    logger.log("Groups created/used:");
    logger.log("  " + groupContributors);
    logger.log("  " + groupReviewers);
    logger.log("  " + groupManagers);
    logger.log("  " + groupReaders);
})();