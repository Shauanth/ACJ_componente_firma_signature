package com.acj.firma.acjfirmalocal;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

public class FirmaApplication extends Application {

    private static String[] applicationArgs;
    private static boolean modoServicio;
    private static com.acj.firma.acjfirmalocal.controller.FirmaController controllerRef;
    private static Stage primaryStageRef;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(FirmaApplication.class.getResource("firma-main.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1000, 700);

        controllerRef = fxmlLoader.getController();
        primaryStageRef = stage;

        scene.getRoot().getProperties().put("fxmlLoader", fxmlLoader);

        stage.setTitle("ACJ Signature Agente");
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        stage.setResizable(true);

        try {
            stage.getIcons().add(new Image(getClass().getResourceAsStream("/images/acj-icon.png")));
        } catch (Exception e) {
            System.out.println("No se pudo cargar el icono: " + e.getMessage());
        }

        if (modoServicio) {
            // Corriendo como daemon: cerrar la ventana solo la oculta, no mata
            // el proceso (sigue escuchando peticiones en LocalHttpServer).
            Platform.setImplicitExit(false);
            stage.setOnCloseRequest(event -> {
                event.consume();
                stage.hide();
            });

            // JavaFX no completa el layout/CSS de una ventana que nunca fue
            // mostrada: si se muestra por primera vez recién cuando llega una
            // firma real (mostrarVentana()), el contenido queda en blanco.
            // Se fuerza un show/hide silencioso acá para que la ventana quede
            // completamente "realizada" desde el arranque del daemon.
            stage.show();
            stage.hide();
        } else {
            stage.setOnCloseRequest(event -> {
                Platform.exit();
                System.exit(0);
            });
            stage.show();
        }

        if (applicationArgs != null && applicationArgs.length > 0 && applicationArgs[0].startsWith("acjfirma://")) {
            procesarUri(applicationArgs[0]);
        }

        System.out.println("ACJ Signature Agente iniciado correctamente" + (modoServicio ? " (modo servicio, ventana oculta hasta la próxima firma)" : ""));
    }

    /**
     * Muestra/enfoca la ventana principal. Usado tanto al procesar una URI del
     * protocolo como cuando otra invocación pide explícitamente mostrar el
     * agente (GET /firma/mostrar, ver LocalHttpServer).
     */
    public static void mostrarVentana() {
        Platform.runLater(() -> {
            if (primaryStageRef != null) {
                primaryStageRef.show();
                primaryStageRef.setIconified(false);
                primaryStageRef.toFront();
            }
        });
    }

    /**
     * Procesa una URI acjfirma://... Se usa tanto en el arranque normal (args
     * de línea de comandos) como cuando el daemon ya corriendo recibe una
     * nueva invocación reenviada por Launcher vía POST /firma/invocar.
     */
    public static void procesarUri(String url) {
        try {
            if (url != null && url.startsWith("acjfirma://")) {
                System.out.println("Procesando protocolo: " + url.substring(0, Math.min(100, url.length())) + "...");

                URI uri = new URI(url);
                String path = uri.getPath();
                String query = uri.getQuery();

                if (path != null && path.contains("verificar")) {
                    System.out.println("Verificación de instalación - aplicativo respondiendo");
                    return;
                }

                if (query != null) {
                    Map<String, String> params = parseQueryString(query);

                    String documentosJson = params.get("documentos");
                    String idDocumento = params.get("idDocumento");

//                    String token = params.get("token");
                    String tokenCodificado = params.get("token");
                    String token = null;
                    if (tokenCodificado != null) {
                        try {
                            token = URLDecoder.decode(tokenCodificado, "UTF-8");
                            System.out.println("Token decodificado exitosamente:");
                            System.out.println("   - Longitud original: " + tokenCodificado.length());
                            System.out.println("   - Longitud decodificada: " + token.length());
                            System.out.println("   - Primeros 50 chars: " + token.substring(0, Math.min(50, token.length())));
                        } catch (Exception e) {
                            System.err.println("Error decodificando token: " + e.getMessage());
                            token = tokenCodificado;
                        }
                    }

                    String datosBackendJson = params.get("datosBackend");
                    String baseUrlBackend = params.get("baseUrlBackend");

                    System.out.println("PARÁMETROS RECIBIDOS:");
                    System.out.println("   - Documentos JSON: " + (documentosJson != null ? "Presente (" + documentosJson.length() + " chars)" : "Ausente"));
                    System.out.println("   - ID Documento: " + idDocumento);
                    System.out.println("   - Token: " + (token != null ? "Presente (decodificado)" : "Ausente"));
                    System.out.println("   - Datos Backend: " + (datosBackendJson != null ? "Presente (" + datosBackendJson.length() + " chars)" : "Ausente"));
                    System.out.println("   - Base URL Backend: " + baseUrlBackend);

                    if (documentosJson != null && idDocumento != null) {
                        String finalToken = token;
                        mostrarVentana();
                        Platform.runLater(() -> {
                            try {
                                if (controllerRef != null) {
                                    System.out.println("Enviando datos al controlador...");

                                    controllerRef.procesarDocumentosDesdeWeb(
                                            documentosJson,
                                            idDocumento,
                                            finalToken,
                                            datosBackendJson,
                                            baseUrlBackend
                                    );

                                    System.out.println("Datos enviados al controlador exitosamente");
                                }
                            } catch (Exception e) {
                                System.err.println("Error obteniendo controlador: " + e.getMessage());
                                e.printStackTrace();
                            }
                        });
                    } else {
                        System.err.println("Faltan parámetros requeridos en la URL");
                        System.err.println("   - documentosJson: " + (documentosJson != null ? "SI" : "NO"));
                        System.err.println("   - idDocumento: " + (idDocumento != null ? "SI" : "NO"));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error procesando protocolo: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new HashMap<>();

        try {
            for (String param : query.split("&")) {
                String[] keyValue = param.split("=", 2);
                if (keyValue.length == 2) {
                    String key = URLDecoder.decode(keyValue[0], "UTF-8");
                    String value = URLDecoder.decode(keyValue[1], "UTF-8");
                    params.put(key, value);
                }
            }
        } catch (Exception e) {
            System.err.println("Error parseando query string: " + e.getMessage());
        }

        return params;
    }

    public static void main(String[] args) {
        modoServicio = args.length > 0 && "--service".equals(args[0]);
        applicationArgs = modoServicio ? new String[0] : args;

        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.text", "t2k");

        configurarSSLPermisivo();

        launch(args);
    }

    private static void configurarSSLPermisivo() {
        try {
            System.out.println("Configurando SSL en modo permisivo...");

            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

            System.out.println("SSL configurado exitosamente");

        } catch (Exception e) {
            System.err.println("Error configurando SSL: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
