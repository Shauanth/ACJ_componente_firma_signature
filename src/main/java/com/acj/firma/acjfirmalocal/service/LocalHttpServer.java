package com.acj.firma.acjfirmalocal.service;

import com.acj.firma.acjfirmalocal.model.ResultadoFirma;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class LocalHttpServer {

    private HttpServer server;
    private volatile ResultadoFirma ultimoResultado;
    private int puertoActual;
    private static final int[] PUERTOS_INTENTAR = {8765, 8766, 8767, 8768, 8769, 9876, 9877, 9878};
    private ObjectMapper objectMapper;
    private volatile Consumer<String> invocacionListener;
    private volatile Runnable mostrarListener;

    public LocalHttpServer() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Registra el callback que procesa una URI acjfirma://... recibida vía
     * POST /firma/invocar (usado cuando el agente ya está corriendo como daemon
     * y una nueva invocación del protocolo llega a esta misma instancia en vez
     * de levantar un segundo proceso).
     */
    public void setInvocacionListener(Consumer<String> listener) {
        this.invocacionListener = listener;
    }

    /**
     * Registra el callback que muestra/enfoca la ventana, usado por
     * GET /firma/mostrar cuando otra invocación (sin payload de protocolo)
     * detecta que este daemon ya está corriendo.
     */
    public void setMostrarListener(Runnable listener) {
        this.mostrarListener = listener;
    }

    public boolean iniciar() {
        for (int puerto : PUERTOS_INTENTAR) {
            try {
                // SEGURIDAD: new InetSocketAddress(puerto) (sin dirección) hace
                // bind en el wildcard address (0.0.0.0), es decir en TODAS las
                // interfaces de red, no solo loopback - cualquier equipo en la
                // misma LAN (o en internet, si el firewall/NAT no lo bloquea)
                // podría hablarle a este servidor sin autenticación y pedirle
                // que firme documentos y los reenvíe a una baseUrlBackend
                // arbitraria (ver handleInvocar). Se fuerza explícitamente
                // loopback: este servidor solo lo debe usar la web abierta en
                // el propio equipo del usuario.
                server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), puerto), 0);
                puertoActual = puerto;

                server.createContext("/firma/status", this::handleStatus);
                server.createContext("/health", this::handleHealth);
                server.createContext("/firma/invocar", this::handleInvocar);
                server.createContext("/firma/mostrar", this::handleMostrar);

                server.setExecutor(Executors.newFixedThreadPool(2));
                server.start();

                System.out.println("Servidor HTTP local iniciado en puerto: " + puerto);
                System.out.println("Endpoint disponible: http://localhost:" + puerto + "/firma/status");
                return true;

            } catch (BindException e) {
                System.out.println("Puerto " + puerto + " ocupado, intentando siguiente...");
            } catch (IOException e) {
                System.err.println("Error iniciando servidor en puerto " + puerto + ": " + e.getMessage());
            }
        }

        System.err.println("No se pudo iniciar servidor en ningún puerto disponible");
        return false;
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().add("Content-Type", "application/json");

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        Map<String, Object> response = new HashMap<>();

        if (ultimoResultado != null) {
            response.put("status", "completed");
            response.put("exito", ultimoResultado.isExito());
            response.put("error", ultimoResultado.getError());
            response.put("idDocumento", ultimoResultado.getIdDocumento());
            response.put("timestamp", ultimoResultado.getTimestamp());

            System.out.println("Enviando resultado a Angular: " +
                    (ultimoResultado.isExito() ? "EXITO" : "ERROR"));

            ultimoResultado = null;
        } else {
            response.put("status", "waiting");
        }

        String jsonResponse = objectMapper.writeValueAsString(response);
        byte[] responseBytes = jsonResponse.getBytes("UTF-8");

        exchange.sendResponseHeaders(200, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().add("Content-Type", "application/json");

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String response = "{\"status\":\"ok\",\"puerto\":" + puertoActual + "}";
        byte[] responseBytes = response.getBytes("UTF-8");

        exchange.sendResponseHeaders(200, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }

    private void handleInvocar(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().add("Content-Type", "application/json");

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String cuerpo;
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int leidos;
            while ((leidos = is.read(chunk)) != -1) {
                buffer.write(chunk, 0, leidos);
            }
            cuerpo = buffer.toString(StandardCharsets.UTF_8.name());
        }

        String respuesta;
        int status;

        if (invocacionListener != null && cuerpo != null && !cuerpo.isEmpty()) {
            invocacionListener.accept(cuerpo);
            status = 200;
            respuesta = "{\"status\":\"recibido\"}";
        } else {
            status = 400;
            respuesta = "{\"status\":\"error\",\"mensaje\":\"cuerpo vacio o instancia no lista\"}";
        }

        byte[] responseBytes = respuesta.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }

    private void handleMostrar(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json");

        if (mostrarListener != null) {
            mostrarListener.run();
        }

        byte[] responseBytes = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }

    public void notificarResultado(boolean exito, String error, String idDocumento) {
        this.ultimoResultado = new ResultadoFirma(exito, error, idDocumento, System.currentTimeMillis());
        System.out.println("Resultado almacenado para enviar a Angular: " +
                (exito ? "EXITO" : "ERROR - " + error));
    }

    public void detener() {
        if (server != null) {
            server.stop(0);
            System.out.println("Servidor HTTP local detenido");
        }
    }

    public int getPuertoActual() {
        return puertoActual;
    }
}