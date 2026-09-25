package com.correoargentino.sga.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Solicitud de creación de factura")
public class FacturaRequest {

    private Emisor emisor;
    private Comprobante comprobante;
    private DatosFiscales datosFiscales;
    private DatosPago datosPago;
    private List<Item> items;
    private SucursalInmueble sucursalInmueble;

    public Emisor getEmisor() {
        return emisor;
    }

    public void setEmisor(Emisor emisor) {
        this.emisor = emisor;
    }

    public Comprobante getComprobante() {
        return comprobante;
    }

    public void setComprobante(Comprobante comprobante) {
        this.comprobante = comprobante;
    }

    public DatosFiscales getDatosFiscales() {
        return datosFiscales;
    }

    public void setDatosFiscales(DatosFiscales datosFiscales) {
        this.datosFiscales = datosFiscales;
    }

    public DatosPago getDatosPago() {
        return datosPago;
    }

    public void setDatosPago(DatosPago datosPago) {
        this.datosPago = datosPago;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public SucursalInmueble getSucursalInmueble() {
        return sucursalInmueble;
    }

    public void setSucursalInmueble(SucursalInmueble sucursalInmueble) {
        this.sucursalInmueble = sucursalInmueble;
    }

    public static class Emisor {

        @Schema(example = "Inmobiliaria Andina SRL")
        private String razonSocial;

        @Schema(example = "30687412308")
        private String cuit;

        @Schema(example = "RI")
        private String condicionIva;

        public String getRazonSocial() {
            return razonSocial;
        }

        public void setRazonSocial(String razonSocial) {
            this.razonSocial = razonSocial;
        }

        public String getCuit() {
            return cuit;
        }

        public void setCuit(String cuit) {
            this.cuit = cuit;
        }

        public String getCondicionIva() {
            return condicionIva;
        }

        public void setCondicionIva(String condicionIva) {
            this.condicionIva = condicionIva;
        }
    }

    public static class Comprobante {

        private String tipo;
        private String puntoVenta;
        private String numero;
        private String fechaEmision;
        private String fechaVencimientoPago;
        private PeriodoFacturado periodoFacturado;
        private String moneda;
        private Importes importes;

        public String getTipo() {
            return tipo;
        }

        public void setTipo(String tipo) {
            this.tipo = tipo;
        }

        public String getPuntoVenta() {
            return puntoVenta;
        }

        public void setPuntoVenta(String puntoVenta) {
            this.puntoVenta = puntoVenta;
        }

        public String getNumero() {
            return numero;
        }

        public void setNumero(String numero) {
            this.numero = numero;
        }

        public String getFechaEmision() {
            return fechaEmision;
        }

        public void setFechaEmision(String fechaEmision) {
            this.fechaEmision = fechaEmision;
        }

        public String getFechaVencimientoPago() {
            return fechaVencimientoPago;
        }

        public void setFechaVencimientoPago(String fechaVencimientoPago) {
            this.fechaVencimientoPago = fechaVencimientoPago;
        }

        public PeriodoFacturado getPeriodoFacturado() {
            return periodoFacturado;
        }

        public void setPeriodoFacturado(PeriodoFacturado periodoFacturado) {
            this.periodoFacturado = periodoFacturado;
        }

        public String getMoneda() {
            return moneda;
        }

        public void setMoneda(String moneda) {
            this.moneda = moneda;
        }

        public Importes getImportes() {
            return importes;
        }

        public void setImportes(Importes importes) {
            this.importes = importes;
        }
    }

    public static class PeriodoFacturado {

        private String desde;
        private String hasta;

        public String getDesde() {
            return desde;
        }

        public void setDesde(String desde) {
            this.desde = desde;
        }

        public String getHasta() {
            return hasta;
        }

        public void setHasta(String hasta) {
            this.hasta = hasta;
        }
    }

    public static class Importes {

        private BigDecimal neto;
        private BigDecimal alicuotaIva;
        private BigDecimal iva;
        private BigDecimal otrosTributos;
        private BigDecimal total;

        public BigDecimal getNeto() {
            return neto;
        }

        public void setNeto(BigDecimal neto) {
            this.neto = neto;
        }

        public BigDecimal getAlicuotaIva() {
            return alicuotaIva;
        }

        public void setAlicuotaIva(BigDecimal alicuotaIva) {
            this.alicuotaIva = alicuotaIva;
        }

        public BigDecimal getIva() {
            return iva;
        }

        public void setIva(BigDecimal iva) {
            this.iva = iva;
        }

        public BigDecimal getOtrosTributos() {
            return otrosTributos;
        }

        public void setOtrosTributos(BigDecimal otrosTributos) {
            this.otrosTributos = otrosTributos;
        }

        public BigDecimal getTotal() {
            return total;
        }

        public void setTotal(BigDecimal total) {
            this.total = total;
        }
    }

    public static class DatosFiscales {

        private String cae;
        private String fechaVencimientoCae;

        public String getCae() {
            return cae;
        }

        public void setCae(String cae) {
            this.cae = cae;
        }

        public String getFechaVencimientoCae() {
            return fechaVencimientoCae;
        }

        public void setFechaVencimientoCae(String fechaVencimientoCae) {
            this.fechaVencimientoCae = fechaVencimientoCae;
        }
    }

    public static class DatosPago {

        private String cbu;
        private String aliasCbu;

        public String getCbu() {
            return cbu;
        }

        public void setCbu(String cbu) {
            this.cbu = cbu;
        }

        public String getAliasCbu() {
            return aliasCbu;
        }

        public void setAliasCbu(String aliasCbu) {
            this.aliasCbu = aliasCbu;
        }
    }

    public static class Item {

        private String descripcion;
        private BigDecimal cantidad;
        private String unidadMedida;

        public String getDescripcion() {
            return descripcion;
        }

        public void setDescripcion(String descripcion) {
            this.descripcion = descripcion;
        }

        public BigDecimal getCantidad() {
            return cantidad;
        }

        public void setCantidad(BigDecimal cantidad) {
            this.cantidad = cantidad;
        }

        public String getUnidadMedida() {
            return unidadMedida;
        }

        public void setUnidadMedida(String unidadMedida) {
            this.unidadMedida = unidadMedida;
        }
    }

    public static class SucursalInmueble {

        private String referencia;

        public String getReferencia() {
            return referencia;
        }

        public void setReferencia(String referencia) {
            this.referencia = referencia;
        }
    }
}