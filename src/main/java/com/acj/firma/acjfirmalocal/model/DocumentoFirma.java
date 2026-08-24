package com.acj.firma.acjfirmalocal.model;

import lombok.Data;

@Data
public class DocumentoFirma {
    private Long idDocumento;
    private String id;
    private String nombre;
    private String keyDocumento;
    private String contenidoBase64;
    private String tipo;
    private PosicionFirma posicionFirma;
}