package com.mikedev.mutxamelcf.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class FamiliarDTO {

    private static final String PATRON_NOMBRE = "^[\\p{L} .'-]+$";

    private Long id;

    @Pattern(regexp = PATRON_NOMBRE, message = "El nombre solo puede contener letras y espacios")
    @Size(max = 100, message = "El nombre es demasiado largo")
    private String nombre;

    @Pattern(regexp = PATRON_NOMBRE, message = "Los apellidos solo pueden contener letras y espacios")
    @Size(max = 100, message = "Los apellidos son demasiado largos")
    private String apellidos;

    @Pattern(regexp = "^[0-9 +()-]*$", message = "El teléfono contiene caracteres no válidos")
    @Size(max = 20, message = "El teléfono es demasiado largo")
    private String telefono;

    @Email(message = "El email no es válido")
    @Size(max = 150, message = "El email es demasiado largo")
    private String email;

    private Integer recibeInfoClub;
    private Integer whatsappActivo;

    public FamiliarDTO() {
        super();
    }

    public FamiliarDTO(Long id, String nombre, String apellidos, String telefono,
                       String email, Integer recibeInfoClub, Integer whatsappActivo) {
        super();
        this.id = id;
        this.nombre = nombre;
        this.apellidos = apellidos;
        this.telefono = telefono;
        this.email = email;
        this.recibeInfoClub = recibeInfoClub;
        this.whatsappActivo = whatsappActivo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
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

    public Integer getRecibeInfoClub() {
        return recibeInfoClub;
    }

    public void setRecibeInfoClub(Integer recibeInfoClub) {
        this.recibeInfoClub = recibeInfoClub;
    }

    public Integer getWhatsappActivo() {
        return whatsappActivo;
    }

    public void setWhatsappActivo(Integer whatsappActivo) {
        this.whatsappActivo = whatsappActivo;
    }
}