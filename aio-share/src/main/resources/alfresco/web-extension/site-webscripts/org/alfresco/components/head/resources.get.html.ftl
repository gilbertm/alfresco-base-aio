<#include "../component.head.inc">
<#-- RESOURCES - Customized: Dynamic favicon per theme + dynamic browser title -->
<@markup id="favicons">
   <link rel="shortcut icon" href="${url.context}/res/themes/${theme}/images/${msg(theme + '.favicon')!msg('CUDCustomTheme.favicon')}" type="image/vnd.microsoft.icon" />
   <link rel="icon" href="${url.context}/res/themes/${theme}/images/${msg(theme + '.favicon')!msg('CUDCustomTheme.favicon')}" type="image/vnd.microsoft.icon" />
</@markup>
<@markup id="yui">
   <@link rel="stylesheet" type="text/css" href="${url.context}/res/css/yui-fonts-grids.css" group="template-common" media="screen,print" />
   <@link rel="stylesheet" type="text/css" href="${url.context}/res/yui/columnbrowser/assets/columnbrowser.css" group="template-common" media="screen,print" />
   <@link rel="stylesheet" type="text/css" href="${url.context}/res/yui/columnbrowser/assets/skins/default/columnbrowser-skin.css" group="template-common" media="screen,print" />
   <#if theme = 'default'>
      <@link rel="stylesheet" type="text/css" href="${url.context}/res/yui/assets/skins/default/skin.css" group="template-common" media="screen,print" />
   <#else>
      <@link rel="stylesheet" type="text/css" href="${url.context}/res/themes/${theme}/yui/assets/skin.css" group="template-common" media="screen,print" />
   </#if>
   <@script type="text/javascript" src="${url.context}/res/js/yui-common.js" group="template-common"/>
   <@script type="text/javascript" src="${url.context}/res/yui/history/history.js" group="template-common"/>
   <@script type="text/javascript" src="${url.context}/res/js/bubbling.v2.1.js" group="template-common"/>
   <@inlineScript group="template-common">
      YAHOO.Bubbling.unsubscribe = function(layer, handler, scope) { this.bubble[layer].unsubscribe(handler, scope); };
   </@>
</@>
<@markup id="alfrescoConstants">
   <@inlineScript group="template-common">
      Alfresco.constants = Alfresco.constants || {};
      Alfresco.constants.DEBUG = ${DEBUG?string};
      Alfresco.constants.AUTOLOGGING = ${AUTOLOGGING?string};
      Alfresco.constants.PROXY_URI = window.location.protocol + "//" + window.location.host + "${url.context?js_string}/proxy/alfresco/";
      Alfresco.constants.PROXY_URI_RELATIVE = "${url.context?js_string}/proxy/alfresco/";
      Alfresco.constants.PROXY_FEED_URI = window.location.protocol + "//" + window.location.host + "${url.context?js_string}/proxy/alfresco-feed/";
      Alfresco.constants.THEME = "${theme}";
      Alfresco.constants.URL_CONTEXT = "${url.context?js_string}/";
      Alfresco.constants.URL_RESCONTEXT = "${url.context?js_string}/res/";
      Alfresco.constants.URL_PAGECONTEXT = "${url.context?js_string}/page/";
      Alfresco.constants.URL_SERVICECONTEXT = "${url.context?js_string}/service/";
      Alfresco.constants.URL_FEEDSERVICECONTEXT = "${url.context?js_string}/feedservice/";
      Alfresco.constants.USERNAME = "${(user.name!"")?js_string}";
      Alfresco.constants.SITE = "<#if page??>${(page.url.templateArgs.site!"")?url?js_string}</#if>";
      var regex = /^[a-zA-Z0-9-]+$/g;
      if ("<#if page??>${(page.url.templateArgs.site!"")?url?js_string}</#if>" && !regex.test(Alfresco.constants.SITE)) {
        redirectErrorPageUrl = window.location.protocol + "//" + window.location.host+ "/share/page/error500";
        window.location.href = redirectErrorPageUrl;
      }
      Alfresco.constants.PAGECONTEXT = "<#if page??>${(page.url.templateArgs.pagecontext!"")?url?js_string}</#if>";
      Alfresco.constants.PAGEID = "<#if page??>${(page.url.templateArgs.pageid!"")?url?js_string}</#if>";
      Alfresco.constants.JS_LOCALE = "${locale}";
      Alfresco.constants.USERPREFERENCES = "${preferences?js_string}";
      Alfresco.constants.CSRF_POLICY = { enabled: ${((config.scoped["CSRFPolicy"]["filter"].getChildren("rule")?size > 0)?string)!false}, cookie: "${config.scoped["CSRFPolicy"]["client"].getChildValue("cookie")!""}", header: "${config.scoped["CSRFPolicy"]["client"].getChildValue("header")!""}", parameter: "${config.scoped["CSRFPolicy"]["client"].getChildValue("parameter")!""}", properties: {} };
      Alfresco.constants.IFRAME_POLICY = { sameDomain: "${config.scoped["IFramePolicy"]["same-domain"].value!"allow"}", crossDomainUrls: [] };
      Alfresco.constants.HIDDEN_PICKER_VIEW_MODES = [];
      Alfresco.constants.MENU_ARROW_SYMBOL = "&#9662;";
      Alfresco.constants.TINY_MCE_SUPPORTED_LOCALES = "${config.global["I18N"].getChildValue("tiny-mce-supported-locales")}";
      Alfresco.constants.AIMS_ENABLED = false;
   </@>
</@>
<@markup id="alfrescoResources">
   <@link rel="stylesheet" type="text/css" href="${url.context}/res/css/base.css" group="template-common" media="screen,print" />
   <@link rel="stylesheet" type="text/css" href="${url.context}/res/css/yui-layout.css" group="template-common" media="screen,print" />
   <@script type="text/javascript" src="${url.context}/res/js/alfresco.js" group="template-common"/>
   <script type="text/javascript" src="<@checksumResource src="${url.context}/res/modules/editors/tinymce/tinymce.min.js" parameter="checksum"/>"></script>
   <@script type="text/javascript" src="${url.context}/res/modules/editors/tiny_mce.js" group="template-common"/>
   <@script type="text/javascript" src="${url.context}/res/modules/editors/yui_editor.js" group="template-common"/>
   <@script type="text/javascript" src="${url.context}/res/js/forms-runtime.js" group="template-common"/>
   <@link rel="stylesheet" type="text/css" href="${url.context}/res/components/form/form.css" />
</@>
<@markup id="shareConstants">
   <@inlineScript group="template-common">
      Alfresco.service.Preferences.FAVOURITE_DOCUMENTS = "org.alfresco.share.documents.favourites";
      Alfresco.service.Preferences.FAVOURITE_FOLDERS = "org.alfresco.share.folders.favourites";
      Alfresco.service.Preferences.FAVOURITE_SITES = "org.alfresco.share.sites.favourites";
      Alfresco.service.Preferences.COLLAPSED_TWISTERS = "org.alfresco.share.twisters.collapsed";
      Alfresco.constants.URI_TEMPLATES = {};
      Alfresco.constants.HELP_PAGES = {};
      Alfresco.constants.HTML_EDITOR = 'tinyMCE';
   </@>
</@>
<@markup id="shareResources">
   <@script type="text/javascript" src="${url.context}/res/js/share.js" group="template-common"/>
   <@script type="text/javascript" src="${url.context}/res/js/lightbox.js" group="template-common"/>
   <@link rel="stylesheet" type="text/css" href="${url.context}/res/themes/${theme}/presentation.css" group="template-common" />
   <@script src="${url.context}/res/modules/create-site.js" group="template-common"/>
   <@link rel="stylesheet" type="text/css" href="${url.context}/res/modules/create-site.css" group="template-common" />
   <@link rel="stylesheet" type="text/css" href="${url.context}/${sitedata.getDojoPackageLocation('dijit')}/themes/claro/claro.css" group="share" forceAggregation="true"/>
</@>
<@markup id="resources">
   <script type="text/javascript">//<![CDATA[YAHOO.util.Event.onDOMReady(function(){var t="${msg(theme + '.title')!msg('page.title.default')}";if(t&&t!=="page.title.default"){document.title=t;}});//]]></script>
</@>
