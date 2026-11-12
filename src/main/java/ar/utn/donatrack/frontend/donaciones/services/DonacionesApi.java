package ar.utn.donatrack.frontend.donaciones.services;

import ar.utn.donatrack.frontend.comun.clientes.ApiClient;
import ar.utn.donatrack.frontend.comun.config.FrontendProperties;
import ar.utn.donatrack.frontend.comun.util.Mapas;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

/**
 * Cliente REST de servicio-donaciones: donantes, entidades, campañas,
 * necesidades y donaciones. Acá no hay reglas de negocio.
 */
@Service
public class DonacionesApi {

    /** El máximo que acepta servicio-donaciones por página: menos viajes para traer un listado. */
    static final int TAMANIO_DE_PAGINA = 100;
    /** Tope de viajes por listado, para que un listado enorme no cuelgue una pantalla. */
    static final int MAXIMO_DE_PAGINAS = 10;
    /** Elementos por página en las pantallas de administración. */
    public static final int TAMANIO_ADMINISTRACION = 20;

    private final ApiClient api;
    private final String donaciones;

    public DonacionesApi(ApiClient api, FrontendProperties props) {
        this.api = api;
        this.donaciones = props.servicios().donaciones();
    }

    /**
     * Los listados de servicio-donaciones (donantes, entidades y donaciones)
     * vienen paginados: la respuesta es un objeto con `contenido` y
     * `totalPaginas`, no un array, así que no sirve ApiClient.lista(). Recorre
     * las páginas y devuelve todos los elementos juntos.
     */
    List<Map<String, Object>> paginado(String url, Object... variables) {
        String separador = url.contains("?") ? "&" : "?";
        List<Map<String, Object>> elementos = new ArrayList<>();
        for (int pagina = 0; pagina < MAXIMO_DE_PAGINAS; pagina++) {
            Map<String, Object> respuesta =
                    api.objeto(url + separador + "page=" + pagina + "&size=" + TAMANIO_DE_PAGINA, variables);
            elementos.addAll(Mapas.lista(respuesta.get("contenido")));
            int totalPaginas = respuesta.get("totalPaginas") instanceof Number n ? n.intValue() : 0;
            if (pagina + 1 >= totalPaginas) {
                break;
            }
        }
        return elementos;
    }

    /**
     * La persona donante con ese email, o null si no está registrada. Lo usa la
     * administración para registrar una donación a nombre de alguien (requisito
     * 15): la búsqueda por email es solo de la administración en servicio-donaciones.
     * Las pantallas de donante y entidad no la usan: su perfil sale del perfilId
     * del token (SesionUsuario).
     */
    public UUID buscarDonante(String email) {
        List<Map<String, Object>> encontrados = paginado(donaciones + "/donantes?email={email}", email);
        return encontrados.isEmpty() ? null : UUID.fromString(Mapas.texto(encontrados.get(0), "id"));
    }

    public Map<String, Object> donante(UUID id) {
        return api.objeto(donaciones + "/donantes/{id}", id);
    }

    public UUID registrarDonante(Map<String, Object> cuerpo) {
        return UUID.fromString(Mapas.texto(api.crear(donaciones + "/donantes", cuerpo), "id"));
    }

    public List<Map<String, Object>> donacionesDe(UUID donanteId) {
        List<Map<String, Object>> r = new ArrayList<>(
                paginado(donaciones + "/donaciones?idDonante={id}", donanteId));
        r.sort((a, b) -> Mapas.texto(b, "fechaDonacion").compareTo(Mapas.texto(a, "fechaDonacion")));
        return r;
    }

    public Map<String, Object> donacion(UUID id) {
        return api.objeto(donaciones + "/donaciones/{id}", id);
    }

    public void crearDonacion(Map<String, Object> cuerpo) {
        api.enviar(HttpMethod.POST, donaciones + "/donaciones", cuerpo);
    }

    public List<Map<String, Object>> entidades() {
        return paginado(donaciones + "/entidades");
    }

    public UUID crearEntidad(Map<String, Object> cuerpo) {
        return UUID.fromString(Mapas.texto(api.crear(donaciones + "/entidades", cuerpo), "id"));
    }

    public Map<String, Object> entidad(UUID id) {
        return api.objeto(donaciones + "/entidades/{id}", id);
    }

    public void crearCampania(UUID entidadId, Map<String, Object> cuerpo) {
        api.enviar(HttpMethod.POST, donaciones + "/entidades/{id}/campanias", cuerpo, entidadId);
    }

    public void crearNecesidad(UUID entidadId, UUID campaniaId, Map<String, Object> cuerpo) {
        api.enviar(HttpMethod.POST,
                donaciones + "/entidades/{e}/campanias/{c}/necesidades", cuerpo, entidadId, campaniaId);
    }

    public void eliminarNecesidad(UUID entidadId, UUID campaniaId, UUID necesidadId) {
        api.enviar(HttpMethod.DELETE,
                donaciones + "/entidades/{e}/campanias/{c}/necesidades/{n}", null, entidadId, campaniaId, necesidadId);
    }

    /** Donaciones asignadas a la entidad (en camino o recibidas). */
    public List<Map<String, Object>> donacionesHaciaEntidad(UUID entidadId) {
        List<Map<String, Object>> r = new ArrayList<>(
                paginado(donaciones + "/donaciones?idEntidad={id}", entidadId));
        r.sort((a, b) -> Mapas.texto(b, "fechaDonacion").compareTo(Mapas.texto(a, "fechaDonacion")));
        return r;
    }

    /**
     * Cuántas donaciones se hicieron desde esa fecha (incluida), para la landing
     * ("donaciones del último mes"). Pide una página de un elemento: solo
     * interesa el total, que calcula el servicio.
     */
    public long cantidadDeDonacionesDesde(LocalDate desde) {
        Map<String, Object> respuesta = api.objeto(donaciones + "/donaciones?desde={desde}&page=0&size=1", desde);
        return respuesta.get("totalElementos") instanceof Number n ? n.longValue() : 0;
    }

    public List<Map<String, Object>> donacionesEnDeposito() {
        return paginado(donaciones + "/donaciones?estado=EN_DEPOSITO");
    }

    public Map<String, Object> candidatos(UUID donacionId) {
        return api.objeto(donaciones + "/donaciones/{id}/candidatos", donacionId);
    }

    public void asignar(UUID donacionId, UUID entidadId) {
        api.enviar(HttpMethod.PATCH, donaciones + "/donaciones/{id}/asignar",
                Map.of("idEntidadBeneficiaria", entidadId), donacionId);
    }

    // ── Administración ──────────────────────────────────────────────────────────
    // Las pantallas de la persona administradora muestran una página por vez:
    // los listados completos pueden ser grandes y ahí no hace falta tenerlos enteros.

    /**
     * Una página del listado, con los filtros que tengan valor. Los valores van
     * como variables de la URL para que se codifiquen (un email con "+", una
     * subcategoría con espacios).
     */
    Pagina pagina(String ruta, Map<String, Object> filtros, int numero) {
        StringBuilder url = new StringBuilder(donaciones).append(ruta).append('?');
        List<Object> valores = new ArrayList<>();
        for (Map.Entry<String, Object> filtro : filtros.entrySet()) {
            Object valor = filtro.getValue();
            if (valor == null || valor instanceof String texto && texto.isBlank()) {
                continue;
            }
            url.append(filtro.getKey()).append("={").append(filtro.getKey()).append("}&");
            valores.add(valor instanceof String texto ? texto.strip() : valor);
        }
        url.append("page=").append(Math.max(0, numero)).append("&size=").append(TAMANIO_ADMINISTRACION);
        return Pagina.desde(api.objeto(url.toString(), valores.toArray()));
    }

    /** Filtros posibles: estado, subcategoria, desde, hasta y vencidas (ver GET /donaciones). */
    public Pagina paginaDeDonaciones(Map<String, Object> filtros, int numero) {
        return pagina("/donaciones", filtros, numero);
    }

    public Pagina paginaDeDonantes(String estado, String email, int numero) {
        Map<String, Object> filtros = new HashMap<>();
        filtros.put("estado", estado);
        filtros.put("email", email);
        return pagina("/donantes", filtros, numero);
    }

    public Pagina paginaDeEntidades(String email, int numero) {
        Map<String, Object> filtros = new HashMap<>();
        filtros.put("email", email);
        return pagina("/entidades", filtros, numero);
    }

    /** Donaciones con entidad asignada que todavía no se mandaron a logística. */
    public List<Map<String, Object>> donacionesParaDespachar() {
        return paginado(donaciones + "/donaciones?estado=ASIGNACION_REALIZADA");
    }

    /** Resultado del matchmaking nocturno: donaciones en depósito con sus entidades sugeridas. */
    public List<Map<String, Object>> candidatosPendientes() {
        return api.lista(donaciones + "/donaciones/candidatos");
    }

    public void cambiarEstado(UUID donacionId, String estado, String justificacion) {
        api.enviar(HttpMethod.PATCH, donaciones + "/donaciones/{id}/estado",
                conJustificacion(estado, justificacion), donacionId);
    }

    /** proveedor: DONATRACK, EXTERNA, o null para que el broker elija. */
    public void despachar(List<UUID> donacionIds, String proveedor) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("idsDonaciones", donacionIds);
        cuerpo.put("proveedor", proveedor == null || proveedor.isBlank() ? null : proveedor);
        api.enviar(HttpMethod.POST, donaciones + "/donaciones/envios", cuerpo);
    }

    /** Baja lógica: la donación queda guardada pero deja de aparecer en los listados. */
    public void eliminarDonacion(UUID donacionId) {
        api.enviar(HttpMethod.DELETE, donaciones + "/donaciones/{id}", null, donacionId);
    }

    public void cambiarEstadoDonante(UUID donanteId, String estado, String justificacion) {
        api.enviar(HttpMethod.PATCH, donaciones + "/donantes/{id}/estado",
                conJustificacion(estado, justificacion), donanteId);
    }

    public void eliminarDonante(UUID donanteId) {
        api.enviar(HttpMethod.DELETE, donaciones + "/donantes/{id}", null, donanteId);
    }

    public void eliminarEntidad(UUID entidadId) {
        api.enviar(HttpMethod.DELETE, donaciones + "/entidades/{id}", null, entidadId);
    }

    private static Map<String, Object> conJustificacion(String estado, String justificacion) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("estado", estado);
        cuerpo.put("justificacion", justificacion == null || justificacion.isBlank() ? null : justificacion.strip());
        return cuerpo;
    }
}
