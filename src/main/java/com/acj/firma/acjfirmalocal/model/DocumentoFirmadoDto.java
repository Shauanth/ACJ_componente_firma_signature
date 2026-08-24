package com.acj.firma.acjfirmalocal.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentoFirmadoDto {
    private Long idDocumento;
    private String documentoFirmado;
    private List<SignParameter> parametrosFirma;
}
