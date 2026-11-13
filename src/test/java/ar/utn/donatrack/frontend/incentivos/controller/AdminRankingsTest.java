package ar.utn.donatrack.frontend.incentivos.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.incentivos.services.IncentivosApi;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rankings de la administración (Entrega 6, requisito 19). */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Rankings de la administración")
class AdminRankingsTest {

    @Autowired MockMvc mvc;
    @MockitoBean IncentivosApi incentivos;

    private static SesionUsuario administracion() {
        return new SesionUsuario("admin@example.com", "Admin", SesionUsuario.ADMINISTRADOR, "jwt", Instant.now().plusSeconds(600));
    }

    private static Map<String, Object> puesto(int numero, String nombre) {
        java.util.HashMap<String, Object> p = new java.util.HashMap<>();
        p.put("puesto", numero);
        p.put("donanteId", UUID.randomUUID().toString());
        p.put("nombreVisible", nombre);
        p.put("misionesCompletadas", 10 - numero);
        return p;
    }

    @Test
    @DisplayName("Muestra el podio y el ranking del mes con el nombre que da Incentivos, y el historial")
    void muestraElRanking() throws Exception {
        YearMonth actual = YearMonth.now();
        YearMonth cerrado = actual.minusMonths(1);
        when(incentivos.podio()).thenReturn(Map.of("provisional", true,
                "puestos", List.of(puesto(1, "Ana P."), puesto(2, null))));
        when(incentivos.rankingMesesDisponibles()).thenReturn(List.of(
                Map.of("anio", cerrado.getYear(), "mes", cerrado.getMonthValue())));
        when(incentivos.rankingDeMes(actual.getYear(), actual.getMonthValue(), 0, 20)).thenReturn(Map.of(
                "provisional", true, "contenido", List.of(puesto(1, "Ana P.")), "totalPaginas", 1, "totalElementos", 1));

        mvc.perform(get("/admin/rankings").sessionAttr(SesionUsuario.ATRIBUTO, administracion()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("podio", hasItem(hasEntry("nombre", "Donante"))))
                .andExpect(model().attribute("periodosDisponibles",
                        Set.of(actual.getYear() + "-" + actual.getMonthValue(), cerrado.getYear() + "-" + cerrado.getMonthValue())))
                .andExpect(content().string(containsString("Ana P.")))
                .andExpect(content().string(containsString("href=\"/admin/rankings\"")));
    }

    @Test
    @DisplayName("Un mes sin ranking guardado se muestra vacío; si Incentivos no responde, se avisa")
    void mesSinRankingOServicioCaido() throws Exception {
        when(incentivos.podio()).thenThrow(new ApiException("Incentivos no responde.", 503));
        when(incentivos.rankingMesesDisponibles()).thenReturn(List.of());
        when(incentivos.rankingDeMes(2025, 3, 0, 20)).thenThrow(new ApiException("No hay un ranking guardado para ese mes.", 404));

        mvc.perform(get("/admin/rankings").param("anio", "2025").param("mes", "3")
                        .sessionAttr(SesionUsuario.ATRIBUTO, administracion()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("podioNoDisponible", true))
                .andExpect(model().attribute("rankingNoDisponible", false))
                .andExpect(model().attribute("ranking", List.of()));

        when(incentivos.rankingDeMes(anyInt(), anyInt(), anyInt(), anyInt())).thenThrow(new ApiException("caído", 503));
        mvc.perform(get("/admin/rankings").sessionAttr(SesionUsuario.ATRIBUTO, administracion()))
                .andExpect(model().attribute("rankingNoDisponible", true));
    }

    @Test
    @DisplayName("Es solo de la administración")
    void soloAdministracion() throws Exception {
        mvc.perform(get("/admin/rankings")).andExpect(redirectedUrl("/admin/ingreso?sesion=expirada"));
        mvc.perform(get("/admin/rankings").sessionAttr(SesionUsuario.ATRIBUTO, new SesionUsuario("ana@example.com", "Ana",
                        SesionUsuario.DONANTE, "jwt", Instant.now().plusSeconds(600))))
                .andExpect(redirectedUrl("/donante/panel"));
    }
}
