package ar.utn.donatrack.frontend.comun.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Datos comunes a todas las vistas y pantalla de error unificada. */
@ControllerAdvice
public class AvisosAdvice {

    @ModelAttribute("usuario")
    public SesionUsuario usuario(HttpSession sesion) {
        return sesion.getAttribute(SesionUsuario.ATRIBUTO) instanceof SesionUsuario u ? u : null;
    }

    @ModelAttribute("rutaActual")
    public String ruta(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @ExceptionHandler(ApiException.class)
    public String falloDeServicio(ApiException e, Model model, HttpServletResponse respuesta) {
        respuesta.setStatus(e.getStatus() == 404 ? 404 : 502);
        model.addAttribute("titulo", e.getStatus() == 404 ? "No encontramos eso" : "Algo no salió como esperábamos");
        model.addAttribute("detalle", e.getMessage());
        return "comun/error";
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public String noExiste(Model model, HttpServletResponse respuesta) {
        respuesta.setStatus(404);
        model.addAttribute("titulo", "Esa página no existe");
        model.addAttribute("detalle", "Revisá la dirección o volvé al inicio.");
        return "comun/error";
    }

    @ExceptionHandler(Exception.class)
    public String inesperado(Exception e, Model model, HttpServletResponse respuesta) {
        respuesta.setStatus(500);
        model.addAttribute("titulo", "Algo no salió como esperábamos");
        model.addAttribute("detalle", "Tuvimos un problema inesperado. Probá de nuevo en unos minutos.");
        return "comun/error";
    }
}
