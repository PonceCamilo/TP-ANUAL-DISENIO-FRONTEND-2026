package ar.utn.donatrack.frontend.notificaciones.services;

import ar.utn.donatrack.frontend.comun.clientes.ApiClient;
import ar.utn.donatrack.frontend.comun.config.FrontendProperties;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Entrega 6 (F-N1): el filtro por destinatario y la paginación los hace el
 * servicio; el front manda los destinatarios como variables (un "+54..." llega
 * con su "+") y la clave del front para los medios que no son el email.
 */
@DisplayName("NotificacionesApi contra el servicio que filtra y pagina")
class NotificacionesApiTest {

    private static final String CLAVE = "clave-del-frontend-de-al-menos-32-caracteres";
    private final ApiClient api = mock(ApiClient.class);
    private final NotificacionesApi notificaciones = new NotificacionesApi(api,
            new FrontendProperties(new FrontendProperties.Servicios("d", "i", "http://notificaciones", "l", "a")), CLAVE);

    @Test
    @DisplayName("Pide la primera página filtrada por cada destinatario, sin repetirlos, con la clave del front")
    void pideFiltradoYPaginado() {
        when(api.objetoConCabeceras(any(), any(), any(Object[].class))).thenReturn(Map.of(
                "contenido", List.of(Map.of("mensaje", "Hola"))));

        List<Map<String, Object>> lista = notificaciones.notificacionesDe(List.of("ana@example.com", "+54 11 5555", "ana@example.com"));

        assertThat(lista).hasSize(1);
        verify(api).objetoConCabeceras(Map.of("X-DonaTrack-Frontend", CLAVE),
                "http://notificaciones/notificaciones?page=1&size=50&destinatario={d0}&destinatario={d1}",
                "ana@example.com", "+54 11 5555");
    }

    @Test
    @DisplayName("La campanita pide solo la cantidad")
    void cantidadSinLeer() {
        when(api.objetoConCabeceras(any(), any(), any(Object[].class))).thenReturn(Map.of("cantidad", 3));

        assertThat(notificaciones.cantidadSinLeer(List.of("ana@example.com"))).isEqualTo(3);
        verify(api).objetoConCabeceras(Map.of("X-DonaTrack-Frontend", CLAVE),
                "http://notificaciones/notificaciones/no-leidas?destinatario={d0}", "ana@example.com");
    }

    @Test
    @DisplayName("Marcar como leídas manda los destinatarios y la clave")
    void marcarLeidas() {
        notificaciones.marcarLeidas(List.of("ana@example.com", " ", "+54 11 5555"));
        verify(api).enviarConCabeceras(eq(HttpMethod.PATCH), eq("http://notificaciones/notificaciones/leidas"),
                eq(Map.of("destinatarios", List.of("ana@example.com", "+54 11 5555"))),
                eq(Map.of("X-DonaTrack-Frontend", CLAVE)));
    }
}
