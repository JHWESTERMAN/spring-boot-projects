package nl.jeroen.robotmock.warehouse;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 10x10 magazijn grid.
 *
 * Layout:
 *   - Rij 0 en 9: docks (N en Z kant)
 *   - Kolom 0 en 9: docks (W en O kant)
 *   - Overige locaties: racks met producten
 *
 * Docks:
 *   DOCK_N: (3,0), (6,0)
 *   DOCK_Z: (3,9), (6,9)
 *   DOCK_W: (0,3), (0,6)
 *   DOCK_O: (9,3), (9,6)
 */
public class Warehouse {

    private static final Logger LOG = Logger.getLogger(Warehouse.class.getName());

    public static final String[] PRODUCT_TYPES = {"PRODUCT_A", "PRODUCT_B", "PRODUCT_C"};

    private final Location[][] grid = new Location[10][10];
    private final List<Location> docks = new ArrayList<>();
    private final Random random = new Random(42); // vaste seed voor reproduceerbaarheid

    public Warehouse() {
        initGrid();
        fillStock();
    }

    // ── initialisatie ─────────────────────────────────────────────────────────

    private void initGrid() {
        // dock posities
        Set<String> dockPositions = Set.of(
            "3,0","6,0",  // Noord
            "3,9","6,9",  // Zuid
            "0,3","0,6",  // West
            "9,3","9,6"   // Oost
        );

        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                String key = x + "," + y;
                Location.Type type = dockPositions.contains(key)
                    ? Location.Type.DOCK
                    : Location.Type.RACK;
                grid[x][y] = new Location(x, y, type);

                if (type == Location.Type.DOCK) {
                    docks.add(grid[x][y]);
                }
            }
        }
    }

    private void fillStock() {
        // Vul elk rack met een willekeurig product en voorraad
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                Location loc = grid[x][y];
                if (loc.getType() == Location.Type.RACK) {
                    String product = PRODUCT_TYPES[random.nextInt(PRODUCT_TYPES.length)];
                    int stock = 5 + random.nextInt(20); // 5-25 stuks
                    loc.setProductType(product);
                    loc.setStock(stock);
                }
            }
        }
        LOG.info("Warehouse geïnitialiseerd: 10x10 grid, " + docks.size() + " docks");
    }

    // ── route optimalisatie ───────────────────────────────────────────────────

    /**
     * Nearest Neighbour algoritme.
     * Gegeven een startpositie en een lijst van te picken locaties,
     * retourneert de geoptimaliseerde volgorde.
     */
    public List<Location> optimizeRoute(double startX, double startY,
                                         List<Location> pickLocations) {
        if (pickLocations.isEmpty()) return Collections.emptyList();

        List<Location> remaining = new ArrayList<>(pickLocations);
        List<Location> route     = new ArrayList<>();

        final double[] current = {startX, startY};

while (!remaining.isEmpty()) {
    Location nearest = remaining.stream()
        .min(Comparator.comparingDouble(loc -> loc.distanceTo(current[0], current[1])))
        .orElseThrow();

    route.add(nearest);
    remaining.remove(nearest);
    current[0] = nearest.getX();
    current[1] = nearest.getY();

        }

        return route;
    }

    /**
     * Vindt de dichtstbijzijnde locatie met voldoende voorraad voor het gevraagde product.
     */
    public Optional<Location> findNearestLocation(String productType, int quantity,
                                                   double fromX, double fromY) {
        Optional<Location> result = Arrays.stream(grid)
            .flatMap(Arrays::stream)
            .filter(loc -> loc.getType() == Location.Type.RACK)
            .filter(loc -> loc.hasStock(productType, quantity))
            .min(Comparator.comparingDouble(loc -> loc.distanceTo(fromX, fromY)));

        if (result.isEmpty()) {
            LOG.info("Voorraad " + productType + " op - aanvullen...");
            restockProduct(productType);
            result = Arrays.stream(grid)
                .flatMap(Arrays::stream)
                .filter(loc -> loc.getType() == Location.Type.RACK)
                .filter(loc -> loc.hasStock(productType, quantity))
                .min(Comparator.comparingDouble(loc -> loc.distanceTo(fromX, fromY)));
        }
        return result;
    }

    private void restockProduct(String productType) {
        Arrays.stream(grid).flatMap(Arrays::stream)
            .filter(loc -> loc.getType() == Location.Type.RACK)
            .filter(loc -> productType.equals(loc.getProductType()))
            .forEach(loc -> loc.setStock(10 + random.nextInt(15)));
    }

    /**
     * Vindt de dichtstbijzijnde dock voor aflevering.
     */
    public Location findNearestDock(double fromX, double fromY) {
        return docks.stream()
            .min(Comparator.comparingDouble(loc -> loc.distanceTo(fromX, fromY)))
            .orElse(docks.get(0));
    }

    // ── getters ───────────────────────────────────────────────────────────────

    public Location getLocation(int x, int y) { return grid[x][y]; }
    public List<Location> getDocks()           { return Collections.unmodifiableList(docks); }

    public List<Location> getAllRacks() {
        return Arrays.stream(grid)
            .flatMap(Arrays::stream)
            .filter(loc -> loc.getType() == Location.Type.RACK)
            .collect(Collectors.toList());
    }

    /**
     * Snapshot van het grid voor de dashboard visualisatie.
     * Retourneert een platte lijst van alle locaties.
     */
    public List<Map<String, Object>> getGridSnapshot() {
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                Location loc = grid[x][y];
                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("x",       loc.getX());
                cell.put("y",       loc.getY());
                cell.put("id",      loc.getId());
                cell.put("type",    loc.getType().name());
                cell.put("product", loc.getProductType());
                cell.put("stock",   loc.getStock());
                snapshot.add(cell);
            }
        }
        return snapshot;
    }
}
