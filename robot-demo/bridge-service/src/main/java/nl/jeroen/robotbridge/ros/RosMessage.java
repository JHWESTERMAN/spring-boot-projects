package nl.jeroen.robotbridge.ros;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Representeert een inkomend rosbridge v2.0 bericht.
 *
 * Formaat:
 * { "op": "publish", "topic": "/joint_states", "msg": { ... } }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RosMessage {

    private String op;
    private String topic;
    private JsonNode msg;

    public String getOp()       { return op; }
    public String getTopic()    { return topic; }
    public JsonNode getMsg()    { return msg; }

    public void setOp(String op)         { this.op = op; }
    public void setTopic(String topic)   { this.topic = topic; }
    public void setMsg(JsonNode msg)     { this.msg = msg; }

    @Override
    public String toString() {
        return "RosMessage{op='" + op + "', topic='" + topic + "'}";
    }
}
