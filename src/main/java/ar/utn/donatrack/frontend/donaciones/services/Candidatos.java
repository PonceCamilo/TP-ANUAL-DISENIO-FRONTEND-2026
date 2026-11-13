package ar.utn.donatrack.frontend.donaciones.services;

import ar.utn.donatrack.frontend.comun.util.Mapas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lectura de la respuesta de candidatos de servicio-donaciones:
 * {rankings: [{algoritmo, entidades: [...]}], coincidencias: [...]}.
 * Vale para GET /donaciones/{id}/candidatos y para cada elemento de
 * GET /donaciones/candidatos.
 */
public final class Candidatos {

    private static final Map<String, String> ALGORITMOS = Map.of(
            "COMPATIBILIDAD_SEMANTICA", "Compatibilidad con sus necesidades",
            "PRIORIDAD_SUBATENDIDOS", "Prioridad a las menos atendidas");

    private Candidatos() {}

    /** Ids de todas las entidades candidatas, sin repetir: las de cada ranking y las coincidencias. */
    public static Set<String> idsDeEntidades(Map<String, Object> respuesta) {
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> ranking : Mapas.lista(respuesta.get("rankings"))) {
            for (Map<String, Object> entidad : Mapas.lista(ranking.get("entidades"))) {
                ids.add(Mapas.texto(entidad, "id"));
            }
        }
        for (Map<String, Object> entidad : Mapas.lista(respuesta.get("coincidencias"))) {
            ids.add(Mapas.texto(entidad, "id"));
        }
        ids.remove("");
        return ids;
    }

    public static boolean incluye(Map<String, Object> respuesta, UUID idEntidad) {
        return idEntidad != null && idsDeEntidades(respuesta).contains(idEntidad.toString());
    }

    /** Nombre para personas del algoritmo que armó un ranking. */
    public static String nombreDelAlgoritmo(String codigo) {
        if (codigo == null) {
            return "";
        }
        return ALGORITMOS.getOrDefault(codigo, codigo.replace('_', ' ').toLowerCase());
    }

    /**
     * Entrega 6: una sola lista de entidades candidatas en lugar de un recuadro por
     * algoritmo más otro de coincidencias. Cada entidad trae el puesto que le dio
     * cada algoritmo, si la recomiendan todos (las "coincidencias"), y sus
     * necesidades abiertas de la misma subcategoría que la donación, con unidad y
     * avance. Primero las recomendadas por todos; después, por la suma de puestos
     * (un algoritmo que no la nombra cuenta como el último puesto más uno).
     */
    public static List<Map<String, Object>> unificados(Map<String, Object> respuesta, String subcategoria) {
        List<Map<String, Object>> rankings = Mapas.lista(respuesta.get("rankings"));
        Set<String> recomendadas = new LinkedHashSet<>();
        for (Map<String, Object> entidad : Mapas.lista(respuesta.get("coincidencias"))) {
            recomendadas.add(Mapas.texto(entidad, "id"));
        }
        Map<String, Map<String, Object>> porId = new LinkedHashMap<>();
        Map<String, Integer> suma = new LinkedHashMap<>();
        for (Map<String, Object> ranking : rankings) {
            for (Map<String, Object> entidad : Mapas.lista(ranking.get("entidades"))) {
                porId.putIfAbsent(Mapas.texto(entidad, "id"), entidad);
            }
        }
        for (Map<String, Object> entidad : Mapas.lista(respuesta.get("coincidencias"))) {
            porId.putIfAbsent(Mapas.texto(entidad, "id"), entidad);
        }
        porId.remove("");

        List<Map<String, Object>> filas = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> par : porId.entrySet()) {
            String id = par.getKey();
            Map<String, Object> entidad = par.getValue();
            List<Map<String, Object>> puestos = new ArrayList<>();
            int total = 0;
            for (Map<String, Object> ranking : rankings) {
                List<Map<String, Object>> entidades = Mapas.lista(ranking.get("entidades"));
                int puesto = 0;
                for (int i = 0; i < entidades.size(); i++) {
                    if (id.equals(Mapas.texto(entidades.get(i), "id"))) {
                        puesto = i + 1;
                        break;
                    }
                }
                total += puesto == 0 ? entidades.size() + 1 : puesto;
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("algoritmo", nombreDelAlgoritmo(Mapas.texto(ranking, "algoritmo")));
                p.put("puesto", puesto);
                puestos.add(p);
            }
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id", id);
            fila.put("razonSocial", Mapas.texto(entidad, "razonSocial"));
            Map<String, Object> localidad = Mapas.mapa(Mapas.mapa(entidad.get("direccion")).get("localidad"));
            fila.put("localidad", Mapas.texto(localidad, "nombre"));
            fila.put("provincia", Mapas.texto(Mapas.mapa(localidad.get("provincia")), "nombre"));
            fila.put("recomendada", recomendadas.contains(id));
            fila.put("puestos", puestos);
            fila.put("necesidades", necesidadesDe(entidad, subcategoria));
            suma.put(id, total);
            filas.add(fila);
        }
        filas.sort(Comparator
                .comparing((Map<String, Object> f) -> !(Boolean) f.get("recomendada"))
                .thenComparing(f -> suma.get((String) f.get("id")))
                .thenComparing(f -> ((String) f.get("razonSocial")).toLowerCase()));
        return filas;
    }

    /** Las necesidades abiertas de la entidad para esa subcategoría, con lo que falta. */
    static List<Map<String, Object>> necesidadesDe(Map<String, Object> entidad, String subcategoria) {
        List<Map<String, Object>> necesidades = new ArrayList<>();
        for (Map<String, Object> campania : Mapas.lista(entidad.get("campanias"))) {
            for (Map<String, Object> necesidad : Mapas.lista(campania.get("necesidades"))) {
                if (Boolean.TRUE.equals(necesidad.get("satisfecha"))
                        || subcategoria != null && !subcategoria.equalsIgnoreCase(Mapas.texto(necesidad, "subcategoria"))) {
                    continue;
                }
                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("nombre", Mapas.texto(necesidad, "nombre"));
                fila.put("unidad", Mapas.texto(necesidad, "unidad"));
                int objetivo = entero(necesidad.get("cantidadObjetivo"));
                int recibida = entero(necesidad.get("cantidadRecibida"));
                fila.put("objetivo", objetivo);
                fila.put("recibida", recibida);
                fila.put("falta", Math.max(0, objetivo - recibida));
                necesidades.add(fila);
            }
        }
        return necesidades;
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
