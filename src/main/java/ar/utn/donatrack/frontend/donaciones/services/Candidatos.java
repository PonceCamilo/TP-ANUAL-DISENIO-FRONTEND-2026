package ar.utn.donatrack.frontend.donaciones.services;

import ar.utn.donatrack.frontend.comun.util.Mapas;

import java.util.LinkedHashSet;
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
}
