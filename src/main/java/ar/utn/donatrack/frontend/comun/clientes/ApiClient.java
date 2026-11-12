package ar.utn.donatrack.frontend.comun.clientes;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.exceptions.SesionVencidaException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpSession;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

/**
 * Único punto por donde el front le habla a los servicios de aplicación.
 *
 * - Agrega a cada llamada el JWT de la persona logueada (Authorization: Bearer).
 * - Trabaja con Map/List: el cliente es liviano y no comparte clases con el
 *   backend, así los servicios pueden cambiar sus DTOs sin romper la compilación.
 * - Convierte cualquier falla en un ApiException con un mensaje presentable.
 */
@Component
public class ApiClient {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LISTA =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, Object>> OBJETO =
            new ParameterizedTypeReference<>() {};

    private final RestClient rest;

    public ApiClient() {
        // Cliente HTTP del JDK y no HttpURLConnection: este último no soporta PATCH,
        // que usan los servicios para cambiar estados y asignar entidades.
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofSeconds(15));
        this.rest = RestClient.builder()
                .requestFactory(fabrica)
                .requestInterceptor(this::agregarToken)
                .build();
    }

    public List<Map<String, Object>> lista(String url, Object... variables) {
        return ejecutar(() -> {
            List<Map<String, Object>> r = rest.get().uri(url, variables).retrieve().body(LISTA);
            return r == null ? List.of() : r;
        });
    }

    public Map<String, Object> objeto(String url, Object... variables) {
        return ejecutar(() -> {
            Map<String, Object> r = rest.get().uri(url, variables).retrieve().body(OBJETO);
            return r == null ? Map.of() : r;
        });
    }

    public Object leer(String url, Object... variables) {
        return ejecutar(() -> rest.get().uri(url, variables).retrieve().body(Object.class));
    }

    /** POST que devuelve una lista. El cuerpo puede ir vacío. */
    public List<Map<String, Object>> publicarLista(String url, Object cuerpo) {
        return ejecutar(() -> {
            var pedido = rest.post().uri(url).contentType(MediaType.APPLICATION_JSON);
            if (cuerpo != null) {
                pedido.body(cuerpo);
            }
            List<Map<String, Object>> r = pedido.retrieve().body(LISTA);
            return r == null ? List.of() : r;
        });
    }

    public Map<String, Object> crear(String url, Object cuerpo) {
        return ejecutar(() -> rest.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo).retrieve().body(OBJETO));
    }

    public void enviar(HttpMethod metodo, String url, Object cuerpo, Object... variables) {
        ejecutar(() -> {
            RestClient.RequestBodySpec spec = rest.method(metodo).uri(url, variables)
                    .contentType(MediaType.APPLICATION_JSON);
            if (cuerpo != null) {
                spec.body(cuerpo);
            }
            spec.retrieve().toBodilessEntity();
            return null;
        });
    }

    /** Envía un archivo como multipart/form-data (ej: importación de CSV). */
    public Map<String, Object> enviarArchivo(String url, String nombreCampo, MultipartFile archivo) {
        return ejecutar(() -> {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add(nombreCampo, archivo.getResource());
            return rest.post().uri(url).contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body).retrieve().body(OBJETO);
        });
    }

    private <T> T ejecutar(Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (RestClientResponseException e) {
            // Entrega 6: los servicios validan el JWT. Un 401 es un token vencido o
            // inválido: hay que volver a ingresar (SesionVencidaAdvice), no mostrar un aviso.
            if (e.getStatusCode().value() == 401) {
                throw new SesionVencidaException();
            }
            throw new ApiException(mensajeDe(e), e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new ApiException(
                    "No pudimos conectarnos con el servicio. Revisá que esté en funcionamiento e intentá de nuevo.", 503);
        }
    }

    private String mensajeDe(RestClientResponseException e) {
        try {
            Map<?, ?> cuerpo = e.getResponseBodyAs(Map.class);
            if (cuerpo != null) {
                Object msg = cuerpo.get("message") != null ? cuerpo.get("message") : cuerpo.get("mensaje");
                if (msg != null && !String.valueOf(msg).isBlank()) {
                    return String.valueOf(msg);
                }
            }
        } catch (RuntimeException ignorado) {
            // el cuerpo no era JSON: se usa el mensaje genérico
        }
        return switch (e.getStatusCode().value()) {
            case 400 -> "Los datos enviados no son válidos.";
            case 403 -> "No tenés permiso para hacer esto.";
            case 404 -> "No encontramos lo que buscabas.";
            case 409 -> "La operación no se puede realizar en el estado actual.";
            default -> "El servicio respondió con un error (" + e.getStatusCode().value() + ").";
        };
    }

    private ClientHttpResponse agregarToken(HttpRequest request, byte[] cuerpo, ClientHttpRequestExecution ejecucion)
            throws IOException {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpSession sesion = attrs.getRequest().getSession(false);
            if (sesion != null && sesion.getAttribute(SesionUsuario.ATRIBUTO) instanceof SesionUsuario u
                    && u.getToken() != null) {
                request.getHeaders().setBearerAuth(u.getToken());
            }
        }
        return ejecucion.execute(request, cuerpo);
    }
}
