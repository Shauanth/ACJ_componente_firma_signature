package com.acj.firma.acjfirmalocal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.logging.Logger;
import java.util.logging.Level;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.io.InputStream;
import java.io.FileInputStream;

public class HttpService {

    private static final Logger logger = Logger.getLogger(HttpService.class.getName());

    private HttpClient httpClient;
    private String baseUrl;
    private String authToken;

    public HttpService(String baseUrl) {
        this.baseUrl = baseUrl;
        configurarCertificadoSSL();
        System.out.println("HttpService configurado con baseUrl: " + baseUrl);
    }

    public void setAuthToken(String token) {
        this.authToken = token;
        System.out.println("Token configurado en HttpService: " + (token != null ? "SI" : "NO"));
    }

    private void configurarCertificadoSSL() {
        try {
            System.out.println("Configurando certificado SSL personalizado...");

            InputStream certStream = getClass().getResourceAsStream("/certificates/acjdigital.crt");
            if (certStream == null) {
                throw new RuntimeException("Certificado acjdigital.crt no encontrado en /certificates/");
            }

            System.out.println("Certificado encontrado, procesando...");

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate cert = cf.generateCertificate(certStream);
            certStream.close();

            System.out.println("Certificado parseado correctamente");

            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());

            String javaHome = System.getProperty("java.home");
            String cacertsPath = javaHome + "/lib/security/cacerts";

            System.out.println("Cargando cacerts desde: " + cacertsPath);

            try (FileInputStream cacertsStream = new FileInputStream(cacertsPath)) {
                trustStore.load(cacertsStream, "changeit".toCharArray());
            }

            trustStore.setCertificateEntry("acjdigital", cert);
            System.out.println("Certificado ACJ agregado al truststore");

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());

            this.httpClient = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            System.out.println("Certificado ACJ configurado exitosamente");

        } catch (Exception e) {
            System.err.println("Error configurando certificado SSL: " + e.getMessage());
            e.printStackTrace();

            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            logger.log(Level.SEVERE, "SSL configuration failed, using basic HttpClient", e);
        }
    }

    public String obtenerDocumentos(String keyDocument) {
        try {
            logger.log(Level.INFO, "Obteniendo documentos para keyDocument: " + keyDocument);
            System.out.println("HTTP: Iniciando petición para " + keyDocument);
            System.out.println("HTTP: URL base: " + baseUrl);

            String url = baseUrl + "/s3/documento";
            System.out.println("HTTP: URL completa: " + url);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("codigoCanal", "APP-FIRMA")
                    .header("codigoFuncionalidad", "FIRMA-WEB")
                    .header("nameBucket", "aws-test-prueba")
                    .header("keyDocument", java.net.URLEncoder.encode(keyDocument, "UTF-8"));

            if (authToken != null && !authToken.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + authToken);
                System.out.println("Token agregado a la petición");
            }

            HttpRequest request = requestBuilder.GET().build();

            System.out.println("HTTP: Enviando petición...");
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            System.out.println("HTTP: Respuesta recibida - Status: " + response.statusCode());

            if (response.statusCode() == 200) {
                logger.log(Level.INFO, "Documentos obtenidos exitosamente del S3");
                System.out.println("HTTP: Documentos obtenidos exitosamente");

                return convertirRespuestaS3AFormatoLocal(response.body());
            } else {
                String error = "Error HTTP " + response.statusCode() + ": " + response.body();
                logger.log(Level.SEVERE, error);
                System.err.println("HTTP ERROR: " + error);
                throw new RuntimeException(error);
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error obteniendo documentos", e);
            System.err.println("Error en obtenerDocumentos: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al obtener documentos: " + e.getMessage(), e);
        }
    }

    public String procesarFirmaSignature(Long idDocumento, String documentoFirmadoBase64,
                                         String bucket, String usuarioModificacion,
                                         String nombreDocumento, Integer tamanoDocumento,
                                         String codigoGenerado) {
        try {
            System.out.println("Procesando firma con signature");
            System.out.println("   - ID Documento: " + idDocumento);
            System.out.println("   - Bucket: " + bucket);
            System.out.println("   - Usuario: " + usuarioModificacion);
            System.out.println("   - Nombre: " + nombreDocumento);
            System.out.println("   - Código: " + codigoGenerado);

            String url = baseUrl + "/process";

            ObjectMapper bodyMapper = new ObjectMapper();
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("idDocumento", idDocumento);
            body.put("documentoFirmadoBase64", documentoFirmadoBase64);
            body.put("bucket", bucket);
            body.put("usuarioModificacion", usuarioModificacion);
            body.put("nombreDocumento", nombreDocumento);
            body.put("tamanoDocumento", tamanoDocumento);
            body.put("codigoGenerado", codigoGenerado);
            String jsonBody = bodyMapper.writeValueAsString(body);

            System.out.println("Enviando a: " + url);
            System.out.println("Payload preparado (tamaño: " + jsonBody.length() + " chars)");

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("codigoCanal", "APP-FIRMA")
                    .header("codigoFuncionalidad", "FIRMA-WEB");

            if (authToken != null && !authToken.isEmpty()) {
                requestBuilder.header("Authorization", authToken);
                System.out.println("Token de autorización agregado");
                System.out.println("Token agregado SIN prefijo Bearer");
                System.out.println("Token enviado: " + authToken.substring(0, Math.min(50, authToken.length())) + "...");
            }

            HttpRequest request = requestBuilder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();

            System.out.println("Enviando petición al servicio signature...");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Respuesta recibida - Status: " + response.statusCode());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("Documento procesado exitosamente en signature");

                String responseBody = response.body();
                if (responseBody != null && responseBody.length() > 200) {
                    System.out.println("Respuesta (preview): " + responseBody.substring(0, 200) + "...");
                } else {
                    System.out.println("Respuesta completa: " + responseBody);
                }

                return responseBody;
            } else {
                String errorMsg = "Error HTTP " + response.statusCode() + ": " + response.body();
                System.err.println("Error: " + errorMsg);
                throw new RuntimeException(errorMsg);
            }

        } catch (Exception e) {
            System.err.println("Error enviando documento a signature: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al procesar firma con signature: " + e.getMessage(), e);
        }
    }

    private String convertirRespuestaS3AFormatoLocal(String respuestaS3) {
        try {
            System.out.println("Convirtiendo respuesta S3 a formato local...");

            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(respuestaS3);

            if (!rootNode.path("meta").path("resultado").asBoolean()) {
                throw new RuntimeException("Error en API: " + rootNode.path("meta").path("mensaje").path("mensaje").asText());
            }

            JsonNode datosNode = rootNode.path("datos");

            if (!datosNode.isArray() || datosNode.size() == 0) {
                throw new RuntimeException("No se encontraron documentos en la respuesta");
            }

            StringBuilder jsonResult = new StringBuilder();
            jsonResult.append("{\"documentos\":[");

            for (int i = 0; i < datosNode.size(); i++) {
                if (i > 0) jsonResult.append(",");

                JsonNode docNode = datosNode.get(i);

                String documentoBase64 = docNode.path("documento").asText();

                jsonResult.append("{")
                        .append("\"id\":\"").append(docNode.path("key").asText()).append("\",")
                        .append("\"nombre\":\"").append(docNode.path("key").asText()).append("\",")
                        .append("\"contenido\":\"").append(documentoBase64).append("\",")
                        .append("\"tipo\":\"PDF\",")
                        .append("\"posicionFirma\":{")
                        .append("\"pagina\":1,")
                        .append("\"x\":100,")
                        .append("\"y\":100")
                        .append("}")
                        .append("}");
            }

            jsonResult.append("]}");

            System.out.println("Respuesta S3 convertida exitosamente");
            logger.log(Level.INFO, "Respuesta S3 convertida exitosamente");

            return jsonResult.toString();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error convirtiendo respuesta S3", e);
            System.err.println("Error convirtiendo respuesta S3: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error procesando respuesta S3: " + e.getMessage(), e);
        }
    }

}
