package ar.utn.donatrack.frontend.autenticacion.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Las pantallas de ingreso y activación (Entrega 6), con la aplicación entera. */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Pantallas de ingreso y activación")
class PantallasDeIngresoTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("El ingreso ofrece activar la cuenta a quien la cargó la organización")
    void ingresoConEnlaceDeActivacion() throws Exception {
        mvc.perform(get("/login").param("rol", "ENTIDAD_BENEFICIARIA"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/activar?rol=ENTIDAD_BENEFICIARIA\"")));
    }

    @Test
    @DisplayName("Sin código pide el email; con el código del correo pide la contraseña")
    void activar() throws Exception {
        mvc.perform(get("/activar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mandarme el enlace")))
                .andExpect(content().string(not(containsString("Elegí tu contraseña"))));
        mvc.perform(get("/activar").param("codigo", "codigo-del-correo"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Elegí tu contraseña")))
                .andExpect(content().string(containsString("value=\"codigo-del-correo\"")));
    }

    @Test
    @DisplayName("La recepción y el mapa piden sesión; la recepción manda al ingreso de entidades")
    void recepcionYMapaPidenSesion() throws Exception {
        mvc.perform(get("/recepcion").param("donacion", "11111111-1111-1111-1111-111111111111"))
                .andExpect(redirectedUrl("/login?rol=ENTIDAD_BENEFICIARIA&sesion=expirada"));
        mvc.perform(get("/tracking/ruta/11111111-1111-1111-1111-111111111111"))
                .andExpect(redirectedUrl("/?sesion=expirada"));
        mvc.perform(get("/tracking/ruta/11111111-1111-1111-1111-111111111111/posicion"))
                .andExpect(status().isUnauthorized());
    }
}
