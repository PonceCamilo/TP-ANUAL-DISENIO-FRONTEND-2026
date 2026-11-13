package ar.utn.donatrack.frontend.donaciones.services;

import ar.utn.donatrack.frontend.comun.util.Mapas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entrega 6: las necesidades de una entidad (de todas sus campañas) listas para
 * mostrar a la administración: con la unidad, cuánto recibió, cuánto falta y el
 * porcentaje de avance.
 */
public final class Necesidades {

    private Necesidades() {}

    public static List<Map<String, Object>> de(Map<String, Object> entidad) {
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Map<String, Object> campania : Mapas.lista(entidad.get("campanias"))) {
            for (Map<String, Object> necesidad : Mapas.lista(campania.get("necesidades"))) {
                filas.add(fila(entidad, campania, necesidad));
            }
        }
        return filas;
    }

    public static Map<String, Object> fila(Map<String, Object> entidad, Map<String, Object> campania,
                                    Map<String, Object> necesidad) {
        int objetivo = entero(necesidad.get("cantidadObjetivo"));
        int recibida = entero(necesidad.get("cantidadRecibida"));
        String unidad = Mapas.texto(necesidad, "unidad");
        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("idEntidad", Mapas.texto(entidad, "id"));
        fila.put("razonSocial", Mapas.texto(entidad, "razonSocial"));
        fila.put("campania", Mapas.texto(campania, "descripcionGeneral"));
        fila.put("nombre", Mapas.texto(necesidad, "nombre"));
        fila.put("descripcion", Mapas.texto(necesidad, "descripcion"));
        fila.put("subcategoria", Mapas.texto(necesidad, "subcategoria"));
        fila.put("tipo", "RECURRENTE".equals(Mapas.texto(necesidad, "tipo")) ? "Recurrente" : "Extraordinaria");
        fila.put("unidad", unidad.isBlank() ? "unidades" : unidad);
        fila.put("objetivo", objetivo);
        fila.put("recibida", recibida);
        fila.put("falta", Math.max(0, objetivo - recibida));
        fila.put("porcentaje", objetivo <= 0 ? 100 : Math.min(100, recibida * 100 / objetivo));
        fila.put("satisfecha", Boolean.TRUE.equals(necesidad.get("satisfecha")));
        return fila;
    }

    private static int entero(Object valor) {
        if (valor instanceof Number n) {
            return n.intValue();
        }
        try {
            return valor == null ? 0 : Integer.parseInt(valor.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
