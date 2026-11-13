package ar.utn.donatrack.frontend.logistica.services;

import ar.utn.donatrack.frontend.comun.clientes.ApiClient;
import ar.utn.donatrack.frontend.comun.config.FrontendProperties;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

/**
 * Cliente REST de servicio-logistica. Acá no hay reglas de negocio:
 * la flota, las rutas y las entregas las decide el servicio.
 */
@Service
public class LogisticaApi {

    private final ApiClient api;
    private final String logistica;

    public LogisticaApi(ApiClient api, FrontendProperties props) {
        this.api = api;
        this.logistica = props.servicios().logistica();
    }

    public List<Map<String, Object>> camiones() {
        return api.lista(logistica + "/api/logistica/camiones");
    }

    public Map<String, Object> camion(UUID id) {
        return api.objeto(logistica + "/api/logistica/camiones/{id}", id);
    }

    public Map<String, Object> ruta(UUID id) {
        return api.objeto(logistica + "/api/logistica/rutas/{id}", id);
    }

    /** La ruta abierta del camión, o null si no tiene (el servicio responde 404). */
    public Map<String, Object> rutaVigente(UUID camionId) {
        try {
            return api.objeto(logistica + "/api/logistica/camiones/{id}/ruta", camionId);
        } catch (ApiException e) {
            if (e.getStatus() == 404) {
                return null;
            }
            throw e;
        }
    }

    public void pasarAMantenimiento(UUID id) {
        api.enviar(HttpMethod.POST, logistica + "/api/logistica/camiones/{id}/mantenimiento", null, id);
    }

    public void volverADisponible(UUID id) {
        api.enviar(HttpMethod.POST, logistica + "/api/logistica/camiones/{id}/disponible", null, id);
    }

    public List<Map<String, Object>> entregasPorEstado(String estado) {
        return api.lista(logistica + "/api/logistica/entregas?estado={estado}", estado);
    }

    public Map<String, Object> entrega(UUID id) {
        return api.objeto(logistica + "/api/logistica/entregas/{id}", id);
    }

    public Map<String, Object> entregaPorDonacion(UUID idDonacion) {
        return api.objeto(logistica + "/api/logistica/entregas/por-donacion/{id}", idDonacion);
    }

    public void confirmarEntrega(UUID entregaId, List<String> fotosComprobante) {
        api.enviar(HttpMethod.POST, logistica + "/api/logistica/entregas/{id}/confirmar",
                Map.of("fotosComprobante", fotosComprobante), entregaId);
    }

    public void regresarADeposito(UUID entregaId) {
        api.enviar(HttpMethod.POST, logistica + "/api/logistica/entregas/{id}/regreso-deposito", null, entregaId);
    }

    public void iniciarRuta(UUID rutaId) {
        api.enviar(HttpMethod.POST, logistica + "/api/logistica/rutas/{id}/iniciar", null, rutaId);
    }

    public void marcarNoRecibida(UUID entregaId, String motivo) {
        api.enviar(HttpMethod.POST, logistica + "/api/logistica/entregas/{id}/no-recibida",
                Map.of("motivo", motivo), entregaId);
    }

    public void registrarUbicacion(UUID camionId, double latitud, double longitud) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("latitud", latitud);
        cuerpo.put("longitud", longitud);
        api.enviar(HttpMethod.POST, logistica + "/api/logistica/camiones/{id}/ubicacion", cuerpo, camionId);
    }

    /** Entregas que ya volvieron al depósito. Lista vacía si no hay ninguna. */
    public List<Map<String, Object>> replanificarPendientes() {
        return api.publicarLista(logistica + "/api/logistica/planificaciones/reintentos", null);
    }

    public void eliminarCamion(UUID id) {
        api.enviar(HttpMethod.DELETE, logistica + "/api/logistica/camiones/{id}", null, id);
    }

    public void registrarCamion(String patente, double volumenM3, double alturaM, double cargaKg) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("patente", patente);
        cuerpo.put("capacidadVolumenM3", volumenM3);
        cuerpo.put("alturaM", alturaM);
        cuerpo.put("capacidadCargaKg", cargaKg);
        api.enviar(HttpMethod.POST, logistica + "/api/logistica/camiones", cuerpo);
    }
}
