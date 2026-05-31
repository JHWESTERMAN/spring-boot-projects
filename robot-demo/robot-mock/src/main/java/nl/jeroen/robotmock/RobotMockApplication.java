package nl.jeroen.robotmock;

import nl.jeroen.robotmock.publisher.RosBridgeProtocol;
import nl.jeroen.robotmock.scenario.ScenarioEngine;
import nl.jeroen.robotmock.scenario.ScenarioState;
import nl.jeroen.robotmock.ws.RobotMockEndpoint;
import org.glassfish.tyrus.server.Server;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * RobotMock — lichtgewicht vervanger voor Gazebo + rosbridge_suite.
 *
 * Start een WebSocket server op ws://localhost:9090/ en publiceert
 * elke 100ms joint states + events in rosbridge v2.0 JSON formaat.
 *
 * Gebruik: java -jar robot-mock.jar [port]
 */
public class RobotMockApplication {

    private static final Logger LOG = Logger.getLogger(RobotMockApplication.class.getName());
    private static final int DEFAULT_PORT = 9090;
    private static final long PUBLISH_INTERVAL_MS = 100; // 10 Hz

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        // Scenario engine met event callback
        ScenarioEngine engine = new ScenarioEngine((eventType, message) -> {
            try {
                String json;
                if (eventType.startsWith("ALERT:")) {
                    String code = eventType.substring(6);
                    json = RosBridgeProtocol.alert(code, message, "ERROR");
                } else {
                    json = RosBridgeProtocol.event(eventType, message);
                }
                RobotMockEndpoint.broadcast(json);
                LOG.info("EVENT [" + eventType + "] " + message);
            } catch (Exception e) {
                LOG.warning("Kon event niet broadcasten: " + e.getMessage());
            }
        });

        // Start Tyrus WebSocket server
        Server server = new Server("localhost", port, "/", null, RobotMockEndpoint.class);
        server.start();
        LOG.info("══════════════════════════════════════════");
        LOG.info("  RobotMock gestart op ws://localhost:" + port + "/");
        LOG.info("  Simuleert rosbridge_suite v2.0");
        LOG.info("  Topics: /joint_states, /robot_events, /robot_alerts");
        LOG.info("══════════════════════════════════════════");

        // Publisher loop: elke 100ms een tick + broadcast
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "robot-publisher");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                double[] joints = engine.tick();

                // Publiceer joint states naar alle clients
                String jointJson = RosBridgeProtocol.jointState(joints);
                RobotMockEndpoint.broadcast(jointJson);

                String stateJson = RosBridgeProtocol.scenarioState(
                        engine.getState().name(), engine.getCycleCount());
                RobotMockEndpoint.broadcast(stateJson);

                // Status log elke 5 seconden (50 ticks)
                // (wordt gefilterd door de publisher zelf via tickcount)

            } catch (Exception e) {
                LOG.warning("Publisher fout: " + e.getMessage());
            }
        }, 0, PUBLISH_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // Status reporter: elke 5 seconden
        ScheduledExecutorService statusReporter = Executors.newSingleThreadScheduledExecutor();
        statusReporter.scheduleAtFixedRate(() -> {
            LOG.info(String.format(
                "Status: state=%-20s clients=%d cycles=%d",
                engine.getState(),
                RobotMockEndpoint.getConnectedClients(),
                engine.getCycleCount()
            ));
        }, 5, 5, TimeUnit.SECONDS);

        // Wacht op Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Stopping RobotMock...");
            scheduler.shutdownNow();
            statusReporter.shutdownNow();
            server.stop();
        }));

        Thread.currentThread().join();
    }
}
