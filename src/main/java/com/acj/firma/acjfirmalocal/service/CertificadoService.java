package com.acj.firma.acjfirmalocal.service;

import com.acj.acjfirmalib.controller.FirmaController;
import com.acj.acjfirmalib.util.UtilSign;
import com.acj.acjfirmalib.util.UtilWin;

import java.lang.reflect.Method;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;

public class CertificadoService {

    private UtilWin utilWin;
    private FirmaController firmaController;

    private Map<String, String> mapOrganizaciones = new java.util.HashMap<>();
    private final java.text.SimpleDateFormat formatoFecha = new java.text.SimpleDateFormat("dd/MM/yyyy");

    /**
     * Advertencias legibles para el usuario final generadas en la última
     * llamada a {@link #obtenerCertificados()} (p.ej. certificados
     * duplicados/ambiguos que se ocultaron). El log de consola no llega al
     * usuario final en el instalador empaquetado, así que el llamador debe
     * mostrarlas en la propia ventana de la app.
     */
    private final List<String> advertencias = new ArrayList<>();

    public List<String> obtenerAdvertencias() {
        return Collections.unmodifiableList(advertencias);
    }

    public CertificadoService() {
        inicializarServicio();
    }

    private void inicializarServicio() {
        try {
            System.out.println("[CertificadoService] - Inicializando servicio de certificados ACJ...");
            utilWin = new UtilWin();
            firmaController = new FirmaController();
            System.out.println("[CertificadoService] - Servicio de certificados inicializado correctamente");
        } catch (Exception e) {
            System.err.println("[CertificadoService] - Error al inicializar el servicio de certificados:");
            e.printStackTrace();
            utilWin = null;
            firmaController = null;
        }
    }

    /**
     * Simple DTO propio con la vigencia del certificado, ya que el DTO
     * {@code Certificado} de la libreria de firma solo expone CN/O.
     */
    private static class CertificadoInfo {
        String cn;
        String o;
        String alias;
        boolean vigente;
        Date notAfter;
    }

    public List<String> obtenerCertificados() {
        if (!isServicioDisponible()) {
            System.err.println("[CertificadoService] - Servicio no disponible");
            return Collections.emptyList();
        }

        try {
            System.out.println("[CertificadoService] - Obteniendo lista de certificados...");
            List<CertificadoInfo> certificados = leerCertificados();
            advertencias.clear();

            // La libreria de firma resuelve el certificado a usar buscando por
            // CN+O y devuelve el PRIMERO que coincida en el almacen, sin
            // importar su vigencia (ver UtilWin.getPrivateK). Si dos
            // certificados comparten CN+O (p.ej. una renovacion del mismo
            // titular) no hay forma de garantizar cual de los dos se usara al
            // firmar. Ante esa ambiguedad se ocultan ambos en vez de arriesgar
            // una firma hecha con el certificado equivocado; el usuario debe
            // eliminar el vencido el mismo desde certmgr.msc.
            Map<String, List<CertificadoInfo>> agrupados = new LinkedHashMap<>();
            for (CertificadoInfo info : certificados) {
                String clave = (info.cn + "|" + (info.o == null ? "" : info.o)).toUpperCase();
                agrupados.computeIfAbsent(clave, k -> new ArrayList<>()).add(info);
            }

            List<String> certificadosFormateados = new ArrayList<>();
            mapOrganizaciones.clear();

            for (List<CertificadoInfo> grupo : agrupados.values()) {
                CertificadoInfo primero = grupo.get(0);

                if (grupo.size() > 1) {
                    String detalleVigencias = grupo.stream()
                            .map(info -> info.vigente
                                    ? "vigente"
                                    : "VENCIDO" + (info.notAfter != null ? " el " + formatoFecha.format(info.notAfter) : ""))
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("");

                    String advertencia = "El certificado \"" + primero.cn + "\" no se puede usar: hay " + grupo.size()
                            + " certificados con esa misma identidad en tu almacén de Windows (" + detalleVigencias
                            + "). Abre el Administrador de certificados de Windows (certmgr.msc → Personal → "
                            + "Certificados), busca ese nombre y elimina el que dice VENCIDO para poder firmar.";

                    System.err.println("[CertificadoService] - " + advertencia);
                    advertencias.add(advertencia);
                    continue;
                }

                if (!primero.vigente) {
                    System.out.println("[CertificadoService] - Certificado vencido omitido: " + primero.cn
                            + (primero.notAfter != null ? " (vencio " + primero.notAfter + ")" : ""));
                    continue;
                }

                String orgLimpia = (primero.o != null && !primero.o.trim().isEmpty()) ? primero.o.trim() : "";
                mapOrganizaciones.put(primero.cn, orgLimpia);

                certificadosFormateados.add(orgLimpia.isEmpty()
                        ? primero.cn + " (Sin organización)"
                        : primero.cn + " - " + orgLimpia);
            }

            System.out.println("[CertificadoService] - Certificados encontrados: " + certificadosFormateados.size());
            return certificadosFormateados;

        } catch (Exception e) {
            System.err.println("[CertificadoService] - Error al obtener certificados:");
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private List<CertificadoInfo> leerCertificados() throws Exception {
        if (!"Windows".equals(utilWin.verifySO())) {
            // Fuera de Windows se usa el listado original: MSCAPI (y su
            // colision de alias por CN duplicado) es un problema especifico
            // del almacen de certificados de Windows.
            return leerCertificadosLibreria();
        }
        return leerCertificadosAlmacenWindows();
    }

    @SuppressWarnings("unchecked")
    private List<CertificadoInfo> leerCertificadosLibreria() throws Exception {
        List<CertificadoInfo> resultado = new ArrayList<>();
        Object listado = utilWin.getListCertificados();
        if (!(listado instanceof List<?>)) {
            return resultado;
        }
        for (Object cert : (List<?>) listado) {
            if (cert == null) continue;
            CertificadoInfo info = new CertificadoInfo();
            info.cn = invocarMetodoString(cert.getClass(), cert, "getCN");
            info.o = invocarMetodoString(cert.getClass(), cert, "getO");
            info.vigente = true;
            if (info.cn != null) {
                resultado.add(info);
            }
        }
        return resultado;
    }

    private List<CertificadoInfo> leerCertificadosAlmacenWindows() throws Exception {
        List<CertificadoInfo> resultado = new ArrayList<>();

        KeyStore ks = KeyStore.getInstance("Windows-MY");
        ks.load(null, null);
        Date ahora = new Date();

        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (!ks.isKeyEntry(alias)) continue;

            Certificate cert = ks.getCertificate(alias);
            if (!(cert instanceof X509Certificate)) continue;

            X509Certificate x509 = (X509Certificate) cert;
            if (!UtilSign.nonRepudiation(x509)) continue;

            String cn = UtilSign.getCertificadoInfo(x509, "CN=");
            if (cn == null || cn.isEmpty()) continue;

            String o = UtilSign.getCertificadoInfo(x509, "O=");

            CertificadoInfo info = new CertificadoInfo();
            info.cn = cn;
            info.o = (o == null || o.isEmpty()) ? null : o;
            info.alias = alias;
            info.notAfter = x509.getNotAfter();
            info.vigente = !ahora.before(x509.getNotBefore()) && !ahora.after(x509.getNotAfter());

            resultado.add(info);
        }

        return resultado;
    }

    private String invocarMetodoString(Class<?> clazz, Object obj, String methodName) {
        try {
            Method method = clazz.getMethod(methodName);
            Object result = method.invoke(obj);
            return (result instanceof String) ? (String) result : null;
        } catch (Exception e) {
            return null;
        }
    }

    public String obtenerOrganizacion(String certificadoSeleccionado) {
        String cn = extraerCNDeCertificadoFormateado(certificadoSeleccionado);
        String org = mapOrganizaciones.getOrDefault(cn, "");
        System.out.println("[CertificadoService] - Organización para CN '" + cn + "': '" + org + "'");
        return org;
    }

    public String extraerCNDeCertificadoFormateado(String certificadoFormateado) {
        if (certificadoFormateado == null || certificadoFormateado.trim().isEmpty()) {
            return "";
        }

        String input = certificadoFormateado.trim();

        if (input.contains(" - ")) {
            return input.substring(0, input.indexOf(" - ")).trim();
        }
        if (input.contains(" (Sin organización)")) {
            return input.replace(" (Sin organización)", "").trim();
        }
        if (input.contains(" (")) {
            return input.substring(0, input.indexOf(" (")).trim();
        }

        return input;
    }

    public boolean isServicioDisponible() {
        boolean disponible = utilWin != null && firmaController != null;
        System.out.println("[CertificadoService] - Servicio disponible: " + disponible);
        return disponible;
    }
}
