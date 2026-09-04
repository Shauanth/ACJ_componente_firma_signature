package com.acj.firma.acjfirmalocal.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Reemplaza el panel "Registro de Actividad" que antes mostraba System.out /
 * System.err directamente en la ventana del aplicativo: ese mismo flujo (más
 * los java.util.logging.Logger de FirmaLocalService/HttpService, que por
 * defecto también escriben a System.err) se redirige acá a un archivo de
 * texto en disco, uno por día, dentro de una carpeta "logs" junto a la
 * instalación del agente. Si esa carpeta no es escribible (instalación
 * antigua sin los permisos del instalador actualizado, o ejecución directa
 * desde el IDE) se usa %LOCALAPPDATA%\ACJ-Signature-Agente\logs como
 * respaldo.
 */
public final class LogService {

    private static final String CARPETA_LOGS = "logs";
    private static final DateTimeFormatter FORMATO_ARCHIVO = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FORMATO_LINEA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static boolean iniciado = false;
    private static File carpetaLogs;
    private static File archivoActual;
    private static PrintStream streamActual;
    private static LocalDate diaDelArchivoActual;

    private LogService() {
    }

    /**
     * Idempotente: puede llamarse tanto desde {@code Launcher.main} (cubre
     * también el camino de reenvío a una instancia ya corriendo, que nunca
     * llega a FirmaApplication) como desde {@code FirmaApplication.main}
     * (arranque directo vía javafx-maven-plugin en desarrollo); la primera
     * llamada gana.
     */
    public static synchronized void iniciar() {
        if (iniciado) {
            return;
        }
        iniciado = true;

        carpetaLogs = resolverCarpetaLogs();

        try {
            abrirArchivoDelDia();

            OutputStream redireccion = new OutputStream() {
                private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                @Override
                public synchronized void write(int b) {
                    if (b == '\n') {
                        String linea = buffer.toString(StandardCharsets.UTF_8);
                        buffer.reset();
                        escribirLinea(linea);
                    } else {
                        buffer.write(b);
                    }
                }
            };

            PrintStream redirigido = new PrintStream(redireccion, true, StandardCharsets.UTF_8);
            System.setOut(redirigido);
            System.setErr(redirigido);

            System.out.println("Logging inicializado en: " + archivoActual.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("No se pudo inicializar el archivo de logs, se mantiene la salida estándar: " + e.getMessage());
        }
    }

    private static synchronized void escribirLinea(String linea) {
        try {
            garantizarArchivoDelDiaVigente();
            if (streamActual != null) {
                streamActual.println("[" + LocalDateTime.now().format(FORMATO_LINEA) + "] " + linea);
            }
        } catch (IOException e) {
            // No hay a dónde reportar esto sin recursión (System.err ya está
            // redirigido acá mismo); se descarta en silencio.
        }
    }

    private static void garantizarArchivoDelDiaVigente() throws IOException {
        if (!LocalDate.now().equals(diaDelArchivoActual)) {
            abrirArchivoDelDia();
        }
    }

    private static void abrirArchivoDelDia() throws IOException {
        if (streamActual != null) {
            streamActual.flush();
            streamActual.close();
        }
        carpetaLogs.mkdirs();
        diaDelArchivoActual = LocalDate.now();
        archivoActual = new File(carpetaLogs, "acj-firma-" + diaDelArchivoActual.format(FORMATO_ARCHIVO) + ".log");
        streamActual = new PrintStream(new FileOutputStream(archivoActual, true), true, StandardCharsets.UTF_8);
    }

    private static File resolverCarpetaLogs() {
        File instalacion = resolverCarpetaInstalacion();
        if (instalacion != null) {
            File logs = new File(instalacion, CARPETA_LOGS);
            if ((logs.exists() || logs.mkdirs()) && logs.canWrite()) {
                return logs;
            }
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        File base = localAppData != null
                ? new File(localAppData, "ACJ-Signature-Agente")
                : new File(System.getProperty("user.home"), ".acj-signature-agente");
        return new File(base, CARPETA_LOGS);
    }

    /**
     * El jar corre siempre desde {@code <instalación>\app\*.jar} (estructura
     * fija de jpackage --type app-image, ver installer/setup.iss), así que
     * subir dos niveles desde la ubicación del código en ejecución da la
     * carpeta de instalación real sin necesidad de configurarla a mano.
     */
    private static File resolverCarpetaInstalacion() {
        try {
            File ubicacion = new File(LogService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File carpetaApp = ubicacion.isFile() ? ubicacion.getParentFile() : ubicacion;
            if (carpetaApp != null && "app".equalsIgnoreCase(carpetaApp.getName()) && carpetaApp.getParentFile() != null) {
                return carpetaApp.getParentFile();
            }
        } catch (Exception e) {
            // Ejecución desde el IDE (mvn javafx:run): la "ubicación" es un
            // directorio de clases sueltas, no un jar dentro de app\. Se usa
            // el fallback de LOCALAPPDATA en ese caso.
        }
        return null;
    }

    public static synchronized File getCarpetaLogs() {
        return carpetaLogs;
    }

    /**
     * Borra todos los archivos de log acumulados y abre uno nuevo de inmediato
     * para que el aplicativo pueda seguir escribiendo sin necesitar reinicio.
     */
    public static synchronized boolean borrarLogs() {
        boolean huboError = false;
        try {
            if (streamActual != null) {
                streamActual.flush();
                streamActual.close();
                streamActual = null;
            }

            File[] archivos = carpetaLogs != null
                    ? carpetaLogs.listFiles((dir, nombre) -> nombre.toLowerCase().endsWith(".log"))
                    : null;
            if (archivos != null) {
                for (File archivo : archivos) {
                    if (!archivo.delete()) {
                        huboError = true;
                    }
                }
            }

            abrirArchivoDelDia();
        } catch (IOException e) {
            huboError = true;
        }
        return !huboError;
    }
}
