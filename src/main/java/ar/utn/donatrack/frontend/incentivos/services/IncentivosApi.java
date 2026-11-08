package ar.utn.donatrack.frontend.incentivos.services;

import ar.utn.donatrack.frontend.comun.clientes.ApiClient;
import ar.utn.donatrack.frontend.comun.config.FrontendProperties;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    public List<Map<String, Object>> insignias(UUID donanteId) {
        return api.lista(incentivos + "/api/incentivos/donantes/{id}/insignias", donanteId);
    }
}
