package ar.utn.donatrack.frontend.notificaciones.services;

import ar.utn.donatrack.frontend.comun.clientes.ApiClient;
import ar.utn.donatrack.frontend.comun.config.FrontendProperties;
import ar.utn.donatrack.frontend.comun.util.Mapas;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/** Cliente REST de servicio-notificaciones. */
@Service
public class NotificacionesApi {

    private final ApiClient api;
    private final String notificaciones;

    public NotificacionesApi(ApiClient api, FrontendProperties props) {
        this.api = api;
        this.notificaciones = props.servicios().notificaciones();
    }

    /** El servicio de notificaciones no filtra por persona: se filtra acá por destinatario. */
    public List<Map<String, Object>> notificacionesDe(List<String> destinatarios) {
        List<Map<String, Object>> todas = api.lista(notificaciones + "/notificaciones");
        List<Map<String, Object>> propias = new ArrayList<>();
        for (Map<String, Object> n : todas) {
            String d = Mapas.texto(n, "destinatario");
            if (destinatarios.stream().anyMatch(x -> Mapas.igual(x, d))) {
                propias.add(n);
            }
        }
        propias.sort((a, b) -> Mapas.texto(b, "fechaCreacion").compareTo(Mapas.texto(a, "fechaCreacion")));
        return propias;
    }
}
