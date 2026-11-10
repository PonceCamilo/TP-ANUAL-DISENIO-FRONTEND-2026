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

    static Map<String, Object> cuerpo(UUID idDonante, String descripcion, String subcategoria, String subcategoriaOtra,
                                      String detalle, int cantidad, String unidad, boolean esNuevo,
                                      String fechaVencimiento) {
        String tipo = "Otro".equals(subcategoria) && subcategoriaOtra != null && !subcategoriaOtra.isBlank()
                ? subcategoriaOtra.trim()
                : subcategoria.trim();
        Map<String, Object> bien = new LinkedHashMap<>();
        bien.put("subcategoria", tipo);
        bien.put("descripcion", detalle == null || detalle.isBlank() ? tipo : detalle.trim());
        bien.put("cantidad", cantidad);
        bien.put("unidad", unidad.trim());
        bien.put("esNuevo", esNuevo);
        if (fechaVencimiento != null && !fechaVencimiento.isBlank()) {
            bien.put("fechaVencimiento", fechaVencimiento);
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("idDonante", idDonante);
        cuerpo.put("descripcion", descripcion.trim());
        cuerpo.put("bienes", List.of(bien));
        return cuerpo;
    }
}
