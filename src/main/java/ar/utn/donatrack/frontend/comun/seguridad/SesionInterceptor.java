package ar.utn.donatrack.frontend.comun.seguridad;


import java.io.IOException;
import java.util.Set;

import ar.utn.donatrack.frontend.autenticacion.services.AuthClient;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Deja pasar solo a quien tiene sesión vigente y entra por su sección:
 * Cada rol tiene un prefijo propio, protegido también al entrar por URL directa.
 *
 * Entrega 6: la recepción (/recepcion) es de la entidad beneficiaria y el mapa de
 * una ruta (/tracking) lo ve cualquiera con sesión (los servicios controlan que
 * sea suya). Quien llega sin sesión vuelve a esa página después de ingresar
 * (VolverA): así funcionan los enlaces de los avisos.
 */
@Component
public class SesionInterceptor implements HandlerInterceptor {
    private static final Set<String> CON_SESION = Set.of(SesionUsuario.DONANTE, SesionUsuario.ENTIDAD_BENEFICIARIA,
            SesionUsuario.ADMINISTRADOR, SesionUsuario.CHOFER);

    private final AuthClient autenticacion;

    public SesionInterceptor(AuthClient autenticacion) {
        this.autenticacion = autenticacion;
    }

    /** Los roles que pueden entrar a cada sección (el primer tramo de la ruta). */
    static Set<String> rolesDe(String area) {
        return switch (area) {
            case "donante" -> Set.of(SesionUsuario.DONANTE);
            case "entidad", "recepcion" -> Set.of(SesionUsuario.ENTIDAD_BENEFICIARIA);
            case "admin" -> Set.of(SesionUsuario.ADMINISTRADOR);
            case "transporte", "chofer" -> Set.of(SesionUsuario.CHOFER);
            case "tracking", "cuenta" -> CON_SESION;
            default -> Set.of();
        };
    }

    /** Si ese rol puede ver esa ruta. */
    public static boolean permite(String rol, String ruta) {
        return rolesDe(area(ruta)).contains(rol);
    }

    private static String area(String ruta) {
        String[] tramos = ruta.split("/", 3);
        return tramos.length > 1 ? tramos[1] : "";
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        String area = area(ruta);
        HttpSession sesion = request.getSession(false);
        Object attr = sesion == null ? null : sesion.getAttribute(SesionUsuario.ATRIBUTO);

        if (!(attr instanceof SesionUsuario usuario) || usuario.vencida()) {
            if (sesion != null) {
                sesion.invalidate();
            }
            if (ruta.endsWith("/posicion")) {
                // El mapa pide la posición con fetch: un 401 le avisa que recargue y vuelva a ingresar.
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
            VolverA.recordar(request);
            String ingreso = switch (area) {
                case "admin" -> "/admin/ingreso?sesion=expirada";
                case "transporte", "chofer" -> "/transporte/ingreso?sesion=expirada";
                case "recepcion" -> "/login?rol=ENTIDAD_BENEFICIARIA&sesion=expirada";
                case "cuenta" -> "/login?sesion=expirada";
                default -> "/?sesion=expirada";
            };
            response.sendRedirect(request.getContextPath() + ingreso);
            return false;
        }

        if (!rolesDe(area).contains(usuario.getRol())) {
            response.sendRedirect(request.getContextPath() + SesionUsuario.panelDe(usuario.getRol()));
            return false;
        }
        if (usuario.esDonanteJuridico() && ruta.startsWith("/donante/insignias/")) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        if (SesionUsuario.CHOFER.equals(usuario.getRol()) && !"tracking".equals(area)) {
            usuario.setCamionId(autenticacion.camionActual(usuario));
        }
        return true;
    }
}
