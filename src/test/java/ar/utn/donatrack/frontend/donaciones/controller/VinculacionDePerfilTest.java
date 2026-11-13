package ar.utn.donatrack.frontend.donaciones.controller;

import ar.utn.donatrack.frontend.autenticacion.services.AuthClient;
import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.logistica.services.LogisticaApi;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Después de crear la cuenta, la persona registra su perfil en Donaciones y el
 * front lo vincula a la cuenta en Autenticación (perfilId en el token). Entrega 6,
 * F-A1: el flujo que ya existía, con un servicio de autenticación simulado.
 */
@DisplayName("Registro del perfil de la persona donante y vinculación con su cuenta")
class VinculacionDePerfilTest {

    private final DonacionesApiFalsa donaciones = new DonacionesApiFalsa();
    private final AuthClient auth = mock(AuthClient.class);
    private MockMvc mvc;
    private MockHttpSession sesion;
    private SesionUsuario cuentaNueva;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new DonanteDonacionesController(donaciones, auth, mock(LogisticaApi.class))).build();
        cuentaNueva = new SesionUsuario("ana@example.com", "Ana", SesionUsuario.DONANTE, "jwt",
                Instant.now().plusSeconds(600), "HUMANA");
        sesion = new MockHttpSession();
        sesion.setAttribute(SesionUsuario.ATRIBUTO, cuentaNueva);
    }

    private ResultActions registrarPerfil() throws Exception {
        return mvc.perform(post("/donante/registro").session(sesion)
                .param("nombre", "Ana").param("apellido", "Pérez").param("tipoDocumento", "DNI")
                .param("numeroDocumento", "30111222").param("fechaNacimiento", "1990-05-20").param("genero", "FEMENINO")
                .param("calle", "Medrano").param("numero", "951").param("codigoPostal", "C1179")
                .param("localidad", "CABA").param("provincia", "Buenos Aires"));
    }

    @Test
    @DisplayName("Crea el perfil con el email de la cuenta y lo vincula: la sesión queda con el perfil")
    void registraYVincula() throws Exception {
        SesionUsuario vinculada = new SesionUsuario("ana@example.com", "Ana", SesionUsuario.DONANTE, "jwt-con-perfil",
                Instant.now().plusSeconds(600), "HUMANA");
        vinculada.setDonanteId(UUID.randomUUID());
        when(auth.vincularPerfil(eq(cuentaNueva), any())).thenReturn(vinculada);

        registrarPerfil().andExpect(redirectedUrl("/donante/panel"));

        assertThat(donaciones.cuerpoEnviado).containsEntry("email", "ana@example.com");
        assertThat(sesion.getAttribute(SesionUsuario.ATRIBUTO)).isSameAs(vinculada);
    }

    @Test
    @DisplayName("Si la vinculación falla, el reintento no crea un segundo perfil")
    void reintentoSinDuplicar() throws Exception {
        when(auth.vincularPerfil(eq(cuentaNueva), any()))
                .thenThrow(new ApiException("El servicio de autenticación no responde.", 503))
                .thenReturn(cuentaNueva);

        registrarPerfil().andExpect(redirectedUrl("/donante/registro"))
                .andExpect(flash().attribute("toastTipo", "error"));
        registrarPerfil().andExpect(redirectedUrl("/donante/panel"));

        assertThat(donaciones.acciones.stream().filter("registrarDonante"::equals)).hasSize(1);
    }

    @Test
    @DisplayName("Sin la clave del front configurada no crea el perfil (no podría vincularlo)")
    void sinClaveDelFront() throws Exception {
        doThrow(new ApiException("Falta configurar AUTH_FRONTEND_KEY para vincular tu perfil.", 503))
                .when(auth).exigirVinculacionConfigurada();

        registrarPerfil().andExpect(redirectedUrl("/donante/registro"))
                .andExpect(flash().attribute("toast", "Falta configurar AUTH_FRONTEND_KEY para vincular tu perfil."));

        assertThat(donaciones.acciones).doesNotContain("registrarDonante");
        verify(auth, never()).vincularPerfil(any(), any());
    }
}
