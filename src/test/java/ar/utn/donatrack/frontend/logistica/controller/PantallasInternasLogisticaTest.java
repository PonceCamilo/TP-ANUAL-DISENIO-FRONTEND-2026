package ar.utn.donatrack.frontend.logistica.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ar.utn.donatrack.frontend.autenticacion.services.AuthClient;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;
import ar.utn.donatrack.frontend.logistica.services.LogisticaApi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Las pantallas de flota y de no recibidas quedan en el área del administrador,
 * y Mi ruta usa el camión de la sesión. Las direcciones públicas anteriores no responden.
 */
class PantallasInternasLogisticaTest {

    private final LogisticaApi api = mock(LogisticaApi.class);
    private final DonacionesApi donaciones = mock(DonacionesApi.class);
    private final AuthClient authClient = mock(AuthClient.class);
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(
                new FlotaController(api, authClient),
                new RevisionEntregasController(api, donaciones),
                new MiRutaController(api),
                new RecepcionController(api),
                new ReplanificacionController(api)).build();
    }

    @Test
    void flotaYNoRecibidasCuelganDelAdministrador() throws Exception {
        when(api.camiones()).thenReturn(List.of());
        when(api.entregasPorEstado("NO_RECIBIDA")).thenReturn(List.of());
        when(donaciones.donacionesPorEstado("ENTREGA_FALLIDA")).thenReturn(List.of());

        mvc.perform(get("/admin/flota"))
                .andExpect(status().isOk())
                .andExpect(view().name("logistica/flota"));
        mvc.perform(get("/admin/entregas/no-recibidas"))
                .andExpect(status().isOk())
                .andExpect(view().name("logistica/no-recibidas"));

        mvc.perform(get("/logistica/flota")).andExpect(status().isNotFound());
        mvc.perform(get("/logistica/entregas/no-recibidas")).andExpect(status().isNotFound());
    }

    @Test
    void miRutaSinCamionNoConsultaLogistica() throws Exception {
        mvc.perform(get("/chofer/mi-ruta").sessionAttr(SesionUsuario.ATRIBUTO, chofer(null)))
                .andExpect(status().isOk())
                .andExpect(view().name("logistica/mi-ruta"))
                .andExpect(model().attribute("sinCamion", true));
        verifyNoInteractions(api);
        mvc.perform(get("/logistica/mi-ruta/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void miRutaUsaElCamionDeLaSesion() throws Exception {
        UUID camion = UUID.randomUUID();
        when(api.camion(camion)).thenReturn(Map.of("id", camion.toString(), "patente", "AB123CD"));
        when(api.rutaVigente(camion)).thenReturn(null);

        mvc.perform(get("/chofer/mi-ruta").sessionAttr(SesionUsuario.ATRIBUTO, chofer(camion)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sinCamion", false));

        verify(api).camion(camion);
        verify(api).rutaVigente(camion);
    }

    @Test
    void iniciarTomaElCamionDeLaSesion() throws Exception {
        UUID camion = UUID.randomUUID();
        UUID ruta = UUID.randomUUID();
        when(api.rutaVigente(camion)).thenReturn(Map.of("id", ruta.toString()));

        mvc.perform(post("/chofer/mi-ruta/iniciar").sessionAttr(SesionUsuario.ATRIBUTO, chofer(camion)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chofer/mi-ruta"));

        verify(api).iniciarRuta(ruta);
    }

    @Test
    void iniciarSinCamionNoTocaLaRuta() throws Exception {
        mvc.perform(post("/chofer/mi-ruta/iniciar").sessionAttr(SesionUsuario.ATRIBUTO, chofer(null)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chofer/mi-ruta"));
        verify(api, never()).iniciarRuta(any());
    }

    @Test
    void recepcionMuestraLaPatenteSinEnlaceALaFlota() throws Exception {
        UUID donacion = UUID.randomUUID();
        UUID camion = UUID.randomUUID();
        when(api.entregaPorDonacion(donacion)).thenReturn(Map.of(
                "id", UUID.randomUUID().toString(),
                "estado", "EN_TRASLADO",
                "camionId", camion.toString(),
                "historial", List.of()));
        when(api.camion(camion)).thenReturn(Map.of("patente", "AB123CD"));

        mvc.perform(get("/recepcion").param("donacion", donacion.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("logistica/recepcion"))
                .andExpect(model().attribute("camionTexto", "AB123CD"));
    }

    @Test
    void replanificarAvisaCuantasRutasSeArmaron() throws Exception {
        when(api.replanificarPendientes()).thenReturn(List.of(
                Map.of("rutas", List.of(Map.of("id", "ruta-1")))));

        mvc.perform(post("/admin/replanificar"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/panel"))
                .andExpect(flash().attribute("toast", "Se armó 1 ruta nueva. El chofer la ve en Mi ruta si es su camión."));
    }

    @Test
    void replanificarSinPendientesNoInventaRutas() throws Exception {
        when(api.replanificarPendientes()).thenReturn(List.of());

        mvc.perform(post("/admin/replanificar"))
                .andExpect(flash().attribute("toast", "No hay entregas en el depósito listas para una ruta nueva."));
    }

    private static SesionUsuario chofer(UUID camion) {
        SesionUsuario usuario = new SesionUsuario(
                "chofer@b.com", "Leo Díaz", SesionUsuario.CHOFER, "token", Instant.now().plusSeconds(600));
        usuario.setCamionId(camion);
        return usuario;
    }
}
