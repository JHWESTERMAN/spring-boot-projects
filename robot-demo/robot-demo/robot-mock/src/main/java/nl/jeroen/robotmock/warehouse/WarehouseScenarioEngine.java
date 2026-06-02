package nl.jeroen.robotmock.warehouse;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Warehouse pick scenario engine.
 *
 * De robot voert pick orders uit in een 10x10 magazijn:
 *
 *   IDLE
 *     → ontvangt pick order met meerdere orderregels
 *     → plant route via Nearest Neighbour
 *   NAVIGATING_TO_PICK
 *     → beweegt naar volgende picklocatie
 *   PICKING
 *     → pakt product van rek
 *   NAVIGATING_TO_DOCK
 *     → alle items gepicked → rijdt naar dichtstbijzijnde dock
 *   DELIVERING
 *     → zet order klaar bij dock
 *   IDLE
 *     → klaar, wacht op volgende order
 */
public class WarehouseScenarioEngine {

    private static final Logger LOG = Logger.getLogger(WarehouseScenarioEngine.class.getName());

    public enum State {
        IDLE, NAVIGATING_TO_PICK, PICKING, NAVIGATING_TO_DOCK, DELIVERING
    }

    private static final int TICKS_PER_CELL  = 8;   // ticks om 1 cel te bewegen (bij 100ms = 0.8s)
    private static final int TICKS_PICKING   = 15;  // ticks om te picken
    private static final int TICKS_DELIVERING= 10;  // ticks om te leveren
    private static final int TICKS_IDLE      = 30;  // ticks wachten voor nieuwe order

    private final Warehouse            warehouse;
    private final BiConsumer<String, String> eventCallback;
    private final Random               random = new Random();

    // robot positie (decimaal voor smooth beweging)
    private double robotX = 5.0;
    private double robotY = 5.0;

    private State   state      = State.IDLE;
    private int     ticksInState = 0;
    private int     stateDuration = TICKS_IDLE;
    private int     ordersCompleted = 0;
    private int     totalFaults     = 0;

    // huidige order
    private PickOrder    currentOrder    = null;
    private Location     targetLocation  = null;
    private Location     targetDock      = null;
    private List<Location> plannedRoute  = new ArrayList<>();

    public WarehouseScenarioEngine(Warehouse warehouse,
                                   BiConsumer<String, String> eventCallback) {
        this.warehouse     = warehouse;
        this.eventCallback = eventCallback;
    }

    // ── main tick ─────────────────────────────────────────────────────────────

    public synchronized void tick() {
        ticksInState++;

        switch (state) {
            case IDLE               -> handleIdle();
            case NAVIGATING_TO_PICK -> handleNavigating();
            case PICKING            -> handlePicking();
            case NAVIGATING_TO_DOCK -> handleNavigatingToDock();
            case DELIVERING         -> handleDelivering();
        }
    }

    // ── state handlers ────────────────────────────────────────────────────────

    private void handleIdle() {
        if (ticksInState >= stateDuration) {
            startNewOrder();
        }
    }

    private void handleNavigating() {
        moveTowards(targetLocation.getX(), targetLocation.getY());

        if (hasArrived(targetLocation.getX(), targetLocation.getY())) {
            transitionTo(State.PICKING, TICKS_PICKING);
            PickOrderLine line = currentOrder.getCurrentLine();
            line.start();
            fireEvent("ARRIVED_AT_PICK",
                "Arrived at " + targetLocation.getId() +
                " for " + line.getQuantity() + "x " + line.getProductType());
        }
    }

    private void handlePicking() {
        if (ticksInState >= stateDuration) {
            PickOrderLine line = currentOrder.getCurrentLine();
            targetLocation.pick(line.getQuantity());
            line.complete();

            fireEvent("PICKED",
                line.getQuantity() + "x " + line.getProductType() +
                " picked from " + targetLocation.getId());

            boolean hasNext = currentOrder.advanceLine();
            if (hasNext) {
                navigateToNextLine();
            } else {
                navigateToDock();
            }
        }
    }

    private void handleNavigatingToDock() {
        moveTowards(targetDock.getX(), targetDock.getY());

        if (hasArrived(targetDock.getX(), targetDock.getY())) {
            transitionTo(State.DELIVERING, TICKS_DELIVERING);
            fireEvent("ARRIVED_AT_DOCK",
                "Arrived at dock " + targetDock.getId() + " for delivery");
        }
    }

    private void handleDelivering() {
        if (ticksInState >= stateDuration) {
            ordersCompleted++;
            fireEvent("ORDER_DELIVERED",
                "Order " + currentOrder.getId() + " delivered at dock " +
                targetDock.getId() + " (" + currentOrder.getTotalLines() + " lines)");
            fireEvent("CYCLE_COMPLETE",
                "Pick order #" + ordersCompleted + " complete");
            currentOrder = null;
            transitionTo(State.IDLE, TICKS_IDLE);
        }
    }

    // ── order planning ────────────────────────────────────────────────────────

    private void startNewOrder() {
        PickOrder order = generateRandomOrder();
        boolean planned = planRoute(order);

        if (!planned) {
            LOG.warning("Kon geen route plannen — onvoldoende voorraad");
            transitionTo(State.IDLE, TICKS_IDLE);
            return;
        }

        currentOrder = order;
        currentOrder.start();

        fireEvent("ORDER_STARTED",
            "Order " + order.getId() + " started: " +
            order.getTotalLines() + " pick lines, route: " + routeDescription());

        navigateToNextLine();
    }

    private PickOrder generateRandomOrder() {
        List<PickOrderLine> lines = new ArrayList<>();
        int numLines = 2 + random.nextInt(3); // 2-4 orderregels

        String[] products = Warehouse.PRODUCT_TYPES;
        Set<String> usedProducts = new HashSet<>();

        for (int i = 0; i < numLines; i++) {
            String product;
            int attempts = 0;
            do {
                product = products[random.nextInt(products.length)];
                attempts++;
            } while (usedProducts.contains(product) && attempts < 10);

            usedProducts.add(product);
            int qty = 1 + random.nextInt(3); // 1-3 stuks
            lines.add(new PickOrderLine(product, qty));
        }

        return new PickOrder(lines);
    }

    private boolean planRoute(PickOrder order) {
        double currentX = robotX;
        double currentY = robotY;

        List<Location> pickLocations = new ArrayList<>();

        // zoek voor elke orderregel de dichtstbijzijnde locatie
        for (PickOrderLine line : order.getLines()) {
            Optional<Location> loc = warehouse.findNearestLocation(
                line.getProductType(), line.getQuantity(), currentX, currentY);

            if (loc.isEmpty()) {
                LOG.warning("Geen locatie gevonden voor " + line.getProductType());
                return false;
            }

            line.assignLocation(loc.get());
            pickLocations.add(loc.get());
            currentX = loc.get().getX();
            currentY = loc.get().getY();
        }

        // optimaliseer de route via Nearest Neighbour
        plannedRoute = warehouse.optimizeRoute(robotX, robotY, pickLocations);
        return true;
    }

    private void navigateToNextLine() {
        PickOrderLine line = currentOrder.getCurrentLine();
        targetLocation = line.getAssignedLocation();

        int distance = (int) Math.ceil(
            new Location(0, 0, Location.Type.AISLE)
                .distanceTo(targetLocation) + robotX + robotY -
                Math.min(robotX, targetLocation.getX()) -
                Math.min(robotY, targetLocation.getY()));

        int travelTicks = Math.max(5, (int)(
            Math.ceil(Math.abs(robotX - targetLocation.getX()) +
                      Math.abs(robotY - targetLocation.getY())) * TICKS_PER_CELL / 2));

        transitionTo(State.NAVIGATING_TO_PICK, travelTicks);
        fireEvent("NAVIGATING",
            "Navigating to " + targetLocation.getId() +
            " for " + line.getQuantity() + "x " + line.getProductType());
    }

    private void navigateToDock() {
        targetDock = warehouse.findNearestDock(robotX, robotY);

        int travelTicks = Math.max(5, (int)(
            Math.ceil(Math.abs(robotX - targetDock.getX()) +
                      Math.abs(robotY - targetDock.getY())) * TICKS_PER_CELL / 2));

        transitionTo(State.NAVIGATING_TO_DOCK, travelTicks);
        fireEvent("NAVIGATING_TO_DOCK",
            "All items picked, navigating to dock " + targetDock.getId());
    }

    // ── beweging ──────────────────────────────────────────────────────────────

    private void moveTowards(double tx, double ty) {
        double speed = 0.15; // cellen per tick
        double dx = tx - robotX;
        double dy = ty - robotY;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist > speed) {
            robotX += (dx / dist) * speed;
            robotY += (dy / dist) * speed;
        } else {
            robotX = tx;
            robotY = ty;
        }
    }

    private boolean hasArrived(double tx, double ty) {
        return Math.abs(robotX - tx) < 0.2 && Math.abs(robotY - ty) < 0.2;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void transitionTo(State next, int duration) {
        LOG.info("State → " + next + " (duration: " + duration + " ticks)");
        state         = next;
        stateDuration = duration;
        ticksInState  = 0;
    }

    private void fireEvent(String type, String message) {
        eventCallback.accept(type, message);
    }

    private String routeDescription() {
        if (plannedRoute.isEmpty()) return "empty";
        StringBuilder sb = new StringBuilder();
        for (Location loc : plannedRoute) {
            if (sb.length() > 0) sb.append(" → ");
            sb.append(loc.getId());
        }
        return sb.toString();
    }

    // ── getters voor publisher ────────────────────────────────────────────────

    public double  getRobotX()          { return robotX; }
    public double  getRobotY()          { return robotY; }
    public State   getState()           { return state; }
    public int     getOrdersCompleted() { return ordersCompleted; }
    public PickOrder getCurrentOrder()  { return currentOrder; }
    public List<Location> getPlannedRoute() { return plannedRoute; }
    public Location getTargetLocation() { return targetLocation; }
    public Location getTargetDock()     { return targetDock; }
}
