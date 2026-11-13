package ar.utn.donatrack.frontend.donaciones.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.donaciones.services.Pagina;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las pantallas de donante y entidad de la Entrega 6 se renderizan de verdad
 * (Thymeleaf incluido) con datos con la forma que devuelve servicio-donaciones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Pantallas de donante y entidad (Entrega 6)")
class PantallasDonanteYEntidadTest {

    @TestConfiguration
    static class ConServicioFalso {
        @Bean
        @Primary
        DonacionesApiFalsa donacionesApiFalsa() {
            return new DonacionesApiFalsa();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DonacionesApiFalsa api;

    private final UUID idDonante = UUID.randomUUID();
    private SesionUsuario donante;
    private SesionUsuario entidad;

    @BeforeEach
    void datosBase() {
        api.falla = null;
        api.pagina = new Pagina(List.of(), 1, 0, 0);
        api.catalogo = List.of("Abrigo", "Juguetes");
        donante = new SesionUsuario("ana@example.com", "Ana Pérez", SesionUsuario.DONANTE, "jwt",
                Instant.now().plusSeconds(600), "HUMANA");
        donante.setDonanteId(idDonante);
        entidad = new SesionUsuario("comedor@example.org", "Comedor", SesionUsuario.ENTIDAD_BENEFICIARIA, "jwt",
                Instant.now().plusSeconds(600));
    }

    @Test
    @DisplayName("Mis donaciones pide al servicio el estado, varias subcategorías y la página, y muestra el catálogo")
    void misDonacionesFiltradas() throws Exception {
        api.pagina = new Pagina(List.of(api.donacion), 2, 3, 41);

        mvc.perform(get("/donante/donaciones").sessionAttr(SesionUsuario.ATRIBUTO, donante)
                        .param("estado", "ENTREGADA").param("subcategoria", "Abrigo").param("subcategoria", "Juguetes")
                        .param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Juguetes\"")))
                .andExpect(content().string(containsString("41 donaciones")))
                .andExpect(content().string(containsString("Página 2 de 3")))
                .andExpect(content().string(containsString("Quitar filtros")));

        assertThat(api.estadoPedido).isEqualTo("ENTREGADA");
        assertThat(api.subcategoriasPedidas).containsExactly("Abrigo", "Juguetes");
        assertThat(api.paginaPedida).isEqualTo(2);
    }

    @Test
    @DisplayName("Si el catálogo de subcategorías no responde, la lista se muestra igual")
    void sinCatalogo() throws Exception {
        api.falla = new ApiException("Servicio caído", 503);

        mvc.perform(get("/donante/donaciones").sessionAttr(SesionUsuario.ATRIBUTO, donante))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mis donaciones")));
    }

    @Test
    @DisplayName("El detalle de una donación entregada muestra la fecha y las fotos de la recepción")
    void detalleEntregada() throws Exception {
        Map<String, Object> entregada = new HashMap<>(DonacionesApiFalsa.donacion(UUID.randomUUID(), "ENTREGADA"));
        entregada.put("idDonante", idDonante.toString());
        entregada.put("fechaEntrega", "2026-11-10T10:00:00");
        entregada.put("fotosEntrega", List.of("https://fotos.org/recepcion-1.jpg"));
        api.donacion = entregada;

        mvc.perform(get("/donante/donaciones/{id}", entregada.get("id")).sessionAttr(SesionUsuario.ATRIBUTO, donante))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("¡Llegó a destino!")))
                .andExpect(content().string(containsString("2026-11-10")))
                .andExpect(content().string(containsString("https://fotos.org/recepcion-1.jpg")));
    }

    @Test
    @DisplayName("El formulario de donación tiene el campo de foto por URL")
    void donarConFoto() throws Exception {
        mvc.perform(get("/donante/donar").sessionAttr(SesionUsuario.ATRIBUTO, donante))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"foto\"")));
    }

    @Test
    @DisplayName("Entidades muestra nombre, zona y necesidades abiertas, sin datos de contacto")
    void entidades() throws Exception {
        api.paginaDeEntidadesPublicas = new Pagina(List.of(Map.of(
                "ubicacion", Map.of("idEntidad", UUID.randomUUID().toString(), "razonSocial", "Comedor Los Pibes",
                        "localidad", "Lanús", "provincia", "Buenos Aires"),
                "necesidadesAbiertas", List.of(Map.of("nombre", "Frazadas", "subcategoria", "Abrigo",
                        "cantidadObjetivo", 30, "cantidadRecibida", 10, "unidad", "kg", "campania", "Invierno")))), 1, 1, 1);

        mvc.perform(get("/donante/entidades").sessionAttr(SesionUsuario.ATRIBUTO, donante))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Comedor Los Pibes")))
                .andExpect(content().string(containsString("Lanús, Buenos Aires")))
                .andExpect(content().string(containsString("10 de 30 kg recibidas")))
                .andExpect(content().string(containsString("Campaña: Invierno")));
    }

    @Test
    @DisplayName("El menú del donante lleva a Entidades; el de la entidad ya no tiene Disponibles")
    void menus() throws Exception {
        mvc.perform(get("/donante/donar").sessionAttr(SesionUsuario.ATRIBUTO, donante))
                .andExpect(content().string(containsString("href=\"/donante/entidades\"")));
        mvc.perform(get("/entidad/registro").sessionAttr(SesionUsuario.ATRIBUTO, entidad))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/entidad/disponibles"))));
    }

    @Test
    @DisplayName("Disponibles se eliminó: la entidad no puede autoasignarse donaciones (requisito 17)")
    void sinDisponibles() throws Exception {
        entidad.setEntidadId(UUID.randomUUID());

        mvc.perform(get("/entidad/disponibles").sessionAttr(SesionUsuario.ATRIBUTO, entidad))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("El registro de la entidad pide la ubicación (opcional) para el mapa")
    void registroConUbicacion() throws Exception {
        mvc.perform(get("/entidad/registro").sessionAttr(SesionUsuario.ATRIBUTO, entidad))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"latitud\"")))
                .andExpect(content().string(containsString("name=\"longitud\"")))
                .andExpect(content().string(containsString("Usar mi ubicación actual")));
    }

    @Test
    @DisplayName("La administración ve el menú con sus pantallas de Donaciones")
    void menuDeAdministracion() throws Exception {
        SesionUsuario admin = new SesionUsuario("admin@donatrack.org", "Admin", SesionUsuario.ADMINISTRADOR, "jwt",
                Instant.now().plusSeconds(600));

        mvc.perform(get("/admin/donaciones").sessionAttr(SesionUsuario.ATRIBUTO, admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/admin/panel\"")))
                .andExpect(content().string(containsString("href=\"/admin/donantes\"")));
    }
}
