package nl.jeroen.robotmock.warehouse;

/**
 * Een locatie in het 10x10 magazijn grid.
 * Coördinaten lopen van (0,0) linksboven tot (9,9) rechtsonder.
 */
public class Location {

    public enum Type { RACK, DOCK, AISLE }

    private final int    x;
    private final int    y;
    private final String id;        // bijv. "A3", "C7"
    private final Type   type;
    private String       productType;
    private int          stock;

    public Location(int x, int y, Type type) {
        this.x    = x;
        this.y    = y;
        this.id   = toLabel(x, y);
        this.type = type;
    }

    // afstand tot andere locatie (Euclidisch)
    public double distanceTo(Location other) {
        int dx = this.x - other.x;
        int dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // afstand tot coördinaat
    public double distanceTo(double ox, double oy) {
        double dx = this.x - ox;
        double dy = this.y - oy;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public boolean hasStock(String product, int qty) {
        return product.equals(productType) && stock >= qty;
    }

    public void pick(int qty) {
        stock = Math.max(0, stock - qty);
    }

    private static String toLabel(int x, int y) {
        return String.valueOf((char) ('A' + x)) + y;
    }

    // ── getters / setters ─────────────────────────────────────────────────────
    public int    getX()          { return x; }
    public int    getY()          { return y; }
    public String getId()         { return id; }
    public Type   getType()       { return type; }
    public String getProductType(){ return productType; }
    public int    getStock()      { return stock; }

    public void setProductType(String p) { this.productType = p; }
    public void setStock(int s)          { this.stock = s; }

    @Override
    public String toString() { return id + "(" + x + "," + y + ")"; }
}
