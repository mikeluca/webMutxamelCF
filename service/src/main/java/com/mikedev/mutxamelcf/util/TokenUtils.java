package com.mikedev.mutxamelcf.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class TokenUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TokenUtils() {
    }

    public static String generarToken() {

        byte[] bytes = new byte[32];

        SECURE_RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    /**
     * Código numérico de 6 dígitos (con ceros a la izquierda si hace
     * falta) para activación de cuentas. Pensado para escribirse a
     * mano desde un email, no para resistir fuerza bruta por sí solo:
     * el llamante debe combinarlo con una caducidad corta y un
     * límite de intentos por cuenta (ver UsuarioAppServiceImpl).
     */
    public static String generarCodigoActivacion() {

        int numero = SECURE_RANDOM.nextInt(1_000_000);

        return String.format("%06d", numero);
    }

    public static String hashToken(String token) {

        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {

                String hex = Integer.toHexString(0xff & b);

                if (hex.length() == 1) {
                    hexString.append('0');
                }

                hexString.append(hex);
            }

            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(
                    "No se ha podido generar el hash SHA-256",
                    e);
        }
    }
}