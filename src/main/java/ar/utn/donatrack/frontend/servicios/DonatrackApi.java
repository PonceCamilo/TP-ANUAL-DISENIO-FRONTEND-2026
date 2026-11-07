package ar.utn.donatrack.frontend.servicios;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ar.utn.donatrack.frontend.clientes.ApiClient;
import ar.utn.donatrack.frontend.config.FrontendProperties;
import ar.utn.donatrack.frontend.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.util.Mapas;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

/**
 * Fachada con las operaciones que necesitan las pantallas. Cada método es una
 * llamada REST a un servicio de aplicación; acá no hay reglas de negocio.
 */
@Service
public class DonatrackApi {

    private final ApiClient api;
    private final String donaciones;
    private final String incentivos;
    private final String notificaciones;

    public DonatrackApi(ApiClient api, FrontendProperties props) {
        this.api = api;
        this.donaciones = props.servicios().donaciones();
        this.incentivos = props.servicios().incentivos();
        this.notificaciones = props.servicios().notificaciones();
    }

    // ---------- identidad: de email (Google) a id de dominio ----------

    /** Busca al donante cuyo email es el del login. Devuelve null si todavía no se registró. */
    public UUID buscarDonante(String email) {
        for (Map<String, Object> d : api.lista(donaciones + "/donantes")) {
            if (Mapas.igual(Mapas.texto(d, "email"), email)) {
                return UUID.fromString(Mapas.texto(d, "id"));
            }
        }
        return null;
    }

    /** Busca la entidad que tiene como contacto o representante el email del login. */
    public UUID buscarEntidad(String email) {
        for (Map<String, Object> e : api.lista(donaciones + "/entidades")) {
            boolean coincide = Mapas.lista(e.get("contactos")).stream()
                    .anyMatch(c -> Mapas.igual(Mapas.texto(c, "valor"), email))
                    || Mapas.lista(e.get("representantes")).stream()
                    .anyMatch(r -> Mapas.igual(Mapas.texto(r, "email"), email));
            if (coincide) {
                return UUID.fromString(Mapas.texto(e, "id"));
            }
        }
        return null;
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

    // ---------- donante ----------

    public Map<String, Object> donante(UUID id) {
        return api.objeto(donaciones + "/donantes/{id}", id);
    }

    public void registrarDonante(Map<String, Object> cuerpo) {
        api.enviar(HttpMethod.POST, donaciones + "/donantes", cuerpo);
    }

    public List<Map<String, Object>> donacionesDe(UUID donanteId) {
        List<Map<String, Object>> r = new ArrayList<>(
                api.lista(donaciones + "/donaciones?idDonante={id}", donanteId));
        r.sort((a, b) -> Mapas.texto(b, "fechaDonacion").compareTo(Mapas.texto(a, "fechaDonacion")));
        return r;
    }

    public Map<String, Object> donacion(UUID id) {
        return api.objeto(donaciones + "/donaciones/{id}", id);
    }

    public void crearDonacion(Map<String, Object> cuerpo) {
        api.enviar(HttpMethod.POST, donaciones + "/donaciones", cuerpo);
    }

    public Map<String, Object> metricas(UUID donanteId) {
        return api.objeto(incentivos + "/api/incentivos/donantes/{id}/metricas", donanteId);
    }

    public List<Map<String, Object>> misiones(UUID donanteId) {
        return api.lista(incentivos + "/api/incentivos/donantes/{id}/misiones", donanteId);
    }

    public List<Map<String, Object>> insignias(UUID donanteId) {
        return api.lista(incentivos + "/api/incentivos/donantes/{id}/insignias", donanteId);
    }

    /** El servicio de notificaciones no filtra por persona: se filtra acá por destinatario. */
    public List<Map<String, Object>> notificacionesDe(List<String> destinatarios) {
        List<Map<String, Object>> todas = api.lista(notificaciones + "/notificaciones");
        List<Map<String, Object>> propias = new ArrayList<>();
        for (Map<String, Object> n : todas) {
            String d = Mapas.texto(n, "destinatario");
            if (destinatarios.stream().anyMatch(x -> Mapas.igual(x, d))) {
                propias.add(n);
            }
        }
        propias.sort((a, b) -> Mapas.texto(b, "fechaCreacion").compareTo(Mapas.texto(a, "fechaCreacion")));
        return propias;
    }

    // ---------- entidad ----------

    public List<Map<String, Object>> entidades() {
        return api.lista(donaciones + "/entidades");
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
        List<Map<String, Object>> r = new ArrayList<>();
        for (Map<String, Object> d : api.lista(donaciones + "/donaciones")) {
            if (Mapas.texto(d, "idEntidadBeneficiaria").equals(String.valueOf(entidadId))) {
                r.add(d);
            }
        }
        r.sort((a, b) -> Mapas.texto(b, "fechaDonacion").compareTo(Mapas.texto(a, "fechaDonacion")));
        return r;
    }

    public List<Map<String, Object>> donacionesEnDeposito() {
        return api.lista(donaciones + "/donaciones?estado=EN_DEPOSITO");
    }

    public Map<String, Object> candidatos(UUID donacionId) {
        return api.objeto(donaciones + "/donaciones/{id}/candidatos", donacionId);
    }

    public void asignar(UUID donacionId, UUID entidadId) {
        api.enviar(HttpMethod.PATCH, donaciones + "/donaciones/{id}/asignar",
                Map.of("idEntidadBeneficiaria", entidadId), donacionId);
    }

}
