package ar.utn.donatrack.frontend.comun.controller;

import ar.utn.donatrack.frontend.autenticacion.services.LoginGoogleHandler;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpSession;

/** Pantalla de inicio (landing). Si ya hay sesión, manda al panel que corresponde. */
@Controller
public class InicioController {

    @GetMapping("/")
    public String inicio(HttpSession sesion, Model model) {
        if (sesion.getAttribute(SesionUsuario.ATRIBUTO) instanceof SesionUsuario u && !u.vencida()) {
            return "redirect:" + LoginGoogleHandler.destino(u.getRol());
        }
        return "comun/inicio";
    }
}
