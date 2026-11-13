package ar.utn.donatrack.frontend.comun.config;

import ar.utn.donatrack.frontend.autenticacion.services.AuthClient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Entrega 6: "la primera vez no entra y al recargar sí". Un formulario abierto
 * desde antes de reiniciar el front o de que venza la sesión trae un token CSRF
 * vencido; en vez de un 403, vuelve a su pantalla con un aviso.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Formulario con el token vencido")
class FormularioVencidoTest {

    @Autowired MockMvc mvc;
    @MockitoBean AuthClient autenticacion;

    @Test
    @DisplayName("El ingreso vuelve a la pantalla de ingreso con el aviso y el email, sin intentar entrar")
    void ingreso() throws Exception {
        MvcResult vuelta = mvc.perform(post("/login").param("email", "ana@example.com").param("password", "x")
                        .param("rol", "DONANTE").param("_csrf", "token-de-una-sesion-vieja"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?rol=DONANTE"))
                .andReturn();
        verifyNoInteractions(autenticacion);

        // La pantalla de ingreso muestra el aviso y conserva el email, y trae un token nuevo.
        mvc.perform(get("/login").param("rol", "DONANTE").session((MockHttpSession) vuelta.getRequest().getSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(FormularioVencidoHandler.AVISO)))
                .andExpect(content().string(containsString("value=\"ana@example.com\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        mvc.perform(post("/admin/ingreso").param("email", "admin@donatrack.org").param("password", "x"))
                .andExpect(redirectedUrl("/admin/ingreso"));
    }

    @Test
    @DisplayName("Otro formulario vuelve a la página desde la que se mandó")
    void otroFormulario() throws Exception {
        MvcResult vuelta = mvc.perform(post("/donante/donar").header("Referer", "http://localhost/donante/donar?x=1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/donante/donar?x=1"))
                .andReturn();

        mvc.perform(get("/").session((MockHttpSession) vuelta.getRequest().getSession()))
                .andExpect(content().string(containsString(FormularioVencidoHandler.AVISO)));
    }

    @Test
    @DisplayName("Salir con la sesión vencida lleva al inicio sin aviso")
    void salir() throws Exception {
        mvc.perform(post("/salir"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("Un Referer de otro sitio no se usa para volver")
    void refererAjeno() {
        MockHttpServletRequest pedido = new MockHttpServletRequest("POST", "/donante/donar");
        pedido.setServerName("localhost");
        pedido.addHeader("Referer", "https://otro-sitio.example/robar");
        assertThat(FormularioVencidoHandler.destino(pedido)).isEqualTo("/");

        MockHttpServletRequest raro = new MockHttpServletRequest("POST", "/donante/donar");
        raro.addHeader("Referer", "//otro-sitio.example/robar");
        assertThat(FormularioVencidoHandler.destino(raro)).isEqualTo("/");

        MockHttpServletRequest rolRaro = new MockHttpServletRequest("POST", "/login");
        rolRaro.setParameter("rol", "DONANTE\r\nSet-Cookie: x");
        assertThat(FormularioVencidoHandler.destino(rolRaro)).isEqualTo("/login");
    }
}
