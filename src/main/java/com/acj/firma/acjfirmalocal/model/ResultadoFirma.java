package com.acj.firma.acjfirmalocal.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoFirma {
    private boolean exito;
    private String error;
    private String idDocumento;
    private long timestamp;
}