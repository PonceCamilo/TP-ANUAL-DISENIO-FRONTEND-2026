package ar.utn.donatrack.frontend.comun.util;


import java.util.List;
import java.util.Map;

/** Lectura segura de las respuestas JSON (Map/List) sin ensuciar los controllers con casteos. */
public final class Mapas {

    private Mapas() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> mapa(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> lista(Object o) {
        return o instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    public static String texto(Map<String, Object> m, String clave) {
        Object v = m.get(clave);
        return v == null ? "" : String.valueOf(v);
    }

    /** true si algún texto de la lista coincide (sin mayúsculas) con el valor. */
    public static boolean igual(String a, String b) {
        return a != null && b != null && !a.isBlank() && a.trim().equalsIgnoreCase(b.trim());
    }
}
