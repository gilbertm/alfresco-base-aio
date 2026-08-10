// Official Aikau header customization pattern
// Removes the My Sites menu item for non-admins (the extension module already restricts this file)

widgetUtils.deleteObjectFromArray(model.jsonModel, "id", "HEADER_SITES_MENU");
widgetUtils.deleteObjectFromArray(model.jsonModel, "id", "HEADER_MY_SITES");

// Register debug-user service for browser console logging
model.jsonModel.services.push("aio-share-debug/debug-user");

if (user && user.userName && !user.isAdmin)
{
   // Remove the top-level "My Sites" entry if present
   widgetUtils.deleteObjectFromArray(model.jsonModel, "id", "HEADER_MY_SITES");

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