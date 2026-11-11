package ar.utn.donatrack.frontend.donaciones.services;

import ar.utn.donatrack.frontend.comun.clientes.ApiClient;
import ar.utn.donatrack.frontend.comun.config.FrontendProperties;
import ar.utn.donatrack.frontend.comun.util.Mapas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Cliente REST de servicio-donaciones: donantes, entidades, campañas,
 * necesidades y donaciones. Acá no hay reglas de negocio.
 */
@Service
public class DonacionesApi {

    private final ApiClient api;
    private final String donaciones;

    public DonacionesApi(ApiClient api, FrontendProperties props) {
        this.api = api;
        this.donaciones = props.servicios().donaciones();
    }

    public Map<String, Object> donante(UUID id) {
        return api.objeto(donaciones + "/donantes/{id}", id);
    }

    public UUID registrarDonante(Map<String, Object> cuerpo) {
        return UUID.fromString(Mapas.texto(api.crear(donaciones + "/donantes", cuerpo), "id"));
    }

    public List<Map<String, Object>> donacionesDe(UUID donanteId) {
        List<Map<String, Object>> r = new ArrayList<>(
                listado("/donaciones?idDonante={id}", donanteId));
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
        return listado("/entidades");
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
        List<Map<String, Object>> r = new ArrayList<>(listado("/donaciones?idEntidad={id}", entidadId));
        r.sort((a, b) -> Mapas.texto(b, "fechaDonacion").compareTo(Mapas.texto(a, "fechaDonacion")));
        return r;
    }

    public List<Map<String, Object>> donacionesEnDeposito() {
        return listado("/donaciones?estado=EN_DEPOSITO");
    }

    private List<Map<String, Object>> listado(String ruta, Object... variables) {
        String paginada = donaciones + ruta + (ruta.contains("?") ? "&" : "?") + "size=100&page=";
        List<Map<String, Object>> resultado = new ArrayList<>();
        int pagina = 0;
        int totalPaginas;
        do {
            Object respuesta = api.leer(paginada + pagina, variables);
            if (respuesta instanceof List<?>) {
                return Mapas.lista(respuesta);
            }
            Map<String, Object> datos = Mapas.mapa(respuesta);
            resultado.addAll(Mapas.lista(datos.get("contenido")));
            totalPaginas = datos.get("totalPaginas") instanceof Number n ? n.intValue() : 0;
            pagina++;
        } while (pagina < totalPaginas);
        return resultado;
    }

    public Map<String, Object> candidatos(UUID donacionId) {
        return api.objeto(donaciones + "/donaciones/{id}/candidatos", donacionId);
    }

    public void asignar(UUID donacionId, UUID entidadId) {
        api.enviar(HttpMethod.PATCH, donaciones + "/donaciones/{id}/asignar",
                Map.of("idEntidadBeneficiaria", entidadId), donacionId);
    }

    /** Donaciones en depósito con recomendación de los algoritmos, pendientes de confirmar destino. */
    public List<Map<String, Object>> candidatosPendientes() {
        return listado("/donaciones/candidatos");
    }

    public void cambiarEstadoDonacion(UUID id, String estado, String justificacion) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("estado", estado);
        if (justificacion != null && !justificacion.isBlank()) {
            cuerpo.put("justificacion", justificacion);
        }
        api.enviar(HttpMethod.PATCH, donaciones + "/donaciones/{id}/estado", cuerpo, id);
    }

    /** Personas donantes, paginadas (trae todas las páginas). */
    public List<Map<String, Object>> donantes() {
        return listado("/donantes");
    }

    public Map<String, Object> importarDonantesCsv(MultipartFile archivo) {
        return api.enviarArchivo(donaciones + "/donantes/import", "file", archivo);
    }
}
