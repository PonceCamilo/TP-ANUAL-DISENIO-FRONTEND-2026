package ar.utn.donatrack.frontend.logistica.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.logistica.services.LogisticaApi;

import java.util.Map;
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
 * Flota de la organización. Vive bajo /admin: el interceptor deja pasar
 * solo a la persona administradora.
 */
@Controller
@RequestMapping("/admin/flota")
public class FlotaController {

    private final LogisticaApi api;

    public FlotaController(LogisticaApi api) {
        this.api = api;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("seccion", "flota");
        model.addAttribute("camiones", api.camiones());
        return "logistica/flota";
    }

    @PostMapping
    public String registrar(@RequestParam String patente,
                            @RequestParam double capacidadVolumenM3,
                            @RequestParam double alturaM,
                            @RequestParam double capacidadCargaKg,
                            RedirectAttributes flash) {
        String patenteLimpia = patente == null ? "" : patente.trim();
        if (patenteLimpia.isBlank() || capacidadVolumenM3 <= 0 || alturaM <= 0 || capacidadCargaKg <= 0) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", "Completá la patente y las tres capacidades con un número mayor que cero.");
            return "redirect:/admin/flota";
        }
        try {
            api.registrarCamion(patenteLimpia, capacidadVolumenM3, alturaM, capacidadCargaKg);
        } catch (ApiException e) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", e.getMessage());
            return "redirect:/admin/flota";
        }
        flash.addFlashAttribute("toastTipo", "ok");
        flash.addFlashAttribute("toast", "Camión " + patenteLimpia + " registrado.");
        return "redirect:/admin/flota";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable UUID id, Model model) {
        Map<String, Object> camion = api.camion(id);
        Map<String, Object> ruta = api.rutaVigente(id);
        String estado = String.valueOf(camion.get("estado"));
        boolean rutaAbierta = ruta != null;
        model.addAttribute("seccion", "flota");
        model.addAttribute("camion", camion);
        model.addAttribute("ruta", ruta);
        model.addAttribute("puedeMantenimiento", "DISPONIBLE".equals(estado) && !rutaAbierta);
        model.addAttribute("puedeDisponible", "MANTENIMIENTO".equals(estado) && !rutaAbierta);
        return "logistica/camion";
    }

    @PostMapping("/{id}/mantenimiento")
    public String mantenimiento(@PathVariable UUID id, RedirectAttributes flash) {
        return cambiarDisponibilidad(id, flash, true);
    }

    @PostMapping("/{id}/disponible")
    public String disponible(@PathVariable UUID id, RedirectAttributes flash) {
        return cambiarDisponibilidad(id, flash, false);
    }

    private String cambiarDisponibilidad(UUID id, RedirectAttributes flash, boolean aMantenimiento) {
        try {
            if (aMantenimiento) {
                api.pasarAMantenimiento(id);
            } else {
                api.volverADisponible(id);
            }
        } catch (ApiException e) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", e.getMessage());
            return "redirect:/admin/flota/" + id;
        }
        flash.addFlashAttribute("toastTipo", "ok");
        flash.addFlashAttribute("toast", aMantenimiento
                ? "El camión pasó a mantenimiento."
                : "El camión volvió a estar disponible.");
        return "redirect:/admin/flota/" + id;
    }
}
