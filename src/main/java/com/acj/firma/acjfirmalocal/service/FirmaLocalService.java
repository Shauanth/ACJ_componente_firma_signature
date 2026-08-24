package com.acj.firma.acjfirmalocal.service;

import com.acj.acjfirmalib.controller.FirmaController;
import com.acj.acjfirmalib.model.parametersign.Parametros;
import com.acj.firma.acjfirmalocal.model.RequestFirma;
import com.acj.firma.acjfirmalocal.model.ResponseFirma;

import java.io.*;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Objects;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FirmaLocalService {

    private static final Logger logger = Logger.getLogger(FirmaLocalService.class.getName());
    private static final String TAG = "FirmaLocalService";

    public FirmaLocalService() {
        inicializarCarpetasBasicas();
    }

    public static class CodigoRespuesta {
        public static final String EXITO = "00";
        public static final String ERROR = "01";
        public static final String ARCHIVO_FIRMA_VACIO = "300";
        public static final String ARCHIVO_IMAGEN_VACIO = "301";
    }

    private void inicializarCarpetasBasicas() {
        try {
            String userHome = System.getProperty("user.home");
            File acjDir = new File(userHome, "acj-resources");
            File logDir = new File(acjDir, "log");
            File subLogDir = new File(logDir, "log");

            if (!acjDir.exists()) {
                boolean created = acjDir.mkdirs();
                System.out.println("Carpeta ACJ creada: " + acjDir.getAbsolutePath() + " (éxito: " + created + ")");
            }

            if (!logDir.exists()) {
                boolean created = logDir.mkdirs();
                System.out.println("Carpeta log creada: " + logDir.getAbsolutePath() + " (éxito: " + created + ")");
            }

            if (!subLogDir.exists()) {
                boolean created = subLogDir.mkdirs();
                System.out.println("Subcarpeta log/log creada: " + subLogDir.getAbsolutePath() + " (éxito: " + created + ")");
            }

            System.setProperty("user.dir", userHome);
            System.out.println("Directorio de trabajo configurado: " + userHome);

        } catch (Exception e) {
            System.err.println("Error creando carpetas automáticamente: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Object firmarDocumento(RequestFirma request) {
        logger.log(Level.INFO, "[" + TAG + "] - Empezando el servicio para firmar documento");
        System.out.println("FIRMA PRINCIPAL - Iniciando");
        System.out.println("Documento presente: " + (request.getDocument() != null));
        System.out.println("Documento tamaño: " + (request.getDocument() != null ? request.getDocument().length() : "null"));

        ResponseFirma response = null;
        File pdfFile = null;
        File imgFile = null;
        File docFirmado = null;

        try {
            System.out.println("Validando documento...");
            System.out.println("Documento presente: " + (request.getDocument() != null));
            if (request.getDocument() != null) {
                System.out.println("Documento tamaño: " + request.getDocument().length());
            }

            if (!textNotNullNotEmpty(request.getDocument())) {
                System.out.println("FALLO: Documento vacío");
                logger.log(Level.SEVERE, "[" + TAG + "] - No se envió ningún documento para firmar.");
                return CodigoRespuesta.ARCHIVO_FIRMA_VACIO;
            }
            System.out.println("Documento válido");

            logger.log(Level.INFO, "[" + TAG + "] - Cargando archivos");
            System.out.println("Creando response...");
            response = new ResponseFirma("NO DEFINIDO", "NO DEFINIDO", "NO DEFINIDO", null);

            System.out.println("Obteniendo carpeta temporal...");
            String rutaCarpetaTemporal = obtenerCarpetaTemporal();
            System.out.println("Carpeta temporal: " + rutaCarpetaTemporal);

            UUID uniqueKey = UUID.randomUUID();
            System.out.println("UUID generado: " + uniqueKey);

            String rutaPdfTemp = rutaCarpetaTemporal + File.separator + uniqueKey + ".pdf";
            String rutaImagenTemp = rutaCarpetaTemporal + File.separator + uniqueKey + ".jpg";
            System.out.println("Ruta PDF: " + rutaPdfTemp);

            System.out.println("Creando archivo PDF temporal...");
            pdfFile = base64ToFile(rutaPdfTemp, request.getDocument());
            System.out.println("Archivo PDF creado: " + (pdfFile != null));

            if (pdfFile != null) {
                boolean isVisible = request.isVisibleFirma();
                System.out.println("Firma visible: " + isVisible);

                if (isVisible) {
                    System.out.println("Validando imagen...");
                    if (!textNotNullNotEmpty(request.getImage())) {
                        System.out.println("FALLO: Imagen requerida pero no enviada");
                        logger.log(Level.SEVERE, "[" + TAG + "] - No se envió la imagen para firmar.");
                        pdfFile.delete();
                        return CodigoRespuesta.ARCHIVO_IMAGEN_VACIO;
                    } else {
                        System.out.println("Creando archivo de imagen...");
                        imgFile = base64ToFile(rutaImagenTemp, request.getImage());
                        System.out.println("Archivo imagen creado: " + (imgFile != null));
                    }
                }

                logger.log(Level.INFO, "[" + TAG + "] - Cargando parámetros para la firma");
                System.out.println("Configurando parámetros de firma...");

                Parametros parametros = new Parametros();
                parametros.setArchivoRuta(rutaPdfTemp);
                parametros.setMotivo(request.getMotivo());
                parametros.setLocation(request.getLocation());
                parametros.setAliasCertificado(request.getAliasCertificado());
                parametros.setLevel(request.getLevel());
                parametros.setSufijo(request.getSufijo());
                parametros.setEmpresa(Objects.isNull(request.getEmpresa()) ? "" : request.getEmpresa());

                System.out.println("Parámetros básicos configurados");
                System.out.println("Alias certificado: " + request.getAliasCertificado());

                parametros.setVisibleFirma(isVisible);
                parametros.setSignType(request.getSignType());
                parametros.setExtras(request.getExtras());

                parametros.setRutaImagen(rutaImagenTemp);
                parametros.setTituloFirma(request.getTituloFirma());
                parametros.setTextoPosc1(request.getTexto1());
                parametros.setTextoPosc2(request.getTexto2());
                parametros.setTextoPosc3(request.getTexto3());

                parametros.setAppearance("S");

                parametros.setPagina(request.getPagina());
                parametros.setX(request.getX());
                parametros.setY(request.getY());
                parametros.setFontSize(request.getFontSize());
                parametros.setTextWidth(request.getTextWidth());

//                parametros.setVerificarTsl(request.isVerifyTsl());
                parametros.setVerificarTsl(true);
                parametros.setTslUrl("https://iofe.indecopi.gob.pe/TSL/tsl-pe.xml");
//                parametros.setTslUrl("https://cdn.glitch.global/efa1df4a-e1a8-4105-bd0f-84ef518eb3f1/tsl2025.xml?v=1738875469894");
//                parametros.setTslUrl(request.getTslURL());

                parametros.setVerificarTsa(false);
                parametros.setTsaUrl("");
                parametros.setUsuario(request.getUsuario());
                parametros.setPassword(request.getPassword());

                parametros.setRutaDestino(rutaCarpetaTemporal);

                System.out.println("Todos los parámetros configurados");

                logger.log(Level.INFO, "[" + TAG + "] - Firmando documento utilizando la librería");
                System.out.println("Creando FirmaController...");

                FirmaController firmaController = new FirmaController();
                System.out.println("FirmaController creado, iniciando firma...");

                System.out.println("PARAMETROS FINALES:");
                System.out.println("  - Archivo: " + parametros.getArchivoRuta());
                System.out.println("  - Certificado: " + parametros.getAliasCertificado());
                System.out.println("  - Empresa: " + parametros.getEmpresa());
                System.out.println("  - Level: " + parametros.getLevel());
                System.out.println("  - TSL URL: " + parametros.getTslUrl());
                System.out.println("  - Verificar TSL: " + parametros.isVerificarTsl());

                System.out.println("LLAMANDO A firmaController.firmarDocumento()...");

                long inicio = System.currentTimeMillis();

                try {
                    firmaController.firmarDocumento(parametros);
                    System.out.println("FIRMA COMPLETADA EXITOSAMENTE");
                } catch (Exception firmaEx) {
                    System.out.println("EXCEPCION EN FIRMA: " + firmaEx.getMessage());
                    firmaEx.printStackTrace();
                    throw firmaEx;
                }

                long fin = System.currentTimeMillis();
                double tiempo = (double) ((fin - inicio) / 1000);

                logger.log(Level.INFO, "[" + TAG + "] - La firma finalizó en [" + tiempo + " segundos]");
                System.out.println("Firma completada en " + tiempo + " segundos");

                docFirmado = new File(rutaCarpetaTemporal + File.separator + uniqueKey + "_FIRMADO.pdf");

                response.setTituloDocumento(uniqueKey + ".pdf");
                response.setFechaFirma(getDateActual("yyyy-MM-dd HH:mm"));

                String docFirmadoB64 = fileToBase64(docFirmado);

                if (docFirmadoB64 == null) {
                    logger.log(Level.SEVERE, "[" + TAG + "] - Ocurrió un error al convertir el archivo firmado a base64.");
                    pdfFile.delete();
                    docFirmado.delete();
                    return CodigoRespuesta.ERROR;
                }

                response.setDocumentoFirmado(docFirmadoB64);

                pdfFile.delete();
                if (imgFile != null) {
                    imgFile.delete();
                }
                docFirmado.delete();

                logger.log(Level.INFO, "[" + TAG + "] - Documento firmado exitosamente.");

                return response;

            } else {
                System.out.println("FALLO: No se pudo crear archivo PDF temporal");
                logger.log(Level.SEVERE, "[" + TAG + "] - Ocurrió un error durante la conversión del base64.");
                return CodigoRespuesta.ERROR;
            }

        } catch (Exception e) {
            System.out.println("EXCEPCIÓN GENERAL: " + e.getMessage());
            e.printStackTrace();
            logger.log(Level.SEVERE, "[" + TAG + "] - Ocurrió un error al firmar el documento. [" + e.getMessage() + "]", e);

            if (pdfFile != null) pdfFile.delete();
            if (imgFile != null) imgFile.delete();
            if (docFirmado != null) docFirmado.delete();

//            return CodigoRespuesta.ERROR;
            return e.getMessage() != null ? e.getMessage() : CodigoRespuesta.ERROR;
        }
    }

    private String obtenerMensajeError(String codigo) {
        switch (codigo) {
            case "00": return "Operación exitosa";
            case "01": return "Error general al realizar la operación";
            case "300": return "No se envió ningún documento para firmar";
            case "301": return "No se envió ninguna imagen para firmar";
            default: return "Error desconocido: " + codigo;
        }
    }

    private String obtenerCarpetaTemporal() {
        try {
            String userHome = System.getProperty("user.home");
            String acjResourcesPath = userHome + File.separator + "acj-resources";

            File acjResourcesDir = new File(acjResourcesPath);
            if (!acjResourcesDir.exists()) {
                boolean created = acjResourcesDir.mkdirs();
                System.out.println("Carpeta ACJ Resources creada: " + acjResourcesPath + " (éxito: " + created + ")");

                crearPropertiesSiNoExiste(acjResourcesPath);
            }

            File propsFile = new File(acjResourcesPath + File.separator + "aplicacion.properties");
            if (propsFile.exists()) {
                Properties props = new Properties();
                props.load(new FileInputStream(propsFile));
                String temporal = props.getProperty("file.temporal");
                if (temporal != null) {
                    File tempDir = new File(temporal);
                    if (!tempDir.exists()) {
                        boolean created = tempDir.mkdirs();
                        System.out.println("Carpeta temporal creada: " + temporal + " (éxito: " + created + ")");
                    }
                    return temporal;
                }
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "No se pudo configurar carpeta de usuario, usando directorio temporal del sistema", e);
        }

        String tempDir = System.getProperty("java.io.tmpdir") + File.separator + "acj-firma";
        File dir = new File(tempDir);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            System.out.println("Carpeta temporal fallback creada: " + tempDir + " (éxito: " + created + ")");
        }
        return tempDir;
    }

    private void crearPropertiesSiNoExiste(String rutaAcjResources) {
        try {
            File propsFile = new File(rutaAcjResources + File.separator + "aplicacion.properties");
            if (!propsFile.exists()) {
                String defaultProps = """
                # Configuración ACJ Firma Local - Generado automáticamente
                
                # Configuración de carpeta temporal para procesamiento de documentos
                file.temporal=%s%sacj-firma-temp
                
                # Configuración de logging
                log.nivel=INFO
                log.archivo=acj-firma.log
                
                # Configuración de certificados
                certificados.validar=true
                certificados.cache=true
                
                # Configuración de TSL (Trust Service List)
                tsl.url=https://iofe.indecopi.gob.pe/TSL/tsl-pe.xml
                tsl.verificar=true
                
                # Configuración de TSA (Time Stamp Authority)
                tsa.url=
                tsa.verificar=false
                tsa.usuario=
                tsa.password=
                
                # Configuración de firma por defecto
                firma.motivo=Firma digital local ACJ
                firma.ubicacion=Lima, Perú
                firma.nivel=B
                firma.sufijo=_FIRMADO
                firma.empresa=ACJ Digital
                
                # Configuración de apariencia de firma
                firma.visible=true
                firma.tipo=LT
                firma.extras=E
                firma.titulo=Firma ACJ Digital
                firma.texto1=Firmado digitalmente
                firma.texto2=por ACJ Signature
                firma.texto3=Documento aprobado
                
                # Configuración de posición de firma (por defecto)
                firma.pagina=1
                firma.x=100
                firma.y=100
                firma.fontSize=8
                firma.textWidth=50
                
                # Configuración de timeouts (en segundos)
                http.timeout.conexion=30
                http.timeout.lectura=60
                
                # Configuración de SSL
                ssl.verificar=false
                ssl.certificado.personalizado=true
                """.formatted(
                        System.getProperty("user.home"),
                        File.separator
                );

                try (FileWriter writer = new FileWriter(propsFile)) {
                    writer.write(defaultProps);
                    System.out.println("Archivo aplicacion.properties creado automáticamente");
                }
            }
        } catch (Exception e) {
            System.err.println("No se pudo crear archivo properties automáticamente: " + e.getMessage());
        }
    }

    private File base64ToFile(String filePath, String fileBase64) {
        logger.log(Level.INFO, "[" + TAG + "] - Generando un archivo a partir del base64");
        File fileTmp = null;

        try {
            fileTmp = new File(filePath);
            boolean alreadyExist = fileTmp.createNewFile();
            if (alreadyExist) {
                FileOutputStream fos = new FileOutputStream(fileTmp);
                byte[] backToBytes = Base64.getDecoder().decode(fileBase64);
                fos.write(backToBytes);
                fos.close();
            } else {
                logger.log(Level.SEVERE, "[" + TAG + "] - La ruta temporal para la creación del archivo ya existe.");
                fileTmp = null;
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "[" + TAG + "] - Ocurrió un error durante la conversión.", e);
        }

        return fileTmp;
    }

    private String fileToBase64(File file) {
        logger.log(Level.INFO, "[" + TAG + "] - Generando cadena base64 a partir del archivo");
        String responseBase64 = null;

        try {
            responseBase64 = Base64.getEncoder().encodeToString(getFileDataAsBytes(file));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[" + TAG + "] - Ocurrió un error durante la conversión.", e);
        }

        return responseBase64;
    }

    private byte[] getFileDataAsBytes(File file) throws Exception {
        int length = (int) file.length();
        BufferedInputStream reader = new BufferedInputStream(new FileInputStream(file));
        byte[] bytes = new byte[length];
        reader.read(bytes, 0, length);
        reader.close();
        return bytes;
    }

    private boolean textNotNullNotEmpty(String texto) {
        return (texto != null && !texto.trim().isEmpty());
    }

    private String getDateActual(String pattern) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(pattern);
        LocalDateTime now = LocalDateTime.now();
        return dtf.format(now);
    }


    public String crearImagenFirmaBasica() {
        return "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";
    }
}
