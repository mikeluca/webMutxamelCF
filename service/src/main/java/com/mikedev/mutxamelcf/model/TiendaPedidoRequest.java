package com.mikedev.mutxamelcf.model;

import java.util.List;

public class TiendaPedidoRequest {

    private String nombre;

    private String telefono;

    private String email;

    private List<TiendaPedidoItem> items;

    public TiendaPedidoRequest() {
    }

    public TiendaPedidoRequest(String nombre, String telefono, String email, List<TiendaPedidoItem> items) {
        this.nombre = nombre;
        this.telefono = telefono;
        this.email = email;
        this.items = items;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<TiendaPedidoItem> getItems() {
        return items;
    }

    public void setItems(List<TiendaPedidoItem> items) {
        this.items = items;
    }
}
