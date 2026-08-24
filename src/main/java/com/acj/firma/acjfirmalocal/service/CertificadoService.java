package com.acj.firma.acjfirmalocal.service;

import com.acj.acjfirmalib.controller.FirmaController;
import com.acj.acjfirmalib.util.UtilWin;

import java.lang.reflect.Method;
import java.util.*;

public class CertificadoService {

    private UtilWin utilWin;
    private FirmaController firmaController;

    private Map<String, String> mapOrganizaciones = new java.util.HashMap<>();


    public CertificadoService() {
        inicializarServicio();
    }

    private void inicializarServicio() {
        try {
            System.out.println("[CertificadoService] - Inicializando servicio de certificados ACJ...");
            utilWin = new UtilWin();
            firmaController = new FirmaController();

            Object resultado = utilWin.getListCertificados();
            System.out.println("[CertificadoService] - Certificados obtenidos: " + (resultado != null ? "OK" : "null"));

            System.out.println("[CertificadoService] - Servicio de certificados inicializado correctamente");
        } catch (Exception e) {
            System.err.println("[CertificadoService] - Error al inicializar el servicio de certificados:");
            e.printStackTrace();
            utilWin = null;
            firmaController = null;
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> obtenerCertificados() {
        if (!isServicioDisponible()) {
            System.err.println("[CertificadoService] - Servicio no disponible");
            return Collections.emptyList();
        }

        try {
            System.out.println("[CertificadoService] - Obteniendo lista de certificados...");
            Object resultado = utilWin.getListCertificados();

            if (!(resultado instanceof List<?>)) {
                System.err.println("[CertificadoService] - Resultado inesperado de getListCertificados: " + resultado);
                return Collections.emptyList();
            }

            List<?> listaCertificados = (List<?>) resultado;
            List<String> certificadosFormateados = new ArrayList<>();

            for (Object cert : listaCertificados) {
                if (cert != null) {
                    String certStr = convertirCertificadoAString(cert);
                    certificadosFormateados.add(certStr);
                }
            }

            System.out.println("[CertificadoService] - Certificados encontrados: " + certificadosFormateados.size());
            return certificadosFormateados;

        } catch (Exception e) {
            System.err.println("[CertificadoService] - Error al obtener certificados:");
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private String convertirCertificadoAString(Object certificado) {
        try {
            Class<?> clazz = certificado.getClass();
            String cn = invocarMetodoString(clazz, certificado, "getCN");
            String o = invocarMetodoString(clazz, certificado, "getO");

            String orgLimpia = (o != null && !o.trim().isEmpty() && !"null".equalsIgnoreCase(o.trim()))
                    ? o.trim() : "";

            mapOrganizaciones.put(cn != null ? cn : "", orgLimpia);

            if (cn != null) {
                return orgLimpia.isEmpty()
                        ? cn + " (Sin organización)"
                        : cn + " - " + orgLimpia;
            }

            return certificado.toString();

        } catch (Exception e) {
            System.err.println("[CertificadoService] - Error al convertir certificado: " + e.getMessage());
            return certificado.toString();
        }
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
