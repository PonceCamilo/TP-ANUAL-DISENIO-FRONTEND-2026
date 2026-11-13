package ar.utn.donatrack.frontend.logistica.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.exceptions.SesionVencidaException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.comun.util.Mapas;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;
import ar.utn.donatrack.frontend.logistica.services.LogisticaApi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Mapa de una ruta. No cambia estados.
 *
 * Entrega 6: pide sesión (SesionInterceptor). Logística deja ver la ruta a la
 * entidad solo si pasa por ella, y a la administración y al chofer siempre. A la
 * persona donante Logística no la puede controlar (no sabe quién donó): por eso
 * el mapa se abre desde el detalle de su donación (?donacion=), se le pregunta a
 * Donaciones con su token que esa donación sea suya (si no, 403) y se comprueba
 * que la ruta la lleve.
 */
@Controller
public class SeguimientoController {

    private final LogisticaApi api;
    private final DonacionesApi donaciones;

    public SeguimientoController(LogisticaApi api, DonacionesApi donaciones) {
        this.api = api;
        this.donaciones = donaciones;
    }

    @GetMapping("/tracking/ruta/{id}")
    public String ver(@PathVariable UUID id, @RequestParam(required = false) UUID donacion,
                      HttpSession sesion, Model model) {
        Map<String, Object> ruta = rutaVisible(id, donacion, sesion);
        model.addAttribute("ruta", ruta);
        model.addAttribute("camion", camionDe(ruta));
        model.addAttribute("donacion", donacion);
        return "logistica/mapa";
    }

    /** La consulta el mapa cada 15 segundos. 401: la sesión venció; 403: ya no la puede ver. */
    @GetMapping("/tracking/ruta/{id}/posicion")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> posicion(@PathVariable UUID id,
                                                        @RequestParam(required = false) UUID donacion,
                                                        HttpSession sesion) {
        Map<String, Object> ruta;
        Map<String, Object> camion;
        try {
            ruta = rutaVisible(id, donacion, sesion);
            camion = camionDe(ruta);
        } catch (SesionVencidaException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Tu sesión venció."));
        } catch (ApiException e) {
            return ResponseEntity.status(e.getStatus() == 403 ? 403 : 502).body(Map.of("error", e.getMessage()));
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("estado", ruta.get("estado"));
        cuerpo.put("patente", camion == null ? null : camion.get("patente"));
        cuerpo.put("latitud", camion == null ? null : camion.get("latitud"));
        cuerpo.put("longitud", camion == null ? null : camion.get("longitud"));
        cuerpo.put("actualizada", camion == null ? null : hora(camion.get("ubicacionActualizadaEn")));
        return ResponseEntity.ok(cuerpo);
    }

    private Map<String, Object> rutaVisible(UUID id, UUID donacion, HttpSession sesion) {
        SesionUsuario usuario = (SesionUsuario) sesion.getAttribute(SesionUsuario.ATRIBUTO);
        if (usuario == null || !usuario.esDonante()) {
            return api.ruta(id);
        }
        if (donacion == null) {
            throw new ApiException("Abrí el mapa desde el detalle de tu donación.", 403);
        }
        donaciones.donacion(donacion);
        Map<String, Object> ruta = api.ruta(id);
        if (!llevaLaDonacion(ruta, donacion)) {
            throw new ApiException("Esta ruta no lleva tu donación.", 403);
        }
        return ruta;
    }

    static boolean llevaLaDonacion(Map<String, Object> ruta, UUID donacion) {
        return Mapas.lista(ruta.get("paradas")).stream()
                .flatMap(parada -> Mapas.lista(parada.get("entregas")).stream())
                .anyMatch(entrega -> donacion.toString().equals(Mapas.texto(entrega, "idDonacion")));
    }

    private Map<String, Object> camionDe(Map<String, Object> ruta) {
        Object camionId = ruta.get("camionId");
        if (camionId == null) {
            return null;
        }
        return api.camion(UUID.fromString(String.valueOf(camionId)));
    }

    private static String hora(Object valor) {
        if (valor == null) {
            return null;
        }
        String texto = String.valueOf(valor);
        if (texto.length() >= 16) {
            return texto.substring(0, 16).replace('T', ' ');
        }
        return texto;
    }
}
