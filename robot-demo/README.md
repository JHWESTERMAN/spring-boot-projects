# Robot Integration Demo

A fully working robotics integration stack in Java — built as a portfolio project demonstrating enterprise middleware patterns applied to industrial robotics.

A lightweight robot simulator streams live movement data over WebSocket to a Spring Boot service, which processes and routes the data to a message broker and visualises it on a live dashboard.

**No ROS2 or Gazebo required.** The simulator is interchangeable with a real robot arm by changing a single URL.

---

## What it demonstrates

- Event-driven integration with WebSocket and JMS
- Spring Boot service with auto-reconnect and message routing
- Real-time browser dashboard with kinematic arm diagram
- Fault detection and automatic recovery
- Clean separation between data source and integration layer

---

## Architecture

```
┌─────────────────────────────────────────┐
│  RobotMock  (Java, WebSocket :9091)     │
│  pick → move → place state machine      │
│  speaks rosbridge v2.0 JSON protocol    │
└────────────────┬────────────────────────┘
                 │ WebSocket JSON
┌────────────────▼────────────────────────┐
│  Bridge Service  (Spring Boot :8090)    │
│  RosBridgeClient → RobotStateService   │
│  REST API  +  JMS telemetry            │
└──────────┬──────────────┬──────────────┘
           │              │
    browser dashboard   ActiveMQ
    localhost:8090      robot.telemetry
                        robot.events
                        robot.alerts
```

---

## Quick start

**Prerequisites:** Java 17, Maven, Docker

```bash
# Terminal 1 — ActiveMQ
docker run -p 61616:61616 -p 8161:8161 \
  -e ARTEMIS_USER=admin -e ARTEMIS_PASSWORD=admin \
  apache/activemq-artemis:latest

# Terminal 2 — RobotMock
cd robot-mock
mvn package -DskipTests
java -jar target/robot-mock-1.0-SNAPSHOT.jar 9091

# Terminal 3 — Bridge Service
cd bridge-service
mvn spring-boot:run
```

Open **http://localhost:8090** for the live dashboard.

---

## REST API

| Endpoint | Description |
|---|---|
| `GET /robot/status` | Scenario state, cycle count, last event, fault status |
| `GET /robot/joints` | Live joint positions (6-DOF, radians) |
| `GET /robot/health` | Bridge connection status |

```bash
curl http://localhost:8090/robot/status
```

---

## JMS Queues

| Queue | Content | Frequency |
|---|---|---|
| `robot.telemetry` | Joint positions (JSON) | max 1/sec |
| `robot.events` | CYCLE_COMPLETE, TASK_EVENT, FAULT_CLEARED | on occurrence |
| `robot.alerts` | E_STOP, hardware faults | on occurrence |

ActiveMQ console: http://localhost:8161 (admin/admin)

---

## Robot scenario

The mock runs a continuous 6-step cycle (~10 seconds per cycle):

```
IDLE → MOVING_TO_PICK → PICKING → MOVING_TO_PLACE → PLACING → RETURNING_HOME → IDLE
```

Joint positions interpolate linearly toward each target pose. A fault injector fires a random E_STOP with ~0.5% probability per tick — the robot auto-recovers after 2 seconds.

---

## Connecting a real robot

Change one line in `bridge-service/src/main/resources/application.yml`:

```yaml
robot:
  mock:
    url: ws://<ros2-server-ip>:9090/
```

The bridge service is unaware of the source — mock or real robot, the integration layer is identical.

---

## Stack

- Java 17
- Spring Boot 3.3
- ActiveMQ Artemis
- Tyrus WebSocket (Jakarta EE)
- Java-WebSocket client
- Vanilla JS dashboard
