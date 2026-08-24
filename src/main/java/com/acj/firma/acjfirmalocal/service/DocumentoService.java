package com.acj.firma.acjfirmalocal.service;

import com.acj.firma.acjfirmalocal.model.DocumentoFirma;
import com.acj.firma.acjfirmalocal.model.PosicionFirma;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public class DocumentoService {

    private final ObjectMapper objectMapper;

    public DocumentoService() {
        this.objectMapper = new ObjectMapper();
    }

    public List<DocumentoFirma> procesarTramaDocumentos(String trama) {
        if (trama == null || trama.trim().isEmpty()) {
            System.err.println("[DocumentoService] - Trama nula o vacía");
            return new ArrayList<>();
        }

        try {
            System.out.println("[DocumentoService] - Procesando trama: " + trama.substring(0, Math.min(100, trama.length())));

            JsonNode rootNode = objectMapper.readTree(trama);
            List<DocumentoFirma> documentos = new ArrayList<>();

            if (rootNode.has("documentos") && rootNode.get("documentos").isArray()) {
                for (JsonNode docNode : rootNode.get("documentos")) {
                    documentos.add(crearDocumentoDesdeNodo(docNode));
                }
            }

            System.out.println("[DocumentoService] - Documentos procesados: " + documentos.size());
            return documentos;

        } catch (Exception e) {
            System.err.println("[DocumentoService] - Error al procesar trama de documentos: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al procesar documentos: " + e.getMessage(), e);
        }
    }

    private DocumentoFirma crearDocumentoDesdeNodo(JsonNode docNode) {
        DocumentoFirma doc = new DocumentoFirma();

        doc.setIdDocumento(docNode.path("idDocumento").asLong(0L));
        doc.setId(docNode.path("id").asText());

        String nombre = docNode.path("titulo").asText("");
        if (nombre.isEmpty()) {
            nombre = docNode.path("nombre").asText("Documento");
        }
        doc.setNombre(nombre);

        doc.setKeyDocumento(docNode.path("keyDocumento").asText());

        doc.setContenidoBase64(docNode.path("contenido").asText(""));

        doc.setTipo(docNode.path("tipo").asText("PDF"));

        doc.setPosicionFirma(extraerPosicionFirma(docNode));

        return doc;
    }

    private PosicionFirma extraerPosicionFirma(JsonNode docNode) {
        PosicionFirma pos = new PosicionFirma();

        JsonNode posNode = docNode.path("posicionFirma");

        if (posNode.isMissingNode() || posNode.isNull()) {
            pos.setPagina(docNode.path("pagina").asInt(1));
            pos.setX(docNode.path("posicionX").asInt(100));
            pos.setY(docNode.path("posicionY").asInt(100));
        } else {
            pos.setPagina(posNode.path("pagina").asInt(1));
            pos.setX(posNode.path("x").asInt(100));
            pos.setY(posNode.path("y").asInt(100));
        }

        System.out.println("[DocumentoService] - Posición extraída: página=" + pos.getPagina() +
                ", x=" + pos.getX() + ", y=" + pos.getY());

        return pos;
    }
}
