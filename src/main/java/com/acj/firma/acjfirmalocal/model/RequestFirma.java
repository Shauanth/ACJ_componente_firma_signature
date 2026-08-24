package com.acj.firma.acjfirmalocal.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RequestFirma {

    private String document;
    private String motivo = "Firma digital local ACJ";
    private String location = "Lima, Perú";
    private String aliasCertificado;
    private String level = "B";
    private String sufijo = "_FIRMADO";
    private String empresa = "";
    private boolean visibleFirma = true;
    private String signType = "LT";
    private String extras = "CE";
    private String image = "";
    private String tituloFirma = "Firma ACJ Digital";
    private String texto1 = "Firmado digitalmente";
    private String texto2 = "por ACJ Signature";
    private String texto3 = "Documento aprobado";
    private Integer pagina = 1;
    private Integer fontSize = 12;
    private Integer textWidth = 50;
    private Integer x = 100;
    private Integer y = 100;
    private boolean verifyTsl = true;
    private String tslURL = "https://iofe.indecopi.gob.pe/TSL/tsl-pe.xml";
    private boolean verifyTsa = false;
    private String tsaURL = "";
    private String usuario = "";
    private String password = "";

    public RequestFirma(String document, String aliasCertificado) {
        this.document = document;
        this.aliasCertificado = aliasCertificado;
    }
}
