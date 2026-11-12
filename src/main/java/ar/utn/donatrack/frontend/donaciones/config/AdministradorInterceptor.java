package ar.utn.donatrack.frontend.donaciones.config;

import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Las pantallas de administración de donaciones son solo para quien entró con
 * el rol ADMINISTRADOR (el nombre que usa servicio-autenticacion). Sin sesión
 * vigente se vuelve al inicio, como en el resto del front; con otro rol, a su panel.
 */
@Component
public class AdministradorInterceptor implements HandlerInterceptor {

    public static final String ROL = "ADMINISTRADOR";

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        HttpSession sesion = request.getSession(false);
        Object attr = sesion == null ? null : sesion.getAttribute(SesionUsuario.ATRIBUTO);

        if (!(attr instanceof SesionUsuario usuario) || usuario.vencida()) {
            if (sesion != null) {
                sesion.invalidate();
            }
            response.sendRedirect("/?sesion=expirada");
            return false;
        }
        if (!ROL.equals(usuario.getRol())) {
            response.sendRedirect("/");
            return false;
        }
        return true;
    }
}
