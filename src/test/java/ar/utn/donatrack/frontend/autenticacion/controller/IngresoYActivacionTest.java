package ar.utn.donatrack.frontend.autenticacion.controller;

import ar.utn.donatrack.frontend.autenticacion.services.AuthClient;
import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.comun.seguridad.VolverA;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Ingreso, creación de cuenta y activación (Entrega 6): el flujo de cada formulario
 * contra un servicio de autenticación simulado.
 */
@DisplayName("Ingreso, creación de cuenta y activación de cuentas")
class IngresoYActivacionTest {

    private final AuthClient auth = mock(AuthClient.class);
    private MockMvc mvc;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        ObjectProvider<ClientRegistrationRepository> sinGoogle = mock(ObjectProvider.class);
        mvc = MockMvcBuilders.standaloneSetup(new AutenticacionController(auth, sinGoogle),
                new ActivacionController(auth)).build();
    }

    private static SesionUsuario donante() {
        return new SesionUsuario("ana@example.com", "Ana", SesionUsuario.DONANTE, "jwt",
                Instant.now().plusSeconds(3600), "HUMANA");
    }

    @Nested
    @DisplayName("Ingreso")
    class Ingreso {
        @Test
        @DisplayName("Con datos correctos guarda la sesión y va al panel del rol")
        void ingresaAlPanel() throws Exception {
            when(auth.ingresar("ana@example.com", "clave-larga", "DONANTE")).thenReturn(donante());
            MockHttpSession sesion = new MockHttpSession();

            mvc.perform(post("/login").session(sesion).param("email", " ana@example.com ")
                            .param("password", "clave-larga").param("rol", "DONANTE"))
                    .andExpect(redirectedUrl("/donante/panel"));
            assertThat(sesion.getAttribute(SesionUsuario.ATRIBUTO)).isNotNull();
        }

        @Test
        @DisplayName("Si venía de un enlace (el mapa de su donación), vuelve ahí")
        void vuelveADondeIba() throws Exception {
            when(auth.ingresar(anyString(), anyString(), anyString())).thenReturn(donante());
            MockHttpSession sesion = new MockHttpSession();
            String mapa = "/tracking/ruta/" + UUID.randomUUID() + "?donacion=" + UUID.randomUUID();
            sesion.setAttribute(VolverA.ATRIBUTO, mapa);

            mvc.perform(post("/login").session(sesion).param("email", "ana@example.com")
                            .param("password", "clave-larga").param("rol", "DONANTE"))
                    .andExpect(redirectedUrl(mapa));
            assertThat(sesion.getAttribute(VolverA.ATRIBUTO)).isNull();
        }

        @Test
        @DisplayName("No vuelve a una página de otro rol ni a otro sitio")
        void noVuelveAOtroLado() throws Exception {
            when(auth.ingresar(anyString(), anyString(), anyString())).thenReturn(donante());
            for (String guardada : new String[]{"/entidad/recepciones", "//otro.sitio/robar", "https://otro.sitio"}) {
                MockHttpSession sesion = new MockHttpSession();
                sesion.setAttribute(VolverA.ATRIBUTO, guardada);
                mvc.perform(post("/login").session(sesion).param("email", "ana@example.com")
                                .param("password", "clave-larga").param("rol", "DONANTE"))
                        .andExpect(redirectedUrl("/donante/panel"));
            }
        }

        @Test
        @DisplayName("Si falla, vuelve al formulario con el aviso y el email cargado")
        void ingresoFallido() throws Exception {
            when(auth.ingresar(anyString(), anyString(), anyString()))
                    .thenThrow(new ApiException("Email, contraseña o perfil incorrectos.", 401));

            mvc.perform(post("/login").param("email", "ana@example.com").param("password", "mal").param("rol", "DONANTE"))
                    .andExpect(redirectedUrl("/login?rol=DONANTE"))
                    .andExpect(flash().attribute("toast", "Email, contraseña o perfil incorrectos."))
                    .andExpect(flash().attribute("emailIngresado", "ana@example.com"));
        }

        @Test
        @DisplayName("La administración ingresa por su propia pantalla")
        void administracion() throws Exception {
            when(auth.ingresar("admin@example.com", "clave-larga", "ADMINISTRADOR")).thenReturn(new SesionUsuario(
                    "admin@example.com", "Admin", SesionUsuario.ADMINISTRADOR, "jwt", Instant.now().plusSeconds(3600)));
            mvc.perform(post("/admin/ingreso").param("email", "admin@example.com").param("password", "clave-larga"))
                    .andExpect(redirectedUrl("/admin/panel"));
        }
    }

    @Nested
    @DisplayName("Creación de cuenta")
    class CrearCuenta {
        @Test
        @DisplayName("Las contraseñas distintas no llegan al servicio")
        void contraseniasDistintas() throws Exception {
            mvc.perform(post("/crear-cuenta").param("nombre", "Ana").param("email", "ana@example.com")
                            .param("password", "clave-larga").param("repetir", "otra-clave").param("rol", "DONANTE"))
                    .andExpect(redirectedUrl("/crear-cuenta?rol=DONANTE"))
                    .andExpect(flash().attribute("toast", "Las contraseñas no coinciden."));
            verify(auth, never()).registrar(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Si el email ya tiene cuenta (por ejemplo, la cargó la administración), ofrece activarla")
        void emailYaCargado() throws Exception {
            when(auth.registrar(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new ApiException("La administración ya cargó tus datos con ese email.", 409));

            mvc.perform(post("/crear-cuenta").param("nombre", "Ana").param("email", "ana@example.com")
                            .param("password", "clave-larga").param("repetir", "clave-larga").param("rol", "DONANTE"))
                    .andExpect(redirectedUrl("/crear-cuenta?rol=DONANTE"))
                    .andExpect(flash().attribute("cuentaExistente", true));
        }

        @Test
        @DisplayName("Con la cuenta creada queda la sesión iniciada")
        void cuentaCreada() throws Exception {
            when(auth.registrar("Ana", "ana@example.com", "clave-larga", "DONANTE", "HUMANA")).thenReturn(donante());
            MockHttpSession sesion = new MockHttpSession();
            mvc.perform(post("/crear-cuenta").session(sesion).param("nombre", "Ana").param("email", "ana@example.com")
                            .param("password", "clave-larga").param("repetir", "clave-larga").param("rol", "DONANTE"))
                    .andExpect(redirectedUrl("/donante/panel"));
            assertThat(sesion.getAttribute(SesionUsuario.ATRIBUTO)).isNotNull();
        }
    }

    @Nested
    @DisplayName("Activación (requisito 20)")
    class Activacion {
        @Test
        @DisplayName("Pedir el enlace siempre muestra el mismo aviso")
        void pedirEnlace() throws Exception {
            mvc.perform(post("/activar/pedir").param("email", " ana@example.com ").param("rol", "DONANTE"))
                    .andExpect(redirectedUrl("/activar?rol=DONANTE"))
                    .andExpect(flash().attribute("enlaceEnviado", true));
            verify(auth).pedirActivacion("ana@example.com", "DONANTE");
        }

        @Test
        @DisplayName("Con el enlace elige la contraseña y entra a su cuenta")
        void elegirContrasenia() throws Exception {
            when(auth.activar("codigo-del-correo", "clave-nueva-2026")).thenReturn(donante());
            MockHttpSession sesion = new MockHttpSession();

            mvc.perform(post("/activar").session(sesion).param("codigo", "codigo-del-correo")
                            .param("password", "clave-nueva-2026").param("repetir", "clave-nueva-2026"))
                    .andExpect(redirectedUrl("/donante/panel"));
            assertThat(sesion.getAttribute(SesionUsuario.ATRIBUTO)).isNotNull();
        }

        @Test
        @DisplayName("Las contraseñas distintas no llegan al servicio")
        void contraseniasDistintas() throws Exception {
            mvc.perform(post("/activar").param("codigo", "abc").param("password", "clave-nueva-2026")
                            .param("repetir", "otra-clave-2026"))
                    .andExpect(redirectedUrl("/activar?codigo=abc"));
            verify(auth, never()).activar(any(), any());
        }

        @Test
        @DisplayName("Un enlace vencido, usado o reemplazado manda a pedir otro, con el motivo")
        void enlaceQueNoSirve() throws Exception {
            for (int status : new int[]{404, 409, 410}) {
                org.mockito.Mockito.doThrow(new ApiException("El enlace venció. Pedí otro.", status))
                        .when(auth).activar("viejo", "clave-nueva-2026");
                mvc.perform(post("/activar").param("codigo", "viejo").param("password", "clave-nueva-2026")
                                .param("repetir", "clave-nueva-2026"))
                        .andExpect(redirectedUrl("/activar"))
                        .andExpect(flash().attribute("enlaceInvalido", "El enlace venció. Pedí otro."));
            }
        }
    }
}
