package ar.utn.donatrack.frontend.donaciones.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Arma el cuerpo de POST /donaciones a partir del formulario de donación. Lo
 * usan el donante (para sí mismo, con uno o más bienes) y la persona
 * administradora (un bien, a nombre de un donante).
 */
final class FormularioDonacion {

    private static final Set<String> UNIDADES = Set.of("unidades", "kg", "litros", "cajas");

    private FormularioDonacion() {}

    /**
     * Un bien con fecha de vencimiento es perecedero y va sin esNuevo: el servicio
     * rechaza un bien que trae los dos datos, porque no sabe de qué tipo es.
     */
    static Map<String, Object> cuerpo(UUID idDonante, String descripcion, String subcategoria, String subcategoriaOtra,
                                      String detalle, int cantidad, String unidad, boolean esNuevo,
                                      String fechaVencimiento, String foto) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("idDonante", idDonante);
        cuerpo.put("descripcion", descripcion.trim());
        cuerpo.put("bienes", List.of(bien(subcategoria, subcategoriaOtra, detalle, cantidad, unidad, esNuevo,
                fechaVencimiento, foto)));
        return cuerpo;
    }

    /** Un solo pedido a POST /donaciones, con todos los bienes de la carga. */
    static Map<String, Object> cuerpo(UUID idDonante, CargaDeDonacion carga) {
        List<Map<String, Object>> bienes = new ArrayList<>();
        for (CargaDeDonacion.Bien item : carga.getBienes()) {
            bienes.add(bien(item.getSubcategoria(), item.getSubcategoriaOtra(), item.getDetalle(),
                    Integer.parseInt(item.getCantidad().trim()), item.getUnidad(),
                    "true".equals(item.getEsNuevo()), item.getFechaVencimiento(), item.getFoto()));
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("idDonante", idDonante);
        cuerpo.put("descripcion", carga.getDescripcion().trim());
        cuerpo.put("bienes", bienes);
        return cuerpo;
    }

    /**
     * @return el mensaje para mostrar, o null si la carga se puede enviar
     */
    static String validar(CargaDeDonacion carga) {
        if (carga == null || carga.getDescripcion() == null || carga.getDescripcion().isBlank()) {
            return "Ponele un título a la donación.";
        }
        if (carga.getDescripcion().trim().length() > 120) {
            return "El título puede tener hasta 120 caracteres.";
        }
        if (carga.getBienes() == null || carga.getBienes().isEmpty()) {
            return "Agregá al menos un bien.";
        }
        for (int i = 0; i < carga.getBienes().size(); i++) {
            String error = validarBien(carga.getBienes().get(i), i + 1);
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    private static String validarBien(CargaDeDonacion.Bien bien, int numero) {
        String donde = "En el bien " + numero + ", ";
        if (bien == null || bien.getSubcategoria() == null || bien.getSubcategoria().isBlank()) {
            return donde + "elegí el tipo de donación.";
        }
        if ("Otro".equals(bien.getSubcategoria())
                && (bien.getSubcategoriaOtra() == null || bien.getSubcategoriaOtra().isBlank())) {
            return donde + "indicá qué tipo es.";
        }
        if (bien.getSubcategoriaOtra() != null && bien.getSubcategoriaOtra().trim().length() > 60) {
            return donde + "el tipo puede tener hasta 60 caracteres.";
        }
        if (bien.getCantidad() == null || !bien.getCantidad().trim().matches("[1-9]\\d*")) {
            return donde + "la cantidad tiene que ser un número mayor a cero.";
        }
        if (bien.getUnidad() == null || !UNIDADES.contains(bien.getUnidad())) {
            return donde + "elegí la unidad.";
        }
        boolean conVencimiento = bien.getFechaVencimiento() != null && !bien.getFechaVencimiento().isBlank();
        if (conVencimiento) {
            try {
                if (LocalDate.parse(bien.getFechaVencimiento()).isBefore(LocalDate.now())) {
                    return donde + "la fecha de vencimiento no puede ser anterior a hoy.";
                }
            } catch (DateTimeParseException e) {
                return donde + "la fecha de vencimiento no es válida.";
            }
        } else if (!"true".equals(bien.getEsNuevo()) && !"false".equals(bien.getEsNuevo())) {
            return donde + "indicá si está nuevo o usado.";
        }
        if (bien.getDetalle() != null && bien.getDetalle().trim().length() > 300) {
            return donde + "el detalle puede tener hasta 300 caracteres.";
        }
        if (bien.getFoto() != null && !bien.getFoto().isBlank()) {
            String foto = bien.getFoto().trim();
            if (foto.length() > 2048 || !foto.matches("https?://\\S+")) {
                return donde + "la foto tiene que ser un enlace http o https.";
            }
        }
        return null;
    }

    private static Map<String, Object> bien(String subcategoria, String subcategoriaOtra, String detalle,
                                            int cantidad, String unidad, boolean esNuevo,
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
        return bien;
    }
}
