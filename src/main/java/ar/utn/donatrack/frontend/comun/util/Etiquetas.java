package ar.utn.donatrack.frontend.comun.util;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Traduce los valores técnicos de los servicios (EN_DEPOSITO, ENTREGA_FALLIDA…)
 * a lenguaje de persona y arma el "recorrido" de una donación como un envío.
 * Se expone a las plantillas como @etiquetas.
 */
public final class Etiquetas {

    private static final Map<String, String> ESTADOS = Map.of(
            "EN_DEPOSITO", "En depósito",
            "ASIGNACION_REALIZADA", "Entidad asignada",
            "LISTA_PARA_ENTREGAR", "Lista para entregar",
            "EN_TRASLADO", "En camino",
            "ENTREGADA", "Entregada",
            "ENTREGA_FALLIDA", "Entrega fallida",
            "VENCIDA", "Vencida");

    /** Estados del camino feliz, en orden. Cada uno es una parada del recorrido. */
    private static final List<String> PARADAS = List.of(
            "EN_DEPOSITO", "ASIGNACION_REALIZADA", "LISTA_PARA_ENTREGAR", "EN_TRASLADO", "ENTREGADA");
    private static final List<String> ICONOS_PARADAS = List.of(
            "warehouse", "pin", "box", "truck", "check");

    private Etiquetas() {}

    public static String estado(String codigo) {
        if (codigo == null) {
            return "";
        }
        return ESTADOS.getOrDefault(codigo, codigo.replace('_', ' ').toLowerCase());
    }

    /** Variante visual del estado: ok, info, warn, error. El color nunca es lo único que lo distingue. */
    public static String variante(String codigo) {
        if (codigo == null) {
            return "info";
        }
        return switch (codigo) {
            case "ENTREGADA" -> "ok";
            case "ENTREGA_FALLIDA", "VENCIDA" -> "error";
            case "EN_DEPOSITO" -> "warn";
            default -> "info";
        };
    }

    /** Nombre del ícono (sprite de base.html) que acompaña al estado. */
    public static String icono(String codigo) {
        if (codigo == null) {
            return "info";
        }
        return switch (codigo) {
            case "ENTREGADA" -> "check";
            case "ENTREGA_FALLIDA", "VENCIDA" -> "alert";
            case "EN_TRASLADO" -> "truck";
            case "EN_DEPOSITO" -> "warehouse";
            case "ASIGNACION_REALIZADA" -> "pin";
            case "LISTA_PARA_ENTREGAR" -> "box";
            default -> "info";
        };
    }

    /** Una parada del recorrido, lista para dibujar. */
    public static final class Paso {
        private final String nombre;
        private final String icono;
        private final String clase;
        private final String leyenda;

        Paso(String nombre, String icono, String clase, String leyenda) {
            this.nombre = nombre;
            this.icono = icono;
            this.clase = clase;
            this.leyenda = leyenda;
        }

        public String getNombre() { return nombre; }
        public String getIcono() { return icono; }
        /** Clases CSS: hecho / actual / fallo / fin. */
        public String getClase() { return clase; }
        /** Texto para lectores de pantalla (el color solo no alcanza). */
        public String getLeyenda() { return leyenda; }
    }

    /**
     * Dibuja las cinco paradas según el estado. ENTREGADA completa todo el camino;
     * ENTREGA_FALLIDA marca con problema la parada "En camino"; VENCIDA, la primera.
     */
    public static List<Paso> recorrido(String codigo) {
        String c = codigo == null ? "" : codigo;
        int actual = switch (c) {
            case "ENTREGA_FALLIDA" -> 3;
            case "VENCIDA" -> 0;
            default -> Math.max(0, PARADAS.indexOf(c));
        };
        boolean fallo = "ENTREGA_FALLIDA".equals(codigo) || "VENCIDA".equals(codigo);
        boolean entregada = "ENTREGADA".equals(codigo);

        List<Paso> pasos = new ArrayList<>();
        for (int i = 0; i < PARADAS.size(); i++) {
            String nombre = estado(PARADAS.get(i));
            String icono = ICONOS_PARADAS.get(i);
            String clase;
            String leyenda;
            if (entregada || i < actual) {
                clase = "hecho" + (i == PARADAS.size() - 1 ? " fin" : "");
                leyenda = "completado";
            } else if (i == actual) {
                clase = fallo ? "actual fallo" : "actual";
                leyenda = fallo ? "con problemas" : "paso actual";
                if (fallo) {
                    icono = "alert";
                }
            } else {
                clase = "";
                leyenda = "pendiente";
            }
            pasos.add(new Paso(nombre, icono, clase, leyenda));
        }
        return pasos;
    }
}
