package ar.utn.donatrack.frontend.donaciones.services;

import ar.utn.donatrack.frontend.comun.clientes.ApiClient;
import ar.utn.donatrack.frontend.comun.config.FrontendProperties;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Qué le pide el front a servicio-donaciones.
 *
 * Los listados de ese servicio (donantes, entidades y donaciones) vienen
 * paginados: {"contenido": [...], "totalPaginas": N}. Si se leían como un array
 * fallaban todas las pantallas con sesión.
 *
 * El ApiClient se reemplaza por un doble escrito a mano en lugar de un mock,
 * para no depender de qué versiones de Java soporta la librería de mocks.
 */
@DisplayName("DonacionesApi - llamadas a servicio-donaciones")
class DonacionesApiTest {

    private static final String BASE = "http://donaciones";

    /**
     * Responde cada pedido con una página armada a partir de lo que se le cargó
     * para esa URL, y anota qué se le pidió.
     */
    private static class ApiDePrueba extends ApiClient {
        final Map<String, List<Map<String, Object>>> elementos = new HashMap<>();
        final List<String> urlsPedidas = new ArrayList<>();
        final List<Object> variablesPedidas = new ArrayList<>();
        int pedidosComoLista = 0;
        /** Páginas que dice tener el servicio; si es null, las calcula. */
        Integer totalPaginasForzado = null;
        /** Si no es null, se devuelve tal cual en lugar de armar una página. */
        Map<String, Object> respuestaFija = null;

        @Override
        public Map<String, Object> objeto(String url, Object... variables) {
            urlsPedidas.add(url);
            variablesPedidas.addAll(List.of(variables));
            if (respuestaFija != null) {
                return respuestaFija;
            }
            String ruta = url.replaceAll("[?&]page=\\d+&size=\\d+$", "");
            int pagina = Integer.parseInt(url.replaceAll(".*[?&]page=(\\d+)&size=\\d+$", "$1"));
            List<Map<String, Object>> todos = elementos.getOrDefault(ruta, List.of());
            int tamanio = DonacionesApi.TAMANIO_DE_PAGINA;
            List<Map<String, Object>> contenido = totalPaginasForzado != null
                    ? todos
                    : todos.subList(Math.min(pagina * tamanio, todos.size()), Math.min((pagina + 1) * tamanio, todos.size()));
            int totalPaginas = totalPaginasForzado != null
                    ? totalPaginasForzado
                    : (int) Math.ceil((double) todos.size() / tamanio);
            return Map.of("contenido", contenido, "totalPaginas", totalPaginas);
        }

        @Override
        public List<Map<String, Object>> lista(String url, Object... variables) {
            pedidosComoLista++;
            return List.of();
        }

        /** Lo que se mandó con enviar(): "MÉTODO url", el cuerpo y las variables. */
        final List<String> envios = new ArrayList<>();
        final List<Object> cuerpos = new ArrayList<>();

        @Override
        public void enviar(org.springframework.http.HttpMethod metodo, String url, Object cuerpo, Object... variables) {
            envios.add(metodo.name() + " " + url);
            cuerpos.add(cuerpo);
            variablesPedidas.addAll(List.of(variables));
        }
    }

    private final ApiDePrueba api = new ApiDePrueba();
    private final DonacionesApi donaciones = new DonacionesApi(api, new FrontendProperties(
            new FrontendProperties.Servicios(BASE, null, null, null, null), false));

    private Map<String, Object> conId(Object id) {
        return Map.of("id", id.toString());
    }

    @Nested
    @DisplayName("Lectura de listados paginados")
    class Paginacion {

        @Test
        @DisplayName("Lee los elementos de `contenido`, no la página entera")
        void leeElContenido() {
            api.elementos.put(BASE + "/entidades", List.of(conId("a"), conId("b")));

            assertThat(donaciones.entidades()).extracting(e -> e.get("id")).containsExactly("a", "b");
            assertThat(api.urlsPedidas).containsExactly(BASE + "/entidades?page=0&size=100");
        }

        @Test
        @DisplayName("Si hay más de una página las recorre todas y junta los elementos")
        void recorreTodasLasPaginas() {
            List<Map<String, Object>> muchas = new ArrayList<>();
            for (int i = 0; i < 150; i++) {
                muchas.add(conId(i));
            }
            api.elementos.put(BASE + "/entidades", muchas);

            assertThat(donaciones.entidades()).hasSize(150);
            assertThat(api.urlsPedidas).hasSize(2);
        }

        @Test
        @DisplayName("Conserva los filtros de la URL al agregar la página")
        void conservaFiltros() {
            donaciones.donacionesEnDeposito();

            assertThat(api.urlsPedidas).containsExactly(BASE + "/donaciones?estado=EN_DEPOSITO&page=0&size=100");
        }

        @Test
        @DisplayName("Un listado sin resultados hace un solo pedido y devuelve una lista vacía")
        void listadoVacio() {
            assertThat(donaciones.entidades()).isEmpty();
            assertThat(api.urlsPedidas).hasSize(1);
        }

        @Test
        @DisplayName("Corta en el tope de páginas aunque el servicio diga que hay más")
        void respetaElTope() {
            api.elementos.put(BASE + "/entidades", List.of(conId("x")));
            api.totalPaginasForzado = 9999;

            assertThat(donaciones.entidades()).hasSize(DonacionesApi.MAXIMO_DE_PAGINAS);
            assertThat(api.urlsPedidas).hasSize(DonacionesApi.MAXIMO_DE_PAGINAS);
        }

        @Test
        @DisplayName("Ningún listado de donaciones se pide como array")
        void nuncaUsaLista() {
            donaciones.entidades();
            donaciones.donacionesEnDeposito();
            donaciones.donacionesDe(UUID.randomUUID());
            donaciones.donacionesHaciaEntidad(UUID.randomUUID());

            assertThat(api.pedidosComoLista).isZero();
        }
    }

    @Nested
    @DisplayName("Identidad de quien inició sesión")
    class Identidad {

        @Test
        @DisplayName("Busca al donante por email, sin recorrer el listado completo")
        void buscaDonantePorEmail() {
            UUID id = UUID.randomUUID();
            api.elementos.put(BASE + "/donantes?email={email}", List.of(conId(id)));

            assertThat(donaciones.buscarDonante("juan@example.com")).isEqualTo(id);
            assertThat(api.variablesPedidas).containsExactly("juan@example.com");
        }

        @Test
        @DisplayName("Si nadie tiene ese email devuelve null: la persona todavía no se registró")
        void donanteSinRegistrar() {
            assertThat(donaciones.buscarDonante("nueva@example.com")).isNull();
        }

        @Test
        @DisplayName("Busca la entidad por el email de su contacto o representante")
        void buscaEntidadPorEmail() {
            UUID id = UUID.randomUUID();
            api.elementos.put(BASE + "/entidades?email={email}", List.of(conId(id)));

            assertThat(donaciones.buscarEntidad("ana@comedor.org")).isEqualTo(id);
            assertThat(api.variablesPedidas).containsExactly("ana@comedor.org");
        }

        @Test
        @DisplayName("Si el email figura en varias entidades toma la primera que devuelve el servicio")
        void variasEntidadesTomaLaPrimera() {
            UUID primera = UUID.randomUUID();
            api.elementos.put(BASE + "/entidades?email={email}", List.of(conId(primera), conId(UUID.randomUUID())));

            assertThat(donaciones.buscarEntidad("ana@comedor.org")).isEqualTo(primera);
        }

        @Test
        @DisplayName("resolverIdentidad() no vuelve a consultar si la sesión ya conoce al donante")
        void noRepiteLaBusqueda() {
            SesionUsuario sesion = new SesionUsuario("juan@example.com", "Juan", SesionUsuario.DONANTE,
                    "token", Instant.now().plusSeconds(60));
            sesion.setDonanteId(UUID.randomUUID());

            donaciones.resolverIdentidad(sesion);

            assertThat(api.urlsPedidas).isEmpty();
        }
    }

    @Nested
    @DisplayName("Administración")
    class Administracion {

        @Test
        @DisplayName("Pide una sola página con los filtros que tienen valor, como variables de la URL")
        void paginaConFiltros() {
            api.respuestaFija = Map.of("contenido", List.of(conId("d1")), "pagina", 2, "totalPaginas", 5, "totalElementos", 81);
            Map<String, Object> filtros = new java.util.LinkedHashMap<>();
            filtros.put("estado", "EN_DEPOSITO");
            filtros.put("subcategoria", "  ");
            filtros.put("desde", LocalDate.of(2026, 10, 10));
            filtros.put("vencidas", null);

            Pagina pagina = donaciones.paginaDeDonaciones(filtros, 2);

            assertThat(api.urlsPedidas).containsExactly(BASE + "/donaciones?estado={estado}&desde={desde}&page=2&size=20");
            assertThat(api.variablesPedidas).containsExactly("EN_DEPOSITO", LocalDate.of(2026, 10, 10));
            assertThat(pagina.getContenido()).hasSize(1);
            assertThat(pagina.getNumero()).isEqualTo(2);
            assertThat(pagina.getTotalElementos()).isEqualTo(81);
            assertThat(pagina.isHayAnterior()).isTrue();
            assertThat(pagina.isHaySiguiente()).isTrue();
        }

        @Test
        @DisplayName("Sin filtros pide la página sin parámetros extra; un número negativo se toma como 0")
        void paginaSinFiltros() {
            api.respuestaFija = Map.of("contenido", List.of(), "pagina", 0, "totalPaginas", 0, "totalElementos", 0);

            Pagina pagina = donaciones.paginaDeEntidades(null, -3);

            assertThat(api.urlsPedidas).containsExactly(BASE + "/entidades?page=0&size=20");
            assertThat(pagina.isVacia()).isTrue();
            assertThat(pagina.isHayAnterior()).isFalse();
            assertThat(pagina.isHaySiguiente()).isFalse();
        }

        @Test
        @DisplayName("Los donantes se filtran por estado y email")
        void paginaDeDonantes() {
            api.respuestaFija = Map.of("contenido", List.of());

            donaciones.paginaDeDonantes("BLOQUEADO", " ana@example.com ", 0);

            assertThat(api.urlsPedidas.getFirst()).contains("estado={estado}").contains("email={email}");
            assertThat(api.variablesPedidas).containsExactlyInAnyOrder("BLOQUEADO", "ana@example.com");
        }

        @Test
        @DisplayName("Cambiar el estado manda el estado y la justificación; una justificación en blanco va como null")
        void cambiarEstado() {
            UUID id = UUID.randomUUID();

            donaciones.cambiarEstado(id, "VENCIDA", "  ");

            assertThat(api.envios).containsExactly("PATCH " + BASE + "/donaciones/{id}/estado");
            Map<?, ?> cuerpo = (Map<?, ?>) api.cuerpos.getFirst();
            assertThat(cuerpo.get("estado")).isEqualTo("VENCIDA");
            assertThat(cuerpo.get("justificacion")).isNull();
            assertThat(api.variablesPedidas).containsExactly(id);
        }

        @Test
        @DisplayName("Despachar manda los ids y deja que el broker elija si no se indica proveedor")
        void despachar() {
            List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());

            donaciones.despachar(ids, "");

            assertThat(api.envios).containsExactly("POST " + BASE + "/donaciones/envios");
            Map<?, ?> cuerpo = (Map<?, ?>) api.cuerpos.getFirst();
            assertThat(cuerpo.get("idsDonaciones")).isEqualTo(ids);
            assertThat(cuerpo.get("proveedor")).isNull();
        }

        @Test
        @DisplayName("Las bajas usan DELETE sobre el recurso que corresponde")
        void bajas() {
            donaciones.eliminarDonacion(UUID.randomUUID());
            donaciones.eliminarDonante(UUID.randomUUID());
            donaciones.eliminarEntidad(UUID.randomUUID());

            assertThat(api.envios).containsExactly(
                    "DELETE " + BASE + "/donaciones/{id}",
                    "DELETE " + BASE + "/donantes/{id}",
                    "DELETE " + BASE + "/entidades/{id}");
        }

        @Test
        @DisplayName("Bloquear a un donante manda el motivo")
        void bloquearDonante() {
            donaciones.cambiarEstadoDonante(UUID.randomUUID(), "BLOQUEADO", " fraude ");

            assertThat(api.envios).containsExactly("PATCH " + BASE + "/donantes/{id}/estado");
            assertThat(((Map<?, ?>) api.cuerpos.getFirst()).get("justificacion")).isEqualTo("fraude");
        }
    }

    @Nested
    @DisplayName("Listados de donaciones")
    class Listados {

        @Test
        @DisplayName("Las donaciones de un donante se piden filtradas y salen de la más nueva a la más vieja")
        void donacionesDelDonante() {
            UUID idDonante = UUID.randomUUID();
            api.elementos.put(BASE + "/donaciones?idDonante={id}", List.of(
                    Map.of("id", "vieja", "fechaDonacion", "2026-10-01T10:00:00"),
                    Map.of("id", "nueva", "fechaDonacion", "2026-11-05T10:00:00")));

            assertThat(donaciones.donacionesDe(idDonante)).extracting(d -> d.get("id")).containsExactly("nueva", "vieja");
            assertThat(api.variablesPedidas).containsExactly(idDonante);
        }

        @Test
        @DisplayName("La cantidad desde una fecha pide una sola página mínima y usa el total del servicio")
        void cantidadDesdeUnaFecha() {
            LocalDate desde = LocalDate.of(2026, 10, 10);
            api.respuestaFija = Map.of("contenido", List.of(), "totalElementos", 42, "totalPaginas", 42);

            assertThat(donaciones.cantidadDeDonacionesDesde(desde)).isEqualTo(42);
            assertThat(api.urlsPedidas).containsExactly(BASE + "/donaciones?desde={desde}&page=0&size=1");
            assertThat(api.variablesPedidas).containsExactly(desde);
        }

        @Test
        @DisplayName("Si el servicio no informa el total, la cantidad es 0")
        void cantidadSinTotal() {
            api.respuestaFija = Map.of();

            assertThat(donaciones.cantidadDeDonacionesDesde(LocalDate.of(2026, 10, 10))).isZero();
        }

        @Test
        @DisplayName("Las donaciones hacia una entidad las filtra el servicio, no el front")
        void donacionesHaciaEntidad() {
            UUID idEntidad = UUID.randomUUID();
            api.elementos.put(BASE + "/donaciones?idEntidad={id}",
                    List.of(Map.of("id", "d1", "fechaDonacion", "2026-11-05T10:00:00")));

            assertThat(donaciones.donacionesHaciaEntidad(idEntidad)).hasSize(1);
            assertThat(api.variablesPedidas).containsExactly(idEntidad);
        }
    }
}
