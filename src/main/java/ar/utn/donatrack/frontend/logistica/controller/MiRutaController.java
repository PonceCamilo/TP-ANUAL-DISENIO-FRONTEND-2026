package ar.utn.donatrack.frontend.logistica.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.logistica.services.LogisticaApi;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Recorrido del chofer. El id del camión va en la dirección hasta que
 * autenticación informe cuál le toca.
 */
@Controller
@RequestMapping("/logistica/mi-ruta")
public class MiRutaController {

    private static final Set<String> MOTIVOS = Set.of(
            "ENTIDAD_AUSENTE",
            "DIRECCION_INCORRECTA",
            "RECHAZADA_POR_ENTIDAD",
            "MERCADERIA_ROTA",
            "MERCADERIA_PERDIDA",
            "ROBO");

    private final LogisticaApi api;

    public MiRutaController(LogisticaApi api) {
        this.api = api;
    }

    @GetMapping("/{camionId}")
    public String ver(@PathVariable UUID camionId, Model model) {
        Map<String, Object> camion = api.camion(camionId);
        Map<String, Object> ruta = api.rutaVigente(camionId);
        completarEstados(ruta);
        model.addAttribute("camion", camion);
        model.addAttribute("ruta", ruta);
        return "logistica/mi-ruta";
    }

    @PostMapping("/{camionId}/iniciar")
    public String iniciar(@PathVariable UUID camionId, RedirectAttributes flash) {
        Map<String, Object> ruta = api.rutaVigente(camionId);
        if (ruta == null) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", "Este camión no tiene una ruta para iniciar.");
            return volver(camionId);
        }
        try {
            api.iniciarRuta(UUID.fromString(String.valueOf(ruta.get("id"))));
        } catch (ApiException e) {
            return error(flash, e, camionId);
        }
        flash.addFlashAttribute("toastTipo", "ok");
        flash.addFlashAttribute("toast", "Ruta iniciada. Las entregas están en traslado.");
        return volver(camionId);
    }

    @PostMapping("/{camionId}/entregas/{entregaId}/incidente")
    public String incidente(@PathVariable UUID camionId, @PathVariable UUID entregaId,
                            @RequestParam String motivo, RedirectAttributes flash) {
        if (!MOTIVOS.contains(motivo)) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", "Elegí un motivo de la lista.");
            return volver(camionId);
        }
        try {
            api.marcarNoRecibida(entregaId, motivo);
        } catch (ApiException e) {
            return error(flash, e, camionId);
        }
        flash.addFlashAttribute("toastTipo", "ok");
        flash.addFlashAttribute("toast", "Incidente registrado.");
        return volver(camionId);
    }

    @PostMapping("/{camionId}/ubicacion")
    public String ubicacion(@PathVariable UUID camionId,
                            @RequestParam double latitud, @RequestParam double longitud,
                            RedirectAttributes flash) {
        if (latitud < -90 || latitud > 90 || longitud < -180 || longitud > 180) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", "La posición que informó el navegador no es válida.");
            return volver(camionId);
        }
        try {
            api.registrarUbicacion(camionId, latitud, longitud);
        } catch (ApiException e) {
            return error(flash, e, camionId);
        }
        flash.addFlashAttribute("toastTipo", "ok");
        flash.addFlashAttribute("toast", "Ubicación enviada.");
        return volver(camionId);
    }

    private String volver(UUID camionId) {
        return "redirect:/logistica/mi-ruta/" + camionId;
    }

    private String error(RedirectAttributes flash, ApiException e, UUID camionId) {
        flash.addFlashAttribute("toastTipo", "error");
        flash.addFlashAttribute("toast", e.getMessage());
        return volver(camionId);
    }

    @SuppressWarnings("unchecked")
    private void completarEstados(Map<String, Object> ruta) {
        if (ruta == null || !(ruta.get("paradas") instanceof List<?> paradas)) {
            return;
        }
        for (Object parada : paradas) {
            if (!(parada instanceof Map<?, ?> cruda)) {
                continue;
            }
            Object entregas = cruda.get("entregas");
            if (!(entregas instanceof List<?> lista)) {
                continue;
            }
            for (Object entrega : lista) {
                if (entrega instanceof Map<?, ?> mapa && mapa.get("id") != null) {
                    Map<String, Object> mutable = (Map<String, Object>) mapa;
                    Map<String, Object> detalle = api.entrega(UUID.fromString(String.valueOf(mapa.get("id"))));
                    mutable.put("estado", detalle.get("estado"));
                }
            }
        }
    }
}
