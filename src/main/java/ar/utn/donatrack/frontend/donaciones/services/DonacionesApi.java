package ar.utn.donatrack.frontend.donaciones.services;

import ar.utn.donatrack.frontend.comun.clientes.ApiClient;
import ar.utn.donatrack.frontend.comun.config.FrontendProperties;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.comun.util.Mapas;

import java.time.LocalDate;
import java.util.ArrayList;
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

    /** Busca al donante cuyo email es el del login. Devuelve null si todavía no se registró. */
    public UUID buscarDonante(String email) {
        return idDelPrimero(paginado(donaciones + "/donantes?email={email}", email));
    }

    /**
     * Busca la entidad que tiene como contacto o representante el email del login.
     * Si esa persona figura en más de una entidad, se toma la primera por razón
     * social (el orden en que las devuelve el servicio).
     */
    public UUID buscarEntidad(String email) {
        return idDelPrimero(paginado(donaciones + "/entidades?email={email}", email));
    }

    private UUID idDelPrimero(List<Map<String, Object>> encontrados) {
        return encontrados.isEmpty() ? null : UUID.fromString(Mapas.texto(encontrados.get(0), "id"));
    }

    public void resolverIdentidad(SesionUsuario u) {
        if (u.esDonante()) {
            if (u.getDonanteId() == null) {
                u.setDonanteId(buscarDonante(u.getEmail()));
            }
        } else if (u.getEntidadId() == null) {
            u.setEntidadId(buscarEntidad(u.getEmail()));
        }
    }

    public Map<String, Object> donante(UUID id) {
        return api.objeto(donaciones + "/donantes/{id}", id);
    }

    public void registrarDonante(Map<String, Object> cuerpo) {
        api.enviar(HttpMethod.POST, donaciones + "/donantes", cuerpo);
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

    public void crearEntidad(Map<String, Object> cuerpo) {
        api.enviar(HttpMethod.POST, donaciones + "/entidades", cuerpo);
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
}
