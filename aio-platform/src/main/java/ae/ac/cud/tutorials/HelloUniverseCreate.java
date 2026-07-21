/**
 * AIO Platform — HelloUniverse Create
 * ====================================
 * File: aio-platform/src/main/java/ae/ac/cud/tutorials/HelloUniverseCreate.java
 * Role: POST /sample/hellouniverse — create a new universe record
 * Registered in: webscript-context.xml
 */
package ae.ac.cud.tutorials;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.namespace.QName;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;

public class HelloUniverseCreate extends HelloUniverseBase {

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {
        Map<String, Object> model = new HashMap<>();
        try {
            JSONObject body = readJsonBody(req);
            String planetName = body.optString("planetName", null);
            if (planetName == null || planetName.isEmpty()) {
                status.setCode(Status.STATUS_BAD_REQUEST);
                model.put("result", new JSONObject().put("error", "planetName is required").toString(2));
                return model;
            }

            Map<QName, Serializable> props = new HashMap<>();
            props.put(ContentModel.PROP_NAME, planetName + ".txt");
            props.put(PROP_PLANET_NAME, planetName);
            if (body.has("galaxy")) props.put(PROP_GALAXY, body.optString("galaxy"));
            if (body.has("distanceFromEarth")) props.put(PROP_DISTANCE, body.optDouble("distanceFromEarth", 0));
            if (body.has("habitable")) props.put(PROP_HABITABLE, body.optBoolean("habitable", false));

            // Find Company Home under the store root
            NodeRef storeRoot = nodeService.getRootNode(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
            NodeRef parent = storeRoot;
            for (ChildAssociationRef childAssoc : nodeService.getChildAssocs(storeRoot)) {
                if ("Company Home".equals(nodeService.getProperty(childAssoc.getChildRef(), ContentModel.PROP_NAME))) {
                    parent = childAssoc.getChildRef();
                    break;
                }
            }
            QName typeQName = QName.createQName(TYPE_QNAME);
            ChildAssociationRef child = nodeService.createNode(
                    parent,
                    ContentModel.ASSOC_CONTAINS,
                    QName.createQName(NAMESPACE, QName.createValidLocalName(planetName)),
                    typeQName,
                    props
            );

            JSONObject resp = nodeToJson(child.getChildRef());
            resp.put("created", true);
            model.put("result", resp.toString(2));
        } catch (Exception e) {
            JSONObject err = new JSONObject();
            err.put("error", e.getMessage());
            model.put("result", err.toString(2));
        }
        return model;
    }
}