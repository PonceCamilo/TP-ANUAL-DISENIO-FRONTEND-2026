package ar.utn.donatrack.frontend.autenticacion.controller;

import ar.utn.donatrack.frontend.autenticacion.services.AuthClient;
import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Nombre visible de la cuenta. El email y la contraseña no se cambian acá. */
@Controller
public class CuentaController {

    private final AuthClient authClient;

    public CuentaController(AuthClient authClient) {
        this.authClient = authClient;
    }

    @GetMapping("/cuenta")
    public String ver(HttpSession sesion, Model model) {
        model.addAttribute("seccion", "cuenta");
        model.addAttribute("volver", SesionUsuario.panelDe(usuario(sesion).getRol()));
        return "autenticacion/cuenta";
    }

    @PostMapping("/cuenta")
    public String guardar(@RequestParam String nombre, HttpSession sesion, RedirectAttributes flash) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isBlank() || limpio.length() > 100) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", "El nombre tiene que tener entre 1 y 100 caracteres.");
            return "redirect:/cuenta";
        }
        try {
            sesion.setAttribute(SesionUsuario.ATRIBUTO, authClient.cambiarNombre(usuario(sesion), limpio));
        } catch (ApiException e) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", e.getMessage());
            return "redirect:/cuenta";
        }
        flash.addFlashAttribute("toastTipo", "ok");
        flash.addFlashAttribute("toast", "Guardamos tu nombre.");
        return "redirect:/cuenta";
    }

    private static SesionUsuario usuario(HttpSession sesion) {
        return (SesionUsuario) sesion.getAttribute(SesionUsuario.ATRIBUTO);
    }
}
