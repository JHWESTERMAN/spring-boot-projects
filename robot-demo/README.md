# Robot Demo — RobotMock + Spring Boot Bridge

Lichtgewicht robotica integratiedemo zonder Gazebo of ROS2 installatie.

## Architectuur

```
┌─────────────────────────────────────────┐
│  RobotMock  (Java, WebSocket :9090)     │
│  pick→move→place state machine          │
│  simuleert rosbridge v2.0 protocol      │
└────────────────┬────────────────────────┘
                 │ WebSocket JSON
┌────────────────▼────────────────────────┐
│  Bridge Service  (Spring Boot :8080)    │
│  RosBridgeClient → RobotStateService   │
│  REST API + JMS telemetrie             │
└────────────────┬────────────────────────┘
                 │ JMS
┌────────────────▼────────────────────────┐
│  ActiveMQ  (:61616)                     │
│  robot.telemetry / robot.events         │
│  robot.alerts                           │
└─────────────────────────────────────────┘
```

## Starten

### Optie A — volledig via Docker Compose

```bash
docker-compose up --build
```

### Optie B — lokaal (voor ontwikkeling)

**Terminal 1 — ActiveMQ**
```bash
docker run -p 61616:61616 -p 8161:8161 \
  -e ARTEMIS_USER=admin -e ARTEMIS_PASSWORD=admin \
  apache/activemq-artemis:latest
```

**Terminal 2 — RobotMock**
```bash
cd robot-mock
mvn package -DskipTests
java -jar target/robot-mock-1.0-SNAPSHOT.jar
```

**Terminal 3 — Bridge Service**
```bash
cd bridge-service
mvn spring-boot:run
```

## REST API

| Endpoint | Beschrijving |
|---|---|
| `GET /robot/status` | Huidige staat: scenarioState, cycleCount, lastEvent |
| `GET /robot/joints` | Joint posities van de 6-DOF arm |
| `GET /robot/health` | Bridge verbindingsstatus |
| `GET /actuator/health` | Spring Boot health check |

**Voorbeeld:**
```bash
curl http://localhost:8080/robot/status | jq
```

## JMS Queues

| Queue | Inhoud | Frequentie |
|---|---|---|
| `robot.telemetry` | Joint posities (JSON) | max 1/sec |
| `robot.events` | CYCLE_COMPLETE, TASK_EVENT, FAULT_CLEARED | bij occurrence |
| `robot.alerts` | E_STOP, hardware faults | bij occurrence |

ActiveMQ console: http://localhost:8161 (admin/admin)

## RobotMock — Scenario

De mock doorloopt een 6-stap cyclus (~10 seconden per cyclus):

```
IDLE → MOVING_TO_PICK → PICKING → MOVING_TO_PLACE → PLACING → RETURNING_HOME → IDLE
```

Elke joint interpoleert lineair naar de doelpositie.
Met ~0.5% kans per tick gooit de FaultInjector een E_STOP.
Na 2 seconden herstelt de robot automatisch (FAULT_CLEARED event).

## Uitbreiden naar echte robot

Vervang in `application.yml`:
```yaml
robot:
  mock:
    url: ws://<ip-van-ros2-server>:9090/
```

De bridge service werkt identiek — het enige wat verandert is de bron.
