package com.acj.firma.acjfirmalocal.controller;

import com.acj.firma.acjfirmalocal.FirmaApplication;

import com.acj.firma.acjfirmalocal.model.*;
import com.acj.firma.acjfirmalocal.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.controlsfx.control.Notifications;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;

public class FirmaController implements Initializable {
    private static final Logger logger = Logger.getLogger(FirmaController.class.getName());
    @FXML private Label lblAppTitle;
    @FXML private Label lblStatus;
    @FXML private Button btnSelectDocument;
    @FXML private Button btnSign;
    @FXML private Button btnRefreshCerts;
    @FXML private ComboBox<String> cbCertificates;
    @FXML private VBox certificatesList;
    @FXML private WebView webPreview;
    @FXML private ProgressBar progressBar;
    @FXML private TabPane tabDocuments;
    @FXML private TextArea logArea;
    @FXML private Button btnCancel;
    @FXML private Label lblDocumentCount;
    @FXML private HBox hboxDocumentStatus;
    @FXML private Label lblDocumentStatus;
    @FXML private ImageView logoImage;

    private CertificadoService certificadoService;
    private DocumentoService documentoService;
    private FirmaLocalService firmaLocalService;
    private ConfiguracionFirma configuracionFirma;
    private LocalHttpServer localServer;

    private List<String> certificadosDisponibles;
    private List<DocumentoFirma> documentosParaFirmar;
    private String idDocumentoActual;
    private String tokenAuth;

    private boolean documentosRecibidosDesdeWeb = false;
    private boolean procesoFirmaEnCurso = false;

    private String datosBackendJson;
    private String baseUrlBackend;

    private String certificadoSeleccionado = null;
    private List<VBox> certificateCards = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        certificadoService = new CertificadoService();
        documentoService = new DocumentoService();
        firmaLocalService = new FirmaLocalService();

//        Image logo = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/LOGO_BLANCO.png")));
//        logoImage.setImage(logo);

        setupUI();
        cargarCertificados();
        configurarEstadoInicial();
        actualizarEstado("Aplicación lista para recibir documentos desde la web");
        setupSimpleLogArea();
        iniciarServidorLocal();
    }

    private void iniciarServidorLocal() {
        try {
            localServer = new LocalHttpServer();
            localServer.setInvocacionListener(com.acj.firma.acjfirmalocal.FirmaApplication::procesarUri);
            localServer.setMostrarListener(com.acj.firma.acjfirmalocal.FirmaApplication::mostrarVentana);
            boolean iniciado = localServer.iniciar();

            if (iniciado) {
                System.out.println("Servidor local listo en puerto: " + localServer.getPuertoActual());
                escribirPuertoEnArchivo(localServer.getPuertoActual());
            } else {
                System.err.println("No se pudo iniciar el servidor local");
            }
        } catch (Exception e) {
            System.err.println("Error iniciando servidor local: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void escribirPuertoEnArchivo(int puerto) {
        try {
            String userHome = System.getProperty("user.home");
            File configFile = new File(userHome, ".acj_firma_config.json");

            Map<String, Object> config = new HashMap<>();
            config.put("puerto", puerto);
            config.put("timestamp", System.currentTimeMillis());

            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(configFile, config);

            System.out.println("Puerto guardado en: " + configFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error guardando puerto: " + e.getMessage());
        }
    }

    private void configurarEstadoInicial() {
        btnSign.setDisable(true);
        configurarBotonCancelar();
//        actualizarEstadoDocumentos(0, false);
        if (lblDocumentCount != null) {
            actualizarEstadoDocumentos(false);
        }
    }

    private void configurarBotonCancelar() {
        if (btnCancel != null) {
            btnCancel.setOnAction(event -> onCancelar());
        }
    }

    private void actualizarEstadoDocumentos(boolean documentosRecibidos) {
        Platform.runLater(() -> {
            if (lblDocumentStatus != null) {
                if (documentosRecibidos) {
                    lblDocumentStatus.setText("LISTOS");
                    lblDocumentStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: #059669; -fx-font-weight: 700; -fx-background-color: #d1fae5; -fx-background-radius: 6; -fx-padding: 4 8;");
                } else {
                    lblDocumentStatus.setText("ESPERANDO");
                    lblDocumentStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: #d97706; -fx-font-weight: 700; -fx-background-color: #fef3c7; -fx-background-radius: 6; -fx-padding: 4 8;");
                }
            }
        });
    }

    private void setupSimpleLogArea() {
        if (logArea != null) {
            logArea.setEditable(false);
            logArea.setWrapText(true);
            logArea.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11px;");

            java.io.OutputStream logOutputStream = new java.io.OutputStream() {
                private final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();

                @Override
                public synchronized void write(int b) {
                    if (b == '\n') {
                        String linea = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
                        buffer.reset();
                        Platform.runLater(() -> {
                            logArea.appendText(linea + "\n");
                            // Auto-scroll
                            logArea.setScrollTop(Double.MAX_VALUE);
                        });
                    } else {
                        buffer.write(b);
                    }
                }
            };

            // autoFlush + UTF-8 explícito: sin esto, tildes/ñ (Perú, página, código...)
            // se corrompían porque el OutputStream anterior casteaba cada byte a char
            // por separado, rompiendo los caracteres multibyte de UTF-8.
            java.io.PrintStream logPrintStream = new java.io.PrintStream(logOutputStream, true, java.nio.charset.StandardCharsets.UTF_8);

            // Antes solo se interceptaba System.out: todos los System.err.println(...)
            // y e.printStackTrace() (la gran mayoría de los logs de error de esta
            // clase) se perdían y nunca llegaban al panel "Registro de Actividad".
            System.setOut(logPrintStream);
            System.setErr(logPrintStream);
        }
    }

    private void setupUI() {
//        btnRefreshCerts.setGraphic(new FontIcon(FontAwesomeSolid.SYNC_ALT));
        btnSign.setGraphic(new FontIcon(FontAwesomeSolid.FILE_SIGNATURE));

        lblAppTitle.setText("ACJ Signature Agente");
        btnSign.setDisable(true);
//        progressBar.setVisible(false);
    }

    private void cargarCertificados() {
        Platform.runLater(() -> {
            try {
                certificatesList.getChildren().clear();
                certificateCards.clear();
                certificadoSeleccionado = null;

                Label loadingLabel = new Label("Cargando certificados...");
                loadingLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #6b7280; -fx-padding: 20;");
                certificatesList.getChildren().add(loadingLabel);

                if (!certificadoService.isServicioDisponible()) {
                    certificatesList.getChildren().clear();
                    Label errorLabel = new Label("Servicio de certificados no disponible");
                    errorLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #dc2626; -fx-padding: 20;");
                    certificatesList.getChildren().add(errorLabel);
                    actualizarEstado("Error: Servicio de certificados no disponible");
                    return;
                }

                certificadosDisponibles = certificadoService.obtenerCertificados();
                System.out.println("Certificados obtenidos: " + certificadosDisponibles);
                System.out.println("Cantidad: " + certificadosDisponibles.size());

                // El log de consola no llega al usuario final en el instalador
                // empaquetado: si CertificadoService ocultó algún certificado
                // ambiguo (misma identidad duplicada en el almacén, uno
                // vencido), hay que decírselo en la propia ventana para que
                // sepa qué certificado borrar en certmgr.msc.
                for (String advertencia : certificadoService.obtenerAdvertencias()) {
                    mostrarAlerta("Certificado no disponible", advertencia);
                }

                certificatesList.getChildren().clear();

                if (certificadosDisponibles.isEmpty()) {
                    VBox emptyState = createEmptyStateCard();
                    certificatesList.getChildren().add(emptyState);
                    actualizarEstado("No se encontraron certificados en el sistema");
                    mostrarNotificacion("Sin certificados", "No se encontraron certificados digitales en el sistema", false);
                } else {
                    for (String certificado : certificadosDisponibles) {
                        VBox certificateCard = createCertificateCard(certificado);
                        certificateCards.add(certificateCard);
                        certificatesList.getChildren().add(certificateCard);
                    }

                    actualizarEstado("Certificados cargados: " + certificadosDisponibles.size());
                    mostrarNotificacion("Certificados cargados", "Se encontraron " + certificadosDisponibles.size() + " certificados", true);
                }

            } catch (Exception e) {
                certificatesList.getChildren().clear();
                VBox errorCard = createErrorStateCard(e.getMessage());
                certificatesList.getChildren().add(errorCard);
                actualizarEstado("Error al cargar certificados: " + e.getMessage());
                mostrarNotificacion("Error", "Error al cargar certificados: " + e.getMessage(), false);
                e.printStackTrace();
            }
        });
    }

    private VBox createCertificateCard(String certificado) {
        VBox card = new VBox();
        card.setSpacing(8.0);
        card.setStyle(
                "-fx-background-color: #f9fafb; " +
                        "-fx-border-color: #e5e7eb; " +
                        "-fx-border-width: 1.5; " +
                        "-fx-border-radius: 8; " +
                        "-fx-background-radius: 8; " +
                        "-fx-padding: 15; " +
                        "-fx-cursor: hand;"
        );

        String nombreCertificado = certificadoService.extraerCNDeCertificadoFormateado(certificado);
        String organizacion = certificadoService.obtenerOrganizacion(certificado);

        Label nombreLabel = new Label(nombreCertificado);
        nombreLabel.setStyle(
                "-fx-font-weight: bold; " +
                        "-fx-font-size: 14px; " +
                        "-fx-text-fill: #1f2937;"
        );
        nombreLabel.setWrapText(true);

        String textoOrganizacion = (organizacion != null && !organizacion.isEmpty())
                ? organizacion
                : "Sin Organización";

        Label organizacionLabel = new Label(textoOrganizacion);
        organizacionLabel.setStyle(
                "-fx-font-size: 12px; " +
                        "-fx-text-fill: #6b7280;"
        );
        organizacionLabel.setWrapText(true);

        card.getChildren().addAll(nombreLabel, organizacionLabel);

        card.setOnMouseClicked(event -> {
            selectCertificateCard(card, certificado);
        });

        card.setOnMouseEntered(event -> {
            if (!card.getStyle().contains("-fx-border-color: #3b82f6")) {
                card.setStyle(card.getStyle().replace(
                        "-fx-background-color: #f9fafb;",
                        "-fx-background-color: #f3f4f6;"
                ));
            }
        });

        card.setOnMouseExited(event -> {
            if (!card.getStyle().contains("-fx-border-color: #3b82f6")) {
                card.setStyle(card.getStyle().replace(
                        "-fx-background-color: #f3f4f6;",
                        "-fx-background-color: #f9fafb;"
                ));
            }
        });

        return card;
    }

    private void selectCertificateCard(VBox selectedCard, String certificado) {
        for (VBox card : certificateCards) {
            card.setStyle(
                    "-fx-background-color: #f9fafb; " +
                            "-fx-border-color: #e5e7eb; " +
                            "-fx-border-width: 1.5; " +
                            "-fx-border-radius: 8; " +
                            "-fx-background-radius: 8; " +
                            "-fx-padding: 15; " +
                            "-fx-cursor: hand;"
            );
        }

        selectedCard.setStyle(
                "-fx-background-color: #eff6ff; " +
                        "-fx-border-color: #3b82f6; " +
                        "-fx-border-width: 2; " +
                        "-fx-border-radius: 8; " +
                        "-fx-background-radius: 8; " +
                        "-fx-padding: 15; " +
                        "-fx-cursor: hand; " +
                        "-fx-effect: dropshadow(gaussian, rgba(59,130,246,0.3), 4, 0, 0, 2);"
        );

        certificadoSeleccionado = certificado;

        String nombreCert = certificadoService.extraerCNDeCertificadoFormateado(certificado);
        actualizarEstado("Certificado seleccionado: " + nombreCert);

        System.out.println("Certificado seleccionado: " + nombreCert);
    }

    private VBox createEmptyStateCard() {
        VBox card = new VBox();
        card.setSpacing(10.0);
        card.setAlignment(javafx.geometry.Pos.CENTER);
        card.setStyle(
                "-fx-background-color: #fef3c7; " +
                        "-fx-border-color: #f59e0b; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 8; " +
                        "-fx-background-radius: 8; " +
                        "-fx-padding: 30;"
        );

        Label iconLabel = new Label("Sin certificados");
        iconLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #92400e;");

        Label titleLabel = new Label("Sin certificados disponibles");
        titleLabel.setStyle(
                "-fx-font-weight: bold; " +
                        "-fx-font-size: 16px; " +
                        "-fx-text-fill: #92400e;"
        );

        Label messageLabel = new Label("No se encontraron certificados digitales en el sistema.\nVerifique que tenga certificados instalados.");
        messageLabel.setStyle(
                "-fx-font-size: 12px; " +
                        "-fx-text-fill: #92400e; " +
                        "-fx-text-alignment: center;"
        );
        messageLabel.setWrapText(true);

        card.getChildren().addAll(iconLabel, titleLabel, messageLabel);
        return card;
    }

    private VBox createErrorStateCard(String error) {
        VBox card = new VBox();
        card.setSpacing(10.0);
        card.setAlignment(javafx.geometry.Pos.CENTER);
        card.setStyle(
                "-fx-background-color: #fef2f2; " +
                        "-fx-border-color: #ef4444; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 8; " +
                        "-fx-background-radius: 8; " +
                        "-fx-padding: 30;"
        );

        Label iconLabel = new Label("Error");
        iconLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #dc2626;");

        Label titleLabel = new Label("Error al cargar certificados");
        titleLabel.setStyle(
                "-fx-font-weight: bold; " +
                        "-fx-font-size: 16px; " +
                        "-fx-text-fill: #dc2626;"
        );

        Label messageLabel = new Label(error);
        messageLabel.setStyle(
                "-fx-font-size: 12px; " +
                        "-fx-text-fill: #dc2626; " +
                        "-fx-text-alignment: center;"
        );
        messageLabel.setWrapText(true);

        card.getChildren().addAll(iconLabel, titleLabel, messageLabel);
        return card;
    }

    @FXML
    private void onCancelar() {
        System.out.println("Botón cancelar presionado");

        if (procesoFirmaEnCurso) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Cancelar Firma");
                alert.setHeaderText("¿Cancelar proceso de firma?");
                alert.setContentText("Hay un proceso de firma en curso. ¿Está seguro que desea cancelar?");

                alert.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.OK) {
                        cancelarProcesoFirma();
                    }
                });
            });
        } else if (documentosRecibidosDesdeWeb) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Cancelar Operación");
                alert.setHeaderText("¿Cancelar y cerrar aplicación?");
                alert.setContentText("Se perderán los documentos cargados. ¿Desea continuar?");

                alert.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.OK) {
                        notificarCancelacionAlFrontend();
                        cerrarAplicativo();
                    }
                });
            });
        } else {
            cerrarAplicativo();
        }

        if (localServer != null) {
            localServer.detener();
        }
    }

    private void cancelarProcesoFirma() {
        procesoFirmaEnCurso = false;

        Platform.runLater(() -> {
            btnSign.setDisable(false);
//            progressBar.setVisible(false);
            actualizarEstado("Proceso de firma cancelado por el usuario");

            notificarCancelacionAlFrontend();

            mostrarNotificacion("Cancelado", "Proceso de firma cancelado", false);
        });
    }

    private void notificarCancelacionAlFrontend() {
        if (localServer != null) {
            localServer.notificarResultado(false, "Operación cancelada por el usuario", idDocumentoActual);
            System.out.println("Cancelación notificada al servidor local");
        }
    }

    private void cerrarAplicativo() {
        System.out.println("Cerrando aplicativo...");
        if (localServer != null) {
            localServer.detener();
        }
        Platform.runLater(() -> {
            try {
                Stage stage = (Stage) btnCancel.getScene().getWindow();
                stage.close();
                Platform.exit();
                System.exit(0);
            } catch (Exception e) {
                System.err.println("Error cerrando aplicativo: " + e.getMessage());
                System.exit(0);
            }
        });
    }

    @FXML
    private void onRefreshCertificates() {
        actualizarEstado("Recargando certificados...");
        cargarCertificados();
    }

    @FXML
    private void onSignDocuments() {
        System.out.println("onSignDocuments() - INICIADO");

        if (!documentosRecibidosDesdeWeb) {
            mostrarAlerta("Error", "No se puede firmar sin documentos recibidos desde la web");
            return;
        }

        if (certificadoSeleccionado == null) {
            mostrarAlerta("Error", "Debe seleccionar un certificado válido para firmar");
            return;
        }

        String cnReal = certificadoService.extraerCNDeCertificadoFormateado(certificadoSeleccionado);
        String organizacion = certificadoService.obtenerOrganizacion(certificadoSeleccionado);

        if (certificadoSeleccionado.equals("Seleccionar certificado...") ||
                certificadoSeleccionado.contains("Cargando") || certificadoSeleccionado.contains("Error") ||
                certificadoSeleccionado.contains("no disponible")) {
            mostrarAlerta("Error", "Debe seleccionar un certificado válido para firmar");
            return;
        }

        if (documentosParaFirmar == null || documentosParaFirmar.isEmpty()) {
            mostrarAlerta("Error", "No hay documentos cargados para firmar");
            return;
        }

        procesarFirmaDocumentoUnico(cnReal, organizacion);
    }

    private void procesarFirmaDocumentoUnico(String cnReal, String organizacion) {
        procesoFirmaEnCurso = true;
        actualizarEstado("Iniciando proceso de firma...");
        btnSign.setDisable(true);

        new Thread(() -> {
            int totalDocumentos = documentosParaFirmar.size();
            List<String> errores = new ArrayList<>();
            int firmadosOk = 0;

            try {
                System.out.println("=== INICIANDO FIRMA CON CONFIGURACIÓN DINÁMICA ===");
                System.out.println("Tipo de servicio: SIGNATURE - documentos a firmar: " + totalDocumentos);

                for (int i = 0; i < totalDocumentos; i++) {
                    if (!procesoFirmaEnCurso) {
                        System.out.println("Proceso cancelado por el usuario");
                        return;
                    }

                    DocumentoFirma doc = documentosParaFirmar.get(i);
                    final int indice = i + 1;

                    try {
                        System.out.println("Firmando documento " + indice + "/" + totalDocumentos + ": " + doc.getNombre());

                        Platform.runLater(() -> actualizarEstado("Firmando documento " + indice + "/" + totalDocumentos + "..."));

                        RequestFirma request = new RequestFirma(doc.getContenidoBase64(), cnReal);
                        PosicionFirma pos = doc.getPosicionFirma();

                        if (configuracionFirma != null) {
                            request.setEmpresa(configuracionFirma.getEmpresa().isEmpty() ? organizacion : configuracionFirma.getEmpresa());
                            request.setMotivo(configuracionFirma.getMotivo());
                            request.setLocation(configuracionFirma.getUbicacion());
                        } else {
                            request.setEmpresa(organizacion);
                        }

                        if (pos != null) {
                            request.setPagina(pos.getPagina());
                            request.setX(pos.getX());
                            request.setY(pos.getY());
                        } else if (configuracionFirma != null) {
                            request.setPagina(configuracionFirma.getPagina());
                            request.setX(configuracionFirma.getPosicionX());
                            request.setY(configuracionFirma.getPosicionY());
                        }

                        if (configuracionFirma != null && configuracionFirma.getImagen() != null && !configuracionFirma.getImagen().isEmpty()) {
                            request.setImage(configuracionFirma.getImagen());
                        } else if (request.isVisibleFirma()) {
                            request.setImage(firmaLocalService.crearImagenFirmaBasica());
                        }

                        Object resultadoFirma = firmaLocalService.firmarDocumento(request);

                        if (!procesoFirmaEnCurso) {
                            System.out.println("Proceso cancelado durante la firma");
                            return;
                        }

                        ResponseFirma resultado;
                        if (resultadoFirma instanceof ResponseFirma) {
                            resultado = (ResponseFirma) resultadoFirma;
                        } else {
                            throw new Exception(resultadoFirma.toString());
                        }

                        if (resultado.getErrorFirma() != null && !resultado.getErrorFirma().isEmpty()) {
                            throw new Exception(resultado.getErrorFirma());
                        }

                        System.out.println("Documento " + indice + "/" + totalDocumentos + " firmado localmente, enviando al backend...");

                        enviarABackendSignature(doc, resultado.getDocumentoFirmado());
                        firmadosOk++;

                    } catch (Exception eDoc) {
                        String mensajeError = "Documento \"" + doc.getNombre() + "\": " + eDoc.getMessage();
                        System.err.println("Error firmando documento " + indice + "/" + totalDocumentos + ": " + eDoc.getMessage());
                        eDoc.printStackTrace();
                        errores.add(mensajeError);
                    }
                }

                final int totalOk = firmadosOk;
                if (errores.isEmpty()) {
                    Platform.runLater(() -> {
                        procesoFirmaEnCurso = false;
                        String mensajeExito = totalDocumentos == 1
                                ? "Documento firmado y procesado exitosamente con Servicio Signature"
                                : totalOk + " documentos firmados y procesados exitosamente";

                        actualizarEstado(mensajeExito);
                        btnSign.setDisable(false);

                        notificarExitoAlFrontend();
                        mostrarAlertaExito(mensajeExito);

                        FirmaApplication.ocultarVentana();
                    });
                } else {
                    String mensajeError = String.join(", ", errores);
                    Platform.runLater(() -> {
                        procesoFirmaEnCurso = false;

                        actualizarEstado("Error en la firma");
                        btnSign.setDisable(false);
                        mostrarAlertaErrorDetallado("Error al Firmar Documento", mensajeError);

                        if (totalOk > 0) {
                            notificarExitoAlFrontend();
                        } else {
                            notificarErrorAlFrontend(mensajeError);
                        }

                        FirmaApplication.ocultarVentana();
                    });
                }

            } catch (Exception e) {
                System.err.println("Error en proceso de firma: " + e.getMessage());
                e.printStackTrace();

                Platform.runLater(() -> {
                    procesoFirmaEnCurso = false;
                    String mensajeError = e.getMessage() != null ? e.getMessage() : "Error desconocido al firmar";

                    actualizarEstado("Error en la firma");
                    btnSign.setDisable(false);
                    mostrarAlertaErrorDetallado("Error al Firmar Documento", mensajeError);
                    notificarErrorAlFrontend(mensajeError);

                    FirmaApplication.ocultarVentana();
                });
            }
        }).start();
    }

    private void mostrarAlertaExito(String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Éxito");
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    /**
     * Se llama siempre desde dentro de un Platform.runLater ya en curso (ver
     * procesarFirmaDocumentoUnico): showAndWait() bloquea aquí mismo para que
     * el cierre del aplicativo, que se dispara justo después en el llamador,
     * ocurra recién cuando el usuario cierra esta alerta.
     */
    private void mostrarAlertaErrorDetallado(String titulo, String mensajeCompleto) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(titulo);
        alert.setHeaderText("Ocurrió un error durante el proceso de firma");

        String mensajeFormateado = formatearMensajeError(mensajeCompleto);

        TextArea textArea = new TextArea(mensajeFormateado);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        textArea.setPrefRowCount(10);

        alert.getDialogPane().setContent(textArea);
        alert.getDialogPane().setMinWidth(600);
        alert.showAndWait();
    }

    private String formatearMensajeError(String mensajeError) {
        if (mensajeError == null || mensajeError.isEmpty()) {
            return "Error desconocido";
        }

        if (mensajeError.contains(",")) {
            StringBuilder sb = new StringBuilder();
            sb.append("Se encontraron los siguientes problemas:\n\n");

            String[] errores = mensajeError.split(",");
            int contador = 1;

            for (String error : errores) {
                sb.append(contador).append(". ").append(error.trim()).append("\n");
                contador++;
            }

            return sb.toString();
        }

        return mensajeError;
    }

    private String enviarABackendSignature(DocumentoFirma doc, String documentoFirmado) throws Exception {
        System.out.println("Enviando documento firmado al SERVICIO SIGNATURE: " + doc.getNombre());

        System.out.println("Token recibido:");
        System.out.println("  - Longitud: " + (tokenAuth != null ? tokenAuth.length() : "null"));
        System.out.println("  - Primeros 50 chars: " + (tokenAuth != null ? tokenAuth.substring(0, Math.min(50, tokenAuth.length())) : "null"));
        System.out.println("  - Últimos 20 chars: " + (tokenAuth != null ? tokenAuth.substring(Math.max(0, tokenAuth.length()-20)) : "null"));

        Long idDocumentoBackend;
        String bucket;
        String usuarioModificacion;
        String nombreDocumento;
        int tamanoDocumento;
        String codigoGenerado;

        if (doc.getIdDocumento() != null) {
            idDocumentoBackend = doc.getIdDocumento();
            bucket = doc.getBucket();
            usuarioModificacion = doc.getUsuarioModificacion();
            nombreDocumento = doc.getNombreDocumento();
            tamanoDocumento = doc.getTamanoDocumento() != null ? doc.getTamanoDocumento().intValue() : 0;
            codigoGenerado = doc.getCodigoGenerado();
        } else {
            if (datosBackendJson == null) {
                throw new Exception("No se recibieron datos del backend para el servicio signature");
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode datosBackend = mapper.readTree(datosBackendJson);

            idDocumentoBackend = datosBackend.path("idDocumento").asLong();
            bucket = datosBackend.path("bucket").asText();
            usuarioModificacion = datosBackend.path("usuarioModificacion").asText();
            nombreDocumento = datosBackend.path("nombreDocumento").asText();
            tamanoDocumento = datosBackend.path("tamanoDocumento").asInt();
            codigoGenerado = datosBackend.path("codigoGenerado").asText();
        }

        String baseUrl = baseUrlBackend != null ? baseUrlBackend : ConfigService.getSignatureBackendUrl();

        HttpService httpService = new HttpService(baseUrl);
        httpService.setAuthToken(tokenAuth);

        return httpService.procesarFirmaSignature(
                idDocumentoBackend,
                documentoFirmado,
                bucket,
                usuarioModificacion,
                nombreDocumento,
                tamanoDocumento,
                codigoGenerado
        );
    }

    private void notificarExitoAlFrontend() {
        if (localServer != null) {
            localServer.notificarResultado(true, null, idDocumentoActual);
            System.out.println("Éxito notificado al servidor local");
        }
    }

    private void notificarErrorAlFrontend(String error) {
        if (localServer != null) {
            localServer.notificarResultado(false, error, idDocumentoActual);
            System.out.println("Error notificado al servidor local: " + error);
        }
    }

    public void procesarDocumentosDesdeWeb(String documentosJson, String idDocumento, String token,
                                           String datosBackendJson, String baseUrlBackend) {
        Platform.runLater(() -> {
            try {
                documentosRecibidosDesdeWeb = true;
                this.idDocumentoActual = idDocumento;
                this.tokenAuth = token;
                this.datosBackendJson = datosBackendJson;
                this.baseUrlBackend = baseUrlBackend;

                System.out.println("=== PROCESANDO DOCUMENTOS ===");
                System.out.println("Base URL Backend: " + baseUrlBackend);
                System.out.println("Datos backend recibidos: " + (datosBackendJson != null ? "SÍ" : "NO"));
                System.out.println("=============================");

                procesarConServicioSignature(documentosJson, idDocumento, token);

            } catch (Exception e) {
                System.err.println("ERROR en procesarDocumentosDesdeWeb: " + e.getMessage());
                actualizarEstado("Error procesando documentos: " + e.getMessage());
                mostrarAlerta("Error", "Error al procesar documentos: " + e.getMessage());
            }
        });
    }

    private void procesarConServicioSignature(String documentosJson, String idDocumento, String token) {
        try {
            System.out.println("PROCESANDO CON SERVICIO SIGNATURE");

            ObjectMapper mapper = new ObjectMapper();
            JsonNode documentosMetadatos = mapper.readTree(documentosJson);

            if (!documentosMetadatos.has("documentos") || !documentosMetadatos.get("documentos").isArray()
                    || documentosMetadatos.get("documentos").isEmpty()) {
                throw new RuntimeException("No se recibieron documentos para firmar");
            }

            HttpService httpServiceS3 = new HttpService(ConfigService.getS3BackendUrl());
            httpServiceS3.setAuthToken(token);

            List<DocumentoFirma> documentosFinal = new ArrayList<>();

            for (JsonNode metaDoc : documentosMetadatos.get("documentos")) {
                String keyDocumento = metaDoc.path("keyDocumento").asText();
                if (keyDocumento == null || keyDocumento.isEmpty()) {
                    throw new RuntimeException("No se pudo extraer keyDocumento de los metadatos");
                }

                System.out.println("Obteniendo documento del S3: " + keyDocumento);
                String documentoJsonS3 = httpServiceS3.obtenerDocumentos(keyDocumento);
                JsonNode s3Root = mapper.readTree(documentoJsonS3);
                JsonNode s3Docs = s3Root.path("documentos");
                if (!s3Docs.isArray() || s3Docs.isEmpty()) {
                    throw new RuntimeException("No se pudo obtener del S3 el documento: " + keyDocumento);
                }

                DocumentoFirma doc = new DocumentoFirma();
                doc.setNombre(metaDoc.path("nombre").asText(metaDoc.path("titulo").asText("Documento")));
                doc.setKeyDocumento(keyDocumento);
                doc.setContenidoBase64(s3Docs.get(0).path("contenido").asText());
                doc.setTipo("PDF");

                PosicionFirma pos = new PosicionFirma();
                JsonNode posNode = metaDoc.path("posicionFirma");
                pos.setPagina(!posNode.isMissingNode() ? posNode.path("pagina").asInt(1) : metaDoc.path("pagina").asInt(1));
                pos.setX(!posNode.isMissingNode() ? posNode.path("x").asInt(100) : metaDoc.path("posicionX").asInt(100));
                pos.setY(!posNode.isMissingNode() ? posNode.path("y").asInt(100) : metaDoc.path("posicionY").asInt(100));
                doc.setPosicionFirma(pos);

                JsonNode datosBackendNode = metaDoc.path("datosBackend");
                if (!datosBackendNode.isMissingNode() && !datosBackendNode.isNull()) {
                    doc.setIdDocumento(datosBackendNode.path("idDocumento").asLong());
                    doc.setBucket(datosBackendNode.path("bucket").asText());
                    doc.setUsuarioModificacion(datosBackendNode.path("usuarioModificacion").asText());
                    doc.setNombreDocumento(datosBackendNode.path("nombreDocumento").asText());
                    doc.setTamanoDocumento(datosBackendNode.path("tamanoDocumento").asLong());
                    doc.setCodigoGenerado(datosBackendNode.path("codigoGenerado").asText());
                }

                documentosFinal.add(doc);
                System.out.println("Documento preparado: " + doc.getNombre() + " (página=" + pos.getPagina()
                        + ", x=" + pos.getX() + ", y=" + pos.getY() + ")");
            }

            documentosParaFirmar = documentosFinal;

            ConfiguracionFirma configTrama = new ConfiguracionFirma();
            configTrama.setMotivo("Firma digital - Servicio Signature");
            configTrama.setUbicacion("Lima, Perú");
            configTrama.setEmpresa("");

            PosicionFirma primeraPos = documentosFinal.get(0).getPosicionFirma();
            configTrama.setPosicionX(primeraPos.getX());
            configTrama.setPosicionY(primeraPos.getY());
            configTrama.setPagina(primeraPos.getPagina());

            this.configuracionFirma = configTrama;

            int totalDocumentos = documentosFinal.size();
            Platform.runLater(() -> {
                btnSign.setDisable(false);
                actualizarEstado("Documento(s) listo(s) para firma (Servicio Signature): " + totalDocumentos);
                mostrarNotificacion("Documentos recibidos", totalDocumentos + " documento(s) listo(s) para firma", true);
                actualizarEstadoDocumentos(true);

                System.out.println("SERVICIO SIGNATURE CONFIGURADO EXITOSAMENTE - " + totalDocumentos + " documento(s)");
            });

        } catch (Exception e) {
            System.err.println("Error en servicio Signature: " + e.getMessage());
            e.printStackTrace();

            Platform.runLater(() -> {
                actualizarEstadoDocumentos(false);
                actualizarEstado("Error en servicio signature: " + e.getMessage());
                mostrarAlerta("Error", "Error al procesar documento: " + e.getMessage());
            });

            throw new RuntimeException(e);
        }
    }

    private void actualizarEstado(String mensaje) {
        Platform.runLater(() -> {
//            lblStatus.setText(mensaje);
            System.out.println("Estado: " + mensaje);
        });
    }

    private void mostrarAlerta(String titulo, String mensaje) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(titulo);
            alert.setHeaderText(null);
            alert.setContentText(mensaje);
            alert.showAndWait();
        });
    }

    private void mostrarNotificacion(String titulo, String mensaje, boolean exitoso) {
        Platform.runLater(() -> {
            // ControlsFX Notifications necesita una ventana visible como owner.
            // En modo servicio la ventana arranca oculta hasta que llega una
            // firma real (que la muestra antes de llegar acá) - si por algún
            // motivo se dispara un toast sin ventana visible, se evita el NPE.
            if (lblStatus == null || lblStatus.getScene() == null || lblStatus.getScene().getWindow() == null
                    || !lblStatus.getScene().getWindow().isShowing()) {
                System.out.println("[Notificación omitida, ventana no visible] " + titulo + ": " + mensaje);
                return;
            }
            Notifications.create()
                    .title(titulo)
                    .text(mensaje)
                    .graphic(new FontIcon(exitoso ? FontAwesomeSolid.CHECK_CIRCLE : FontAwesomeSolid.EXCLAMATION_TRIANGLE))
                    .showInformation();
        });
    }

    @FXML
    private void onClearLogs() {
        if (logArea != null) {
            Platform.runLater(() -> {
                logArea.clear();
                logArea.appendText("[" + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) + "] Logs limpiados\n");
            });
        }
    }

    @FXML
    private void onTestCallback() {
        System.out.println("TEST: Simulando callback al frontend");

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Test Callback");
            alert.setHeaderText("¿Qué quieres probar?");
            alert.setContentText("Elige el tipo de respuesta:");

            ButtonType btnExito = new ButtonType("Éxito");
            ButtonType btnError = new ButtonType("Error");
            ButtonType btnCancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(btnExito, btnError, btnCancel);

            alert.showAndWait().ifPresent(response -> {
                if (response == btnExito) {
                    System.out.println("Enviando callback de ÉXITO");
                    notificarExitoAlFrontend();
                } else if (response == btnError) {
                    System.out.println("Enviando callback de ERROR");
                    notificarErrorAlFrontend("Error de prueba desde el aplicativo");
                }
            });
        });
    }
}