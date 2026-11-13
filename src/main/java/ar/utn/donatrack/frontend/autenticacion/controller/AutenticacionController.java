package ar.utn.donatrack.frontend.autenticacion.controller;

import ar.utn.donatrack.frontend.autenticacion.services.AuthClient;
import ar.utn.donatrack.frontend.autenticacion.services.LoginGoogleHandler;
import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.comun.seguridad.VolverA;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpSession;

import java.util.UUID;

/** Ingreso, creación de cuenta y Google. Habla con servicio-autenticacion. */
@Controller
public class AutenticacionController {

    private final AuthClient authClient;
    private final boolean googleDisponible;

    public AutenticacionController(AuthClient authClient,
                                   ObjectProvider<ClientRegistrationRepository> clientes) {
        this.authClient = authClient;
        this.googleDisponible = clientes.getIfAvailable() != null;
    }

    @GetMapping("/login")
    public String login(@RequestParam(defaultValue = "DONANTE") String rol, HttpSession sesion, Model model) {
        model.addAttribute("rol", rolPublico(rol));
        model.addAttribute("googleDisponible", googleDisponible);
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
        String rolPublico = rolPublico(rol);
        return ingresarConRol(email, password, rolPublico, "/login?rol=" + rolPublico, sesion, flash);
    }

    @GetMapping("/admin/ingreso")
    public String loginAdministracion(HttpSession sesion, Model model) {
        return loginInterno("Administración", "/admin/ingreso", sesion, model);
    }

    @PostMapping("/admin/ingreso")
    public String ingresarAdministracion(@RequestParam String email, @RequestParam String password,
                                         HttpSession sesion, RedirectAttributes flash) {
        return ingresarConRol(email, password, SesionUsuario.ADMINISTRADOR, "/admin/ingreso", sesion, flash);
    }

    @GetMapping("/transporte/ingreso")
    public String loginTransporte(HttpSession sesion, Model model) {
        return loginInterno("Transporte", "/transporte/ingreso", sesion, model);
    }

    @PostMapping("/transporte/ingreso")
    public String ingresarTransporte(@RequestParam String email, @RequestParam String password,
                                      HttpSession sesion, RedirectAttributes flash) {
        return ingresarConRol(email, password, SesionUsuario.CHOFER, "/transporte/ingreso", sesion, flash);
    }

    private String loginInterno(String area, String accion, HttpSession sesion, Model model) {
        if (sesion.getAttribute(SesionUsuario.ATRIBUTO) instanceof SesionUsuario usuario && !usuario.vencida()) {
            return "redirect:" + SesionUsuario.panelDe(usuario.getRol());
        }
        model.addAttribute("area", area);
        model.addAttribute("accionIngreso", accion);
        return "autenticacion/login-interno";
    }

    private String ingresarConRol(String email, String password, String rol, String volverA,
                                  HttpSession sesion, RedirectAttributes flash) {
        try {
            SesionUsuario usuario = authClient.ingresar(email.trim(), password, rol);
            sesion.setAttribute(SesionUsuario.ATRIBUTO, usuario);
            // Entrega 6: si venía de un enlace (mapa, recepción), vuelve ahí.
            return "redirect:" + VolverA.destino(sesion, usuario);
        } catch (ApiException e) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", e.getMessage());
            flash.addFlashAttribute("emailIngresado", email.trim());
            return "redirect:" + volverA;
        }
    }

    @GetMapping("/crear-cuenta")
    public String crearCuenta(@RequestParam(defaultValue = "DONANTE") String rol, Model model) {
        model.addAttribute("rol", rolPublico(rol));
        model.addAttribute("googleDisponible", googleDisponible);
        return "autenticacion/crear-cuenta";
    }

    @PostMapping("/crear-cuenta")
    public String registrar(@RequestParam String nombre, @RequestParam String email, @RequestParam String password,
                            @RequestParam String repetir, @RequestParam String rol,
                            @RequestParam(defaultValue = "HUMANA") String tipoDonante,
                            HttpSession sesion, RedirectAttributes flash) {
        String rolOk = rolPublico(rol);
        flash.addFlashAttribute("tipoDonanteIngresado", tipoDonante);
        if (!password.equals(repetir)) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", "Las contraseñas no coinciden.");
            flash.addFlashAttribute("nombreIngresado", nombre.trim());
            flash.addFlashAttribute("emailIngresado", email.trim());
            return "redirect:/crear-cuenta?rol=" + rolOk;
        }
        try {
            sesion.setAttribute(SesionUsuario.ATRIBUTO, authClient.registrar(nombre.trim(), email.trim(), password, rolOk, tipoDonante));
        } catch (ApiException e) {
            if (e.getStatus() == 409) {
                // Entrega 6: puede ser una persona que cargó la administración; se la manda a activar.
                flash.addFlashAttribute("cuentaExistente", true);
            }
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

    @PostMapping("/admin/choferes")
    public String crearChofer(@RequestParam String nombre, @RequestParam String email, @RequestParam String password,
                              HttpSession sesion, RedirectAttributes flash) {
        SesionUsuario admin = (SesionUsuario) sesion.getAttribute(SesionUsuario.ATRIBUTO);
        try {
            authClient.crearChofer(admin, nombre.trim(), email.trim(), password);
            flash.addFlashAttribute("toastTipo", "ok");
            flash.addFlashAttribute("toast", "Cuenta de chofer creada. Compartile sus credenciales.");
        } catch (ApiException e) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", e.getMessage());
            flash.addFlashAttribute("nombreChofer", nombre.trim());
            flash.addFlashAttribute("emailChofer", email.trim());
        }
        // El formulario de choferes está en el panel de administración (/admin/camiones no existe).
        return "redirect:/admin/panel";
    }

    /** El camión queda en la cuenta del chofer. Logística no guarda esa relación. */
    @PostMapping("/admin/choferes/{id}/camion")
    public String asignarCamion(@PathVariable UUID id, @RequestParam UUID camionId,
                                HttpSession sesion, RedirectAttributes flash) {
        SesionUsuario admin = (SesionUsuario) sesion.getAttribute(SesionUsuario.ATRIBUTO);
        try {
            authClient.asignarCamion(admin, id, camionId);
            flash.addFlashAttribute("toastTipo", "ok");
            flash.addFlashAttribute("toast", "Camión asignado. El chofer lo va a ver en Mi ruta.");
        } catch (ApiException e) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", e.getMessage());
        }
        return "redirect:/admin/panel";
    }

    private static String rolPublico(String rol) {
        return switch (rol) {
            case "ENTIDAD", SesionUsuario.ENTIDAD_BENEFICIARIA -> SesionUsuario.ENTIDAD_BENEFICIARIA;
            default -> SesionUsuario.DONANTE;
        };
    }

    /** Guarda el rol elegido y delega en Spring Security el flujo OIDC con Google. */
    @GetMapping("/login/google")
    public String google(@RequestParam String rol, @RequestParam(defaultValue = "HUMANA") String tipoDonante,
                         HttpSession sesion) {
        sesion.setAttribute(LoginGoogleHandler.ROL_ELEGIDO, rolPublico(rol));
        sesion.setAttribute(LoginGoogleHandler.TIPO_ELEGIDO, tipoDonante);
        return "redirect:/oauth2/authorization/google";
    }

}
