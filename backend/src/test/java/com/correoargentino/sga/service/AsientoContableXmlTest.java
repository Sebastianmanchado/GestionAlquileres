package com.correoargentino.sga.service;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsientoContableXmlTest {

    @Test
    void bovioArmaDosPosiciones() throws Exception {
        LocalDate envio = LocalDate.of(2026, 8, 6);
        AsientoContableXml.Datos datos = new AsientoContableXml.Datos(
                12,
                LocalDate.of(2026, 8, 5),
                "FA",
                envio,
                "ARS",
                AsientoContableXml.referencia(2, "A", "00000086"),
                AsientoContableXml.texto(LocalDate.of(2026, 8, 1), "CDD"),
                "A00822",
                "510802",
                "4952",
                "53025952",
                "C1",
                new BigDecimal("2960684.87"),
                "86316964843664",
                LocalDate.of(2026, 8, 15)
        );

        Document doc = parse(AsientoContableXml.build(datos));
        assertEquals("urn:sat/asientos_contables", doc.getDocumentElement().getNamespaceURI());
        assertEquals("MT_AsientosContables_Proxy_R3_Req", doc.getDocumentElement().getLocalName());

        NodeList lineas = doc.getElementsByTagName("AsientoContable");
        assertEquals(2, lineas.getLength());

        Element proveedor = (Element) lineas.item(0);
        Element gasto = (Element) lineas.item(1);

        assertLineaComun(proveedor);
        assertLineaComun(gasto);

        assertEquals("31", text(proveedor, "CLAVE_CONT"));
        assertEquals("A00822", text(proveedor, "CUENTA"));
        assertEquals("2000", text(proveedor, "DIVISION"));
        assertEquals("", text(proveedor, "CECO"));
        assertEquals("", text(proveedor, "CAL_IMP"));
        assertEquals("", text(proveedor, "IND_IMP"));
        assertEquals("A00822", text(proveedor, "ASIGNACION"));
        assertEquals("06.08.2026", text(proveedor, "FECHA"));
        assertEquals("86316964843664", text(proveedor, "CAE"));
        assertEquals("15.08.2026", text(proveedor, "FECHA_VTO"));

        assertEquals("40", text(gasto, "CLAVE_CONT"));
        assertEquals("510802", text(gasto, "CUENTA"));
        assertEquals("4952", text(gasto, "DIVISION"));
        assertEquals("53025952", text(gasto, "CECO"));
        assertEquals("X", text(gasto, "CAL_IMP"));
        assertEquals("C1", text(gasto, "IND_IMP"));
        assertEquals("", text(gasto, "ASIGNACION"));
        assertEquals("", text(gasto, "FECHA"));
        assertEquals("", text(gasto, "CAE"));
        assertEquals("", text(gasto, "FECHA_VTO"));
    }

    private static void assertLineaComun(Element linea) {
        assertEquals("12", text(linea, "ASIENTO"));
        assertEquals("05.08.2026", text(linea, "FECHA_DOC"));
        assertEquals("FA", text(linea, "CLASE_DOC"));
        assertEquals("COAR", text(linea, "SOCIEDAD"));
        assertEquals("06.08.2026", text(linea, "FECHA_CONT"));
        assertEquals("ARS", text(linea, "MONEDA"));
        assertEquals("00002A00000086", text(linea, "REFERENCIA"));
        assertEquals("ALQ 8/2026 CDD", text(linea, "TEXTO_CAB"));
        assertEquals("ALQ 8/2026 CDD", text(linea, "TEXTO_POSICION"));
        assertEquals("2960684.87", text(linea, "IMPORTE"));
        assertEquals("ALQU", text(linea, "REF1"));
        assertEquals("12", text(linea, "REF2"));
        assertEquals("", text(linea, "TIENDA"));
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String text(Element parent, String tag) {
        return parent.getElementsByTagName(tag).item(0).getTextContent();
    }
}
