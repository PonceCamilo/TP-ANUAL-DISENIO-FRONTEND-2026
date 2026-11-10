package ar.utn.donatrack.frontend.comun.seguridad;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import ar.utn.donatrack.frontend.autenticacion.services.AuthClient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SesionInterceptorTest {

    private final AuthClient autenticacion = mock(AuthClient.class);
    private final SesionInterceptor interceptor = new SesionInterceptor(autenticacion);

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
    void sinSesionCadaAreaInternaRedirigeASuIngreso() throws Exception {
        MockHttpServletResponse administracion = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(pedido("/admin/panel", null), administracion, new Object()));
        assertEquals("/admin/ingreso?sesion=expirada", administracion.getRedirectedUrl());

        MockHttpServletResponse transporte = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(pedido("/chofer/panel", null), transporte, new Object()));
        assertEquals("/transporte/ingreso?sesion=expirada", transporte.getRedirectedUrl());
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
        assertFalse(interceptor.preHandle(pedido("/donante/donar", usuario("ENTIDAD_BENEFICIARIA", 600)), res, new Object()));
        assertEquals("/entidad/panel", res.getRedirectedUrl());
    }

    @Test
    void donanteNoPuedeEntrarEnAdministracionNiTransporte() throws Exception {
        for (String ruta : new String[]{"/admin/panel", "/chofer/panel"}) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(pedido(ruta, usuario("DONANTE", 600)), res, new Object()));
            assertEquals("/donante/panel", res.getRedirectedUrl());
        }
    }

    @Test
    void choferActualizaSuCamionDeSesionAlEntrar() throws Exception {
        SesionUsuario chofer = usuario("CHOFER", 600);
        UUID camion = UUID.randomUUID();
        when(autenticacion.camionActual(chofer)).thenReturn(camion);
        assertTrue(interceptor.preHandle(pedido("/chofer/panel", chofer), new MockHttpServletResponse(), new Object()));
        assertEquals(camion, chofer.getCamionId());
    }
}
