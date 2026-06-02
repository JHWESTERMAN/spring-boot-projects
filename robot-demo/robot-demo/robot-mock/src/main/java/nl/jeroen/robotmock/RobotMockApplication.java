package nl.jeroen.robotmock;

import nl.jeroen.robotmock.publisher.RosBridgeProtocol;
import nl.jeroen.robotmock.warehouse.Warehouse;
import nl.jeroen.robotmock.warehouse.WarehouseScenarioEngine;
import nl.jeroen.robotmock.ws.RobotMockEndpoint;
import org.glassfish.tyrus.server.Server;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * RobotMock — warehouse pick scenario.
 *
 * Simuleert een magazijnrobot die pick orders uitvoert in een 10x10 grid.
 * Publiceert via rosbridge v2.0 WebSocket protocol.
 */
public class RobotMockApplication {

    private static final Logger LOG = Logger.getLogger(RobotMockApplication.class.getName());
    private static final int  DEFAULT_PORT       = 9090;
    private static final long PUBLISH_INTERVAL_MS = 100;  // 10 Hz
    private static final int  GRID_PUBLISH_EVERY  = 50;   // elke 5 seconden grid sturen

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        Warehouse warehouse = new Warehouse();

        WarehouseScenarioEngine engine = new WarehouseScenarioEngine(warehouse,
            (eventType, message) -> {
                try {
                    String json = RosBridgeProtocol.event(eventType, message);
                    RobotMockEndpoint.broadcast(json);
                    LOG.info("EVENT [" + eventType + "] " + message);
                } catch (Exception e) {
                    LOG.warning("Kon event niet broadcasten: " + e.getMessage());
                }
            });

        // Start WebSocket server
        Server server = new Server("localhost", port, "/", null, RobotMockEndpoint.class);
        server.start();
        LOG.info("══════════════════════════════════════════════════");
        LOG.info("  RobotMock (Warehouse) gestart op ws://localhost:" + port + "/");
        LOG.info("  Topics: /warehouse_position, /warehouse_grid,");
        LOG.info("          /warehouse_route, /robot_events, /robot_state");
        LOG.info("══════════════════════════════════════════════════");

        AtomicInteger tickCount = new AtomicInteger(0);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "robot-publisher");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                int tick = tickCount.incrementAndGet();

                // 1. Engine tick
                engine.tick();

                // 2. Positie publiceren (elke tick)
                String posJson = RosBridgeProtocol.warehousePosition(
                    engine.getRobotX(), engine.getRobotY(),
                    engine.getState().name());
                RobotMockEndpoint.broadcast(posJson);

                // 3. State publiceren (elke tick)
                String stateJson = RosBridgeProtocol.scenarioState(
                    engine.getState().name(), engine.getOrdersCompleted());
                RobotMockEndpoint.broadcast(stateJson);

                // 4. Route publiceren (elke tick)
                String routeJson = RosBridgeProtocol.warehouseRoute(
                    engine.getPlannedRoute(),
                    engine.getTargetLocation() != null ? engine.getTargetLocation().getId() : null,
                    engine.getTargetDock()     != null ? engine.getTargetDock().getId()     : null);
                RobotMockEndpoint.broadcast(routeJson);

                // 5. Grid snapshot elke 5 seconden
                if (tick % GRID_PUBLISH_EVERY == 0) {
                    String gridJson = RosBridgeProtocol.warehouseGrid(warehouse.getGridSnapshot());
                    RobotMockEndpoint.broadcast(gridJson);
                }

            } catch (Exception e) {
                LOG.warning("Publisher fout: " + e.getMessage());
            }
        }, 0, PUBLISH_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // Status reporter
        ScheduledExecutorService statusReporter = Executors.newSingleThreadScheduledExecutor();
        statusReporter.scheduleAtFixedRate(() ->
            LOG.info(String.format("Status: state=%-22s clients=%d orders=%d",
                engine.getState(), RobotMockEndpoint.getConnectedClients(),
                engine.getOrdersCompleted())),
            5, 5, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdownNow();
            statusReporter.shutdownNow();
            server.stop();
        }));

        Thread.currentThread().join();
    }
}
