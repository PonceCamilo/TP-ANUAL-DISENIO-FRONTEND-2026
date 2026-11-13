package ar.utn.donatrack.frontend.donaciones.controller;

import ar.utn.donatrack.frontend.comun.config.FrontendProperties;
import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.donaciones.services.ImportacionCsvClient;
import ar.utn.donatrack.frontend.donaciones.services.Pagina;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.assertThat;

/** Lo que deciden los controllers de administración antes y después de llamar al servicio. */
@DisplayName("Controllers de administración de donaciones")
class AdminControllersTest {

    private final DonacionesApiFalsa api = new DonacionesApiFalsa();
    private final RedirectAttributesModelMap flash = new RedirectAttributesModelMap();
    private final ExtendedModelMap model = new ExtendedModelMap();

    private Object toast() {
        return flash.getFlashAttributes().get("toast");
    }

    private Object tipoDeToast() {
        return flash.getFlashAttributes().get("toastTipo");
    }

    @Nested
    @DisplayName("Donaciones")
    class Donaciones {

        private final AdminDonacionesController controller = new AdminDonacionesController(api);
        private final UUID id = UUID.randomUUID();

        @Test
        @DisplayName("El listado pasa los filtros al servicio y arma los links de página conservándolos")
        void listado() {
            api.pagina = new Pagina(List.of(Map.of("id", "d1")), 1, 3, 45);

            String vista = controller.listado("EN_DEPOSITO", "abrigo", "2026-10-01", "2026-10-31", true, 1, model);

            assertThat(vista).isEqualTo("donaciones/admin/donaciones");
            assertThat(api.filtrosPedidos).containsEntry("estado", "EN_DEPOSITO")
                    .containsEntry("desde", LocalDate.of(2026, 10, 1))
                    .containsEntry("vencidas", true);
            assertThat(api.paginaPedida).isEqualTo(1);
            assertThat(model.get("urlAnterior")).isEqualTo(
                    "/admin/donaciones?estado=EN_DEPOSITO&subcategoria=abrigo&desde=2026-10-01&hasta=2026-10-31&vencidas=true");
            assertThat((String) model.get("urlSiguiente")).endsWith("&page=2");
            assertThat(model.get("aviso")).isNull();
        }

        @Test
        @DisplayName("Un rango de fechas invertido no se manda: se avisa y se lista sin rango")
        void rangoInvertido() {
            controller.listado(null, null, "2026-11-10", "2026-10-01", false, 1, model);

            assertThat(api.filtrosPedidos.get("desde")).isNull();
            assertThat(api.filtrosPedidos.get("hasta")).isNull();
            assertThat((String) model.get("aviso")).contains("posterior");
        }

        @Test
        @DisplayName("Una fecha mal escrita se ignora con un aviso, en lugar de mostrar un error del servicio")
        void fechaInvalida() {
            controller.listado(null, null, "10/10/2026", null, false, 1, model);

            assertThat(api.filtrosPedidos.get("desde")).isNull();
            assertThat((String) model.get("aviso")).contains("no es válida");
        }

        @Test
        @DisplayName("El detalle ofrece solo los cambios de estado válidos y busca a quién donó")
        void detalle() {
            controller.detalle(id, model);

            assertThat(model.get("destinos")).isEqualTo(List.of("VENCIDA"));
            assertThat(model.get("nombreDonante")).isEqualTo("Ana Pérez");
            assertThat(model.get("entidad")).isNull();
        }

        @Test
        @DisplayName("Si la persona donante ya no existe, el detalle se muestra igual")
        void detalleSinDonante() {
            DonacionesApiFalsa sinDonante = new DonacionesApiFalsa() {
                @Override
                public Map<String, Object> donante(UUID donanteId) {
                    throw new ApiException("No encontramos lo que buscabas.", 404);
                }
            };

            new AdminDonacionesController(sinDonante).detalle(id, model);

            assertThat(model.get("donante")).isNull();
        }

        @Test
        @DisplayName("Una entrega fallida sin motivo no llega al servicio")
        void fallidaSinMotivo() {
            String destino = controller.cambiarEstado(id, "ENTREGA_FALLIDA", " ", flash);

            assertThat(api.acciones).isEmpty();
            assertThat(tipoDeToast()).isEqualTo("error");
            assertThat(destino).isEqualTo("redirect:/admin/donaciones/" + id);
        }

        @Test
        @DisplayName("Marcar como vencida vuelve al detalle con un aviso")
        void vencida() {
            String destino = controller.cambiarEstado(id, "VENCIDA", null, flash);

            assertThat(api.acciones).containsExactly("cambiarEstado VENCIDA");
            assertThat(tipoDeToast()).isEqualTo("ok");
            assertThat((String) toast()).contains("Vencida");
            assertThat(destino).isEqualTo("redirect:/admin/donaciones/" + id);
        }

        @Test
        @DisplayName("Si el servicio rechaza el cambio, se muestra su mensaje")
        void rechazo() {
            api.falla = new ApiException("Transición de estado no permitida", 422);

            controller.cambiarEstado(id, "EN_TRASLADO", null, flash);

            assertThat(tipoDeToast()).isEqualTo("error");
            assertThat(toast()).isEqualTo("Transición de estado no permitida");
        }

        @Test
        @DisplayName("La baja vuelve al listado")
        void baja() {
            assertThat(controller.eliminar(id, flash)).isEqualTo("redirect:/admin/donaciones");
            assertThat(api.acciones).containsExactly("eliminarDonacion");
        }

        @Test
        @DisplayName("Asignar avisa a qué entidad fue y vuelve al detalle")
        void asignar() {
            UUID entidad = UUID.randomUUID();

            String destino = controller.asignar(id, entidad, "Comedor Los Pibes", flash);

            assertThat(api.acciones).containsExactly("asignar " + entidad);
            assertThat((String) toast()).contains("Comedor Los Pibes");
            assertThat(destino).isEqualTo("redirect:/admin/donaciones/" + id);
        }

        @Test
        @DisplayName("Los candidatos llevan el nombre de cada algoritmo y solo se asigna si sigue en depósito")
        void candidatos() {
            api.candidatos = Map.of("rankings", List.of(Map.of("algoritmo", "COMPATIBILIDAD_SEMANTICA", "entidades", List.of())),
                    "coincidencias", List.of());

            controller.candidatos(id, model);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rankings = (List<Map<String, Object>>) model.get("rankings");
            assertThat(rankings.getFirst()).containsEntry("nombre", "Compatibilidad con sus necesidades");
            assertThat(model.get("asignable")).isEqualTo(true);
        }

        @Test
        @DisplayName("La bandeja de por asignar descarta las donaciones que ya salieron del depósito")
        void porAsignar() {
            UUID sigue = UUID.randomUUID();
            api.enDeposito = List.of(Map.of("id", sigue.toString(), "descripcion", "Frazadas"));
            api.pendientes = List.of(
                    Map.of("idDonacion", sigue.toString(), "rankings", List.of(), "coincidencias", List.of(Map.of("id", "e1"))),
                    Map.of("idDonacion", UUID.randomUUID().toString(), "rankings", List.of(), "coincidencias", List.of()));

            controller.porAsignar(model);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pendientes = (List<Map<String, Object>>) model.get("pendientes");
            assertThat(pendientes).hasSize(1);
            assertThat(pendientes.getFirst()).containsEntry("cantidadDeCandidatas", 1);
        }

        @Test
        @DisplayName("Los despachos muestran a qué entidad va cada donación")
        void despachos() {
            api.entidades = List.of(Map.of("id", "e1", "razonSocial", "Escuela 12"));
            api.paraDespachar = List.of(Map.of("id", "d1", "idEntidadBeneficiaria", "e1"));

            controller.despachos(model);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filas = (List<Map<String, Object>>) model.get("donaciones");
            assertThat(filas.getFirst()).containsEntry("razonSocial", "Escuela 12");
        }

        @Test
        @DisplayName("Despachar sin marcar ninguna no llega al servicio")
        void despacharSinMarcar() {
            controller.despachar(List.of(), null, flash);

            assertThat(api.acciones).isEmpty();
            assertThat(tipoDeToast()).isEqualTo("error");
        }

        @Test
        @DisplayName("Despachar manda las marcadas con el proveedor elegido")
        void despachar() {
            controller.despachar(List.of(UUID.randomUUID(), UUID.randomUUID()), "EXTERNA", flash);

            assertThat(api.acciones).containsExactly("despachar 2 EXTERNA");
            assertThat((String) toast()).contains("las 2 donaciones");
        }

        @Test
        @DisplayName("Registrar una donación para un email sin donante avisa que hay que registrarlo primero")
        void nuevaSinDonante() {
            String destino = controller.registrar("nadie@example.com", "Camperas", "Abrigo", null, null, 2,
                    "unidades", true, null, null, flash);

            assertThat(api.acciones).isEmpty();
            assertThat((String) toast()).contains("nadie@example.com");
            assertThat(destino).isEqualTo("redirect:/admin/donaciones/nueva");
        }

        @Test
        @DisplayName("Registrar una donación la crea a nombre del donante encontrado, con su foto")
        void nueva() {
            api.donanteEncontrado = UUID.randomUUID();

            String destino = controller.registrar(" ana@example.com ", "Camperas", "Otro", "Equipo de pesca", null, 2,
                    "unidades", false, "2027-01-01", "https://fotos.org/equipo.jpg", flash);

            assertThat(api.cuerpoEnviado).containsEntry("idDonante", api.donanteEncontrado);
            @SuppressWarnings("unchecked")
            Map<String, Object> bien = ((List<Map<String, Object>>) api.cuerpoEnviado.get("bienes")).getFirst();
            // Con vencimiento es perecedero y va sin esNuevo: servicio-donaciones rechaza los dos datos juntos.
            assertThat(bien).containsEntry("subcategoria", "Equipo de pesca")
                    .containsEntry("fechaVencimiento", "2027-01-01")
                    .containsEntry("foto", "https://fotos.org/equipo.jpg")
                    .doesNotContainKey("esNuevo");
            assertThat(destino).isEqualTo("redirect:/admin/donaciones");
        }
    }

    @Nested
    @DisplayName("Donantes")
    class Donantes {

        private final ImportacionCsvClientFalso importacion = new ImportacionCsvClientFalso();
        private final AdminDonantesController controller = new AdminDonantesController(api, importacion);

        @Test
        @DisplayName("El listado muestra el nombre de cada persona y los estados a los que puede pasar")
        void listado() {
            api.pagina = new Pagina(List.of(
                    Map.of("id", "1", "nombre", "Ana", "apellido", "Pérez", "estado", "ACTIVO"),
                    Map.of("id", "2", "razonSocial", "Fundación Sur", "estado", "BLOQUEADO")), 1, 1, 2);

            controller.listado(null, null, 1, model);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> donantes = (List<Map<String, Object>>) model.get("donantes");
            assertThat(donantes.get(0)).containsEntry("nombreVisible", "Ana Pérez")
                    .containsEntry("destinos", List.of("INACTIVO", "BLOQUEADO"));
            assertThat(donantes.get(1)).containsEntry("nombreVisible", "Fundación Sur")
                    .containsEntry("destinos", List.of("ACTIVO"));
        }

        @Test
        @DisplayName("Bloquear sin motivo no llega al servicio")
        void bloquearSinMotivo() {
            controller.cambiarEstado(UUID.randomUUID(), "BLOQUEADO", "", flash);

            assertThat(api.acciones).isEmpty();
            assertThat(tipoDeToast()).isEqualTo("error");
        }

        @Test
        @DisplayName("Activar a una persona avisa cómo quedó")
        void activar() {
            controller.cambiarEstado(UUID.randomUUID(), "ACTIVO", null, flash);

            assertThat(api.acciones).containsExactly("cambiarEstadoDonante ACTIVO");
            assertThat((String) toast()).contains("activa");
        }

        @Test
        @DisplayName("Si el servicio no deja eliminar (tiene donaciones), se muestra el motivo")
        void eliminarConDonaciones() {
            api.falla = new ApiException("La persona tiene donaciones registradas", 409);

            controller.eliminar(UUID.randomUUID(), flash);

            assertThat(toast()).isEqualTo("La persona tiene donaciones registradas");
        }

        @Test
        @DisplayName("El alta de una persona manda sus datos y el email como contacto predeterminado")
        void altaPersona() {
            String destino = controller.registrar("HUMANA", " ana@example.com ", "DNI", "30111222", "Ana", "Pérez",
                    "1990-05-01", "", null, null, null, "Mitre", 100, "1870", "Avellaneda", "Buenos Aires", flash);

            assertThat(api.cuerpoEnviado).containsEntry("tipo", "HUMANA")
                    .containsEntry("email", "ana@example.com")
                    .containsEntry("genero", "PREFIERO_NO_DECIR")
                    .containsEntry("medioContactoPredeterminado", Map.of("tipo", "EMAIL", "valor", "ana@example.com"));
            assertThat(destino).isEqualTo("redirect:/admin/donantes");
        }

        @Test
        @DisplayName("El alta de una organización pide razón social, rubro y tipo")
        void altaOrganizacionIncompleta() {
            String destino = controller.registrar("JURIDICA", "info@sur.org", "CUIT", "30712345678", null, null,
                    null, null, "Fundación Sur", "", "ONG", "Mitre", 100, "1870", "Avellaneda", "Buenos Aires", flash);

            assertThat(api.acciones).isEmpty();
            assertThat(destino).isEqualTo("redirect:/admin/donantes/nuevo");
        }

        @Test
        @DisplayName("El alta de una organización manda sus datos de organización")
        void altaOrganizacion() {
            controller.registrar("JURIDICA", "info@sur.org", "CUIT", "30712345678", null, null,
                    null, null, "Fundación Sur", "Alimentos", "ONG", "Mitre", 100, "1870", "Avellaneda", "Buenos Aires", flash);

            assertThat(api.cuerpoEnviado).containsEntry("tipo", "JURIDICA")
                    .containsEntry("razonSocial", "Fundación Sur")
                    .containsEntry("tipoOrganizacion", "ONG")
                    .doesNotContainKey("fechaNacimiento");
        }

        @Test
        @DisplayName("Importar sin archivo avisa sin llamar al servicio")
        void importarSinArchivo() {
            controller.subir(new MockMultipartFile("archivo", new byte[0]), new MockHttpSession(), flash);

            assertThat(importacion.subidas).isZero();
            assertThat(tipoDeToast()).isEqualTo("error");
        }

        @Test
        @DisplayName("Importar sube el archivo con el token de la sesión y deja el resumen para mostrarlo")
        void importar() {
            MockHttpSession sesion = new MockHttpSession();
            sesion.setAttribute(SesionUsuario.ATRIBUTO, new SesionUsuario("admin@donatrack.org", "Admin",
                    "ADMINISTRADOR", "jwt-admin", Instant.now().plusSeconds(600)));
            MockMultipartFile csv = new MockMultipartFile("archivo", "donantes.csv", "text/csv",
                    "TipoPersona,TipoDoc,Documento,Nombre,Email\n".getBytes());

            String destino = controller.subir(csv, sesion, flash);

            assertThat(importacion.tokenRecibido).isEqualTo("jwt-admin");
            assertThat(importacion.nombreRecibido).isEqualTo("donantes.csv");
            assertThat(flash.getFlashAttributes().get("resumen")).isEqualTo(Map.of("creadas", 3));
            assertThat(destino).isEqualTo("redirect:/admin/donantes/importar");
        }
    }

    @Nested
    @DisplayName("Entidades")
    class Entidades {

        private final AdminEntidadesController controller = new AdminEntidadesController(api);

        @Test
        @DisplayName("La búsqueda por email se conserva en los links de página")
        void listado() {
            api.pagina = new Pagina(List.of(Map.of("id", "e1")), 1, 2, 25);

            controller.listado("ana+comedor@example.com", 1, model);

            assertThat((String) model.get("urlSiguiente")).isEqualTo("/admin/entidades?email=ana%2Bcomedor%40example.com&page=2");
        }

        @Test
        @DisplayName("Entrega 6: la administración registra una entidad con su ubicación para el mapa")
        @SuppressWarnings("unchecked")
        void registrarConUbicacion() {
            String destino = controller.crear("Comedor Los Pibes", " contacto@comedor.org ", "Ana", "Pérez",
                    "Medrano", 951, "C1179", "CABA", "Buenos Aires", "", "-34.6037", "-58.3816", flash);

            assertThat(destino).isEqualTo("redirect:/admin/entidades");
            assertThat(api.acciones).contains("crearEntidad");
            Map<String, Object> direccion = (Map<String, Object>) api.cuerpoEnviado.get("direccion");
            assertThat(direccion).containsEntry("latitud", -34.6037).containsEntry("longitud", -58.3816);
            assertThat((List<Map<String, Object>>) api.cuerpoEnviado.get("representantes"))
                    .first().satisfies(r -> assertThat(r).containsEntry("email", "contacto@comedor.org"));
        }

        @Test
        @DisplayName("Una sola coordenada no se manda: vuelve al formulario con el aviso")
        void coordenadaIncompleta() {
            String destino = controller.crear("Comedor", "c@comedor.org", "Ana", "Pérez", "Medrano", 951, "C1179",
                    "CABA", "Buenos Aires", null, "-34.6", "", flash);

            assertThat(destino).isEqualTo("redirect:/admin/entidades/nueva");
            assertThat(tipoDeToast()).isEqualTo("error");
            assertThat(api.acciones).doesNotContain("crearEntidad");
        }

        @Test
        @DisplayName("Si la entidad ya recibió donaciones, el motivo del servicio se muestra")
        void eliminarConDonaciones() {
            api.falla = new ApiException("La entidad tiene donaciones asignadas", 409);

            controller.eliminar(UUID.randomUUID(), flash);

            assertThat(toast()).isEqualTo("La entidad tiene donaciones asignadas");
        }
    }

    /** Sin red: anota lo que se subió y devuelve un resumen fijo. */
    static class ImportacionCsvClientFalso extends ImportacionCsvClient {
        int subidas;
        String tokenRecibido;
        String nombreRecibido;

        ImportacionCsvClientFalso() {
            super(new FrontendProperties(new FrontendProperties.Servicios("http://donaciones", null, null, null, null), false));
        }

        @Override
        public Map<String, Object> importarDonantes(String nombreArchivo, byte[] contenido, String token) {
            subidas++;
            tokenRecibido = token;
            nombreRecibido = nombreArchivo;
            return Map.of("creadas", 3);
        }
    }
}
