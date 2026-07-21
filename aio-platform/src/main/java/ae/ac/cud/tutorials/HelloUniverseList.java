/**
 * AIO Platform — HelloUniverse List
 * ==================================
 * File: aio-platform/src/main/java/ae/ac/cud/tutorials/HelloUniverseList.java
 * Role: GET /sample/hellouniverse — list all universe records
 * Registered in: webscript-context.xml
 */
package ae.ac.cud.tutorials;

import java.util.Map;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchParameters;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;

public class HelloUniverseList extends HelloUniverseBase {

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {
        Map<String, Object> model = new java.util.HashMap<>();
        try {
            SearchParameters sp = new SearchParameters();
            sp.setQuery("TYPE:\"" + TYPE_QNAME + "\"");
            sp.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
            sp.setLanguage("fts-alfresco");
            ResultSet results = searchService.query(sp);

            JSONArray items = new JSONArray();
            for (NodeRef nodeRef : results.getNodeRefs()) {
                items.put(nodeToJson(nodeRef));
            }
            results.close();

            JSONObject resp = new JSONObject();
            resp.put("count", items.length());
            resp.put("items", items);
            model.put("result", resp.toString(2));
        } catch (Exception e) {
            JSONObject err = new JSONObject();
            err.put("error", e.getMessage());
            model.put("result", err.toString(2));
        }
        return model;
    }
}