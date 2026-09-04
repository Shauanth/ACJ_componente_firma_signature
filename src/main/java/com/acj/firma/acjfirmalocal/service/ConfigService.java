package com.acj.firma.acjfirmalocal.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Centraliza las URLs de los backends a los que llama el agente (antes hardcodeadas
 * en HttpService/FirmaController). Se leen de un config.properties editable sin
 * recompilar, en %LOCALAPPDATA%\ACJ-Signature-Agente\config.properties. Si no existe,
 * se crea con los valores por defecto (los mismos que usaba el código antes).
 */
public class ConfigService {

    private static final String KEY_S3_BACKEND_URL = "s3.backend.url";
    private static final String KEY_SIGNATURE_BACKEND_URL = "signature.backend.url";
    private static final String KEY_DOCUMENT_BACKEND_URL = "document.backend.url";

    private static final String DEFAULT_S3_BACKEND_URL = "http://localhost:8093/v1";
    private static final String DEFAULT_SIGNATURE_BACKEND_URL = "http://localhost:8093/v1/postfirma";
    private static final String DEFAULT_DOCUMENT_BACKEND_URL = "http://localhost:8093/ach-signature/v1/document";

    private static final Properties properties = new Properties();
    private static boolean cargado = false;

    private ConfigService() {
    }

    public static synchronized String getS3BackendUrl() {
        cargarSiHaceFalta();
        return properties.getProperty(KEY_S3_BACKEND_URL, DEFAULT_S3_BACKEND_URL);
    }

    public static synchronized String getSignatureBackendUrl() {
        cargarSiHaceFalta();
        return properties.getProperty(KEY_SIGNATURE_BACKEND_URL, DEFAULT_SIGNATURE_BACKEND_URL);
    }

    public static synchronized String getDocumentBackendUrl() {
        cargarSiHaceFalta();
        return properties.getProperty(KEY_DOCUMENT_BACKEND_URL, DEFAULT_DOCUMENT_BACKEND_URL);
    }

    /**
     * SEGURIDAD: el servidor HTTP local (ver LocalHttpServer) recibe
     * baseUrlBackend/baseUrlDocument sin autenticación desde el navegador (o
     * cualquier proceso local) vía el protocolo acjfirma://. Sin esta
     * validación, una página maliciosa podría hacer que el agente firme un
     * documento y mande el resultado (junto con el token de auth) a una URL
     * arbitraria. Los únicos orígenes confiables son los ya configurados por
     * el instalador/administrador en config.properties para esta máquina -
     * cualquier otro origen se descarta y el llamador cae de nuevo al backend
     * configurado localmente (ver FirmaController.enviarABackendSignature y
     * procesarConServicioSignature).
     */
    public static synchronized boolean esOrigenConfiable(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        String origenRecibido = extraerOrigen(url);
        if (origenRecibido == null) {
            return false;
        }

        cargarSiHaceFalta();
        Set<String> origenesConfiables = new HashSet<>();
        for (String backendUrl : Arrays.asList(getS3BackendUrl(), getSignatureBackendUrl(), getDocumentBackendUrl())) {
            String origen = extraerOrigen(backendUrl);
            if (origen != null) {
                origenesConfiables.add(origen);
            }
        }

        return origenesConfiables.contains(origenRecibido);
    }

    private static String extraerOrigen(String url) {
        try {
            URI uri = new URI(url);
            String esquema = uri.getScheme();
            String host = uri.getHost();
            if (esquema == null || host == null) {
                return null;
            }
            int puerto = uri.getPort();
            return esquema.toLowerCase() + "://" + host.toLowerCase() + ":" + (puerto != -1 ? puerto : defaultPort(esquema));
        } catch (Exception e) {
            return null;
        }
    }

    private static int defaultPort(String esquema) {
        return "https".equalsIgnoreCase(esquema) ? 443 : 80;
    }

    private static File getConfigFile() {
        String localAppData = System.getenv("LOCALAPPDATA");
        File dir = localAppData != null
                ? new File(localAppData, "ACJ-Signature-Agente")
                : new File(System.getProperty("user.home"), ".acj-signature-agente");
        return new File(dir, "config.properties");
    }

    private static void cargarSiHaceFalta() {
        if (cargado) {
            return;
        }
        cargado = true;

        File configFile = getConfigFile();

        if (configFile.exists()) {
            try (FileInputStream in = new FileInputStream(configFile)) {
                properties.load(in);
                System.out.println("Config cargada desde: " + configFile.getAbsolutePath());
                return;
            } catch (IOException e) {
                System.err.println("Error leyendo " + configFile.getAbsolutePath() + ", se usan valores por defecto: " + e.getMessage());
            }
        }

        properties.setProperty(KEY_S3_BACKEND_URL, DEFAULT_S3_BACKEND_URL);
        properties.setProperty(KEY_SIGNATURE_BACKEND_URL, DEFAULT_SIGNATURE_BACKEND_URL);
        properties.setProperty(KEY_DOCUMENT_BACKEND_URL, DEFAULT_DOCUMENT_BACKEND_URL);

        crearArchivoConDefaults(configFile);
    }

    private static void crearArchivoConDefaults(File configFile) {
        try {
            File dir = configFile.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            try (FileOutputStream out = new FileOutputStream(configFile)) {
                properties.store(out, "Configuracion de backends de ACJ Signature Agente. Editar y reiniciar el agente para aplicar cambios.");
            }
            System.out.println("Config por defecto creada en: " + configFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("No se pudo crear " + configFile.getAbsolutePath() + ", se usan valores por defecto en memoria: " + e.getMessage());
        }
    }
}
