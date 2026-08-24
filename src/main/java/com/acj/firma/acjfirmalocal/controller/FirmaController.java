package com.acj.firma.acjfirmalocal.controller;

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
    private String tramaCallback;
    private String callbackUrl;
    private String idDocumentoActual;
    private String tokenAuth;

    private boolean documentosRecibidosDesdeWeb = false;
    private boolean procesoFirmaEnCurso = false;

    private boolean esSignature = false;
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

            System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
                private StringBuilder buffer = new StringBuilder();

                @Override
                public void write(int b) throws java.io.IOException {
                    if (b == '\n') {
                        Platform.runLater(() -> {
                            logArea.appendText(buffer.toString() + "\n");
                            buffer.setLength(0);
                            // Auto-scroll
                            logArea.setScrollTop(Double.MAX_VALUE);
                        });
                    } else {
                        buffer.append((char) b);
                    }
                }
            }));
        }
    }

    private void setupUI() {
//        btnRefreshCerts.setGraphic(new FontIcon(FontAwesomeSolid.SYNC_ALT));
        btnSign.setGraphic(new FontIcon(FontAwesomeSolid.FILE_SIGNATURE));

        lblAppTitle.setText("ACJ Signature");
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
        System.out.println("onSignDocuments() - INICIADO con " +
                (esSignature ? "SERVICIO SIGNATURE" : "SERVICIO APUSIGN"));

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

        boolean usarFlujoMultiple = !esSignature && documentosParaFirmar.size() > 1;

        if (usarFlujoMultiple) {
            System.out.println("=== USANDO FLUJO MÚLTIPLE (Apusign con " + documentosParaFirmar.size() + " documentos) ===");
            procesarFirmaMultiplesDocumentos(cnReal, organizacion);
        } else {
            System.out.println("=== USANDO FLUJO TRADICIONAL (Signature o Apusign con 1 documento) ===");
            procesarFirmaDocumentoUnico(cnReal, organizacion);
        }
    }

    private void procesarFirmaDocumentoUnico(String cnReal, String organizacion) {
        procesoFirmaEnCurso = true;
        actualizarEstado("Iniciando proceso de firma...");
        btnSign.setDisable(true);

        new Thread(() -> {
            try {
                System.out.println("=== INICIANDO FIRMA CON CONFIGURACIÓN DINÁMICA ===");
                System.out.println("Tipo de servicio: " + (esSignature ? "SIGNATURE" : "APUSIGN"));

                if (!procesoFirmaEnCurso) {
                    System.out.println("Proceso cancelado por el usuario");
                    return;
                }

                DocumentoFirma primerDoc = documentosParaFirmar.get(0);
                String documentoBase64 = primerDoc.getContenidoBase64();

                System.out.println("Firmando documento localmente: " + primerDoc.getNombre());

                RequestFirma request = new RequestFirma(documentoBase64, cnReal);

                if (configuracionFirma != null) {
                    request.setEmpresa(configuracionFirma.getEmpresa().isEmpty() ? organizacion : configuracionFirma.getEmpresa());
                    request.setMotivo(configuracionFirma.getMotivo());
                    request.setLocation(configuracionFirma.getUbicacion());
                    request.setPagina(configuracionFirma.getPagina());
                    request.setX(configuracionFirma.getPosicionX());
                    request.setY(configuracionFirma.getPosicionY());

                    if (configuracionFirma.getImagen() != null && !configuracionFirma.getImagen().isEmpty()) {
                        request.setImage(configuracionFirma.getImagen());
                    } else if (request.isVisibleFirma()) {
                        request.setImage(firmaLocalService.crearImagenFirmaBasica());
                    }
                } else {
                    request.setEmpresa(organizacion);
                    if (request.isVisibleFirma()) {
                        request.setImage(firmaLocalService.crearImagenFirmaBasica());
                    }
                }

                Object resultadoFirma = firmaLocalService.firmarDocumento(request);

                if (!procesoFirmaEnCurso) {
                    System.out.println("Proceso cancelado durante la firma");
                    return;
                }

                ResponseFirma resultado;
                if (resultadoFirma instanceof ResponseFirma) {
                    resultado = (ResponseFirma) resultadoFirma;

                    if (resultado.getErrorFirma() != null && !resultado.getErrorFirma().isEmpty()) {
                        String errorDetallado = resultado.getErrorFirma();
                        System.err.println("Error detallado en firma: " + errorDetallado);
                        throw new Exception(errorDetallado);
                    }
                } else {
                    String mensajeError = resultadoFirma.toString();
                    System.err.println("Error en firma: " + mensajeError);
                    throw new Exception(mensajeError);
                }

                if (resultado.getErrorFirma() != null && !resultado.getErrorFirma().isEmpty()) {
                    throw new Exception("Error en firma: " + resultado.getErrorFirma());
                }

                System.out.println("Documento firmado localmente, enviando al backend...");

                if (esSignature) {
                    enviarABackendSignature(resultado.getDocumentoFirmado());
                } else {
                    enviarABackendApusignDocumentoUnico(resultado.getDocumentoFirmado());
                }

                Platform.runLater(() -> {
                    procesoFirmaEnCurso = false;

                    String mensajeExito = "Documento firmado y procesado exitosamente con " +
                            (esSignature ? "Servicio Signature" : "Servicio Apusign");

                    actualizarEstado(mensajeExito);
                    btnSign.setDisable(false);

                    notificarExitoAlFrontend();
                    mostrarNotificacion("Éxito", "Documento firmado y guardado en el sistema", true);
                });

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
                });
            }
        }).start();
    }

    private void procesarFirmaMultiplesDocumentos(String cnReal, String organizacion) {
        procesoFirmaEnCurso = true;
        actualizarEstado("Iniciando proceso de firma para " + documentosParaFirmar.size() + " documentos...");
        btnSign.setDisable(true);

        new Thread(() -> {
            try {
                System.out.println("=== FIRMANDO " + documentosParaFirmar.size() + " DOCUMENTOS ===");

                if (!procesoFirmaEnCurso) {
                    System.out.println("Proceso cancelado por el usuario");
                    return;
                }

                List<DocumentoFirmadoDto> documentosFirmados = new ArrayList<>();
                int contador = 1;

                for (DocumentoFirma doc : documentosParaFirmar) {
                    System.out.println("Firmando documento " + contador + "/" + documentosParaFirmar.size() + ": " + doc.getNombre());

                    int finalContador = contador;
                    Platform.runLater(() ->
                            actualizarEstado("Firmando documento " + finalContador + " de " + documentosParaFirmar.size() + "...")
                    );

                    String documentoBase64 = doc.getContenidoBase64();
                    RequestFirma request = new RequestFirma(documentoBase64, cnReal);

                    if (configuracionFirma != null) {
                        request.setEmpresa(configuracionFirma.getEmpresa().isEmpty() ? organizacion : configuracionFirma.getEmpresa());
                        request.setMotivo(configuracionFirma.getMotivo());
                        request.setLocation(configuracionFirma.getUbicacion());

                        if (configuracionFirma.getImagen() != null && !configuracionFirma.getImagen().isEmpty()) {
                            request.setImage(configuracionFirma.getImagen());
                        } else if (request.isVisibleFirma()) {
                            request.setImage(firmaLocalService.crearImagenFirmaBasica());
                        }
                    } else {
                        request.setEmpresa(organizacion);
                        if (request.isVisibleFirma()) {
                            request.setImage(firmaLocalService.crearImagenFirmaBasica());
                        }
                    }

                    if (doc.getPosicionFirma() != null) {
                        request.setPagina(doc.getPosicionFirma().getPagina());
                        request.setX(doc.getPosicionFirma().getX());
                        request.setY(doc.getPosicionFirma().getY());
                    } else if (configuracionFirma != null) {
                        request.setPagina(configuracionFirma.getPagina());
                        request.setX(configuracionFirma.getPosicionX());
                        request.setY(configuracionFirma.getPosicionY());
                    } else {
                        request.setPagina(1);
                        request.setX(100);
                        request.setY(100);
                    }

                    Object resultadoFirma = firmaLocalService.firmarDocumento(request);

                    if (!procesoFirmaEnCurso) {
                        System.out.println("Proceso cancelado durante la firma");
                        return;
                    }

                    ResponseFirma resultado;
                    if (resultadoFirma instanceof ResponseFirma) {
                        resultado = (ResponseFirma) resultadoFirma;

                        if (resultado.getErrorFirma() != null && !resultado.getErrorFirma().isEmpty()) {
                            throw new Exception("Error firmando documento " + contador + ": " + resultado.getErrorFirma());
                        }
                    } else {
                        throw new Exception("Error en firma del documento " + contador + ": " + resultadoFirma.toString());
                    }

                    DocumentoFirmadoDto docFirmado = new DocumentoFirmadoDto();
                    docFirmado.setIdDocumento(doc.getIdDocumento());
                    docFirmado.setDocumentoFirmado(resultado.getDocumentoFirmado());

                    List<SignParameter> params = new ArrayList<>();
                    SignParameter param = new SignParameter();
                    param.setPositionX(Float.valueOf(request.getX()));
                    param.setPositionY(Float.valueOf(request.getY()));
                    param.setValue("firma");
                    params.add(param);

                    docFirmado.setParametrosFirma(params);
                    documentosFirmados.add(docFirmado);

                    System.out.println("Documento " + contador + " firmado exitosamente");
                    contador++;
                }

                System.out.println("Todos los documentos firmados, enviando al backend...");

                enviarABackendApusignMultiplesDocumentos(documentosFirmados);

                Platform.runLater(() -> {
                    procesoFirmaEnCurso = false;

                    String mensajeExito = documentosFirmados.size() + " documento(s) firmado(s) y procesado(s) exitosamente";

                    actualizarEstado(mensajeExito);
                    btnSign.setDisable(false);

                    notificarExitoAlFrontend();
                    mostrarNotificacion("Éxito", mensajeExito, true);
                });

            } catch (Exception e) {
                System.err.println("Error en proceso de firma múltiple: " + e.getMessage());
                e.printStackTrace();

                Platform.runLater(() -> {
                    procesoFirmaEnCurso = false;
                    String mensajeError = e.getMessage() != null ? e.getMessage() : "Error desconocido al firmar";

                    actualizarEstado("Error en la firma");
                    btnSign.setDisable(false);
                    mostrarAlertaErrorDetallado("Error al Firmar Documentos", mensajeError);
                    notificarErrorAlFrontend(mensajeError);
                });
            }
        }).start();
    }

    private String enviarABackendApusignDocumentoUnico(String documentoFirmado) throws Exception {
        System.out.println("Enviando documento firmado al SERVICIO APUSIGN (modo único)");

        HttpService httpService = new HttpService(ConfigService.getApusignBackendUrl());
        httpService.setAuthToken(tokenAuth);

        return httpService.procesarFirmaFinal(
                tramaCallback,
                Long.valueOf(idDocumentoActual),
                documentoFirmado
        );
    }

    private String enviarABackendApusignMultiplesDocumentos(List<DocumentoFirmadoDto> documentosFirmados) throws Exception {
        System.out.println("Enviando " + documentosFirmados.size() + " documentos firmados al SERVICIO APUSIGN (modo múltiple)");

        HttpService httpService = new HttpService(ConfigService.getApusignBackendUrl());
        httpService.setAuthToken(tokenAuth);

        return httpService.procesarFirmaFinalMultiple(
                tramaCallback,
                documentosFirmados
        );
    }
    
    private void mostrarAlertaErrorDetallado(String titulo, String mensajeCompleto) {
        Platform.runLater(() -> {
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
        });
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

    private String enviarABackendSignature(String documentoFirmado) throws Exception {
        System.out.println("Enviando documento firmado al SERVICIO SIGNATURE");

        System.out.println("Token recibido:");
        System.out.println("  - Longitud: " + (tokenAuth != null ? tokenAuth.length() : "null"));
        System.out.println("  - Primeros 50 chars: " + (tokenAuth != null ? tokenAuth.substring(0, Math.min(50, tokenAuth.length())) : "null"));
        System.out.println("  - Últimos 20 chars: " + (tokenAuth != null ? tokenAuth.substring(Math.max(0, tokenAuth.length()-20)) : "null"));

        if (datosBackendJson == null) {
            throw new Exception("No se recibieron datos del backend para el servicio signature");
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode datosBackend = mapper.readTree(datosBackendJson);

        String baseUrl = baseUrlBackend != null ? baseUrlBackend : ConfigService.getSignatureBackendUrl();

        HttpService httpService = new HttpService(baseUrl);
        httpService.setAuthToken(tokenAuth);

        return httpService.procesarFirmaSignature(
                datosBackend.path("idDocumento").asLong(),
                documentoFirmado,
                datosBackend.path("bucket").asText(),
                datosBackend.path("usuarioModificacion").asText(),
                datosBackend.path("nombreDocumento").asText(),
                datosBackend.path("tamanoDocumento").asInt(),
                datosBackend.path("codigoGenerado").asText()
        );
    }

    private String enviarABackendApusign(String documentoFirmado) throws Exception {
        System.out.println("Enviando documento firmado al SERVICIO APUSIGN");

        HttpService httpService = new HttpService(ConfigService.getApusignBackendUrl());
        httpService.setAuthToken(tokenAuth);

        return httpService.procesarFirmaFinal(
                tramaCallback,
                Long.valueOf(idDocumentoActual),
                documentoFirmado
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

    public void procesarDocumentosDesdeWeb(String documentosJson, String trama, String callback,
                                           String idDocumento, String token, boolean esServicioSignature) {
        this.procesarDocumentosDesdeWeb(documentosJson, trama, callback, idDocumento, token, esServicioSignature, null, null);
    }

    public void procesarDocumentosDesdeWeb(String documentosJson, String trama, String callback,
                                           String idDocumento, String token, boolean esServicioSignature,
                                           String datosBackendJson, String baseUrlBackend) {
        Platform.runLater(() -> {
            try {
                documentosRecibidosDesdeWeb = true;
                this.idDocumentoActual = idDocumento;
                this.tokenAuth = token;
                this.tramaCallback = trama;
                this.callbackUrl = callback;
                this.esSignature = esServicioSignature;
                this.datosBackendJson = datosBackendJson;
                this.baseUrlBackend = baseUrlBackend;

                System.out.println("=== PROCESANDO DOCUMENTOS ===");
                System.out.println("Servicio Signature: " + esServicioSignature);
                if (esServicioSignature) {
                    System.out.println("Base URL Backend: " + baseUrlBackend);
                    System.out.println("Datos backend recibidos: " + (datosBackendJson != null ? "SÍ" : "NO"));
                }
                System.out.println("=============================");

                if (esServicioSignature) {
                    procesarConServicioSignature(documentosJson, trama, callback, idDocumento, token);
                } else {
                    procesarConServicioApusign(documentosJson, trama, callback, idDocumento, token);
                }

            } catch (Exception e) {
                System.err.println("ERROR en procesarDocumentosDesdeWeb: " + e.getMessage());
                actualizarEstado("Error procesando documentos: " + e.getMessage());
                mostrarAlerta("Error", "Error al procesar documentos: " + e.getMessage());
            }
        });
    }

    private void procesarConServicioSignature(String documentosJson, String trama, String callback,
                                              String idDocumento, String token) {
        try {
            System.out.println("PROCESANDO CON SERVICIO SIGNATURE");

            ObjectMapper mapper = new ObjectMapper();
            JsonNode documentosArray = mapper.readTree(documentosJson);

            String keyDocumento = null;
            if (documentosArray.has("documentos") && documentosArray.get("documentos").isArray()) {
                JsonNode primerDoc = documentosArray.get("documentos").get(0);
                keyDocumento = primerDoc.path("keyDocumento").asText();

                System.out.println("Key del documento extraído: " + keyDocumento);
            }

            if (keyDocumento == null || keyDocumento.isEmpty()) {
                throw new RuntimeException("No se pudo extraer keyDocumento de los metadatos");
            }

            System.out.println("Obteniendo documento del S3");

            HttpService httpServiceS3 = new HttpService(ConfigService.getS3BackendUrl());
            httpServiceS3.setAuthToken(token);

            String documentoJsonS3 = httpServiceS3.obtenerDocumentos(keyDocumento);
            System.out.println("Documento obtenido del S3 exitosamente");

            documentosParaFirmar = documentoService.procesarTramaDocumentos(documentoJsonS3);

            if (documentosParaFirmar.isEmpty()) {
                throw new RuntimeException("No se pudo procesar el documento obtenido del S3");
            }

            ConfiguracionFirma configTrama = new ConfiguracionFirma();
            configTrama.setMotivo("Firma digital - Servicio Signature");
            configTrama.setUbicacion("Lima, Perú");
            configTrama.setEmpresa("");

            JsonNode documentosMetadatos = mapper.readTree(documentosJson);
            if (documentosMetadatos.has("documentos") && documentosMetadatos.get("documentos").isArray()) {
                JsonNode primerDoc = documentosMetadatos.get("documentos").get(0);
                configTrama.setPosicionX(primerDoc.path("posicionX").asInt(100));
                configTrama.setPosicionY(primerDoc.path("posicionY").asInt(100));
                configTrama.setPagina(primerDoc.path("pagina").asInt(1));

                System.out.println("Posiciones extraídas de metadatos:");
                System.out.println("   - Página: " + configTrama.getPagina());
                System.out.println("   - Posición X: " + configTrama.getPosicionX());
                System.out.println("   - Posición Y: " + configTrama.getPosicionY());
            }

            this.configuracionFirma = configTrama;

            Platform.runLater(() -> {
                btnSign.setDisable(false);
                actualizarEstado("Documento listo para firma (Servicio Signature)");
                mostrarNotificacion("Documento recibido", "Documento listo para firma", true);
                actualizarEstadoDocumentos(true);

                System.out.println("SERVICIO SIGNATURE CONFIGURADO EXITOSAMENTE");
                System.out.println("Documento obtenido del S3 y configurado para firma");
                System.out.println("Posiciones: X=" + configTrama.getPosicionX() + ", Y=" + configTrama.getPosicionY() + ", Página=" + configTrama.getPagina());
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

    private void procesarConServicioApusign(String documentosJson, String trama, String callback,
                                            String idDocumento, String token) {
        try {
            System.out.println("PROCESANDO CON SERVICIO APUSIGN");

            actualizarEstado("Procesando documentos desde web...");

            System.out.println("=== DATOS RECIBIDOS DESDE ANGULAR ===");
            System.out.println("DocumentosJson length: " + documentosJson.length());
            System.out.println("DocumentosJson preview: " + documentosJson.substring(0, Math.min(200, documentosJson.length())));
            System.out.println("Trama length: " + trama.length());
            System.out.println("IdDocumento: " + idDocumento);
            System.out.println("Token: " + (token != null ? "Presente" : "Ausente"));
            System.out.println("=====================================");

            System.out.println("PASO 1: Decodificando trama con endpoint backend...");
            TramaInfo tramaInfo = null;

            HttpService httpService = new HttpService();
            httpService.setAuthToken(token);

            try {
                tramaInfo = httpService.decodificarTramaConBackend(trama);
                System.out.println("Trama decodificada exitosamente: " + tramaInfo.toString());
            } catch (Exception e) {
                System.err.println("ERROR decodificando trama con backend: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Error decodificando trama con backend: " + e.getMessage(), e);
            }

            System.out.println("PASO 2: Extrayendo configuración de TramaInfo...");
            ConfiguracionFirma configTrama = new ConfiguracionFirma();

            try {
                configTrama.setMotivo("Firma digital local ACJ");
                configTrama.setUbicacion("Lima, Perú");
                configTrama.setEmpresa("");

                if (tramaInfo.getDocumentos() != null && !tramaInfo.getDocumentos().isEmpty()) {
                    Map<String, Object> primerDocTrama = tramaInfo.getDocumentos().get(0);

                    if (primerDocTrama.containsKey("posicionX")) {
                        configTrama.setPosicionX((Integer) primerDocTrama.get("posicionX"));
                    }
                    if (primerDocTrama.containsKey("posicionY")) {
                        configTrama.setPosicionY((Integer) primerDocTrama.get("posicionY"));
                    }
                    if (primerDocTrama.containsKey("pagina")) {
                        configTrama.setPagina((Integer) primerDocTrama.get("pagina"));
                    }

                    System.out.println("Posiciones extraídas de TramaInfo:");
                    System.out.println("   - Página: " + configTrama.getPagina());
                    System.out.println("   - Posición X: " + configTrama.getPosicionX());
                    System.out.println("   - Posición Y: " + configTrama.getPosicionY());
                }

                System.out.println("Configuración básica extraída de TramaInfo");
            } catch (Exception e) {
                System.err.println("ERROR extrayendo configuración de TramaInfo: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Error extrayendo configuración de TramaInfo: " + e.getMessage(), e);
            }

            System.out.println("PASO 3: Parseando documentosJson...");
            JsonNode documentosArray = null;

            try {
                ObjectMapper mapper = new ObjectMapper();
                documentosArray = mapper.readTree(documentosJson);

                if (!documentosArray.has("documentos") || !documentosArray.get("documentos").isArray()) {
                    throw new RuntimeException("DocumentosJson no tiene estructura esperada - falta campo 'documentos'");
                }

                JsonNode docs = documentosArray.get("documentos");
                if (docs.size() == 0) {
                    throw new RuntimeException("Array de documentos está vacío");
                }

                JsonNode primerDoc = docs.get(0);
                if (primerDoc.has("posicionX")) {
                    configTrama.setPosicionX(primerDoc.path("posicionX").asInt(configTrama.getPosicionX()));
                }
                if (primerDoc.has("posicionY")) {
                    configTrama.setPosicionY(primerDoc.path("posicionY").asInt(configTrama.getPosicionY()));
                }
                if (primerDoc.has("pagina")) {
                    configTrama.setPagina(primerDoc.path("pagina").asInt(configTrama.getPagina()));
                }

                System.out.println("DocumentosJson parseado - " + docs.size() + " documentos encontrados");
                System.out.println("Posiciones finales (Angular tiene prioridad):");
                System.out.println("   - Página: " + configTrama.getPagina());
                System.out.println("   - Posición X: " + configTrama.getPosicionX());
                System.out.println("   - Posición Y: " + configTrama.getPosicionY());

            } catch (Exception e) {
                System.err.println("ERROR parseando documentosJson: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Error parseando documentosJson: " + e.getMessage(), e);
            }

            System.out.println("PASO 4: Validando configuración...");
            try {
                validarConfiguracionFirma(configTrama);
                this.configuracionFirma = configTrama;
                System.out.println("Configuración validada y guardada");
            } catch (Exception e) {
                System.err.println("ERROR validando configuración: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Error validando configuración: " + e.getMessage(), e);
            }

            System.out.println("=== CONFIGURACIÓN FINAL EXTRAÍDA ===");
            System.out.println(configTrama.toString());
            System.out.println("====================================");

            System.out.println("PASO 5: Procesando documentos con DocumentoService...");
            try {
                documentosParaFirmar = documentoService.procesarTramaDocumentos(documentosJson);
                System.out.println("DocumentoService procesó " + documentosParaFirmar.size() + " documentos");

                System.out.println("PASO 5.1: Obteniendo contenido de documentos desde S3...");

                for (DocumentoFirma doc : documentosParaFirmar) {
                    if (doc.getContenidoBase64() == null || doc.getContenidoBase64().isEmpty()) {
                        System.out.println("Obteniendo documento: " + doc.getNombre() + " (key: " + doc.getKeyDocumento() + ")");

                        try {
                            String documentoJsonS3 = httpService.obtenerDocumentos(doc.getKeyDocumento());

                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode docS3 = mapper.readTree(documentoJsonS3);

                            if (docS3.has("documentos") && docS3.get("documentos").isArray()) {
                                JsonNode primerDoc = docS3.get("documentos").get(0);
                                String contenidoBase64 = primerDoc.path("contenido").asText();

                                if (contenidoBase64 != null && !contenidoBase64.isEmpty()) {
                                    doc.setContenidoBase64(contenidoBase64);
                                    System.out.println("Contenido obtenido correctamente (tamaño: " + contenidoBase64.length() + " chars)");
                                } else {
                                    System.err.println("El contenido del documento está vacío");
                                    throw new RuntimeException("Contenido vacío para documento: " + doc.getNombre());
                                }
                            } else {
                                System.err.println("Respuesta S3 no tiene estructura esperada");
                                throw new RuntimeException("Respuesta S3 inválida para documento: " + doc.getNombre());
                            }

                        } catch (Exception e) {
                            System.err.println("Error obteniendo documento de S3: " + e.getMessage());
                            e.printStackTrace();
                            throw new RuntimeException("No se pudo obtener el documento " + doc.getNombre() + " desde S3: " + e.getMessage());
                        }
                    } else {
                        System.out.println("Documento " + doc.getNombre() + " ya tiene contenido");
                    }
                }

                System.out.println("PASO 5.2: Todos los documentos tienen contenido - Total: " + documentosParaFirmar.size());

            } catch (Exception e) {
                System.err.println("ERROR en DocumentoService.procesarTramaDocumentos: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Error procesando documentos en DocumentoService: " + e.getMessage(), e);
            }

            if (documentosParaFirmar.isEmpty()) {
                System.err.println("ERROR: DocumentoService devolvió lista vacía");
                actualizarEstadoDocumentos(false);
                actualizarEstado("No se recibieron documentos válidos");
                mostrarAlerta("Error", "No se pudieron procesar los documentos recibidos");
                return;
            }

            System.out.println("PASO 6: Actualizando interfaz de usuario...");
            Platform.runLater(() -> {
                try {
                    btnSign.setDisable(false);

                    String mensajeEstado = documentosParaFirmar.size() == 1
                            ? "Documento listo para firma digital"
                            : documentosParaFirmar.size() + " documentos listos para firma digital";

                    actualizarEstado(mensajeEstado);
                    mostrarNotificacion("Documento recibido", mensajeEstado, true);
                    actualizarEstadoDocumentos(true);

                    System.out.println("PROCESO COMPLETADO EXITOSAMENTE!");
                    System.out.println("Cantidad de documentos: " + documentosParaFirmar.size());
                    System.out.println("Configuración lista para firma");
                    System.out.println("Posiciones finales: X=" + configTrama.getPosicionX() + ", Y=" + configTrama.getPosicionY() + ", Página=" + configTrama.getPagina());
                } catch (Exception e) {
                    System.err.println("ERROR actualizando UI: " + e.getMessage());
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {
            System.err.println("ERROR en servicio Apusign: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private boolean validarConfiguracionFirma(ConfiguracionFirma config) {
        if (config == null) {
            System.out.println("Configuración de firma es null");
            return false;
        }

        boolean valida = true;

        if (config.getPagina() < 1) {
            System.out.println("Página inválida (" + config.getPagina() + "), corrigiendo a 1");
            config.setPagina(1);
            valida = false;
        }

        if (config.getPosicionX() < 0 || config.getPosicionX() > 595) {
            System.out.println("Posición X inválida (" + config.getPosicionX() + "), corrigiendo a 100");
            config.setPosicionX(100);
            valida = false;
        }

        if (config.getPosicionY() < 0 || config.getPosicionY() > 842) {
            System.out.println("Posición Y inválida (" + config.getPosicionY() + "), corrigiendo a 100");
            config.setPosicionY(100);
            valida = false;
        }

        if (valida) {
            System.out.println("Configuración de firma validada correctamente");
        } else {
            System.out.println("Configuración de firma corregida automáticamente");
        }

        return true;
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