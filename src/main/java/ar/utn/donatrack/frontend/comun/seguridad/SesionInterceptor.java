package ar.utn.donatrack.frontend.comun.seguridad;


import java.io.IOException;
import ar.utn.donatrack.frontend.autenticacion.services.AuthClient;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Deja pasar solo a quien tiene sesión vigente y entra por su sección:
 * Cada rol tiene un prefijo propio, protegido también al entrar por URL directa.
 */
@Component
public class SesionInterceptor implements HandlerInterceptor {
    private final AuthClient autenticacion;

    public SesionInterceptor(AuthClient autenticacion) {
        this.autenticacion = autenticacion;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        String area = ruta.split("/", 3)[1];
        HttpSession sesion = request.getSession(false);
        Object attr = sesion == null ? null : sesion.getAttribute(SesionUsuario.ATRIBUTO);

        if (!(attr instanceof SesionUsuario usuario) || usuario.vencida()) {
            if (sesion != null) {
                sesion.invalidate();
            }
            String ingreso = switch (area) {
                case "admin" -> "/admin/ingreso";
                case "transporte", "chofer" -> "/transporte/ingreso";
                default -> "/";
            };
            response.sendRedirect(request.getContextPath() + ingreso + "?sesion=expirada");
            return false;
        }

        String requerido = switch (area) {
            case "donante" -> SesionUsuario.DONANTE;
            case "entidad" -> SesionUsuario.ENTIDAD_BENEFICIARIA;
            case "admin" -> SesionUsuario.ADMINISTRADOR;
            case "transporte", "chofer" -> SesionUsuario.CHOFER;
            default -> "";
        };
        if (!requerido.equals(usuario.getRol())) {
            response.sendRedirect(request.getContextPath() + SesionUsuario.panelDe(usuario.getRol()));
            return false;
        }
        if (usuario.esDonanteJuridico() && ruta.startsWith("/donante/insignias/")) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        if (SesionUsuario.CHOFER.equals(requerido)) {
            usuario.setCamionId(autenticacion.camionActual(usuario));
        }
        return true;
    }
}
