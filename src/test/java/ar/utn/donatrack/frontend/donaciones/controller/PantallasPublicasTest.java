package ar.utn.donatrack.frontend.donaciones.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.exceptions.SesionVencidaException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.donaciones.services.Pagina;

import java.time.Instant;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las páginas sin login de la Entrega 6 (landing con entregas y mapa, galería de
 * entregas, información legal y privacidad) y la vuelta al ingreso cuando un
 * servicio rechaza el token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Páginas públicas y sesión vencida (Entrega 6)")
class PantallasPublicasTest {

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

    private static Map<String, Object> entregada(String subcategoria, String entidad, Double lat, Double lng, String foto) {
        Map<String, Object> ubicacion = new java.util.HashMap<>();
        ubicacion.put("idEntidad", UUID.randomUUID().toString());
        ubicacion.put("razonSocial", entidad);
        ubicacion.put("localidad", "Lanús");
        ubicacion.put("latitud", lat);
        ubicacion.put("longitud", lng);
        Map<String, Object> donacion = new java.util.HashMap<>();
        donacion.put("id", UUID.randomUUID().toString());
        donacion.put("subcategoria", subcategoria);
        donacion.put("cantidad", 5);
        donacion.put("unidad", "kg");
        donacion.put("fechaEntrega", "2026-11-10T10:00:00");
        donacion.put("foto", foto);
        donacion.put("entidad", ubicacion);
        return donacion;
    }

    @BeforeEach
    void datosBase() {
        api.falla = null;
        api.fallaDeLectura = null;
        api.entregadas = Map.of(
                "contenido", List.of(
                        entregada("Arroz", "Comedor Los Pibes", -34.70, -58.39, "https://fotos.org/arroz.jpg"),
                        entregada("Abrigo", "Hogar Sin Mapa", null, null, null)),
                "totalElementos", 2);
    }

    @Test
    @DisplayName("La landing muestra las entregas del último mes, su total, el mapa y los enlaces públicos")
    void landing() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Donaciones entregadas en el último mes")))
                .andExpect(content().string(containsString("2 donaciones ya llegaron")))
                .andExpect(content().string(containsString("5 kg de Arroz")))
                .andExpect(content().string(containsString("https://fotos.org/arroz.jpg")))
                .andExpect(content().string(containsString("id=\"mapa-entregas\"")))
                .andExpect(content().string(containsString("data-entidad=\"Comedor Los Pibes\"")))
                .andExpect(content().string(not(containsString("data-entidad=\"Hogar Sin Mapa\""))))
                .andExpect(content().string(containsString("href=\"/entregas\"")))
                .andExpect(content().string(containsString("href=\"/legal\"")))
                .andExpect(content().string(containsString("href=\"/privacidad\"")));
    }

    @Test
    @DisplayName("Si servicio-donaciones no responde, la landing se muestra igual, con un aviso")
    void landingSinServicio() throws Exception {
        api.falla = new ApiException("Servicio caído", 503);

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("no pudimos mostrar las últimas entregas")))
                .andExpect(content().string(not(containsString("id=\"mapa-entregas\""))));
    }

    @Test
    @DisplayName("La galería de entregas es pública y muestra las fotos")
    void entregas() throws Exception {
        api.paginaDeEntregadas = new Pagina(List.of(entregada("Arroz", "Comedor Los Pibes", null, null,
                "https://fotos.org/arroz.jpg")), 1, 1, 1);

        mvc.perform(get("/entregas"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Donaciones que ya llegaron")))
                .andExpect(content().string(containsString("https://fotos.org/arroz.jpg")))
                .andExpect(content().string(containsString("Para Comedor Los Pibes")));
    }

    @Test
    @DisplayName("Información legal y privacidad son públicas")
    void paginasLegales() throws Exception {
        mvc.perform(get("/legal")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Condiciones de uso")));
        mvc.perform(get("/privacidad")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Ley 25.326")));
    }

    @Test
    @DisplayName("Si un servicio rechaza el token (401), se cierra la sesión y se vuelve a ingresar")
    void sesionVencida() throws Exception {
        api.fallaDeLectura = new SesionVencidaException();
        SesionUsuario donante = new SesionUsuario("ana@example.com", "Ana", SesionUsuario.DONANTE, "jwt",
                Instant.now().plusSeconds(600), "HUMANA");
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionUsuario.ATRIBUTO, donante);

        mvc.perform(get("/donante/entidades").session(sesion))
                .andExpect(redirectedUrl("/?sesion=expirada"));

        assertThat(sesion.isInvalid()).isTrue();
    }
}
