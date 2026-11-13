package ar.utn.donatrack.frontend.incentivos.services;

import ar.utn.donatrack.frontend.comun.clientes.ApiClient;
import ar.utn.donatrack.frontend.comun.config.FrontendProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

/** Cliente REST de servicio-incentivos: métricas, misiones e insignias del donante. */
@Service
public class IncentivosApi {

    private final ApiClient api;
    private final String incentivos;

    public IncentivosApi(ApiClient api, FrontendProperties props) {
        this.api = api;
        this.incentivos = props.servicios().incentivos();
    }

    public Map<String, Object> metricas(UUID donanteId) {
        return api.objeto(incentivos + "/api/incentivos/donantes/{id}/metricas", donanteId);
    }

    public List<Map<String, Object>> misiones(UUID donanteId) {
        return api.lista(incentivos + "/api/incentivos/donantes/{id}/misiones", donanteId);
    }

    public Map<String, Object> perfilPublico(UUID donanteId) {
        return api.objeto(incentivos + "/api/incentivos/donantes/{id}/perfil-publico", donanteId);
    }

    public Map<String, Object> podio() {
        return api.objeto(incentivos + "/api/incentivos/rankings/podio");
    }

    public void cambiarVisibilidad(UUID donanteId, long insigniaId, boolean visible) {
        api.enviar(HttpMethod.PATCH, incentivos + "/api/incentivos/donantes/{id}/insignias/{insignia}/visibilidad",
                Map.of("visible", visible), donanteId, insigniaId);
    }

    public List<Map<String, Object>> insignias(UUID donanteId) {
        return sinRepetir(api.lista(incentivos + "/api/incentivos/donantes/{id}/insignias", donanteId));
    }

    /** Una insignia por nombre. El servicio a veces guardó la misma medalla más de una vez. */
    public static List<Map<String, Object>> sinRepetir(List<Map<String, Object>> insignias) {
        Map<String, Map<String, Object>> unicas = new LinkedHashMap<>();
        for (Map<String, Object> insignia : insignias) {
            Object nombre = insignia.get("nombre");
            String clave = nombre == null || nombre.toString().isBlank()
                    ? "id:" + insignia.get("id")
                    : nombre.toString().strip().toLowerCase();
            Map<String, Object> previa = unicas.get(clave);
            if (previa == null || (!Boolean.TRUE.equals(previa.get("visible")) && Boolean.TRUE.equals(insignia.get("visible")))) {
                unicas.put(clave, insignia);
            }
        }
        return new ArrayList<>(unicas.values());
    }

    /** Periodos (anio/mes) que ya tienen un ranking cerrado, para el selector de Rankings de admin. */
    public List<Map<String, Object>> rankingMesesDisponibles() {
        return api.lista(incentivos + "/api/incentivos/admin/rankings/meses");
    }

    /** Ranking de un mes puntual (paginado). Si es el mes actual y no hay cierre, viene "provisional". */
    public Map<String, Object> rankingDeMes(int anio, int mes, int pagina, int tamanio) {
        return api.objeto(incentivos + "/api/incentivos/admin/rankings/{anio}/{mes}?page={pagina}&size={tamanio}",
                anio, mes, pagina, tamanio);
    }
}
