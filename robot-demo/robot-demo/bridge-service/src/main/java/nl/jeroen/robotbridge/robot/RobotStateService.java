package nl.jeroen.robotbridge.robot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.jeroen.robotbridge.messaging.RobotTelemetryPublisher;
import nl.jeroen.robotbridge.ros.RosBridgeClient;
import nl.jeroen.robotbridge.ros.RosMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;

@Service
public class RobotStateService {

    private static final Logger LOG = LoggerFactory.getLogger(RobotStateService.class);

    private final RosBridgeClient rosBridgeClient;
    private final RobotTelemetryPublisher telemetryPublisher;
    private final RobotState state = new RobotState();
    private final ObjectMapper mapper = new ObjectMapper();

    // warehouse state
    private volatile double robotX = 5.0;
    private volatile double robotY = 5.0;
    private volatile List<Map<String, Object>> warehouseGrid = new ArrayList<>();
    private volatile List<Map<String, Object>> plannedRoute  = new ArrayList<>();
    private volatile String targetLocationId = "";
    private volatile String targetDockId     = "";

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

    public RobotState getState() { return state; }

    // warehouse getters
    public double getRobotX()                              { return robotX; }
    public double getRobotY()                              { return robotY; }
    public List<Map<String, Object>> getWarehouseGrid()    { return warehouseGrid; }
    public List<Map<String, Object>> getPlannedRoute()     { return plannedRoute; }
    public String getTargetLocationId()                    { return targetLocationId; }
    public String getTargetDockId()                        { return targetDockId; }

    private void handleMessage(RosMessage msg) {
        switch (msg.getTopic()) {
            case "/robot_events"        -> handleEvent(msg.getMsg());
            case "/robot_alerts"        -> handleAlert(msg.getMsg());
            case "/robot_state"         -> handleScenarioState(msg.getMsg());
            case "/warehouse_position"  -> handleWarehousePosition(msg.getMsg());
            case "/warehouse_grid"      -> handleWarehouseGrid(msg.getMsg());
            case "/warehouse_route"     -> handleWarehouseRoute(msg.getMsg());
            default -> LOG.trace("Onbekend topic: {}", msg.getTopic());
        }
    }

    private void handleEvent(JsonNode msg) {
        String data = msg.get("data").asText();
        String[] parts = data.split("\\|", 3);
        if (parts.length < 2) return;
        String eventType = parts[0];
        String message   = parts[1];
        LOG.info("Robot event: [{}] {}", eventType, message);
        state.setLastEvent(eventType + ": " + message);
        if ("CYCLE_COMPLETE".equals(eventType)) state.incrementCycleCount();
        if ("FAULT_CLEARED".equals(eventType))  state.clearFault();
        telemetryPublisher.publishEvent(eventType, message);
    }

    private void handleAlert(JsonNode msg) {
        int level      = msg.get("level").asInt();
        String message = msg.get("message").asText();
        String code    = "";
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
        if (parts.length < 1) return;
        state.setScenarioState(parts[0]);
    }

    private void handleWarehousePosition(JsonNode msg) {
        robotX = msg.get("x").asDouble();
        robotY = msg.get("y").asDouble();
    }

    private void handleWarehouseGrid(JsonNode msg) {
        try {
            String json = msg.get("data").asText();
            List<Map<String, Object>> grid = mapper.readValue(json,
                mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            warehouseGrid = grid;
        } catch (Exception e) {
            LOG.warn("Kon warehouse grid niet parsen: {}", e.getMessage());
        }
    }

    private void handleWarehouseRoute(JsonNode msg) {
        try {
            List<Map<String, Object>> route = new ArrayList<>();
            JsonNode routeArray = msg.get("route");
            if (routeArray != null && routeArray.isArray()) {
                for (JsonNode node : routeArray) {
                    Map<String, Object> loc = new LinkedHashMap<>();
                    loc.put("id", node.get("id").asText());
                    loc.put("x",  node.get("x").asInt());
                    loc.put("y",  node.get("y").asInt());
                    route.add(loc);
                }
            }
            plannedRoute     = route;
            targetLocationId = msg.get("target").asText();
            targetDockId     = msg.get("dock").asText();
        } catch (Exception e) {
            LOG.warn("Kon route niet parsen: {}", e.getMessage());
        }
    }
}
