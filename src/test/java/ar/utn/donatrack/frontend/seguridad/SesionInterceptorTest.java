package ar.utn.donatrack.frontend.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SesionInterceptorTest {

    private final SesionInterceptor interceptor = new SesionInterceptor();

    private MockHttpServletRequest pedido(String ruta, SesionUsuario usuario) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", ruta);
        if (usuario != null) {
            req.getSession(true).setAttribute(SesionUsuario.ATRIBUTO, usuario);
        }
        return req;
    }

    private SesionUsuario usuario(String rol, long segundos) {
        return new SesionUsuario("a@b.com", "Ana Pérez", rol, "token", Instant.now().plusSeconds(segundos));
    }

    @Test
    void sinSesionRedirigeAlInicio() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(pedido("/donante/panel", null), res, new Object()));
        assertEquals("/?sesion=expirada", res.getRedirectedUrl());
    }

    @Test
    void sesionVencidaSeRechaza() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(pedido("/donante/panel", usuario("DONANTE", -5)), res, new Object()));
        assertEquals("/?sesion=expirada", res.getRedirectedUrl());
    }

    @Test
    void donanteEntraASuSeccion() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(pedido("/donante/panel", usuario("DONANTE", 600)), res, new Object()));
    }

    @Test
    void donanteNoPuedeEntrarAEntidad() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(pedido("/entidad/panel", usuario("DONANTE", 600)), res, new Object()));
        assertEquals("/donante/panel", res.getRedirectedUrl());
    }

    @Test
    void entidadNoPuedeEntrarADonante() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(pedido("/donante/donar", usuario("ENTIDAD", 600)), res, new Object()));
        assertEquals("/entidad/panel", res.getRedirectedUrl());
    }
}
