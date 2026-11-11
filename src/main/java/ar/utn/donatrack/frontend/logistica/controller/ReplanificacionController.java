package ar.utn.donatrack.frontend.logistica.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.logistica.services.LogisticaApi;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Disparo manual de la replanificación. En el servicio corre solo,
 * en horario de baja carga; este botón existe para probarla.
 * El prefijo /admin lo deja en la persona administradora.
 */
@Controller
public class ReplanificacionController {

    private final LogisticaApi api;

    public ReplanificacionController(LogisticaApi api) {
        this.api = api;
    }

    @PostMapping("/admin/replanificar")
    public String replanificar(RedirectAttributes flash) {
        try {
            int rutas = contarRutas(api.replanificarPendientes());
            flash.addFlashAttribute("toastTipo", "ok");
            flash.addFlashAttribute("toast", mensaje(rutas));
        } catch (ApiException e) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", e.getMessage());
        }
        return "redirect:/admin/panel";
    }

    private static int contarRutas(List<Map<String, Object>> lotes) {
        int rutas = 0;
        for (Map<String, Object> lote : lotes) {
            if (lote.get("rutas") instanceof List<?> lista) {
                rutas += lista.size();
            }
        }
        return rutas;
    }

    private static String mensaje(int rutas) {
        if (rutas == 0) {
            return "No hay entregas en el depósito listas para una ruta nueva.";
        }
        if (rutas == 1) {
            return "Se armó 1 ruta nueva. El chofer la ve en Mi ruta si es su camión.";
        }
        return "Se armaron " + rutas + " rutas nuevas. Cada chofer ve la de su camión.";
    }
}
