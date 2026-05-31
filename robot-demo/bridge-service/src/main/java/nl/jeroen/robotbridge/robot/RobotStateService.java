package nl.jeroen.robotbridge.robot;

import com.fasterxml.jackson.databind.JsonNode;
import nl.jeroen.robotbridge.messaging.RobotTelemetryPublisher;
import nl.jeroen.robotbridge.ros.RosBridgeClient;
import nl.jeroen.robotbridge.ros.RosMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Centrale service die:
 * 1. Zich registreert als handler bij RosBridgeClient
 * 2. Inkomende berichten per topic verwerkt
 * 3. RobotState bijwerkt
 * 4. Relevante events naar JMS publiceert
 */
@Service
public class RobotStateService {

    private static final Logger LOG = LoggerFactory.getLogger(RobotStateService.class);

    private final RosBridgeClient rosBridgeClient;
    private final RobotTelemetryPublisher telemetryPublisher;
    private final RobotState state = new RobotState();


    public RobotStateService(RosBridgeClient rosBridgeClient,
                             RobotTelemetryPublisher telemetryPublisher) {
        this.rosBridgeClient    = rosBridgeClient;
        this.telemetryPublisher = telemetryPublisher;
    }

    @PostConstruct
    public void init() {
        rosBridgeClient.addMessageHandler(this::handleMessage);
        LOG.info("RobotStateService geregistreerd als message handler");

    }

    public RobotState getState() {
        return state;
    }

    // ── message routing per topic ─────────────────────────────────────────────

    private void handleMessage(RosMessage msg) {
        switch (msg.getTopic()) {
            case "/joint_states"  -> handleJointState(msg.getMsg());
            case "/robot_events"  -> handleEvent(msg.getMsg());
            case "/robot_alerts"  -> handleAlert(msg.getMsg());
            case "/robot_state" -> handleScenarioState(msg.getMsg());
            default               -> LOG.trace("Onbekend topic: {}", msg.getTopic());
        }
    }

    private void handleJointState(JsonNode msg) {
        JsonNode positions = msg.get("position");
        if (positions == null || !positions.isArray()) return;

        double[] joints = new double[positions.size()];
        for (int i = 0; i < joints.length; i++) {
            joints[i] = positions.get(i).asDouble();
        }
        state.setJointPositions(joints);

        // Telemetrie elke seconde (niet elke 100ms — filtering op JMS kant)
        // De publisher beslist zelf over throttling
        telemetryPublisher.publishJointState(joints);
    }

    private void handleEvent(JsonNode msg) {
        String data = msg.get("data").asText();
        // formaat: "EVENTTYPE|message|timestamp"
        String[] parts = data.split("\\|", 3);
        if (parts.length < 2) return;

        String eventType = parts[0];
        String message   = parts[1];

        LOG.info("Robot event: [{}] {}", eventType, message);
        state.setLastEvent(eventType + ": " + message);

        if ("CYCLE_COMPLETE".equals(eventType)) {
            state.incrementCycleCount();
        }
        if ("FAULT_CLEARED".equals(eventType)) {
            state.clearFault();
        }

        // Publiceer alle events naar JMS
        telemetryPublisher.publishEvent(eventType, message);
    }

    private void handleAlert(JsonNode msg) {
        int level       = msg.get("level").asInt();
        String message  = msg.get("message").asText();
        String code     = "";

        JsonNode values = msg.get("values");
        if (values != null && values.isArray() && values.size() > 0) {
            code = values.get(0).get("value").asText();
        }

        String levelStr = level == 2 ? "ERROR" : level == 1 ? "WARN" : "OK";
        LOG.warn("Robot alert [{}] {}: {}", levelStr, code, message);
        state.setLastAlert("[" + levelStr + "] " + code + ": " + message);

        telemetryPublisher.publishAlert(code, message, levelStr);
    }
    private void handleScenarioState(JsonNode msg) {
        String data = msg.get("data").asText();
        String[] parts = data.split("\\|", 3);
        if (parts.length < 2) return;
        state.setScenarioState(parts[0]);
    }
}
