package ar.utn.donatrack.frontend.clientes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import ar.utn.donatrack.frontend.config.FrontendProperties;
import ar.utn.donatrack.frontend.seguridad.SesionUsuario;
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

    public AuthClient(FrontendProperties props) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(3000);
        fabrica.setReadTimeout(10000);
        this.rest = RestClient.builder().requestFactory(fabrica).build();
        this.baseUrl = props.servicios().autenticacion();
    }

    /** Crea la cuenta y deja la sesión iniciada. */
    public SesionUsuario registrar(String nombre, String email, String password, String rol) {
        Map<String, Object> pedido = new HashMap<>();
        pedido.put("nombre", nombre);
        pedido.put("email", email);
        pedido.put("password", password);
        pedido.put("rol", rol);
        return pedir("/api/auth/registro", pedido);
    }

    /** Ingreso con email y contraseña. */
    public SesionUsuario ingresar(String email, String password, String rol) {
        Map<String, Object> pedido = new HashMap<>();
        pedido.put("email", email);
        pedido.put("password", password);
        pedido.put("rol", rol);
        return pedir("/api/auth/login", pedido);
    }

    public SesionUsuario canjearGoogle(String idToken, String rol) {
        Map<String, Object> pedido = new HashMap<>();
        pedido.put("proveedor", "google");
        pedido.put("idToken", idToken);
        pedido.put("rol", rol);
        return pedir("/api/auth/token", pedido);
    }

    public SesionUsuario canjearDemo(String email, String nombre, String rol) {
        Map<String, Object> pedido = new HashMap<>();
        pedido.put("proveedor", "demo");
        pedido.put("email", email);
        pedido.put("nombre", nombre);
        pedido.put("rol", rol);
        return pedir("/api/auth/token", pedido);
    }

    private SesionUsuario pedir(String ruta, Map<String, Object> pedido) {
        try {
            Map<String, Object> r = rest.post()
                    .uri(baseUrl + ruta)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(pedido)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (r == null || r.get("accessToken") == null) {
                throw new ApiException("El servicio de autenticación no devolvió un token.", 502);
            }
            long segundos = ((Number) r.get("expiresIn")).longValue();
            return new SesionUsuario(
                    String.valueOf(r.get("email")),
                    String.valueOf(r.get("nombre")),
                    String.valueOf(r.get("rol")),
                    String.valueOf(r.get("accessToken")),
                    Instant.now().plusSeconds(segundos));
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
