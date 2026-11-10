package ar.utn.donatrack.frontend.donaciones.services;

import ar.utn.donatrack.frontend.comun.config.FrontendProperties;
import ar.utn.donatrack.frontend.comun.exceptions.ApiException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Sube el CSV de donantes a servicio-donaciones (POST /donantes/import).
 *
 * Va aparte de ApiClient porque ese cliente solo manda JSON y esto es un
 * multipart/form-data. La lectura espera más: el servicio procesa el archivo
 * entero antes de responder.
 */
@Component
public class ImportacionCsvClient {

    private static final ParameterizedTypeReference<Map<String, Object>> OBJETO =
            new ParameterizedTypeReference<>() {};

    private final RestClient rest;
    private final String donaciones;

    public ImportacionCsvClient(FrontendProperties props) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofSeconds(60));
        this.rest = RestClient.builder().requestFactory(fabrica).build();
        this.donaciones = props.servicios().donaciones();
    }

    /** Devuelve el resumen del servicio: totalProcesadas, creadas, actualizadas, conError y errores por fila. */
    public Map<String, Object> importarDonantes(String nombreArchivo, byte[] contenido, String token) {
        MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
        partes.add("file", new ByteArrayResource(contenido) {
            @Override
            public String getFilename() {
                return nombreArchivo == null || nombreArchivo.isBlank() ? "donantes.csv" : nombreArchivo;
            }
        });
        try {
            Map<String, Object> resumen = rest.post()
                    .uri(donaciones + "/donantes/import")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .headers(h -> {
                        if (token != null) {
                            h.setBearerAuth(token);
                        }
                    })
                    .body(partes)
                    .retrieve()
                    .body(OBJETO);
            return resumen == null ? Map.of() : resumen;
        } catch (RestClientResponseException e) {
            throw new ApiException(mensajeDe(e), e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new ApiException(
                    "No pudimos conectarnos con el servicio. Revisá que esté en funcionamiento e intentá de nuevo.", 503);
        }
    }

    private static String mensajeDe(RestClientResponseException e) {
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
        return e.getStatusCode().value() == 413
                ? "El archivo es demasiado grande."
                : "No pudimos procesar el archivo (" + e.getStatusCode().value() + ").";
    }
}
