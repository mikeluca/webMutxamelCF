package com.mikedev.mutxamelcf.model;

import java.util.List;

public class TiendaPedidoItem {

    private String prenda;

    private Integer cantidad;

    private List<String> tallas;

    public TiendaPedidoItem() {
    }

    public TiendaPedidoItem(String prenda, Integer cantidad, List<String> tallas) {
        this.prenda = prenda;
        this.cantidad = cantidad;
        this.tallas = tallas;
    }

    public String getPrenda() {
        return prenda;
    }

    public void setPrenda(String prenda) {
        this.prenda = prenda;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }

    public List<String> getTallas() {
        return tallas;
    }

    public void setTallas(List<String> tallas) {
        this.tallas = tallas;
    }
}
