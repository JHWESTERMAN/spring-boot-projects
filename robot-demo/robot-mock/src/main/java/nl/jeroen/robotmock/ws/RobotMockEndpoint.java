package nl.jeroen.robotmock.ws;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import nl.jeroen.robotmock.publisher.RosBridgeProtocol;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WebSocket endpoint op ws://localhost:9090/
 *
 * Simuleert rosbridge_suite: accepteert meerdere clients,
 * broadcast joint states en events naar alle verbonden sessies.
 */
@ServerEndpoint("/")
public class RobotMockEndpoint {

    private static final Logger LOG = Logger.getLogger(RobotMockEndpoint.class.getName());

    // thread-safe set van alle actieve sessies
    private static final Set<Session> SESSIONS = new CopyOnWriteArraySet<>();

    @OnOpen
    public void onOpen(Session session) {
        SESSIONS.add(session);
        LOG.info("Client verbonden: " + session.getId() + " (totaal: " + SESSIONS.size() + ")");
    }

    @OnClose
    public void onClose(Session session) {
        SESSIONS.remove(session);
        LOG.info("Client verbroken: " + session.getId() + " (totaal: " + SESSIONS.size() + ")");
    }

    @OnError
    public void onError(Session session, Throwable t) {
        LOG.log(Level.WARNING, "WebSocket fout voor " + session.getId(), t);
        SESSIONS.remove(session);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        // rosbridge stuurt subscribe/advertise berichten — wij loggen alleen
        LOG.fine("Van client " + session.getId() + ": " + message);
    }

    /**
     * Broadcast een JSON bericht naar alle verbonden clients.
     * Wordt aangeroepen vanuit de publisher thread.
     */
    public static void broadcast(String json) {
        for (Session session : SESSIONS) {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(json);
                } catch (IOException e) {
                    LOG.warning("Kon niet sturen naar " + session.getId() + ": " + e.getMessage());
                    SESSIONS.remove(session);
                }
            }
        }
    }

    public static int getConnectedClients() {
        return SESSIONS.size();
    }
}
