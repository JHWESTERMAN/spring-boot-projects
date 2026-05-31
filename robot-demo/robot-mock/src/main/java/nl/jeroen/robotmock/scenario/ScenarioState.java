package nl.jeroen.robotmock.scenario;

public enum ScenarioState {
    IDLE,
    MOVING_TO_PICK,
    PICKING,
    MOVING_TO_PLACE,
    PLACING,
    RETURNING_HOME,
    FAULT
}
