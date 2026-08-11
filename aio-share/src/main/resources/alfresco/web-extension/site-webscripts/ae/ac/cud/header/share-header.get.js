// Official Aikau header customization pattern
// Removes the My Sites menu item for non-admins (the extension module already restricts this file)

// Log user details to server log (catalina.out / share.log)
logger.log("=== share-header.get.js is executing ===");
logger.log("Current user: " + (user ? user.userName : "null"));

// Check whether the Sites menu exists
var sitesMenu = widgetUtils.findObject(model.jsonModel, "id", "HEADER_SITES_MENU");
logger.log("HEADER_SITES_MENU found: " + (sitesMenu != null));

widgetUtils.deleteObjectFromArray(model.jsonModel, "id", "HEADER_SITES_MENU");
widgetUtils.deleteObjectFromArray(model.jsonModel, "id", "HEADER_TASKS");
widgetUtils.deleteObjectFromArray(model.jsonModel, "id", "HEADER_PEOPLE");

logger.log("HEADER_SITES_MENU deleted");

if (user && user.userName && !user.isAdmin)
{
   // Remove the top-level "My Sites" entry if present
   widgetUtils.deleteObjectFromArray(model.jsonModel, "id", "HEADER_SITES_MENU");
   widgetUtils.deleteObjectFromArray(model.jsonModel, "id", "HEADER_TASKS");
   widgetUtils.deleteObjectFromArray(model.jsonModel, "id", "HEADER_PEOPLE");

   // Also clean the Sites menu itself (common related items)
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