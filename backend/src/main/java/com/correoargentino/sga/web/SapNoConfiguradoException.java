package com.correoargentino.sga.web;

public class SapNoConfiguradoException extends RuntimeException {

    public SapNoConfiguradoException() {
        super("SAP no está configurado: falta SGA_SAP_URL.");
    }
}
