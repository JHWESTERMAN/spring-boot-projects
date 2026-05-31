package nl.jeroen.robotbridge.robot;

import nl.jeroen.robotbridge.ros.RosBridgeClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API voor de bridge service.
 *
 * GET  /robot/status       — huidige robot staat
 * GET  /robot/joints       — ruwe joint posities
 * GET  /robot/health       — bridge verbindingsstatus
 */
@RestController
@RequestMapping("/robot")
public class RobotController {

    private final RobotStateService stateService;
    private final RosBridgeClient rosBridgeClient;

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

    @GetMapping("/joints")
    public ResponseEntity<Map<String, Object>> getJoints() {
        RobotState state = stateService.getState();

        String[] names = {
            "shoulder_pan", "shoulder_lift", "elbow",
            "wrist_1", "wrist_2", "wrist_3"
        };
        double[] positions = state.getJointPositions();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", Instant.now().toString());

        Map<String, Double> joints = new LinkedHashMap<>();
        for (int i = 0; i < names.length && i < positions.length; i++) {
            joints.put(names[i], Math.round(positions[i] * 1000.0) / 1000.0);
        }
        response.put("joints", joints);

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
