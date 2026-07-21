/**
 * AIO Platform — HelloUniverse Get
 * =================================
 * File: aio-platform/src/main/java/ae/ac/cud/tutorials/HelloUniverseGet.java
 * Role: GET /sample/hellouniverse/{id} — retrieve a single record
 * Registered in: webscript-context.xml
 */
package ae.ac.cud.tutorials;

import java.util.Map;

import org.alfresco.service.cmr.repository.NodeRef;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;

public class HelloUniverseGet extends HelloUniverseBase {

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {
        Map<String, Object> model = new java.util.HashMap<>();
        try {
            String id = req.getParameter("id");
            NodeRef nodeRef = resolveNodeRef(id);
            model.put("result", nodeToJson(nodeRef).toString(2));
        } catch (Exception e) {
            JSONObject err = new JSONObject();
            err.put("error", e.getMessage());
            model.put("result", err.toString(2));
        }
        return model;
    }
}