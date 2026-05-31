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
    // /robot_state — stuurt de huidige scenario state
    public static String scenarioState(String state, int cycleCount) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("op", "publish");
        root.put("topic", "/robot_state");

        ObjectNode msg = root.putObject("msg");
        msg.put("data", state + "|" + cycleCount + "|" + Instant.now().toEpochMilli());

        return MAPPER.writeValueAsString(root);
    }
}
