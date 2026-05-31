package nl.jeroen.robotmock.scenario;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * State machine die een pick→move→place robotarm cyclus simuleert.
 *
 * Per state houdt de engine bij:
 *  - wat de doelpositie van de 6 joints is (in radialen)
 *  - hoeveel ticks de state duurt
 *  - welk event er aan het einde gefired wordt
 *
 * Elke tick (100ms) interpoleren de joints lineair richting het doel.
 * Met een instelbare kans gooit de FaultInjector een FAULT event.
 */
public class ScenarioEngine {

    private static final Logger LOG = Logger.getLogger(ScenarioEngine.class.getName());

    // 6-DOF arm joint hoeken per state [radialen]
    private static final double[] HOME_POSE   = { 0.0,  -1.57,  1.57, -1.57, -1.57,  0.0 };
    private static final double[] PICK_POSE   = { 0.5,  -1.0,   1.2,  -1.3,  -1.57,  0.3 };
    private static final double[] GRASP_POSE  = { 0.5,  -0.8,   1.0,  -1.1,  -1.57,  0.3 };
    private static final double[] PLACE_POSE  = {-0.5,  -1.0,   1.2,  -1.3,  -1.57, -0.3 };
    private static final double[] RELEASE_POSE= {-0.5,  -0.8,   1.0,  -1.1,  -1.57, -0.3 };

    // ticks per state (1 tick = 100ms)
    private static final int TICKS_MOVE  = 30;   // 3 seconden bewegen
    private static final int TICKS_ACT   = 10;   // 1 seconde grijpen/loslaten

    // kans op een fault per tick (1 = 1%)
    private static final double FAULT_PROBABILITY = 0.005;
    // ticks dat de robot in FAULT blijft voor auto-recover
    private static final int FAULT_RECOVERY_TICKS = 20;

    private final double[] currentJoints = HOME_POSE.clone();
    private final AtomicReference<ScenarioState> state = new AtomicReference<>(ScenarioState.IDLE);
    private final Random random = new Random();

    // Callback: (eventType, message)
    private final BiConsumer<String, String> eventCallback;

    private double[] targetJoints = HOME_POSE.clone();
    private int ticksInState = 0;
    private int stateDuration = 0;
    private int faultRecoveryCounter = 0;
    private int cycleCount = 0;

    public ScenarioEngine(BiConsumer<String, String> eventCallback) {
        this.eventCallback = eventCallback;
    }

    /**
     * Wordt elke 100ms aangeroepen vanuit de publisher thread.
     * Retourneert de huidige joint waarden (kopie).
     */
    public synchronized double[] tick() {
        ScenarioState current = state.get();

        if (current == ScenarioState.FAULT) {
            handleFaultState();
            return currentJoints.clone();
        }

        // Kans op plotselinge fault (niet in IDLE)
        if (current != ScenarioState.IDLE && random.nextDouble() < FAULT_PROBABILITY) {
            enterFault("Random hardware fault detected on joint " + (random.nextInt(6) + 1));
            return currentJoints.clone();
        }

        interpolateJoints();
        ticksInState++;

        if (ticksInState >= stateDuration) {
            advance(current);
        }

        return currentJoints.clone();
    }

    public ScenarioState getState() {
        return state.get();
    }

    public int getCycleCount() {
        return cycleCount;
    }

    // ── private state transitions ─────────────────────────────────────────────

    private void advance(ScenarioState current) {
        switch (current) {
            case IDLE -> transitionTo(ScenarioState.MOVING_TO_PICK, PICK_POSE, TICKS_MOVE, null);
            case MOVING_TO_PICK -> transitionTo(ScenarioState.PICKING, GRASP_POSE, TICKS_ACT,
                    "TASK_EVENT:Moving to pick position complete");
            case PICKING -> transitionTo(ScenarioState.MOVING_TO_PLACE, PLACE_POSE, TICKS_MOVE,
                    "TASK_EVENT:Object grasped successfully");
            case MOVING_TO_PLACE -> transitionTo(ScenarioState.PLACING, RELEASE_POSE, TICKS_ACT,
                    "TASK_EVENT:Moving to place position complete");
            case PLACING -> transitionTo(ScenarioState.RETURNING_HOME, HOME_POSE, TICKS_MOVE,
                    "TASK_EVENT:Object placed successfully");
            case RETURNING_HOME -> {
                cycleCount++;
                LOG.info("Cycle " + cycleCount + " complete. Pausing in IDLE...");
                fireEvent("CYCLE_COMPLETE", "Pick-place cycle #" + cycleCount + " completed");
                transitionTo(ScenarioState.IDLE, HOME_POSE, 30, null); // 3s pauze
            }
            default -> {}
        }
    }

    private void transitionTo(ScenarioState next, double[] target, int ticks, String eventMsg) {
        state.set(next);
        targetJoints = target.clone();
        stateDuration = ticks;
        ticksInState = 0;
        LOG.info("State → " + next);
        if (eventMsg != null) {
            String[] parts = eventMsg.split(":", 2);
            fireEvent(parts[0], parts.length > 1 ? parts[1] : eventMsg);
        }
    }

    private void enterFault(String reason) {
        state.set(ScenarioState.FAULT);
        faultRecoveryCounter = 0;
        LOG.warning("FAULT: " + reason);
        fireEvent("FAULT", reason);
        fireAlert("E_STOP", "Emergency stop triggered: " + reason);
    }

    private void handleFaultState() {
        faultRecoveryCounter++;
        if (faultRecoveryCounter >= FAULT_RECOVERY_TICKS) {
            LOG.info("Auto-recovering from fault...");
            fireEvent("FAULT_CLEARED", "Robot recovered, returning to IDLE");
            transitionTo(ScenarioState.IDLE, HOME_POSE, 30, null);
        }
    }

    private void interpolateJoints() {
        double progress = stateDuration == 0 ? 1.0 : (double) ticksInState / stateDuration;
        for (int i = 0; i < currentJoints.length; i++) {
            // lineaire interpolatie per joint
            double start = currentJoints[i]; // huidige positie
            double end   = targetJoints[i];
            currentJoints[i] = start + (end - start) * (1.0 / Math.max(1, stateDuration - ticksInState + 1));
        }
    }

    private void fireEvent(String type, String message) {
        eventCallback.accept(type, message);
    }

    private void fireAlert(String code, String message) {
        eventCallback.accept("ALERT:" + code, message);
    }
}
