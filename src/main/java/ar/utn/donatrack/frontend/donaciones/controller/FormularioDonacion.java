package ar.utn.donatrack.frontend.donaciones.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Arma el cuerpo de POST /donaciones a partir del formulario de donación. Lo
 * usan el donante (para sí mismo) y la persona administradora (a nombre de un
 * donante): los dos formularios tienen los mismos campos.
 */
final class FormularioDonacion {

    private FormularioDonacion() {}

    /**
     * Un bien con fecha de vencimiento es perecedero y va sin esNuevo: el servicio
     * rechaza un bien que trae los dos datos, porque no sabe de qué tipo es. Antes
     * el formulario mandaba siempre esNuevo y una donación de alimentos con
     * vencimiento respondía 400.
     *
     * @param foto URL de una foto del bien (Entrega 6), opcional
     */
    static Map<String, Object> cuerpo(UUID idDonante, String descripcion, String subcategoria, String subcategoriaOtra,
                                      String detalle, int cantidad, String unidad, boolean esNuevo,
                                      String fechaVencimiento, String foto) {
        String tipo = "Otro".equals(subcategoria) && subcategoriaOtra != null && !subcategoriaOtra.isBlank()
                ? subcategoriaOtra.trim()
                : subcategoria.trim();
        Map<String, Object> bien = new LinkedHashMap<>();
        bien.put("subcategoria", tipo);
        bien.put("descripcion", detalle == null || detalle.isBlank() ? tipo : detalle.trim());
        bien.put("cantidad", cantidad);
        bien.put("unidad", unidad.trim());
        if (fechaVencimiento != null && !fechaVencimiento.isBlank()) {
            bien.put("fechaVencimiento", fechaVencimiento);
        } else {
            bien.put("esNuevo", esNuevo);
        }
        if (foto != null && !foto.isBlank()) {
            bien.put("foto", foto.trim());
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("idDonante", idDonante);
        cuerpo.put("descripcion", descripcion.trim());
        cuerpo.put("bienes", List.of(bien));
        return cuerpo;
    }
}
