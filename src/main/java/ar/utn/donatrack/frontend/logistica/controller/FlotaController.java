package ar.utn.donatrack.frontend.logistica.controller;

import ar.utn.donatrack.frontend.autenticacion.services.AuthClient;
import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.logistica.services.LogisticaApi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;

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
    private final AuthClient authClient;

    public FlotaController(LogisticaApi api, AuthClient authClient) {
        this.api = api;
        this.authClient = authClient;
    }

    private SesionUsuario usuario(HttpSession s) {
        return (SesionUsuario) s.getAttribute(SesionUsuario.ATRIBUTO);
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("seccion", "camiones");
        model.addAttribute("pestania", "flota");
        model.addAttribute("camiones", api.camiones());
        return "logistica/flota";
    }

    @GetMapping("/choferes")
    public String choferes(HttpSession sesion, Model model) {
        model.addAttribute("seccion", "camiones");
        model.addAttribute("pestania", "choferes");
        try {
            model.addAttribute("choferes", authClient.choferes(usuario(sesion)));
        } catch (ApiException e) {
            model.addAttribute("choferes", List.of());
            model.addAttribute("toast", e.getMessage());
            model.addAttribute("toastTipo", "error");
        }
        List<Map<String, Object>> camiones;
        try {
            camiones = api.camiones();
        } catch (ApiException e) {
            camiones = List.of();
        }
        model.addAttribute("camiones", camiones);
        Map<String, String> patentes = new HashMap<>();
        for (Map<String, Object> camion : camiones) {
            patentes.put(String.valueOf(camion.get("id")), String.valueOf(camion.get("patente")));
        }
        model.addAttribute("patentes", patentes);
        return "logistica/choferes";
    }

    @PostMapping("/choferes/{id}/eliminar")
    public String eliminarChofer(@PathVariable UUID id, HttpSession sesion, RedirectAttributes flash) {
        try {
            authClient.eliminarChofer(usuario(sesion), id);
        } catch (ApiException e) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", e.getMessage());
            return "redirect:/admin/flota/choferes";
        }
        flash.addFlashAttribute("toastTipo", "ok");
        flash.addFlashAttribute("toast", "Chofer eliminado.");
        return "redirect:/admin/flota/choferes";
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
        model.addAttribute("seccion", "camiones");
        model.addAttribute("camion", camion);
        model.addAttribute("ruta", ruta);
        model.addAttribute("puedeMantenimiento", "DISPONIBLE".equals(estado) && !rutaAbierta);
        model.addAttribute("puedeDisponible", "MANTENIMIENTO".equals(estado) && !rutaAbierta);
        model.addAttribute("puedeEliminar", !"EN_RUTA".equals(estado) && !rutaAbierta);
        return "logistica/camion";
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable UUID id, RedirectAttributes flash) {
        try {
            api.eliminarCamion(id);
        } catch (ApiException e) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", e.getMessage());
            return "redirect:/admin/flota/" + id;
        }
        flash.addFlashAttribute("toastTipo", "ok");
        flash.addFlashAttribute("toast", "Camión eliminado.");
        return "redirect:/admin/flota";
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
