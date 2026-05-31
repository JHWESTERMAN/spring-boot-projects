package nl.jeroen.robotbridge.robot;

import java.time.Instant;

/**
 * Huidige toestand van de robotarm zoals bekend bij de bridge service.
 * Wordt bijgewerkt op basis van inkomende ROS berichten.
 */
public class RobotState {

    private double[] jointPositions = new double[6];
    private String scenarioState    = "UNKNOWN";
    private String lastEvent        = "";
    private String lastAlert        = "";
    private Instant lastUpdate      = Instant.now();
    private int cycleCount          = 0;
    private boolean faultActive     = false;

    // ── getters / setters ────────────────────────────────────────────────────

    public double[] getJointPositions()             { return jointPositions; }
    public void setJointPositions(double[] p)       { this.jointPositions = p; lastUpdate = Instant.now(); }

    public String getScenarioState()                { return scenarioState; }
    public void setScenarioState(String s)          { this.scenarioState = s; }

    public String getLastEvent()                    { return lastEvent; }
    public void setLastEvent(String e)              { this.lastEvent = e; }

    public String getLastAlert()                    { return lastAlert; }
    public void setLastAlert(String a)              { this.lastAlert = a; faultActive = a != null && !a.isEmpty(); }

    public Instant getLastUpdate()                  { return lastUpdate; }

    public int getCycleCount()                      { return cycleCount; }
    public void incrementCycleCount()               { this.cycleCount++; }

    public boolean isFaultActive()                  { return faultActive; }
    public void clearFault()                        { faultActive = false; lastAlert = ""; }
}
