package com.acj.firma.acjfirmalocal;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Launcher simple que no extiende de Application
 * Para evitar problemas con JavaFX en JARs
 */
public class Launcher {

    public static void main(String[] args) {
        // Configurar propiedades del sistema
        System.setProperty("javafx.preloader", "");
        System.setProperty("java.awt.headless", "false");

        boolean esInvocacionProtocolo = args.length > 0 && args[0].startsWith("acjfirma://");
        boolean esModoServicio = args.length > 0 && "--service".equals(args[0]);

        if (esInvocacionProtocolo || esModoServicio) {
            if (reenviarAInstanciaExistente(esInvocacionProtocolo ? args[0] : null)) {
                // Ya había una instancia (daemon) corriendo y se le reenvió la
                // petición. Esta invocación termina acá: no hace falta levantar
                // una segunda JVM/JavaFX compitiendo por el mismo puerto.
                return;
            }
        }

        try {
            // Lanzar la aplicación JavaFX
            FirmaApplication.main(args);
        } catch (Exception e) {
            System.err.println("Error al iniciar ACJ Signature Agente:");
            e.printStackTrace();

            // Intentar con configuraciones alternativas
            System.setProperty("prism.order", "sw");
            System.setProperty("prism.text", "t2k");
            System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");

            try {
                FirmaApplication.main(args);
            } catch (Exception e2) {
                System.err.println("Error crítico. No se puede iniciar la aplicación.");
                e2.printStackTrace();
            }
        }
    }

    /**
     * Si ya hay una instancia del agente corriendo (detectada vía el puerto
     * guardado en ~/.acj_firma_config.json + GET /health), le reenvía la
     * invocación del protocolo (o simplemente le pide mostrar su ventana, si
     * esta invocación es el arranque de servicio) en vez de levantar una
     * segunda JVM/JavaFX compitiendo por el mismo puerto.
     *
     * @param uriProtocolo la URI acjfirma://... a reenviar, o null si esta
     *                     invocación era solo "--service" (arranque de daemon).
     * @return true si había una instancia viva y se le reenvió la petición.
     */
    private static boolean reenviarAInstanciaExistente(String uriProtocolo) {
        Integer puerto = leerPuertoGuardado();
        if (puerto == null) {
            return false;
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(800))
                .build();

        try {
            HttpRequest health = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + puerto + "/health"))
                    .timeout(Duration.ofMillis(800))
                    .GET()
                    .build();
            HttpResponse<String> healthResponse = client.send(health, HttpResponse.BodyHandlers.ofString());
            if (healthResponse.statusCode() != 200) {
                return false;
            }
        } catch (Exception e) {
            // No hay nadie escuchando en ese puerto (proceso anterior murió sin
            // limpiar el archivo, o aún no arrancó). Se sigue con el arranque normal.
            return false;
        }

        try {
            if (uriProtocolo != null) {
                HttpRequest invocar = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + puerto + "/firma/invocar"))
                        .timeout(Duration.ofSeconds(5))
                        .POST(HttpRequest.BodyPublishers.ofString(uriProtocolo))
                        .build();
                client.send(invocar, HttpResponse.BodyHandlers.discarding());
            } else {
                HttpRequest mostrar = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + puerto + "/firma/mostrar"))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                client.send(mostrar, HttpResponse.BodyHandlers.discarding());
            }
            System.out.println("Instancia existente detectada en puerto " + puerto + ", petición reenviada.");
            return true;
        } catch (Exception e) {
            System.err.println("Se detectó una instancia existente pero falló el reenvío: " + e.getMessage());
            return false;
        }
    }

    private static Integer leerPuertoGuardado() {
        try {
            File configFile = new File(System.getProperty("user.home"), ".acj_firma_config.json");
            if (!configFile.exists()) {
                return null;
            }
            ObjectMapper mapper = new ObjectMapper();
            Map<?, ?> config = mapper.readValue(configFile, Map.class);
            Object puerto = config.get("puerto");
            return puerto != null ? Integer.valueOf(puerto.toString()) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
