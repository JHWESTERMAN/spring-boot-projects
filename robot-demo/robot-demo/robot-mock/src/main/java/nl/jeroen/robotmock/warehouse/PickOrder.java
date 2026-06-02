package nl.jeroen.robotmock.warehouse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Een volledige pickorder met meerdere orderregels.
 * Bijv:
 *   - 3x PRODUCT_A
 *   - 2x PRODUCT_B
 *   - 1x PRODUCT_C
 */
public class PickOrder {

    public enum Status { PENDING, IN_PROGRESS, COMPLETED }

    private final String             id;
    private final List<PickOrderLine> lines;
    private Status                   status = Status.PENDING;
    private int                      currentLineIndex = 0;

    public PickOrder(List<PickOrderLine> lines) {
        this.id    = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.lines = new ArrayList<>(lines);
    }

    public void start() { status = Status.IN_PROGRESS; }

    public PickOrderLine getCurrentLine() {
        if (currentLineIndex >= lines.size()) return null;
        return lines.get(currentLineIndex);
    }

    public boolean advanceLine() {
        currentLineIndex++;
        if (currentLineIndex >= lines.size()) {
            status = Status.COMPLETED;
            return false; // geen volgende regel
        }
        return true;
    }

    public boolean isCompleted()         { return status == Status.COMPLETED; }
    public String getId()                { return id; }
    public List<PickOrderLine> getLines(){ return lines; }
    public Status getStatus()            { return status; }
    public int getCurrentLineIndex()     { return currentLineIndex; }
    public int getTotalLines()           { return lines.size(); }
}
