package ar.utn.donatrack.frontend.autenticacion.controller;

import ar.utn.donatrack.frontend.autenticacion.services.AuthClient;
import ar.utn.donatrack.frontend.autenticacion.services.LoginGoogleHandler;
import ar.utn.donatrack.frontend.comun.config.FrontendProperties;
import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpSession;

/** Ingreso, creación de cuenta y Google. Habla con servicio-autenticacion. */
@Controller
public class AutenticacionController {

    private final AuthClient authClient;
    private final FrontendProperties props;
    private final boolean googleDisponible;

    public AutenticacionController(AuthClient authClient, FrontendProperties props,
                                   ObjectProvider<ClientRegistrationRepository> clientes) {
        this.authClient = authClient;
        this.props = props;
        this.googleDisponible = clientes.getIfAvailable() != null;
    }

    @GetMapping("/login")
    public String login(@RequestParam(defaultValue = "DONANTE") String rol, HttpSession sesion, Model model) {
        model.addAttribute("rol", rolValido(rol));
        model.addAttribute("googleDisponible", googleDisponible);
        model.addAttribute("demo", props.demoEnabled());
        Object error = sesion.getAttribute("errorLogin");
        if (error != null) {
            model.addAttribute("errorLogin", error);
            sesion.removeAttribute("errorLogin");
        }
        return "autenticacion/login";
    }

    /** Ingreso con email y contraseña. Cada rol tiene su pantalla y sus cuentas. */
    @PostMapping("/login")
    public String ingresar(@RequestParam String email, @RequestParam String password, @RequestParam String rol,
                           HttpSession sesion, RedirectAttributes flash) {
        String rolOk = rolValido(rol);
        try {
            sesion.setAttribute(SesionUsuario.ATRIBUTO, authClient.ingresar(email.trim(), password, rolOk));
            return "redirect:" + LoginGoogleHandler.destino(rolOk);
        } catch (ApiException e) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", e.getMessage());
            flash.addFlashAttribute("emailIngresado", email.trim());
            return "redirect:/login?rol=" + rolOk;
        }
    }

    @GetMapping("/crear-cuenta")
    public String crearCuenta(@RequestParam(defaultValue = "DONANTE") String rol, Model model) {
        model.addAttribute("rol", rolValido(rol));
        model.addAttribute("googleDisponible", googleDisponible);
        return "autenticacion/crear-cuenta";
    }

    @PostMapping("/crear-cuenta")
    public String registrar(@RequestParam String nombre, @RequestParam String email, @RequestParam String password,
                            @RequestParam String repetir, @RequestParam String rol,
                            HttpSession sesion, RedirectAttributes flash) {
        String rolOk = rolValido(rol);
        if (!password.equals(repetir)) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", "Las contraseñas no coinciden.");
            flash.addFlashAttribute("nombreIngresado", nombre.trim());
            flash.addFlashAttribute("emailIngresado", email.trim());
            return "redirect:/crear-cuenta?rol=" + rolOk;
        }
        try {
            sesion.setAttribute(SesionUsuario.ATRIBUTO, authClient.registrar(nombre.trim(), email.trim(), password, rolOk));
        } catch (ApiException e) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", e.getMessage());
            flash.addFlashAttribute("nombreIngresado", nombre.trim());
            flash.addFlashAttribute("emailIngresado", email.trim());
            return "redirect:/crear-cuenta?rol=" + rolOk;
        }
        flash.addFlashAttribute("toastTipo", "ok");
        flash.addFlashAttribute("toast", "¡Cuenta creada! Ya estás adentro.");
        return "redirect:" + LoginGoogleHandler.destino(rolOk);
    }

    private static String rolValido(String rol) {
        return SesionUsuario.ENTIDAD.equals(rol) ? SesionUsuario.ENTIDAD : SesionUsuario.DONANTE;
    }

    /** Guarda el rol elegido y delega en Spring Security el flujo OIDC con Google. */
    @GetMapping("/login/google")
    public String google(@RequestParam String rol, HttpSession sesion) {
        sesion.setAttribute(LoginGoogleHandler.ROL_ELEGIDO,
                SesionUsuario.ENTIDAD.equals(rol) ? SesionUsuario.ENTIDAD : SesionUsuario.DONANTE);
        return "redirect:/oauth2/authorization/google";
    }

    @PostMapping("/login/demo")
    public String demo(@RequestParam String email, @RequestParam String nombre, @RequestParam String rol,
                       HttpSession sesion, RedirectAttributes flash) {
        if (!props.demoEnabled()) {
            return "redirect:/";
        }
        String rolOk = SesionUsuario.ENTIDAD.equals(rol) ? SesionUsuario.ENTIDAD : SesionUsuario.DONANTE;
        try {
            sesion.setAttribute(SesionUsuario.ATRIBUTO, authClient.canjearDemo(email.trim(), nombre.trim(), rolOk));
            return "redirect:" + LoginGoogleHandler.destino(rolOk);
        } catch (ApiException e) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", e.getMessage());
            return "redirect:/login?rol=" + rolOk;
        }
    }
}
