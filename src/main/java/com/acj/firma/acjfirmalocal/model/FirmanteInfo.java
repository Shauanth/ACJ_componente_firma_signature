package com.acj.firma.acjfirmalocal.model;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class FirmanteInfo {
    private Long idUsuario;
    private Long idUsuarioExterno;
    private String email;
    private Integer ordenFirma;
    private Integer idTipoFirma;
    private Date fechaExpiracion;
    private List<DocumentoTramaFirma> documentos;
}
