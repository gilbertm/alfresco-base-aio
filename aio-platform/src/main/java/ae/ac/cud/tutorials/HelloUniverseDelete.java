/**
 * AIO Platform — HelloUniverse Delete
 * ====================================
 * File: aio-platform/src/main/java/ae/ac/cud/tutorials/HelloUniverseDelete.java
 * Role: DELETE /sample/hellouniverse/{id} — delete a universe record
 * Registered in: webscript-context.xml
 */
package ae.ac.cud.tutorials;

import java.util.Map;

import org.alfresco.service.cmr.repository.NodeRef;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;

public class HelloUniverseDelete extends HelloUniverseBase {

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {
        Map<String, Object> model = new java.util.HashMap<>();
        try {
            String id = req.getParameter("id");
            NodeRef nodeRef = resolveNodeRef(id);
            nodeService.deleteNode(nodeRef);
            JSONObject resp = new JSONObject();
            resp.put("deleted", true);
            resp.put("id", id);
            model.put("result", resp.toString(2));
        } catch (Exception e) {
            JSONObject err = new JSONObject();
            err.put("error", e.getMessage());
            model.put("result", err.toString(2));
        }
        return model;
    }
}