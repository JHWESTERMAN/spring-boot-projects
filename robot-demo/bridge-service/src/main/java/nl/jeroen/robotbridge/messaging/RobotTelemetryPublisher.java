package nl.jeroen.robotbridge.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publiceert robot telemetrie naar ActiveMQ JMS queues.
 *
 * Queues:
 *  - robot.telemetry   : joint states (gethrottled: max 1/sec)
 *  - robot.events      : task events (altijd gepubliceerd)
 *  - robot.alerts      : fouten en alerts (altijd gepubliceerd)
 */
@Service
public class RobotTelemetryPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(RobotTelemetryPublisher.class);
    private static final long JOINT_THROTTLE_MS = 1000; // max 1 joint-state bericht per seconde

    private final JmsTemplate jmsTemplate;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong lastJointPublish = new AtomicLong(0);

    public RobotTelemetryPublisher(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public void publishJointState(double[] joints) {
        long now = System.currentTimeMillis();
        long last = lastJointPublish.get();

        // throttle: niet meer dan 1x per seconde
        if (now - last < JOINT_THROTTLE_MS) return;
        if (!lastJointPublish.compareAndSet(last, now)) return; // concurrent update

        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("timestamp", Instant.now().toString());
            payload.put("type", "JOINT_STATE");

            var posArray = payload.putArray("positions");
            for (double j : joints) posArray.add(Math.round(j * 1000.0) / 1000.0); // 3 decimalen

            String json = mapper.writeValueAsString(payload);
            jmsTemplate.convertAndSend("robot.telemetry", json);
            LOG.debug("JMS → robot.telemetry: {}", json);

        } catch (Exception e) {
            LOG.error("Kon joint state niet publiceren: {}", e.getMessage());
        }
    }

    public void publishEvent(String eventType, String message) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("timestamp", Instant.now().toString());
            payload.put("type", "ROBOT_EVENT");
            payload.put("eventType", eventType);
            payload.put("message", message);

            String json = mapper.writeValueAsString(payload);
            jmsTemplate.convertAndSend("robot.events", json);
            LOG.info("JMS → robot.events: [{}] {}", eventType, message);

        } catch (Exception e) {
            LOG.error("Kon event niet publiceren: {}", e.getMessage());
        }
    }

    public void publishAlert(String code, String message, String level) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("timestamp", Instant.now().toString());
            payload.put("type", "ROBOT_ALERT");
            payload.put("code", code);
            payload.put("message", message);
            payload.put("level", level);

            String json = mapper.writeValueAsString(payload);
            jmsTemplate.convertAndSend("robot.alerts", json);
            LOG.warn("JMS → robot.alerts: [{}] {} - {}", level, code, message);

        } catch (Exception e) {
            LOG.error("Kon alert niet publiceren: {}", e.getMessage());
        }
    }
}
