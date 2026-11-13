package ar.utn.donatrack.frontend.notificaciones.services;

import ar.utn.donatrack.frontend.comun.clientes.ApiClient;
import ar.utn.donatrack.frontend.comun.config.FrontendProperties;
import ar.utn.donatrack.frontend.comun.util.Mapas;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

/**
 * Cliente REST de servicio-notificaciones.
 *
 * Entrega 6: el servicio filtra por destinatario y pagina (antes devolvía las de
 * todos y se filtraban acá). Cada pedido lleva el token de la persona; para los
 * medios que no son su email (sus teléfonos), que el front saca de su perfil en
 * Donaciones, se manda también la clave del front (X-DonaTrack-Frontend).
 */
@Service
public class NotificacionesApi {

    /** Las más recientes que se muestran en la pantalla de notificaciones. */
    static final int CANTIDAD_EN_PANTALLA = 50;

    private final ApiClient api;
    private final String notificaciones;
    private final String claveFrontend;

    public NotificacionesApi(ApiClient api, FrontendProperties props,
                             @Value("${donatrack.auth.clave-frontend:}") String claveFrontend) {
        this.api = api;
        this.notificaciones = props.servicios().notificaciones();
        this.claveFrontend = claveFrontend;
    }

    /** Las notificaciones de esos destinatarios, de la más nueva a la más vieja. */
    public List<Map<String, Object>> notificacionesDe(List<String> destinatarios) {
        List<Object> variables = new ArrayList<>();
        String url = conDestinatarios(notificaciones + "/notificaciones?page=1&size=" + CANTIDAD_EN_PANTALLA,
                destinatarios, variables);
        Map<String, Object> pagina = api.objetoConCabeceras(cabeceras(), url, variables.toArray());
        return Mapas.lista(pagina.get("contenido"));
    }

    /** Cuántas sin leer, para la campanita (sin traer las notificaciones). */
    public long cantidadSinLeer(List<String> destinatarios) {
        List<Object> variables = new ArrayList<>();
        String url = conDestinatarios(notificaciones + "/notificaciones/no-leidas?", destinatarios, variables);
        Object cantidad = api.objetoConCabeceras(cabeceras(), url, variables.toArray()).get("cantidad");
        return cantidad instanceof Number n ? n.longValue() : 0L;
    }

    /**
     * Marca como leídas todas las notificaciones pendientes de esos
     * destinatarios (se llama al abrir la pantalla de notificaciones, para
     * que el contador de la campanita vuelva a cero).
     */
    public void marcarLeidas(List<String> destinatarios) {
        api.enviarConCabeceras(HttpMethod.PATCH, notificaciones + "/notificaciones/leidas",
                Map.of("destinatarios", distintos(destinatarios)), cabeceras());
    }

    /** Un parámetro destinatario por cada uno, como variable: así un "+54..." llega con su "+". */
    private static String conDestinatarios(String base, List<String> destinatarios, List<Object> variables) {
        StringBuilder url = new StringBuilder(base);
        for (String destinatario : distintos(destinatarios)) {
            url.append(url.charAt(url.length() - 1) == '?' ? "" : "&")
                    .append("destinatario={d").append(variables.size()).append('}');
            variables.add(destinatario);
        }
        return url.toString();
    }

    private static List<String> distintos(List<String> destinatarios) {
        LinkedHashSet<String> unicos = new LinkedHashSet<>();
        for (String destinatario : destinatarios) {
            if (destinatario != null && !destinatario.isBlank()) {
                unicos.add(destinatario.strip());
            }
        }
        return new ArrayList<>(unicos);
    }

    private Map<String, String> cabeceras() {
        return claveFrontend == null || claveFrontend.isBlank() ? Map.of() : Map.of("X-DonaTrack-Frontend", claveFrontend);
    }
}
