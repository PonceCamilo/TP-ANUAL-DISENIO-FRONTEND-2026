package ar.utn.donatrack.frontend;

import ar.utn.donatrack.frontend.autenticacion.services.AuthClient;
import ar.utn.donatrack.frontend.comun.clientes.ApiClient;
import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;
import ar.utn.donatrack.frontend.donaciones.services.ImportacionCsvClient;
import ar.utn.donatrack.frontend.incentivos.services.IncentivosApi;
import ar.utn.donatrack.frontend.logistica.services.LogisticaApi;
import ar.utn.donatrack.frontend.notificaciones.services.NotificacionesApi;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Entrega 6, correcciones de la prueba manual: cada pantalla que cambió se
 * renderiza de verdad (Thymeleaf incluido) con datos con la forma que devuelven
 * los servicios, para que un error de plantilla no aparezca recién en el navegador.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Pantallas corregidas en la Entrega 6")
class PantallasCorregidasTest {

    @Autowired MockMvc mvc;
    @MockitoBean ApiClient api;
    @MockitoBean ImportacionCsvClient importacionCsv;
    @MockitoBean DonacionesApi donaciones;
    @MockitoBean LogisticaApi logistica;
    @MockitoBean IncentivosApi incentivos;
    @MockitoBean NotificacionesApi notificaciones;
    @MockitoBean AuthClient autenticacion;

    private final UUID camion = UUID.randomUUID();
    private final UUID ruta = UUID.randomUUID();
    private final UUID entrega = UUID.randomUUID();
    private final UUID donacion = UUID.randomUUID();

    private static SesionUsuario sesion(String rol) {
        return new SesionUsuario("persona@example.com", "Ana Pérez", rol, "jwt", Instant.now().plusSeconds(600));
    }

    private Map<String, Object> rutaConCarga() {
        Map<String, Object> entregaEnParada = new HashMap<>();
        entregaEnParada.put("id", entrega.toString());
        entregaEnParada.put("idDonacion", donacion.toString());
        entregaEnParada.put("resumen", "4 kg de Arroz, 2 cajas de Leche");
        Map<String, Object> parada = new HashMap<>();
        parada.put("orden", 1);
        parada.put("idEntidadBeneficiaria", UUID.randomUUID().toString());
        parada.put("nombreEntidad", "Comedor Los Pibes");
        parada.put("direccion", Map.of("calle", "Medrano", "numero", 951, "localidad", "Almagro", "provincia", "CABA"));
        parada.put("entregas", List.of(entregaEnParada));
        Map<String, Object> r = new HashMap<>();
        r.put("id", ruta.toString());
        r.put("estado", "INICIADA");
        r.put("camionId", camion.toString());
        r.put("fechaInicio", "2026-11-13T09:00:00");
        r.put("paradas", List.of(parada));
        return r;
    }

    @Test
    @DisplayName("Camión del admin: a qué entidad va cada parada y qué lleva")
    void camion() throws Exception {
        when(logistica.camion(camion)).thenReturn(Map.of("id", camion.toString(), "patente", "AB123CD", "estado", "EN_RUTA"));
        when(logistica.rutaVigente(camion)).thenReturn(rutaConCarga());

        mvc.perform(get("/admin/flota/" + camion).sessionAttr(SesionUsuario.ATRIBUTO, sesion(SesionUsuario.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Comedor Los Pibes")))
                .andExpect(content().string(containsString("4 kg de Arroz, 2 cajas de Leche")))
                .andExpect(content().string(containsString("/admin/donaciones/" + donacion)));
    }

    @Test
    @DisplayName("Mi ruta del chofer: qué lleva a cada entidad")
    void miRuta() throws Exception {
        SesionUsuario chofer = sesion(SesionUsuario.CHOFER);
        chofer.setCamionId(camion);
        when(autenticacion.camionActual(any())).thenReturn(camion);
        when(logistica.camion(camion)).thenReturn(Map.of("id", camion.toString(), "patente", "AB123CD"));
        when(logistica.rutaVigente(camion)).thenReturn(rutaConCarga());
        when(logistica.entrega(entrega)).thenReturn(Map.of("estado", "EN_TRASLADO"));

        mvc.perform(get("/chofer/mi-ruta").sessionAttr(SesionUsuario.ATRIBUTO, chofer))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Comedor Los Pibes")))
                .andExpect(content().string(containsString("Llevás:")))
                .andExpect(content().string(containsString("4 kg de Arroz")))
                .andExpect(content().string(containsString("Registrar incidente")));
    }

    @Test
    @DisplayName("Mapa de seguimiento con el nombre de la entidad de cada parada")
    void mapa() throws Exception {
        when(logistica.ruta(ruta)).thenReturn(rutaConCarga());
        when(logistica.camion(camion)).thenReturn(Map.of("id", camion.toString(), "patente", "AB123CD"));

        mvc.perform(get("/tracking/ruta/" + ruta).sessionAttr(SesionUsuario.ATRIBUTO, sesion(SesionUsuario.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Comedor Los Pibes")));
    }

    @Test
    @DisplayName("No recibidas y su detalle: qué llevaba y para quién")
    void noRecibidas() throws Exception {
        Map<String, Object> fallida = new HashMap<>();
        fallida.put("id", entrega.toString());
        fallida.put("idDonacion", donacion.toString());
        fallida.put("estado", "NO_RECIBIDA");
        fallida.put("motivo", "ENTIDAD_AUSENTE");
        fallida.put("resumen", "10 unidades de Frazadas");
        fallida.put("nombreEntidad", "Hogar Belén");
        fallida.put("historial", List.of());
        when(logistica.entregasPorEstado("NO_RECIBIDA")).thenReturn(List.of(fallida));
        when(logistica.entrega(entrega)).thenReturn(fallida);
        SesionUsuario admin = sesion(SesionUsuario.ADMINISTRADOR);

        mvc.perform(get("/admin/entregas/no-recibidas").sessionAttr(SesionUsuario.ATRIBUTO, admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("10 unidades de Frazadas")))
                .andExpect(content().string(containsString("Para Hogar Belén")));
        mvc.perform(get("/admin/entregas/" + entrega).sessionAttr(SesionUsuario.ATRIBUTO, admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hogar Belén")))
                .andExpect(content().string(containsString("Ver la donación")));
    }

    @Test
    @DisplayName("Recepciones de la entidad: la que está en camino se sigue en el mapa")
    void recepciones() throws Exception {
        SesionUsuario entidad = sesion(SesionUsuario.ENTIDAD_BENEFICIARIA);
        UUID idEntidad = UUID.randomUUID();
        entidad.setEntidadId(idEntidad);
        Map<String, Object> enCamino = new HashMap<>();
        enCamino.put("id", donacion.toString());
        enCamino.put("descripcion", "Frazadas");
        enCamino.put("subcategoria", "Abrigo");
        enCamino.put("estado", "EN_TRASLADO");
        enCamino.put("idRutaLogistica", ruta.toString());
        enCamino.put("bienes", List.of());
        Map<String, Object> lista = new HashMap<>(enCamino);
        lista.put("id", UUID.randomUUID().toString());
        lista.put("estado", "LISTA_PARA_ENTREGAR");
        lista.put("idRutaLogistica", null);
        when(donaciones.donacionesHaciaEntidad(idEntidad)).thenReturn(List.of(enCamino, lista));
        when(donaciones.entidad(idEntidad)).thenReturn(Map.of("id", idEntidad.toString(), "contactos", List.of()));

        mvc.perform(get("/entidad/recepciones").sessionAttr(SesionUsuario.ATRIBUTO, entidad))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/tracking/ruta/" + ruta)))
                .andExpect(content().string(containsString("Ver la entrega")));
    }

    @Test
    @DisplayName("Nueva necesidad: se elige la unidad de medida")
    void necesidadNueva() throws Exception {
        SesionUsuario entidad = sesion(SesionUsuario.ENTIDAD_BENEFICIARIA);
        entidad.setEntidadId(UUID.randomUUID());
        when(donaciones.entidad(any())).thenReturn(Map.of("contactos", List.of()));

        mvc.perform(get("/entidad/campanias/" + UUID.randomUUID() + "/necesidades/nueva")
                        .sessionAttr(SesionUsuario.ATRIBUTO, entidad))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"unidad\"")))
                .andExpect(content().string(containsString("value=\"kg\"")));
    }

    @Test
    @DisplayName("Panel de la entidad: el avance de cada necesidad dice la unidad")
    void panelEntidad() throws Exception {
        SesionUsuario entidad = sesion(SesionUsuario.ENTIDAD_BENEFICIARIA);
        UUID idEntidad = UUID.randomUUID();
        entidad.setEntidadId(idEntidad);
        Map<String, Object> necesidad = new HashMap<>();
        necesidad.put("id", UUID.randomUUID().toString());
        necesidad.put("nombre", "Arroz");
        necesidad.put("descripcion", "");
        necesidad.put("subcategoria", "Alimentos");
        necesidad.put("cantidadObjetivo", 30);
        necesidad.put("cantidadRecibida", 10);
        necesidad.put("unidad", "kg");
        necesidad.put("satisfecha", false);
        necesidad.put("tipo", "EXTRAORDINARIA");
        Map<String, Object> campania = new HashMap<>();
        campania.put("idCampania", UUID.randomUUID().toString());
        campania.put("descripcionGeneral", "Invierno");
        campania.put("fechaInicio", "2026-06-01");
        campania.put("fechaFin", "2026-12-31");
        campania.put("necesidades", List.of(necesidad));
        when(donaciones.entidad(idEntidad)).thenReturn(Map.of("id", idEntidad.toString(), "razonSocial", "Comedor",
                "campanias", List.of(campania), "contactos", List.of()));

        mvc.perform(get("/entidad/panel").sessionAttr(SesionUsuario.ATRIBUTO, entidad))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("10 de 30 kg recibidas")));
    }

    @Test
    @DisplayName("Formularios de alta: provincia entre las 24 jurisdicciones y localidad con sugerencias")
    void provinciaYLocalidad() throws Exception {
        SesionUsuario donante = sesion(SesionUsuario.DONANTE);
        SesionUsuario entidad = sesion(SesionUsuario.ENTIDAD_BENEFICIARIA);
        SesionUsuario admin = sesion(SesionUsuario.ADMINISTRADOR);
        for (var pedido : List.of(
                get("/donante/registro").sessionAttr(SesionUsuario.ATRIBUTO, donante),
                get("/entidad/registro").sessionAttr(SesionUsuario.ATRIBUTO, entidad),
                get("/admin/donantes/nuevo").sessionAttr(SesionUsuario.ATRIBUTO, admin),
                get("/admin/entidades/nueva").sessionAttr(SesionUsuario.ATRIBUTO, admin))) {
            mvc.perform(pedido)
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<select id=\"provincia\" name=\"provincia\"")))
                    .andExpect(content().string(containsString("value=\"Tierra del Fuego\"")))
                    .andExpect(content().string(containsString("list=\"localidades-sugeridas\"")))
                    .andExpect(content().string(containsString("/js/localidades.js")));
        }
        SesionUsuario juridico = new SesionUsuario("org@example.com", "Org", SesionUsuario.DONANTE, "jwt",
                Instant.now().plusSeconds(600), "JURIDICA");
        mvc.perform(get("/donante/registro").sessionAttr(SesionUsuario.ATRIBUTO, juridico))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<select id=\"provincia\" name=\"provincia\"")));
    }

    @Test
    @DisplayName("Donar: no se puede elegir un vencimiento pasado")
    void vencimiento() throws Exception {
        SesionUsuario donante = sesion(SesionUsuario.DONANTE);
        donante.setDonanteId(UUID.randomUUID());
        when(donaciones.donante(any())).thenReturn(Map.of("contactos", List.of()));

        mvc.perform(get("/donante/donar").sessionAttr(SesionUsuario.ATRIBUTO, donante))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-desde-hoy")));
        mvc.perform(get("/admin/donaciones/nueva").sessionAttr(SesionUsuario.ATRIBUTO, sesion(SesionUsuario.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-desde-hoy")));
    }

    @Test
    @DisplayName("Panel del donante: si fallan las métricas, las insignias igual se ven")
    void insigniasAunqueFallenLasMetricas() throws Exception {
        SesionUsuario donante = sesion(SesionUsuario.DONANTE);
        UUID idDonante = UUID.randomUUID();
        donante.setDonanteId(idDonante);
        when(donaciones.donante(idDonante)).thenReturn(Map.of("contactos", List.of()));
        when(incentivos.metricas(idDonante)).thenThrow(new ApiException("Incentivos no responde", 502));
        when(incentivos.misiones(idDonante)).thenReturn(List.of());
        when(incentivos.insignias(idDonante)).thenReturn(List.of(
                Map.of("id", 1, "nombre", "Semilla de Solidaridad", "visible", true)));
        when(incentivos.podio()).thenThrow(new ApiException("Incentivos no responde", 502));

        mvc.perform(get("/donante/panel").sessionAttr(SesionUsuario.ATRIBUTO, donante))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tus insignias")))
                .andExpect(content().string(containsString("Semilla de Solidaridad")))
                .andExpect(content().string(containsString("No pudimos cargar tus logros ahora")));
    }

    @Test
    @DisplayName("Cambiar visibilidad de una insignia devuelve la confirmación sin recargar el panel")
    void cambiarVisibilidadSinRecargarPanel() throws Exception {
        SesionUsuario donante = sesion(SesionUsuario.DONANTE);
        UUID idDonante = UUID.randomUUID();
        donante.setDonanteId(idDonante);
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionUsuario.ATRIBUTO, donante);
        when(incentivos.metricas(idDonante)).thenThrow(new ApiException("Incentivos no responde", 502));
        when(incentivos.podio()).thenThrow(new ApiException("Incentivos no responde", 502));
        when(incentivos.insignias(idDonante)).thenReturn(List.of(
                Map.of("id", 1, "nombre", "Semilla de Solidaridad", "visible", false)));
        CsrfToken csrf = (CsrfToken) mvc.perform(get("/donante/panel").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-visibilidad-insignia=\"1\"")))
                .andExpect(content().string(containsString("data-insignia-id=\"1\"")))
                .andReturn().getRequest().getAttribute(CsrfToken.class.getName());

        String ruta = "/donante/insignias/1/visibilidad";
        mvc.perform(post(ruta).session(sesion).param(csrf.getParameterName(), csrf.getToken())
                        .param("visible", "true").header("X-Actualizar-Insignia", "true"))
                .andExpect(status().isNoContent());
        verify(incentivos).cambiarVisibilidad(idDonante, 1, true);

        doThrow(new ApiException("No pudimos guardar el cambio.", 503))
                .when(incentivos).cambiarVisibilidad(idDonante, 1, false);
        mvc.perform(post(ruta).session(sesion).param(csrf.getParameterName(), csrf.getToken())
                        .param("visible", "false").header("X-Actualizar-Insignia", "true"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("No pudimos guardar el cambio."));

        mvc.perform(post(ruta).session(sesion).param(csrf.getParameterName(), csrf.getToken())
                        .param("visible", "false"))
                .andExpect(redirectedUrl("/donante/panel#t-insignias"));
    }

    @Test
    @DisplayName("El ingreso de administración abre el panel aunque Logística no responda")
    void ingresoAdminConFlotaNoDisponible() throws Exception {
        SesionUsuario admin = sesion(SesionUsuario.ADMINISTRADOR);
        when(autenticacion.ingresar(admin.getEmail(), "clave", SesionUsuario.ADMINISTRADOR)).thenReturn(admin);
        when(logistica.camiones()).thenThrow(new ApiException("No pudimos conectarnos con el servicio.", 503));
        when(donaciones.paginaDeDonantes(null, null, 1))
                .thenReturn(new ar.utn.donatrack.frontend.donaciones.services.Pagina(List.of(), 1, 0, 7));
        MockHttpSession sesion = new MockHttpSession();
        CsrfToken csrf = (CsrfToken) mvc.perform(get("/admin/ingreso").session(sesion))
                .andExpect(status().isOk()).andReturn().getRequest().getAttribute(CsrfToken.class.getName());

        mvc.perform(post("/admin/ingreso").session(sesion).param(csrf.getParameterName(), csrf.getToken())
                        .param("email", admin.getEmail()).param("password", "clave"))
                .andExpect(redirectedUrl("/admin/panel"));

        mvc.perform(get("/admin/panel").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No pudimos cargar la flota")))
                .andExpect(content().string(containsString(">7</div>")))
                .andExpect(content().string(not(containsString("toast-error"))))
                .andExpect(content().string(not(containsString("Todavía no hay camiones cargados"))));
    }

    @Test
    @DisplayName("Campanita: la cantidad de avisos sin leer, en rojo")
    void campanita() throws Exception {
        when(notificaciones.cantidadSinLeer(anyList())).thenReturn(3L);
        when(logistica.camiones()).thenReturn(List.of());
        // Una pendiente cuya persona donante ya no se encuentra: el panel igual se muestra.
        when(donaciones.donacionesEnDeposito()).thenReturn(List.of(Map.of("id", donacion.toString(),
                "descripcion", "Alimentos secos", "subcategoria", "Alimentos", "idDonante", UUID.randomUUID().toString(),
                "bienes", List.of(Map.of("descripcion", "Arroz", "cantidad", 4, "unidad", "kg")))));
        when(donaciones.donante(any())).thenThrow(new ApiException("No encontramos a la persona donante", 404));
        when(donaciones.paginaDeDonantes(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new ar.utn.donatrack.frontend.donaciones.services.Pagina(List.of(), 1, 0, 0));

        mvc.perform(get("/admin/panel").sessionAttr(SesionUsuario.ATRIBUTO, sesion(SesionUsuario.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"badge-no-leidas\"")))
                .andExpect(content().string(containsString("3 sin leer")))
                .andExpect(content().string(containsString("barra-admin")))
                .andExpect(content().string(containsString("4 kg de Arroz")))
                .andExpect(content().string(containsString("/admin/donaciones/" + donacion + "/candidatos")))
                .andExpect(content().string(not(containsString("/admin/asignacion\""))));
    }
}
