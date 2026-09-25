package com.correoargentino.sga.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Publica el XML del asiento en el WebService SOAP. El usuario SISALQ
 * es la credencial de conexión; no forma parte del asiento.
 */
@Component
public class SapAsientoClient {

    private final RestClient http;
    private final String url;
    private final String user;
    private final String password;
    private final String soapAction;

    public SapAsientoClient(
            RestClient.Builder restClientBuilder,
            @Value("${sga.sap.url:}") String url,
            @Value("${sga.sap.user:}") String user,
            @Value("${sga.sap.password:}") String password,
            @Value("${sga.sap.soap-action:}") String soapAction) {
        this.http = restClientBuilder.build();
        this.url = url == null ? "" : url.trim();
        this.user = user == null ? "" : user.trim();
        this.password = password == null ? "" : password;
        this.soapAction = soapAction == null ? "" : soapAction.trim();
    }

    public boolean configurado() {
        return !url.isEmpty();
    }

    public String enviar(String asientoXml) {
        if (!configurado()) {
            throw new IllegalStateException("SAP no está configurado: falta SGA_SAP_URL.");
        }
        String envelope = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Header/>
                  <soapenv:Body>%s</soapenv:Body>
                </soapenv:Envelope>
                """.formatted(asientoXml);
        try {
            String body = http.post()
                    .uri(url)
                    .headers(headers -> {
                        headers.setContentType(MediaType.TEXT_XML);
                        if (!user.isEmpty()) {
                            headers.setBasicAuth(user, password);
                        }
                        if (!soapAction.isEmpty()) {
                            headers.set("SOAPAction", soapAction);
                        }
                    })
                    .body(envelope)
                    .retrieve()
                    .body(String.class);
            return body == null ? "" : body;
        } catch (RestClientResponseException ex) {
            String respuesta = ex.getResponseBodyAsString();
            String detalle = respuesta == null || respuesta.isBlank()
                    ? ex.getStatusText()
                    : respuesta;
            throw new IllegalStateException(
                    "SAP respondió " + ex.getStatusCode().value() + ": " + detalle, ex);
        }
    }
}
