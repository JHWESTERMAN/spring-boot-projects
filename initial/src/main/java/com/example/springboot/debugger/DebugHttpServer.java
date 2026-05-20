package com.example.springboot.debugger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.jms.JMSException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class DebugHttpServer {

    private static final Logger log = LoggerFactory.getLogger(DebugHttpServer.class);
    private static final int PORT = 7070;

    private final MessageStore store;
    private final ReplayEngine replayEngine;
    private final ObjectMapper mapper;

    public DebugHttpServer(MessageStore store, ReplayEngine replayEngine) throws IOException {
        this.store = store;
        this.replayEngine = replayEngine;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        start();
    }

    // ----------------------------
    // Server starten
    // ----------------------------
    private void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/debug/messages", this::handleMessages);
        server.createContext("/debug/replay", this::handleReplay);
        server.createContext("/debug/ui", this::handleUi);

        server.start();
        log.info("DebugHttpServer gestart op http://localhost:{}/debug/ui", PORT);
    }

    // ----------------------------
    // GET /debug/messages
    // ----------------------------
    private void handleMessages(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method not allowed");
            return;
        }

        String query = exchange.getRequestURI().getQuery();
        List<MessageStore.CapturedMessage> messages;

        if (query != null && query.startsWith("queue=")) {
            String queue = query.substring(6);
            messages = store.getByQueue(queue);
        } else if (query != null && query.startsWith("from=")) {
            String[] parts = query.split("&");
            Instant from = Instant.parse(parts[0].substring(5));
            Instant to = Instant.parse(parts[1].substring(3));
            messages = store.getByTimeRange(from, to);
        } else {
            messages = store.getAll();
        }

        String json = mapper.writeValueAsString(messages);
        sendJsonResponse(exchange, 200, json);
    }

    // ----------------------------
    // POST /debug/replay/{id}
    // POST /debug/replay/timerange
    // ----------------------------
    private void handleReplay(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method not allowed");
            return;
        }

        String path = exchange.getRequestURI().getPath();

        try {
            if (path.endsWith("/timerange")) {
                // Lees body: {"from": "...", "to": "..."}
                String body = new String(exchange.getRequestBody().readAllBytes());
                Map<String, String> params = mapper.readValue(body, Map.class);
                Instant from = Instant.parse(params.get("from"));
                Instant to = Instant.parse(params.get("to"));
                replayEngine.replayByTimeRange(from, to);
                sendResponse(exchange, 200, "Tijdsbereik replay gestart");

            } else if (path.contains("/correlation/")) {
                String correlationId = path.substring(path.lastIndexOf("/") + 1);
                replayEngine.replayByCorrelationId(correlationId);
                sendResponse(exchange, 200, "Correlatie replay gestart");

            } else {
                String id = path.substring(path.lastIndexOf("/") + 1);
                replayEngine.replayById(id);
                sendResponse(exchange, 200, "Bericht gereplayd");
            }

        } catch (JMSException e) {
            log.error("Replay mislukt", e);
            sendResponse(exchange, 500, "Replay mislukt: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            sendResponse(exchange, 404, e.getMessage());
        }
    }

    // ----------------------------
    // GET /debug/ui — HTML interface
    // ----------------------------
    private void handleUi(HttpExchange exchange) throws IOException {
        String html = """
            <!DOCTYPE html>
            <html lang="nl">
            <head>
                <meta charset="UTF-8">
                <title>MQ Flow Debugger</title>
                <style>
                    body { font-family: monospace; background: #1e1e2e; color: #cdd6f4; padding: 20px; }
                    h1 { color: #89b4fa; }
                    table { width: 100%; border-collapse: collapse; margin-top: 20px; }
                    th { background: #313244; padding: 10px; text-align: left; color: #89b4fa; }
                    td { padding: 8px; border-bottom: 1px solid #313244; font-size: 12px; }
                    tr:hover { background: #313244; }
                    button { background: #89b4fa; color: #1e1e2e; border: none;
                             padding: 4px 10px; cursor: pointer; border-radius: 4px; }
                    button:hover { background: #74c7ec; }
                    .controls { margin: 20px 0; display: flex; gap: 10px; align-items: center; }
                    input { background: #313244; color: #cdd6f4; border: 1px solid #6c7086;
                            padding: 6px; border-radius: 4px; }
                    .replay { background: #a6e3a1; }
                    .send { background: #89b4fa; }
                    .receive { background: #f38ba8; }
                    #status { margin-top: 10px; color: #a6e3a1; }
                </style>
            </head>
            <body>
                <h1>MQ Flow Debugger</h1>

                <div class="controls">
                    <label>Van: <input type="datetime-local" id="from"/></label>
                    <label>Tot: <input type="datetime-local" id="to"/></label>
                    <button onclick="replayTimeRange()">Replay tijdsbereik</button>
                    <button onclick="loadMessages()">Vernieuwen</button>
                </div>

                <div id="status"></div>

                <table>
                    <thead>
                        <tr>
                            <th>Timestamp</th>
                            <th>Richting</th>
                            <th>Queue</th>
                            <th>CorrelationId</th>
                            <th>Body</th>
                            <th>Actie</th>
                        </tr>
                    </thead>
                    <tbody id="messages"></tbody>
                </table>

                <script>
                    async function loadMessages() {
                        const res = await fetch('/debug/messages');
                        const messages = await res.json();
                        const tbody = document.getElementById('messages');
                        tbody.innerHTML = messages.reverse().map(m => `
                            <tr>
                                <td>${new Date(m.timestamp).toLocaleString('nl-NL')}</td>
                                <td><span class="${m.direction.toLowerCase()}">${m.direction}</span></td>
                                <td>${m.queue}</td>
                                <td>${m.correlationId || '-'}</td>
                                <td style="max-width:300px;overflow:hidden;text-overflow:ellipsis">${m.body}</td>
                                <td><button onclick="replay('${m.id}')">Replay</button></td>
                            </tr>
                        `).join('');
                    }

                    async function replay(id) {
                        const res = await fetch('/debug/replay/' + id, { method: 'POST' });
                        const text = await res.text();
                        document.getElementById('status').textContent = text;
                        setTimeout(loadMessages, 1000);
                    }

                    async function replayTimeRange() {
                        const from = new Date(document.getElementById('from').value).toISOString();
                        const to = new Date(document.getElementById('to').value).toISOString();
                        const res = await fetch('/debug/replay/timerange', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ from, to })
                        });
                        const text = await res.text();
                        document.getElementById('status').textContent = text;
                        setTimeout(loadMessages, 1000);
                    }

                    // Auto-refresh elke 3 seconden
                    loadMessages();
                    setInterval(loadMessages, 3000);
                </script>
            </body>
            </html>
            """;

        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        byte[] bytes = html.getBytes();
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ----------------------------
    // Helper methodes
    // ----------------------------
    private void sendJsonResponse(HttpExchange exchange, int status, String json)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = json.getBytes();
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendResponse(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes();
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}