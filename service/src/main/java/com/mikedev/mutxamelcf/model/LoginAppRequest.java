package com.mikedev.mutxamelcf.model;

public class LoginAppRequest {

    private String email;
    private String password;

    public LoginAppRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}