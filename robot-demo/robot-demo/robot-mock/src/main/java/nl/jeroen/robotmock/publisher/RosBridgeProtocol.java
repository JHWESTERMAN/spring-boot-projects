package nl.jeroen.robotmock.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;

/**
 * Bouwt rosbridge v2.0 JSON berichten.
 *
 * rosbridge protocol spec:
 *   { "op": "publish", "topic": "/joint_states", "msg": { ... } }
 */
public class RosBridgeProtocol {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // /joint_states — sensor_msgs/JointState
    public static String jointState(double[] positions) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("op", "publish");
        root.put("topic", "/joint_states");

        ObjectNode msg = root.putObject("msg");

        // header
        ObjectNode header = msg.putObject("header");
        ObjectNode stamp = header.putObject("stamp");
        long epochSeconds = Instant.now().getEpochSecond();
        stamp.put("sec", epochSeconds);
        stamp.put("nanosec", 0);
        header.put("frame_id", "base_link");

        // joint namen (UR5-achtige 6-DOF arm)
        ArrayNode names = msg.putArray("name");
        names.add("shoulder_pan_joint");
        names.add("shoulder_lift_joint");
        names.add("elbow_joint");
        names.add("wrist_1_joint");
        names.add("wrist_2_joint");
        names.add("wrist_3_joint");

        // posities
        ArrayNode pos = msg.putArray("position");
        for (double p : positions) pos.add(p);

        // velocity en effort leeg (niet gesimuleerd)
        msg.putArray("velocity");
        msg.putArray("effort");

        return MAPPER.writeValueAsString(root);
    }

    // /robot_events — std_msgs/String (custom event)
    public static String event(String eventType, String message) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("op", "publish");
        root.put("topic", "/robot_events");

        ObjectNode msg = root.putObject("msg");
        msg.put("data", eventType + "|" + message + "|" + Instant.now().toEpochMilli());

        return MAPPER.writeValueAsString(root);
    }

    // /robot_alerts — diagnostics_msgs/DiagnosticStatus
    public static String alert(String code, String message, String level) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("op", "publish");
        root.put("topic", "/robot_alerts");

        ObjectNode msg = root.putObject("msg");
        // level: 0=OK, 1=WARN, 2=ERROR, 3=STALE
        msg.put("level", "ERROR".equals(level) ? 2 : "WARN".equals(level) ? 1 : 0);
        msg.put("name", "robot_mock");
        msg.put("message", message);
        msg.put("hardware_id", "mock_arm_001");

        ArrayNode values = msg.putArray("values");
        ObjectNode kv = values.addObject();
        kv.put("key", "error_code");
        kv.put("value", code);

        return MAPPER.writeValueAsString(root);
    }
}

    // DEZE METHODEN WORDEN TOEGEVOEGD DOOR WarehouseScenarioEngine SUPPORT

    // /robot_state — warehouse state
    public static String scenarioState(String state, int cycleCount) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("op", "publish");
        root.put("topic", "/robot_state");
        ObjectNode msg = root.putObject("msg");
        msg.put("data", state + "|" + cycleCount + "|" + Instant.now().toEpochMilli());
        return MAPPER.writeValueAsString(root);
    }

    // /warehouse_position — huidige x,y positie van de robot in het grid
    public static String warehousePosition(double x, double y, String state) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("op", "publish");
        root.put("topic", "/warehouse_position");
        ObjectNode msg = root.putObject("msg");
        msg.put("x",         Math.round(x * 100.0) / 100.0);
        msg.put("y",         Math.round(y * 100.0) / 100.0);
        msg.put("state",     state);
        msg.put("timestamp", Instant.now().toEpochMilli());
        return MAPPER.writeValueAsString(root);
    }

    // /warehouse_grid — snapshot van het magazijn grid (locaties, voorraad)
    public static String warehouseGrid(java.util.List<java.util.Map<String, Object>> grid) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("op", "publish");
        root.put("topic", "/warehouse_grid");
        ObjectNode msg = root.putObject("msg");
        msg.put("data", MAPPER.writeValueAsString(grid));
        return MAPPER.writeValueAsString(root);
    }

    // /warehouse_route — geplande route als lijst van locatie IDs
    public static String warehouseRoute(java.util.List<nl.jeroen.robotmock.warehouse.Location> route,
                                         String targetId, String dockId) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("op", "publish");
        root.put("topic", "/warehouse_route");
        ObjectNode msg = root.putObject("msg");
        ArrayNode routeArray = msg.putArray("route");
        for (nl.jeroen.robotmock.warehouse.Location loc : route) {
            ObjectNode node = routeArray.addObject();
            node.put("id", loc.getId());
            node.put("x",  loc.getX());
            node.put("y",  loc.getY());
        }
        msg.put("target", targetId != null ? targetId : "");
        msg.put("dock",   dockId   != null ? dockId   : "");
        return MAPPER.writeValueAsString(root);
}
