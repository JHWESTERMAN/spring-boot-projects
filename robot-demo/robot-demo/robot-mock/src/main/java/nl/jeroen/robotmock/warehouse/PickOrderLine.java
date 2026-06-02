package nl.jeroen.robotmock.warehouse;

/**
 * Één regel in een pickorder.
 * Bijv: 3x PRODUCT_A van locatie B4
 */
public class PickOrderLine {

    public enum Status { PENDING, IN_PROGRESS, PICKED }

    private final String productType;
    private final int    quantity;
    private Location     assignedLocation;
    private Status       status = Status.PENDING;

    public PickOrderLine(String productType, int quantity) {
        this.productType = productType;
        this.quantity    = quantity;
    }

    public String   getProductType()      { return productType; }
    public int      getQuantity()         { return quantity; }
    public Location getAssignedLocation() { return assignedLocation; }
    public Status   getStatus()           { return status; }

    public void assignLocation(Location loc) { this.assignedLocation = loc; }
    public void start()                      { this.status = Status.IN_PROGRESS; }
    public void complete()                   { this.status = Status.PICKED; }
}
