package ar.utn.donatrack.frontend.logistica.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.exceptions.SesionVencidaException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;
import ar.utn.donatrack.frontend.logistica.services.LogisticaApi;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recepción y mapa de seguimiento (Entrega 6): la entidad confirma con URLs de
 * fotos solo lo suyo, y la persona donante ve el mapa solo de una ruta que lleva
 * su donación.
 */
@DisplayName("Recepción con fotos y mapa de seguimiento por rol")
class RecepcionYSeguimientoTest {

    private final LogisticaApi logistica = mock(LogisticaApi.class);
    private final DonacionesApi donaciones = mock(DonacionesApi.class);
    private MockMvc mvc;

    private final UUID donacion = UUID.randomUUID();
    private final UUID entrega = UUID.randomUUID();
    private final UUID ruta = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new RecepcionController(logistica),
                new SeguimientoController(logistica, donaciones)).build();
    }

    private static SesionUsuario conRol(String rol) {
        return new SesionUsuario("persona@example.com", "Persona", rol, "jwt", Instant.now().plusSeconds(600));
    }

    @Nested
    @DisplayName("Recepción")
    class Recepcion {
        @Test
        @DisplayName("Confirma con las URLs de las fotos, una por renglón")
        void confirmaConFotos() throws Exception {
            mvc.perform(post("/recepcion").param("donacion", donacion.toString()).param("entrega", entrega.toString())
                            .param("fotos", "https://fotos.example/1.jpg\r\n\r\n  https://fotos.example/2.jpg  "))
                    .andExpect(redirectedUrl("/recepcion?donacion=" + donacion))
                    .andExpect(flash().attribute("toastTipo", "ok"));
            verify(logistica).confirmarEntrega(entrega, List.of("https://fotos.example/1.jpg", "https://fotos.example/2.jpg"));
        }

        @Test
        @DisplayName("Sin fotos, con algo que no es una URL o con más de 10, no llama a Logística")
        void validaLasFotos() throws Exception {
            String once = String.join("\n", java.util.Collections.nCopies(11, "https://fotos.example/una.jpg"));
            for (String fotos : new String[]{"", "comprobante en papel", once}) {
                mvc.perform(post("/recepcion").param("donacion", donacion.toString()).param("entrega", entrega.toString())
                                .param("fotos", fotos))
                        .andExpect(redirectedUrl("/recepcion?donacion=" + donacion))
                        .andExpect(flash().attribute("toastTipo", "error"));
            }
            verify(logistica, never()).confirmarEntrega(any(), any());
        }

        @Test
        @DisplayName("Si la entrega es de otra entidad, Logística la rechaza y se avisa así")
        void entregaAjena() throws Exception {
            org.mockito.Mockito.doThrow(new ApiException("No tenés permiso para hacer esta operación.", 403))
                    .when(logistica).confirmarEntrega(any(), any());
            mvc.perform(post("/recepcion").param("donacion", donacion.toString()).param("entrega", entrega.toString())
                            .param("fotos", "https://fotos.example/1.jpg"))
                    .andExpect(flash().attribute("toast", "Esta entrega no es para tu entidad."));

            when(logistica.entregaPorDonacion(donacion)).thenThrow(new ApiException("Esta entrega no es para tu entidad.", 403));
            mvc.perform(get("/recepcion").param("donacion", donacion.toString()))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("ajena", true));
        }
    }

    @Nested
    @DisplayName("Mapa de seguimiento")
    class Mapa {
        private Map<String, Object> rutaQueLleva(UUID idDonacion) {
            return Map.of("id", ruta.toString(), "estado", "INICIADA", "paradas", List.of(
                    Map.of("entregas", List.of(Map.of("id", entrega.toString(), "idDonacion", idDonacion.toString())))));
        }

        @Test
        @DisplayName("La persona donante lo ve si la donación es suya (lo confirma Donaciones) y la ruta la lleva")
        void donanteConSuDonacion() throws Exception {
            when(logistica.ruta(ruta)).thenReturn(rutaQueLleva(donacion));
            mvc.perform(get("/tracking/ruta/{id}/posicion", ruta).param("donacion", donacion.toString())
                            .sessionAttr(SesionUsuario.ATRIBUTO, conRol(SesionUsuario.DONANTE)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("INICIADA"));
            verify(donaciones).donacion(donacion);
        }

        @Test
        @DisplayName("Sin la donación, con una donación ajena o con una ruta que no la lleva: 403")
        void donanteSinPermiso() throws Exception {
            mvc.perform(get("/tracking/ruta/{id}/posicion", ruta).sessionAttr(SesionUsuario.ATRIBUTO, conRol(SesionUsuario.DONANTE)))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(logistica);

            when(logistica.ruta(ruta)).thenReturn(rutaQueLleva(UUID.randomUUID()));
            mvc.perform(get("/tracking/ruta/{id}/posicion", ruta).param("donacion", donacion.toString())
                            .sessionAttr(SesionUsuario.ATRIBUTO, conRol(SesionUsuario.DONANTE)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("Esta ruta no lleva tu donación."));

            UUID ajena = UUID.randomUUID();
            when(donaciones.donacion(ajena)).thenThrow(new ApiException("No tenés permiso para hacer esto.", 403));
            mvc.perform(get("/tracking/ruta/{id}/posicion", ruta).param("donacion", ajena.toString())
                            .sessionAttr(SesionUsuario.ATRIBUTO, conRol(SesionUsuario.DONANTE)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("La entidad, el chofer y la administración lo piden directo: Logística controla a la entidad")
        void otrosRoles() throws Exception {
            when(logistica.ruta(ruta)).thenReturn(rutaQueLleva(donacion));
            for (String rol : new String[]{SesionUsuario.ENTIDAD_BENEFICIARIA, SesionUsuario.CHOFER, SesionUsuario.ADMINISTRADOR}) {
                mvc.perform(get("/tracking/ruta/{id}/posicion", ruta).sessionAttr(SesionUsuario.ATRIBUTO, conRol(rol)))
                        .andExpect(status().isOk());
            }
            verifyNoInteractions(donaciones);
        }

        @Test
        @DisplayName("Si la sesión venció mientras miraba el mapa, la posición responde 401 para que recargue")
        void sesionVencida() throws Exception {
            when(logistica.ruta(ruta)).thenThrow(new SesionVencidaException());
            mvc.perform(get("/tracking/ruta/{id}/posicion", ruta)
                            .sessionAttr(SesionUsuario.ATRIBUTO, conRol(SesionUsuario.ENTIDAD_BENEFICIARIA)))
                    .andExpect(status().isUnauthorized());
        }
    }
}
