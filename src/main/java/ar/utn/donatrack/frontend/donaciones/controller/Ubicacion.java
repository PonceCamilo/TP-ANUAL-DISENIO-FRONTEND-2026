package ar.utn.donatrack.frontend.donaciones.controller;

import java.util.Map;

/**
 * La ubicación opcional de una dirección (Entrega 6): latitud y longitud para el
 * mapa de donaciones entregadas. Van las dos o ninguna, igual que valida
 * servicio-donaciones; acá se revisa antes de mandar para dar un mensaje claro.
 */
final class Ubicacion {

    private Ubicacion() {}

    /**
     * Las coordenadas listas para sumar a la dirección, o vacío si no vino ninguna.
     *
     * @throws IllegalArgumentException si viene una sola, no es un número o está fuera de rango
     */
    static Map<String, Object> desde(String latitud, String longitud) {
        boolean sinLatitud = latitud == null || latitud.isBlank();
        boolean sinLongitud = longitud == null || longitud.isBlank();
        if (sinLatitud && sinLongitud) {
            return Map.of();
        }
        if (sinLatitud || sinLongitud) {
            throw new IllegalArgumentException("Para ubicar la entidad en el mapa indicá la latitud y la longitud.");
        }
        double lat = numero(latitud, "latitud");
        double lng = numero(longitud, "longitud");
        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("La latitud tiene que estar entre -90 y 90.");
        }
        if (lng < -180 || lng > 180) {
            throw new IllegalArgumentException("La longitud tiene que estar entre -180 y 180.");
        }
        return Map.of("latitud", lat, "longitud", lng);
    }

    private static double numero(String texto, String campo) {
        try {
            // Se acepta la coma decimal, que es la que se escribe en Argentina.
            return Double.parseDouble(texto.strip().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("La " + campo + " no es un número válido.");
        }
    }
}
