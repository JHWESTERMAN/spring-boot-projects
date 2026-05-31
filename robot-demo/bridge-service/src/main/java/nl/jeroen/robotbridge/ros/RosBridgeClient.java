package nl.jeroen.robotbridge.ros;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * WebSocket client die verbinding maakt met de RobotMock (of echte rosbridge).
 *
 * Ondersteunt:
 * - Auto-reconnect bij verbroken verbinding
 * - Meerdere message handlers (één per topic)
 * - Graceful shutdown
 */
@Component
public class RosBridgeClient {

    private static final Logger LOG = LoggerFactory.getLogger(RosBridgeClient.class);

    @Value("${robot.mock.url:ws://localhost:9090/}")
    private String mockUrl;

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Consumer<RosMessage>> handlers = new ArrayList<>();

    private WebSocketClient wsClient;
    private final ScheduledExecutorService reconnectScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "ws-reconnect"));

    @PostConstruct
    public void connect() {
        tryConnect();
    }

    public void addMessageHandler(Consumer<RosMessage> handler) {
        handlers.add(handler);
    }

    private void tryConnect() {
        try {
            wsClient = new WebSocketClient(new URI(mockUrl)) {

                @Override
                public void onOpen(ServerHandshake handshake) {
                    LOG.info("Verbonden met RobotMock op {}", mockUrl);
                }

                @Override
                public void onMessage(String raw) {
                    dispatch(raw);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    LOG.warn("WebSocket gesloten (code={}, reason={}). Reconnect over 5s...", code, reason);
                    scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    LOG.error("WebSocket fout: {}", ex.getMessage());
                }
            };
            wsClient.connect();
        } catch (Exception e) {
            LOG.error("Kon niet verbinden met {}: {}", mockUrl, e.getMessage());
            scheduleReconnect();
        }
    }

    private void dispatch(String raw) {
        try {
            RosMessage msg = mapper.readValue(raw, RosMessage.class);
            if ("publish".equals(msg.getOp())) {
                handlers.forEach(h -> h.accept(msg));
            }
        } catch (Exception e) {
            LOG.warn("Kon bericht niet parsen: {}", e.getMessage());
        }
    }

    private void scheduleReconnect() {
        reconnectScheduler.schedule(this::tryConnect, 5, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void disconnect() {
        reconnectScheduler.shutdownNow();
        if (wsClient != null && wsClient.isOpen()) {
            wsClient.close();
        }
    }

    public boolean isConnected() {
        return wsClient != null && wsClient.isOpen();
    }
}
