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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Confirmación de la entidad beneficiaria. Entra con el id de la donación.
 */
@Controller
public class RecepcionController {

    private static final Set<String> REPLANIFICABLES = Set.of(
            "ENTIDAD_AUSENTE", "DIRECCION_INCORRECTA", "RECHAZADA_POR_ENTIDAD");

    private final LogisticaApi api;

    public RecepcionController(LogisticaApi api) {
        this.api = api;
    }

    @GetMapping("/recepcion")
    public String ver(@RequestParam(required = false) UUID donacion, Model model) {
        model.addAttribute("donacion", donacion);
        if (donacion == null) {
            return "logistica/recepcion";
        }
        try {
            Map<String, Object> entrega = api.entregaPorDonacion(donacion);
            model.addAttribute("entrega", entrega);
            model.addAttribute("replanificable", esReplanificable(entrega.get("historial")));
        } catch (ApiException e) {
            if (e.getStatus() == 404) {
                model.addAttribute("noEncontrada", true);
                return "logistica/recepcion";
            }
            throw e;
        }
        return "logistica/recepcion";
    }

    @PostMapping("/recepcion")
    public String confirmar(@RequestParam UUID donacion, @RequestParam UUID entrega,
                            @RequestParam String comprobante, RedirectAttributes flash) {
        String texto = comprobante == null ? "" : comprobante.trim();
        if (texto.isBlank()) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", "Indicá al menos un comprobante.");
            return "redirect:/recepcion?donacion=" + donacion;
        }
        try {
            api.confirmarEntrega(entrega, List.of(texto));
        } catch (ApiException e) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", e.getMessage());
            return "redirect:/recepcion?donacion=" + donacion;
        }
        flash.addFlashAttribute("toastTipo", "ok");
        flash.addFlashAttribute("toast", "Recepción confirmada.");
        return "redirect:/recepcion?donacion=" + donacion;
    }

    private static boolean esReplanificable(Object historial) {
        if (!(historial instanceof List<?> cambios)) {
            return false;
        }
        String motivo = null;
        for (Object cambio : cambios) {
            if (cambio instanceof Map<?, ?> mapa
                    && "NO_RECIBIDA".equals(String.valueOf(mapa.get("estado")))
                    && mapa.get("motivoFallo") != null) {
                motivo = String.valueOf(mapa.get("motivoFallo"));
            }
        }
        return motivo != null && REPLANIFICABLES.contains(motivo);
    }
}
