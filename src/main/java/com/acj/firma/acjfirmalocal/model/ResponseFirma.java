package com.acj.firma.acjfirmalocal.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ResponseFirma {

    private String tituloDocumento;
    private String documentoFirmado;
    private String fechaFirma;
    private String errorFirma;

    public ResponseFirma(String tituloDocumento, String documentoFirmado, String fechaFirma, String errorFirma) {
        this.tituloDocumento = tituloDocumento;
        this.documentoFirmado = documentoFirmado;
        this.fechaFirma = fechaFirma;
        this.errorFirma = errorFirma;
    }
}
