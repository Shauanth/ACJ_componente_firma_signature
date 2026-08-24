package com.acj.firma.acjfirmalocal.test;

import com.acj.firma.acjfirmalocal.model.RequestFirma;
import com.acj.firma.acjfirmalocal.model.ResponseFirma;
import com.acj.firma.acjfirmalocal.service.FirmaLocalService;
import com.acj.firma.acjfirmalocal.service.CertificadoService;

public class TestFirmaMain {

    public static void main(String[] args) {
        System.out.println("=== TEST FIRMA DIGITAL ===");

        try {
            System.out.println("1. Inicializando servicios...");
            CertificadoService certificadoService = new CertificadoService();
            FirmaLocalService firmaService = new FirmaLocalService();

            System.out.println("2. Obteniendo certificados...");
            var certificados = certificadoService.obtenerCertificados();

            if (certificados.isEmpty()) {
                System.out.println("ERROR: No se encontraron certificados");
                return;
            }

            System.out.println("Certificados encontrados:");
            for (int i = 0; i < certificados.size(); i++) {
                System.out.println("  [" + i + "] " + certificados.get(i));
            }

            String certificadoSeleccionado = certificados.get(0);
            String organizacion = certificadoService.obtenerOrganizacion(certificadoSeleccionado);

            System.out.println("3. Certificado seleccionado:");
            System.out.println("  CN: " + certificadoSeleccionado);
            System.out.println("  ORG: " + organizacion);

            String documentoBase64 = PDF_BASE64;
            System.out.println("4. Documento cargado - Tamaño: " + documentoBase64.length());

            System.out.println("5. Creando request de firma...");
            RequestFirma request = new RequestFirma(documentoBase64, certificadoSeleccionado);
            request.setEmpresa(organizacion);

            if (request.isVisibleFirma() && (request.getImage() == null || request.getImage().isEmpty())) {
                System.out.println("   Agregando imagen por defecto...");
                request.setImage(getImagenBasica());
            }

            System.out.println("   Request configurado:");
            System.out.println("     - CN: " + request.getAliasCertificado());
            System.out.println("     - ORG: " + request.getEmpresa());
            System.out.println("     - Firma visible: " + request.isVisibleFirma());
            System.out.println("     - Imagen presente: " + (request.getImage() != null && !request.getImage().isEmpty()));

            System.out.println("6. EJECUTANDO FIRMA...");
            Object resultado = firmaService.firmarDocumento(request);

            if (resultado instanceof ResponseFirma) {
                ResponseFirma response = (ResponseFirma) resultado;

                if (response.getErrorFirma() != null && !response.getErrorFirma().isEmpty()) {
                    System.out.println("ERROR EN FIRMA: " + response.getErrorFirma());
                } else {
                    System.out.println("FIRMA EXITOSA!");
                    System.out.println("   Título: " + response.getTituloDocumento());
                    System.out.println("   Fecha: " + response.getFechaFirma());
                    System.out.println("   Documento firmado tamaño: " + response.getDocumentoFirmado().length());

                    guardarDocumentoFirmado(response.getDocumentoFirmado());
                }
            } else {
                System.out.println("ERROR: " + resultado.toString());
            }

        } catch (Exception e) {
            System.out.println("EXCEPCION: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("FIN TEST");
    }

    private static void guardarDocumentoFirmado(String documentoBase64) {
        try {
            java.io.File archivoFirmado = new java.io.File("documento_firmado_test.pdf");
            byte[] bytes = java.util.Base64.getDecoder().decode(documentoBase64);
            java.nio.file.Files.write(archivoFirmado.toPath(), bytes);
            System.out.println("   Archivo guardado: " + archivoFirmado.getAbsolutePath());
        } catch (Exception e) {
            System.out.println("   Error guardando archivo: " + e.getMessage());
        }
    }

    private static String getImagenBasica() {
        return "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";
    }

    private static final String PDF_BASE64 = "JVBERi0xLjQKMSAwIG9iago8PC9UeXBlIC9DYXRhbG9nCi9QYWdlcyAyIDAgUgo+PgplbmRvYmoKMiAwIG9iago8PC9UeXBlIC9QYWdlcwovS2lkcyBbMyAwIFJdCi9Db3VudCAxCj4+CmVuZG9iagozIDAgb2JqCjw8L1R5cGUgL1BhZ2UKL1BhcmVudCAyIDAgUgovTWVkaWFCb3ggWzAgMCA1OTUgODQyXQovQ29udGVudHMgNSAwIFIKL1Jlc291cmNlcyA8PC9Qcm9jU2V0IFsvUERGIC9UZXh0XQovRm9udCA8PC9GMSA0IDAgUj4+Cj4+Cj4+CmVuZG9iago0IDAgb2JqCjw8L1R5cGUgL0ZvbnQKL1N1YnR5cGUgL1R5cGUxCi9OYW1lIC9GMQovQmFzZUZvbnQgL0hlbHZldGljYQovRW5jb2RpbmcgL01hY1JvbWFuRW5jb2RpbmcKPj4KZW5kb2JqCjUgMCBvYmoKPDwvTGVuZ3RoIDUzCj4+CnN0cmVhbQpCVAovRjEgMjAgVGYKMjIwIDQwMCBUZAooRHVtbXkgUERGKSBUagpFVAplbmRzdHJlYW0KZW5kb2JqCnhyZWYKMCA2CjAwMDAwMDAwMDAgNjU1MzUgZgowMDAwMDAwMDA5IDAwMDAwIG4KMDAwMDAwMDA2MyAwMDAwMCBuCjAwMDAwMDAxMjQgMDAwMDAgbgowMDAwMDAwMjc3IDAwMDAwIG4KMDAwMDAwMDM5MiAwMDAwMCBuCnRyYWlsZXIKPDwvU2l6ZSA2Ci9Sb290IDEgMCBSCj4+CnN0YXJ0eHJlZgo0OTUKJSVFT0YK";
}