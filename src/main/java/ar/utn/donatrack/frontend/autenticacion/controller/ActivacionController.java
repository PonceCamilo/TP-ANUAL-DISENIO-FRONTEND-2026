package ar.utn.donatrack.frontend.autenticacion.controller;

import ar.utn.donatrack.frontend.autenticacion.services.AuthClient;
import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.comun.seguridad.VolverA;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Activar una cuenta (Entrega 6, requisito 20).
 *
 * Las personas que cargó la administración (por CSV o a mano) tienen una cuenta
 * sin contraseña. Ponen su email acá, reciben un enlace por correo y con él eligen
 * la contraseña. El mismo camino sirve para recuperar el acceso.
 *
 * - GET /activar: pedir el enlace; con ?codigo= (el enlace del correo), elegir la contraseña.
 * - POST /activar/pedir: manda el enlace. La respuesta es la misma exista o no la cuenta.
 * - POST /activar: guarda la contraseña y deja la sesión iniciada.
 */
@Controller
public class ActivacionController {

    private final AuthClient authClient;

    public ActivacionController(AuthClient authClient) {
        this.authClient = authClient;
    }

    @GetMapping("/activar")
    public String activar(@RequestParam(required = false) String codigo,
                          @RequestParam(defaultValue = "DONANTE") String rol, Model model) {
        model.addAttribute("codigo", codigo == null || codigo.isBlank() ? null : codigo.trim());
        model.addAttribute("rol", rolPublico(rol));
        return "autenticacion/activar";
    }

    @PostMapping("/activar/pedir")
    public String pedir(@RequestParam String email, @RequestParam(defaultValue = "DONANTE") String rol,
                        RedirectAttributes flash) {
        String rolOk = rolPublico(rol);
        try {
            authClient.pedirActivacion(email.trim(), rolOk);
        } catch (ApiException e) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", e.getMessage());
            flash.addFlashAttribute("emailIngresado", email.trim());
            return "redirect:/activar?rol=" + rolOk;
        }
        flash.addFlashAttribute("enlaceEnviado", true);
        flash.addFlashAttribute("emailIngresado", email.trim());
        return "redirect:/activar?rol=" + rolOk;
    }

    @PostMapping("/activar")
    public String confirmar(@RequestParam String codigo, @RequestParam String password, @RequestParam String repetir,
                            HttpSession sesion, RedirectAttributes flash) {
        if (!password.equals(repetir)) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", "Las contraseñas no coinciden.");
            return "redirect:/activar?codigo=" + codigo;
        }
        SesionUsuario usuario;
        try {
            usuario = authClient.activar(codigo.trim(), password);
        } catch (ApiException e) {
            // 404 enlace desconocido o reemplazado, 409 ya usado, 410 vencido: hay que pedir otro.
            if (e.getStatus() == 404 || e.getStatus() == 409 || e.getStatus() == 410) {
                flash.addFlashAttribute("enlaceInvalido", e.getMessage());
                return "redirect:/activar";
            }
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", e.getMessage());
            return "redirect:/activar?codigo=" + codigo;
        }
        sesion.setAttribute(SesionUsuario.ATRIBUTO, usuario);
        flash.addFlashAttribute("toastTipo", "ok");
        flash.addFlashAttribute("toast", "¡Listo! Tu contraseña quedó guardada y ya estás adentro.");
        return "redirect:" + VolverA.destino(sesion, usuario);
    }

    private static String rolPublico(String rol) {
        return "ENTIDAD_BENEFICIARIA".equals(rol) || "ENTIDAD".equals(rol)
                ? SesionUsuario.ENTIDAD_BENEFICIARIA : SesionUsuario.DONANTE;
    }
}
