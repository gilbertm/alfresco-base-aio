/**
 * AIO Platform — HelloUniverse Base Controller
 * =============================================
 * File: aio-platform/src/main/java/ae/ac/cud/tutorials/HelloUniverseBase.java
 * Role: Base class for HelloUniverse CRUD web scripts
 * Injects: NodeService, NamespaceService, SearchService
 * Used by: HelloUniverseList, HelloUniverseGet, HelloUniverseCreate, HelloUniverseUpdate, HelloUniverseDelete
 */
package ae.ac.cud.tutorials;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;

public abstract class HelloUniverseBase extends DeclarativeWebScript {

    protected static final String NAMESPACE = "http://www.acme.org/model/aio/1.0";
    static final String TYPE_QNAME = "{http://www.acme.org/model/aio/1.0}universeRecord";
    static final QName PROP_PLANET_NAME = QName.createQName(NAMESPACE, "planetName");
    static final QName PROP_GALAXY = QName.createQName(NAMESPACE, "galaxy");
    static final QName PROP_DISTANCE = QName.createQName(NAMESPACE, "distanceFromEarth");
    static final QName PROP_HABITABLE = QName.createQName(NAMESPACE, "habitable");

    protected NodeService nodeService;
    protected NamespaceService namespaceService;
    protected SearchService searchService;

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setNamespaceService(NamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }

    public void setSearchService(SearchService searchService) {
        this.searchService = searchService;
    }

    protected NodeRef resolveNodeRef(String id) {
        if (id == null || id.isEmpty()) {
            throw new WebScriptException(Status.STATUS_BAD_REQUEST, "Missing 'id' parameter");
        }
        NodeRef nodeRef = null;
        if (id.startsWith("workspace://")) {
            nodeRef = new NodeRef(id);
        } else if (id.contains("/")) {
            nodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, id);
        } else {
            nodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, id);
        }
        if (!nodeService.exists(nodeRef)) {
            throw new WebScriptException(Status.STATUS_NOT_FOUND, "Node not found: " + id);
        }
        return nodeRef;
    }

    protected JSONObject nodeToJson(NodeRef nodeRef) {
        JSONObject json = new JSONObject();
        json.put("id", nodeRef.getId());
        json.put("nodeRef", nodeRef.toString());
        json.put("name", nodeService.getProperty(nodeRef, ContentModel.PROP_NAME));

        Map<QName, Serializable> props = nodeService.getProperties(nodeRef);
        json.put("planetName", stringOrNull(props.get(PROP_PLANET_NAME)));
        json.put("galaxy", stringOrNull(props.get(PROP_GALAXY)));
        Object dist = props.get(PROP_DISTANCE);
        json.put("distanceFromEarth", dist instanceof Double ? ((Double) dist).doubleValue() : (dist != null ? Double.parseDouble(dist.toString()) : JSONObject.NULL));
        Object hab = props.get(PROP_HABITABLE);
        json.put("habitable", hab instanceof Boolean ? ((Boolean) hab).booleanValue() : false);
        return json;
    }

    protected void applyProperties(NodeRef nodeRef, JSONObject body) {
        Map<QName, Serializable> props = new HashMap<>();
        if (body.has("planetName")) props.put(PROP_PLANET_NAME, body.optString("planetName", null));
        if (body.has("galaxy")) props.put(PROP_GALAXY, body.optString("galaxy", null));
        if (body.has("distanceFromEarth")) props.put(PROP_DISTANCE, body.optDouble("distanceFromEarth", 0));
        if (body.has("habitable")) props.put(PROP_HABITABLE, body.optBoolean("habitable", false));
        if (!props.isEmpty()) {
            for (Map.Entry<QName, Serializable> e : props.entrySet()) {
                nodeService.setProperty(nodeRef, e.getKey(), e.getValue());
            }
        }
    }

    protected JSONObject readJsonBody(org.springframework.extensions.webscripts.WebScriptRequest req) {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(req.getContent().getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return new JSONObject(sb.toString());
        } catch (java.io.IOException e) {
            throw new WebScriptException(Status.STATUS_BAD_REQUEST, "Failed to read request body");
        }
    }

    private String stringOrNull(Object val) {
        return val != null ? val.toString() : null;
    }
}