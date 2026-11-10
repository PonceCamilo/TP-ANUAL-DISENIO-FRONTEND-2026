package ar.utn.donatrack.frontend.donaciones.controller;

import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.donaciones.services.Pagina;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las pantallas de administración se renderizan de verdad (Thymeleaf incluido)
 * con datos con la forma que devuelve servicio-donaciones. Un error en una
 * plantilla se detecta acá y no recién en el navegador.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Pantallas de administración de donaciones")
class PantallasAdminTest {

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

    private final SesionUsuario admin = new SesionUsuario("admin@donatrack.org", "Admin Uno",
            "ADMINISTRADOR", "jwt", Instant.now().plusSeconds(600));

    @BeforeEach
    void datosBase() {
        api.pagina = new Pagina(List.of(), 0, 0, 0);
        api.donacion = DonacionesApiFalsa.donacion(UUID.randomUUID(), "EN_DEPOSITO");
        api.candidatos = Map.of("rankings", List.of(), "coincidencias", List.of());
        api.pendientes = List.of();
        api.enDeposito = List.of();
        api.paraDespachar = List.of();
        api.entidades = List.of();
    }

    private MockHttpServletRequestBuilder comoAdmin(String ruta) {
        return get(ruta).sessionAttr(SesionUsuario.ATRIBUTO, admin);
    }

    @Nested
    @DisplayName("Acceso")
    class Acceso {

        @Test
        @DisplayName("Sin sesión no se ve ninguna pantalla de administración")
        void sinSesion() throws Exception {
            mvc.perform(get("/admin/donaciones")).andExpect(redirectedUrl("/?sesion=expirada"));
            mvc.perform(get("/admin/donantes/importar")).andExpect(redirectedUrl("/?sesion=expirada"));
            mvc.perform(get("/admin/entidades")).andExpect(redirectedUrl("/?sesion=expirada"));
        }

        @Test
        @DisplayName("Un donante que escribe la dirección a mano vuelve al inicio")
        void donante() throws Exception {
            SesionUsuario donante = new SesionUsuario("ana@example.com", "Ana", "DONANTE", "jwt",
                    Instant.now().plusSeconds(600));
            mvc.perform(get("/admin/donaciones/despachos").sessionAttr(SesionUsuario.ATRIBUTO, donante))
                    .andExpect(redirectedUrl("/"));
        }
    }

    @Nested
    @DisplayName("Donaciones")
    class Donaciones {

        @Test
        @DisplayName("Listado con resultados, filtros y paginador")
        void listado() throws Exception {
            api.pagina = new Pagina(List.of(api.donacion), 0, 2, 21);

            mvc.perform(comoAdmin("/admin/donaciones").param("estado", "EN_DEPOSITO"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Camperas de invierno")))
                    .andExpect(content().string(containsString("21 donaciones")))
                    .andExpect(content().string(containsString("Página 1 de 2")))
                    .andExpect(content().string(containsString("aria-current=\"page\"")));
        }

        @Test
        @DisplayName("Listado vacío con su estado vacío")
        void listadoVacio() throws Exception {
            mvc.perform(comoAdmin("/admin/donaciones"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("No hay donaciones para mostrar")));
        }

        @Test
        @DisplayName("Detalle en depósito: elegir entidad, marcar vencida y dar de baja")
        void detalleEnDeposito() throws Exception {
            mvc.perform(comoAdmin("/admin/donaciones/" + api.donacion.get("id")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Elegir entidad")))
                    .andExpect(content().string(containsString("value=\"VENCIDA\"")))
                    .andExpect(content().string(containsString("Ana Pérez")))
                    .andExpect(content().string(containsString("Dar de baja")))
                    .andExpect(content().string(not(containsString("Despachar a logística con"))));
        }

        @Test
        @DisplayName("Detalle asignado: se despacha a logística y muestra la entidad")
        void detalleAsignada() throws Exception {
            api.donacion = DonacionesApiFalsa.donacion(UUID.randomUUID(), "ASIGNACION_REALIZADA");
            api.donacion.put("idEntidadBeneficiaria", api.entidad.get("id"));

            mvc.perform(comoAdmin("/admin/donaciones/" + api.donacion.get("id")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Despachar a logística con")))
                    .andExpect(content().string(containsString("Comedor Los Pibes")))
                    .andExpect(content().string(not(containsString("Cambiar el estado a"))));
        }

        @Test
        @DisplayName("Candidatos agrupados por algoritmo, con el puesto y el botón para asignar")
        void candidatos() throws Exception {
            api.candidatos = Map.of(
                    "rankings", List.of(Map.of("algoritmo", "PRIORIDAD_SUBATENDIDOS",
                            "entidades", List.of(Map.of("id", UUID.randomUUID().toString(), "razonSocial", "Hogar Belén")))),
                    "coincidencias", List.of());

            mvc.perform(comoAdmin("/admin/donaciones/" + api.donacion.get("id") + "/candidatos"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Prioridad a las menos atendidas")))
                    .andExpect(content().string(containsString("Hogar Belén")))
                    .andExpect(content().string(containsString("Asignar a Hogar Belén")));
        }

        @Test
        @DisplayName("Bandeja de por asignar")
        void porAsignar() throws Exception {
            String id = UUID.randomUUID().toString();
            api.enDeposito = List.of(Map.of("id", id, "descripcion", "Frazadas", "subcategoria", "Abrigo"));
            api.pendientes = List.of(Map.of("idDonacion", id, "fechaCalculo", "2026-11-10T03:00:00",
                    "rankings", List.of(), "coincidencias", List.of(Map.of("id", "e1"), Map.of("id", "e2"))));

            mvc.perform(comoAdmin("/admin/donaciones/por-asignar"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Frazadas")))
                    .andExpect(content().string(containsString("2 entidades sugeridas")));
        }

        @Test
        @DisplayName("Despachos con casillas para elegir y el proveedor")
        void despachos() throws Exception {
            api.entidades = List.of(Map.of("id", "e1", "razonSocial", "Escuela 12"));
            api.paraDespachar = List.of(Map.of("id", UUID.randomUUID().toString(), "descripcion", "Útiles",
                    "subcategoria", "Útiles escolares", "idEntidadBeneficiaria", "e1"));

            mvc.perform(comoAdmin("/admin/donaciones/despachos"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("type=\"checkbox\" name=\"ids\"")))
                    .andExpect(content().string(containsString("Escuela 12")));
        }

        @Test
        @DisplayName("Formulario para registrar una donación a nombre de un donante")
        void nueva() throws Exception {
            mvc.perform(comoAdmin("/admin/donaciones/nueva"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("name=\"emailDonante\"")))
                    .andExpect(content().string(containsString("name=\"_csrf\"")));
        }
    }

    @Nested
    @DisplayName("Donantes y entidades")
    class DonantesYEntidades {

        @Test
        @DisplayName("Listado de donantes con nombre, estado y acciones")
        void donantes() throws Exception {
            api.pagina = new Pagina(List.of(
                    Map.of("id", UUID.randomUUID().toString(), "nombre", "Ana", "apellido", "Pérez", "email", "ana@example.com",
                            "tipoDocumento", "DNI", "numeroDocumento", "30111222", "estado", "ACTIVO"),
                    Map.of("id", UUID.randomUUID().toString(), "razonSocial", "Fundación Sur", "email", "info@sur.org",
                            "tipoDocumento", "CUIT", "numeroDocumento", "30712345678", "estado", "BLOQUEADO")), 0, 1, 2);

            mvc.perform(comoAdmin("/admin/donantes"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Ana Pérez")))
                    .andExpect(content().string(containsString("Fundación Sur")))
                    .andExpect(content().string(containsString("Bloqueada")))
                    .andExpect(content().string(containsString("Cambiar estado")));
        }

        @Test
        @DisplayName("Alta de donante con los datos de persona y de organización")
        void altaDonante() throws Exception {
            mvc.perform(comoAdmin("/admin/donantes/nuevo"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("data-muestra-si=\"tipo=JURIDICA\"")))
                    .andExpect(content().string(containsString("value=\"PASAPORTE\"")));
        }

        @Test
        @DisplayName("Importación: el formulario y el resumen con las filas que fallaron")
        void importar() throws Exception {
            Map<String, Object> resumen = Map.of("totalProcesadas", 4, "creadas", 2, "actualizadas", 1, "conError", 1,
                    "errores", List.of(Map.of("numeroLinea", 3, "email", "mal@ejemplo", "detalle", "Email inválido")),
                    "erroresOmitidos", 0);

            mvc.perform(comoAdmin("/admin/donantes/importar").flashAttr("resumen", resumen))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")))
                    .andExpect(content().string(containsString("Línea 3")))
                    .andExpect(content().string(containsString("Email inválido")));
        }

        @Test
        @DisplayName("Listado de entidades con su localidad y la baja")
        void entidades() throws Exception {
            api.pagina = new Pagina(List.of(Map.of("id", UUID.randomUUID().toString(), "razonSocial", "Comedor Los Pibes",
                    "direccion", Map.of("localidad", Map.of("nombre", "Lanús")), "campanias", List.of(Map.of()))), 0, 1, 1);

            mvc.perform(comoAdmin("/admin/entidades"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Comedor Los Pibes")))
                    .andExpect(content().string(containsString("Lanús")))
                    .andExpect(content().string(containsString("1 campaña")))
                    .andExpect(content().string(containsString("Eliminar Comedor Los Pibes")));
        }
    }
}
