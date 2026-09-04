package com.acj.firma.acjfirmalocal;

import com.acj.firma.acjfirmalocal.service.LogService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
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
        scene.getStylesheets().add(FirmaApplication.class.getResource("theme.css").toExternalForm());

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
     * Oculta la ventana principal (no cierra el proceso: sigue corriendo en
     * segundo plano esperando la próxima invocación). Se llama al terminar
     * de firmar exitosamente.
     */
    public static void ocultarVentana() {
        Platform.runLater(() -> {
            if (primaryStageRef != null) {
                primaryStageRef.hide();
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
                            // SEGURIDAD: no loguear ni siquiera un prefijo del token - los
                            // logs ahora persisten indefinidamente en disco (ver LogService),
                            // así que un fragmento del token de auth ya es una fuga de
                            // credenciales. Solo se deja la longitud para depurar truncados.
                            System.out.println("Token decodificado exitosamente (longitud original: "
                                    + tokenCodificado.length() + ", decodificada: " + token.length() + ")");
                        } catch (Exception e) {
                            System.err.println("Error decodificando token: " + e.getMessage());
                            token = tokenCodificado;
                        }
                    }

                    String datosBackendJson = params.get("datosBackend");
                    String baseUrlBackend = params.get("baseUrlBackend");
                    String origen = params.get("origen");
                    String baseUrlDocument = params.get("baseUrlDocument");

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
                                            baseUrlBackend,
                                            origen,
                                            baseUrlDocument
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
        LogService.iniciar();

        modoServicio = args.length > 0 && "--service".equals(args[0]);
        applicationArgs = modoServicio ? new String[0] : args;

        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.text", "t2k");

        // SEGURIDAD: antes acá se llamaba a configurarSSLPermisivo(), que
        // instalaba un TrustManager que acepta CUALQUIER certificado y un
        // HostnameVerifier que aprueba CUALQUIER hostname como los defaults
        // de HttpsURLConnection para TODA la JVM (ver historial de este
        // archivo). Eso deshabilitaba la validación de certificados TLS para
        // cualquier llamada HTTPS hecha por este proceso durante toda su vida
        // - incluyendo, de rebote, cualquier llamada HTTPS que hicieran
        // librerías de terceros vía HttpsURLConnection (p.ej. la verificación
        // de TSL contra https://iofe.indecopi.gob.pe en acj-libreria-firma) -
        // un daemon con el certificado de firma del usuario y un token de
        // sesión en memoria quedaba así expuesto a un man-in-the-middle en
        // cualquiera de esas llamadas. No había ningún comentario ni ticket
        // que explique por qué se agregó (estaba desde el commit inicial del
        // repo) y las llamadas propias del agente al backend (HttpService)
        // NO la necesitan: usan java.net.http.HttpClient con su propio
        // SSLContext explícito (cacerts del sistema + certificados/acjdigital.crt
        // embebido, ver HttpService.configurarCertificadoSSL), que nunca
        // dependió de este default global. Si en algún ambiente hace falta
        // confiar en una CA interna/autofirmada para alguna llamada HTTPS
        // puntual, la forma correcta es armar un truststore específico para
        // esa llamada (como ya hace HttpService), nunca deshabilitar la
        // validación para todo el proceso.

        launch(args);
    }
}
