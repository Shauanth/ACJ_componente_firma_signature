package com.acj.firma.acjfirmalocal.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

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
