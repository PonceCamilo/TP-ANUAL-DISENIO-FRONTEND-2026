package ar.utn.donatrack.frontend.seguridad;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Deja pasar solo a quien tiene sesión vigente y entra por su sección:
 * /donante/** para DONANTE y /entidad/** para ENTIDAD.
 */
@Component
public class SesionInterceptor implements HandlerInterceptor {

    @Override
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

        String ruta = request.getRequestURI();
        boolean rutaDonante = ruta.startsWith("/donante");
        if (rutaDonante != usuario.esDonante()) {
            response.sendRedirect(usuario.esDonante() ? "/donante/panel" : "/entidad/panel");
            return false;
        }
        return true;
    }
}
