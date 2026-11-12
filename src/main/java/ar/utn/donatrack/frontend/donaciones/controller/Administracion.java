package ar.utn.donatrack.frontend.donaciones.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.util.Mapas;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

/** Lo que comparten los controllers de administración: avisos, links de página y nombres para mostrar. */
final class Administracion {

    private Administracion() {}

    static String exito(RedirectAttributes flash, String mensaje, String destino) {
        flash.addFlashAttribute("toastTipo", "ok");
        flash.addFlashAttribute("toast", mensaje);
        return "redirect:" + destino;
    }

    static String error(RedirectAttributes flash, String mensaje, String destino) {
        flash.addFlashAttribute("toastTipo", "error");
        flash.addFlashAttribute("toast", mensaje);
        return "redirect:" + destino;
    }

    /**
     * Link a otra página del mismo listado, conservando los filtros que tengan
     * valor. Los valores van como variables para que se codifiquen del todo: un
     * "+" en un email tiene que viajar como %2B, porque si no llega como espacio.
     */
    static String url(String ruta, Map<String, ?> filtros, int pagina) {
        UriComponentsBuilder url = UriComponentsBuilder.fromPath(ruta);
        Map<String, Object> valores = new LinkedHashMap<>();
        filtros.forEach((clave, valor) -> {
            if (valor != null && !String.valueOf(valor).isBlank() && !Boolean.FALSE.equals(valor)) {
                url.queryParam(clave, "{" + clave + "}");
                valores.put(clave, String.valueOf(valor).strip());
            }
        });
        // La primera página (la 1) no lleva el parámetro.
        if (pagina > DonacionesApi.PRIMERA_PAGINA) {
            url.queryParam("page", pagina);
        }
        return url.encode().buildAndExpand(valores).toUriString();
    }

    /** La fecha del filtro, o null si vino vacía o no es AAAA-MM-DD. */
    static LocalDate fecha(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(texto.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Para datos accesorios de una pantalla: si el servicio no los encuentra, la pantalla se muestra igual. */
    static Map<String, Object> siExiste(Supplier<Map<String, Object>> consulta) {
        try {
            return consulta.get();
        } catch (ApiException e) {
            return null;
        }
    }

    /** Persona humana: nombre y apellido; organización: razón social. */
    static String nombreDeDonante(Map<String, Object> donante) {
        if (donante == null) {
            return "";
        }
        String razonSocial = Mapas.texto(donante, "razonSocial");
        if (!razonSocial.isBlank()) {
            return razonSocial;
        }
        String nombre = (Mapas.texto(donante, "nombre") + " " + Mapas.texto(donante, "apellido")).strip();
        return nombre.isBlank() ? Mapas.texto(donante, "email") : nombre;
    }

    /** Copia de cada donante con "nombreVisible", para no armar el nombre en la plantilla. */
    static List<Map<String, Object>> conNombre(List<Map<String, Object>> donantes) {
        return donantes.stream().map(d -> {
            Map<String, Object> copia = new LinkedHashMap<>(d);
            copia.put("nombreVisible", nombreDeDonante(d));
            return copia;
        }).toList();
    }

    static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
