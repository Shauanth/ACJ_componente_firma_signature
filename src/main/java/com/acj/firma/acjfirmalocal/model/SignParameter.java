package com.acj.firma.acjfirmalocal.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignParameter {
    private Float positionX;
    private Float positionY;
    private String value;
}