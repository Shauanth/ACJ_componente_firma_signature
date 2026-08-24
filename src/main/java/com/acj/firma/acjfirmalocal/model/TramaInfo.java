package com.acj.firma.acjfirmalocal.model;

import lombok.Data;
import lombok.ToString;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@ToString(exclude = {"documentos", "firmantes"})
public class TramaInfo {
    private Long idSeguimientoFirma;
    private Long idFlujo;
    private Long idUsuario;
    private Long idUsuarioExterno;
    private Integer idTipoFirma;
    private Date fechaExpiracion;
    private String emailFirmante;
    private Integer ordenFirma;
    private Integer firmantesRequeridosMismoOrden;
    private List<Map<String, Object>> documentos;
    private List<FirmanteInfo> firmantes;
}