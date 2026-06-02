package nl.jeroen.robotbridge.robot;

import nl.jeroen.robotbridge.ros.RosBridgeClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/robot")
public class RobotController {

    private final RobotStateService stateService;
    private final RosBridgeClient   rosBridgeClient;

    public RobotController(RobotStateService stateService, RosBridgeClient rosBridgeClient) {
        this.stateService    = stateService;
        this.rosBridgeClient = rosBridgeClient;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        RobotState state = stateService.getState();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp",      Instant.now().toString());
        response.put("scenarioState",  state.getScenarioState());
        response.put("cycleCount",     state.getCycleCount());
        response.put("faultActive",    state.isFaultActive());
        response.put("lastEvent",      state.getLastEvent());
        response.put("lastAlert",      state.getLastAlert());
        response.put("lastUpdate",     state.getLastUpdate().toString());
        response.put("bridgeConnected", rosBridgeClient.isConnected());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/warehouse")
    public ResponseEntity<Map<String, Object>> getWarehouse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp",        Instant.now().toString());
        response.put("robotX",           Math.round(stateService.getRobotX() * 100.0) / 100.0);
        response.put("robotY",           Math.round(stateService.getRobotY() * 100.0) / 100.0);
        response.put("state",            stateService.getState().getScenarioState());
        response.put("grid",             stateService.getWarehouseGrid());
        response.put("route",            stateService.getPlannedRoute());
        response.put("targetLocationId", stateService.getTargetLocationId());
        response.put("targetDockId",     stateService.getTargetDockId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        boolean connected = rosBridgeClient.isConnected();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status",    connected ? "UP" : "DEGRADED");
        response.put("bridge",    connected ? "connected" : "disconnected");
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(connected ? 200 : 503).body(response);
    }
}
