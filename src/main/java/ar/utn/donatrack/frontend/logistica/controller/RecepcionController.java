package ar.utn.donatrack.frontend.logistica.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.logistica.services.LogisticaApi;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Confirmación de la entidad beneficiaria. Entra con el id de la donación.
 *
 * Entrega 6 (decisión L2, alternativa A): pide sesión de entidad (SesionInterceptor)
 * y Logística confirma solo si la entrega es de esa entidad; si no, responde 403.
 * La recepción se confirma con las URLs de las fotos (una por renglón, hasta 10),
 * que llegan hasta Donaciones y se ven en la landing.
 */
@Controller
public class RecepcionController {

    static final int MAXIMO_DE_FOTOS = 10;
    private static final Pattern URL = Pattern.compile("^https?://\\S+$");
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
            model.addAttribute("camionTexto", textoDelCamion(entrega.get("camionId")));
            model.addAttribute("replanificable", esReplanificable(entrega.get("historial")));
        } catch (ApiException e) {
            if (e.getStatus() == 404) {
                model.addAttribute("noEncontrada", true);
                return "logistica/recepcion";
            }
            if (e.getStatus() == 403) {
                model.addAttribute("ajena", true);
                return "logistica/recepcion";
            }
            throw e;
        }
        return "logistica/recepcion";
    }

    @PostMapping("/recepcion")
    public String confirmar(@RequestParam UUID donacion, @RequestParam UUID entrega,
                            @RequestParam(defaultValue = "") String fotos, RedirectAttributes flash) {
        List<String> urls = Arrays.stream(fotos.split("\\R")).map(String::trim).filter(f -> !f.isEmpty()).toList();
        String problema = problemaCon(urls);
        if (problema != null) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", problema);
            flash.addFlashAttribute("fotosIngresadas", fotos);
            return "redirect:/recepcion?donacion=" + donacion;
        }
        try {
            api.confirmarEntrega(entrega, urls);
        } catch (ApiException e) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", e.getStatus() == 403 ? "Esta entrega no es para tu entidad." : e.getMessage());
            flash.addFlashAttribute("fotosIngresadas", fotos);
            return "redirect:/recepcion?donacion=" + donacion;
        }
        flash.addFlashAttribute("toastTipo", "ok");
        flash.addFlashAttribute("toast", "Recepción confirmada. ¡Gracias!");
        return "redirect:/recepcion?donacion=" + donacion;
    }

    static String problemaCon(List<String> urls) {
        if (urls.isEmpty()) {
            return "Agregá al menos una foto de lo que recibieron (su dirección web).";
        }
        if (urls.size() > MAXIMO_DE_FOTOS) {
            return "Se aceptan hasta " + MAXIMO_DE_FOTOS + " fotos.";
        }
        for (String url : urls) {
            if (!URL.matcher(url).matches() || url.length() > 2048) {
                return "Cada foto tiene que ser una dirección que empiece con http:// o https:// (" + url + ").";
            }
        }
        return null;
    }

    /** El camión se muestra como texto, sin entrar a la flota. */
    private String textoDelCamion(Object camionId) {
        if (camionId == null) {
            return null;
        }
        try {
            Object patente = api.camion(UUID.fromString(String.valueOf(camionId))).get("patente");
            if (patente != null && !String.valueOf(patente).isBlank()) {
                return String.valueOf(patente);
            }
        } catch (ApiException | IllegalArgumentException e) {
            return String.valueOf(camionId);
        }
        return String.valueOf(camionId);
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
