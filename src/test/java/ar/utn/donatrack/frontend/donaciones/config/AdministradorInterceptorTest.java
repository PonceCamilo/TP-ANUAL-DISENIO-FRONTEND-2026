package ar.utn.donatrack.frontend.donaciones.config;

import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AdministradorInterceptor")
class AdministradorInterceptorTest {

    private final AdministradorInterceptor interceptor = new AdministradorInterceptor();
    private final MockHttpServletResponse respuesta = new MockHttpServletResponse();

    private MockHttpServletRequest pedido(SesionUsuario usuario) {
        MockHttpServletRequest pedido = new MockHttpServletRequest("GET", "/admin/donaciones");
        if (usuario != null) {
            pedido.getSession(true).setAttribute(SesionUsuario.ATRIBUTO, usuario);
        }
        return pedido;
    }

    private SesionUsuario usuario(String rol, long segundos) {
        return new SesionUsuario("admin@donatrack.org", "Admin", rol, "token", Instant.now().plusSeconds(segundos));
    }

    @Test
    @DisplayName("Deja pasar a quien entró como ADMINISTRADOR")
    void administrador() throws Exception {
        assertThat(interceptor.preHandle(pedido(usuario("ADMINISTRADOR", 600)), respuesta, new Object())).isTrue();
    }

    @Test
    @DisplayName("Sin sesión vuelve al inicio avisando que expiró")
    void sinSesion() throws Exception {
        assertThat(interceptor.preHandle(pedido(null), respuesta, new Object())).isFalse();
        assertThat(respuesta.getRedirectedUrl()).isEqualTo("/?sesion=expirada");
    }

    @Test
    @DisplayName("Con la sesión vencida también vuelve al inicio")
    void sesionVencida() throws Exception {
        assertThat(interceptor.preHandle(pedido(usuario("ADMINISTRADOR", -5)), respuesta, new Object())).isFalse();
        assertThat(respuesta.getRedirectedUrl()).isEqualTo("/?sesion=expirada");
    }

    @Test
    @DisplayName("Un donante o una entidad no entran: van a su inicio")
    void otrosRoles() throws Exception {
        assertThat(interceptor.preHandle(pedido(usuario("DONANTE", 600)), respuesta, new Object())).isFalse();
        assertThat(respuesta.getRedirectedUrl()).isEqualTo("/");

        MockHttpServletResponse otra = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(pedido(usuario("ENTIDAD", 600)), otra, new Object())).isFalse();
        assertThat(otra.getRedirectedUrl()).isEqualTo("/");
    }
}
