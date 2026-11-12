package ar.utn.donatrack.frontend.comun.controller;

import ar.utn.donatrack.frontend.comun.exceptions.SesionVencidaException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Si un servicio rechaza el token de la sesión (401), la sesión ya no sirve: se
 * cierra y se vuelve al ingreso que corresponde, con el mismo aviso de "sesión
 * vencida" que usa SesionInterceptor. Va antes que AvisosAdvice, que trata
 * cualquier otro error como una falla del servicio.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SesionVencidaAdvice {

    @ExceptionHandler(SesionVencidaException.class)
    public String sesionVencida(HttpServletRequest pedido) {
        HttpSession sesion = pedido.getSession(false);
        if (sesion != null) {
            sesion.invalidate();
        }
        String ruta = pedido.getRequestURI().substring(pedido.getContextPath().length());
        String ingreso = ruta.startsWith("/admin") ? "/admin/ingreso"
                : ruta.startsWith("/transporte") || ruta.startsWith("/chofer") ? "/transporte/ingreso" : "/";
        return "redirect:" + ingreso + "?sesion=expirada";
    }
}
