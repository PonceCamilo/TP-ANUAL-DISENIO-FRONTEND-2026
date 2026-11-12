package ar.utn.donatrack.frontend.logistica.services;

import ar.utn.donatrack.frontend.comun.clientes.ApiClient;
import ar.utn.donatrack.frontend.comun.config.FrontendProperties;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Cliente REST de servicio-logistica. Por ahora solo lo que necesita
 * Administración: la flota de camiones, para asignárselos a un chofer.
 */
@Service
public class LogisticaApi {

    private final ApiClient api;
    private final String logistica;

    public LogisticaApi(ApiClient api, FrontendProperties props) {
        this.api = api;
        this.logistica = props.servicios().logistica();
    }

    public List<Map<String, Object>> camiones() {
        return api.lista(logistica + "/api/logistica/camiones");
    }

    public Map<String, Object> crearCamion(Map<String, Object> cuerpo) {
        return api.crear(logistica + "/api/logistica/camiones", cuerpo);
    }
}
