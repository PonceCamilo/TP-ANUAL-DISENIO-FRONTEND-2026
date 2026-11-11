package ar.utn.donatrack.frontend.autenticacion.services;

import ar.utn.donatrack.frontend.comun.config.FrontendProperties;
import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Habla con el servicio de autenticación: canjea una identidad verificada por un JWT. */
@Component
public class AuthClient {

    private final RestClient rest;
    private final String baseUrl;
    private final String claveFrontend;

    public AuthClient(FrontendProperties props, @Value("${donatrack.auth.clave-frontend:}") String claveFrontend) {
        this.claveFrontend = claveFrontend;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(3000);
        fabrica.setReadTimeout(10000);
        this.rest = RestClient.builder().requestFactory(fabrica).build();
        this.baseUrl = props.servicios().autenticacion();
    }

    /** Crea la cuenta y deja la sesión iniciada. */
    public SesionUsuario registrar(String nombre, String email, String password, String rol, String tipoDonante) {
        Map<String, Object> pedido = new HashMap<>();
        pedido.put("nombre", nombre);
        pedido.put("email", email);
        pedido.put("password", password);
        pedido.put("rol", rol);
        pedido.put("tipoDonante", tipoDonante);
        return pedir("/api/autenticacion/registro", pedido);
    }

    /** Ingreso con email y contraseña. */
    public SesionUsuario ingresar(String email, String password, String rol) {
        Map<String, Object> pedido = new HashMap<>();
        pedido.put("email", email);
        pedido.put("password", password);
        pedido.put("rol", rol);
        return pedir("/api/autenticacion/ingreso", pedido);
    }

    public SesionUsuario canjearGoogle(String idToken, String rol, String tipoDonante) {
        Map<String, Object> pedido = new HashMap<>();
        pedido.put("proveedor", "google");
        pedido.put("idToken", idToken);
        pedido.put("tipoDonante", tipoDonante);
        pedido.put("rol", rol);
        return pedir("/api/autenticacion/ingreso-social", pedido);
    }

    public void crearChofer(SesionUsuario admin, String nombre, String email, String password) {
        try {
            rest.post().uri(baseUrl + "/api/autenticacion/choferes")
                    .headers(headers -> headers.setBearerAuth(admin.getToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("nombre", nombre, "email", email, "password", password))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            String mensaje = detalle(e);
            throw new ApiException(mensaje.isBlank() ? "No pudimos crear la cuenta del chofer." : mensaje,
                    e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new ApiException("El servicio de autenticación no responde.", 503);
        }
    }

    public List<Map<String, Object>> choferes(SesionUsuario admin) {
        try {
            List<Map<String, Object>> cuerpo = rest.get().uri(baseUrl + "/api/autenticacion/choferes")
                    .headers(headers -> headers.setBearerAuth(admin.getToken()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return cuerpo == null ? List.of() : cuerpo;
        } catch (RestClientResponseException e) {
            String mensaje = detalle(e);
            throw new ApiException(mensaje.isBlank() ? "No pudimos consultar los choferes." : mensaje,
                    e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new ApiException("El servicio de autenticación no responde.", 503);
        }
    }

    public void asignarCamion(SesionUsuario admin, UUID choferId, UUID camionId) {
        try {
            rest.put().uri(baseUrl + "/api/autenticacion/choferes/{id}/camion", choferId)
                    .headers(headers -> headers.setBearerAuth(admin.getToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("camionId", camionId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            String mensaje = detalle(e);
            throw new ApiException(mensaje.isBlank() ? "No pudimos asignar el camión." : mensaje,
                    e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new ApiException("El servicio de autenticación no responde.", 503);
        }
    }

    public UUID camionActual(SesionUsuario usuario) {
        try {
            Map<String, Object> cuenta = rest.get().uri(baseUrl + "/api/autenticacion/sesion")
                    .headers(headers -> headers.setBearerAuth(usuario.getToken())).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return cuenta != null && cuenta.get("camionId") instanceof String id ? UUID.fromString(id) : null;
        } catch (RestClientResponseException e) {
            throw new ApiException("No pudimos consultar la asignación del camión.", e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new ApiException("El servicio de autenticación no responde.", 503);
        }
    }

    public void exigirVinculacionConfigurada() {
        if (claveFrontend.length() < 32) {
            throw new ApiException("Falta configurar AUTH_FRONTEND_KEY para vincular tu perfil.", 503);
        }
    }

    public SesionUsuario vincularPerfil(SesionUsuario usuario, UUID perfilId) {
        exigirVinculacionConfigurada();
        return pedir("/api/autenticacion/perfil", Map.of("perfilId", perfilId), usuario.getToken());
    }

    private SesionUsuario pedir(String ruta, Map<String, Object> pedido) {
        return pedir(ruta, pedido, null);
    }

    private SesionUsuario pedir(String ruta, Map<String, Object> pedido, String token) {
        try {
            var solicitud = rest.post().uri(baseUrl + ruta).contentType(MediaType.APPLICATION_JSON);
            if (token != null) {
                solicitud.headers(headers -> headers.setBearerAuth(token));
                solicitud.header("X-DonaTrack-Frontend", claveFrontend);
            }
            Map<String, Object> r = solicitud.body(pedido)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (r == null || r.get("accessToken") == null) {
                throw new ApiException("El servicio de autenticación no devolvió un token.", 502);
            }
            long segundos = ((Number) r.get("expiresIn")).longValue();
            SesionUsuario usuario = new SesionUsuario(
                    String.valueOf(r.get("email")),
                    String.valueOf(r.get("nombre")),
                    String.valueOf(r.get("rol")),
                    String.valueOf(r.get("accessToken")),
                    Instant.now().plusSeconds(segundos), (String) r.get("tipoDonante"));
            if (r.get("perfilId") instanceof String perfilId) {
                if (usuario.esDonante()) {
                    usuario.setDonanteId(UUID.fromString(perfilId));
                } else if (SesionUsuario.ENTIDAD_BENEFICIARIA.equals(usuario.getRol())) {
                    usuario.setEntidadId(UUID.fromString(perfilId));
                }
            }
            if (r.get("camionId") instanceof String camionId && SesionUsuario.CHOFER.equals(usuario.getRol())) {
                usuario.setCamionId(UUID.fromString(camionId));
            }
            return usuario;
        } catch (RestClientResponseException e) {
            String detalle = detalle(e);
            throw new ApiException(
                    detalle.isBlank() ? "No pudimos completar el ingreso. Probá de nuevo." : detalle,
                    e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new ApiException("El servicio de autenticación no responde. Intentá de nuevo en unos minutos.", 503);
        }
    }

    private String detalle(RestClientResponseException e) {
        try {
            Map<?, ?> cuerpo = e.getResponseBodyAs(Map.class);
            if (cuerpo != null && cuerpo.get("message") != null) {
                return String.valueOf(cuerpo.get("message"));
            }
        } catch (RuntimeException ignorado) {
            // sin detalle
        }
        return "";
    }
}
