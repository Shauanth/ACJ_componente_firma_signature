package com.acj.firma.acjfirmalocal.model;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(exclude = "imagen")
public class ConfiguracionFirma {
    private Integer pagina = 1;
    private Integer posicionX = 100;
    private Integer posicionY = 100;
    private String empresa = "";
    private String imagen = "";
    private String motivo = "Firma digital local ACJ";
    private String ubicacion = "Lima, Perú";
}