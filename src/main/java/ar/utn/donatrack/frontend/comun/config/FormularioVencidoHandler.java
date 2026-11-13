package ar.utn.donatrack.frontend.comun.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

/**
 * Entrega 6: un formulario abierto desde antes de que la sesión venciera (o de que
 * el front se reiniciara) trae un token CSRF que ya no vale. Antes eso terminaba en
 * un 403 y la persona veía que "la primera vez no entra y al recargar sí". Ahora
 * vuelve a la pantalla del formulario con un aviso para que lo mande de nuevo; en
 * el ingreso se conserva el email. La protección CSRF sigue igual: no se procesa
 * nada de lo que se mandó.
 */
public class FormularioVencidoHandler implements AccessDeniedHandler {

    static final String AVISO = "La página estaba abierta desde hace un rato y se venció. Volvé a intentarlo.";
    private static final Set<String> INGRESOS = Set.of("/admin/ingreso", "/transporte/ingreso");

    private final AccessDeniedHandler resto = new AccessDeniedHandlerImpl();
    private final SessionFlashMapManager flashes = new SessionFlashMapManager();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException, jakarta.servlet.ServletException {
        if (!(e instanceof CsrfException) || !"POST".equalsIgnoreCase(request.getMethod())) {
            resto.handle(request, response, e);
            return;
        }
        if ("/salir".equals(ruta(request))) {
            // Salir con la sesión ya vencida: no hay nada que cerrar, se vuelve al inicio.
            if (request.getSession(false) != null) {
                request.getSession(false).invalidate();
            }
            response.sendRedirect(request.getContextPath() + "/");
            return;
        }
        String destino = destino(request);
        FlashMap flash = new FlashMap();
        flash.put("toastTipo", "error");
        flash.put("toast", AVISO);
        String email = request.getParameter("email");
        if (email != null && !email.isBlank() && esIngreso(request)) {
            flash.put("emailIngresado", email.strip());
        }
        flashes.saveOutputFlashMap(flash, request, response);
        response.sendRedirect(request.getContextPath() + destino);
    }

    private static boolean esIngreso(HttpServletRequest request) {
        String ruta = ruta(request);
        return "/login".equals(ruta) || INGRESOS.contains(ruta);
    }

    /** A dónde volver: la pantalla del ingreso, o la página desde la que se mandó el formulario. */
    static String destino(HttpServletRequest request) {
        String ruta = ruta(request);
        if ("/login".equals(ruta)) {
            String rol = request.getParameter("rol");
            return rol != null && rol.matches("[A-Z_]{1,40}") ? "/login?rol=" + rol : "/login";
        }
        if (INGRESOS.contains(ruta)) {
            return ruta;
        }
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                boolean mismoSitio = uri.getHost() == null || uri.getHost().equalsIgnoreCase(request.getServerName());
                String camino = uri.getRawPath();
                if (mismoSitio && camino != null && camino.startsWith("/") && !camino.startsWith("//")) {
                    String sinContexto = camino.startsWith(request.getContextPath())
                            ? camino.substring(request.getContextPath().length()) : camino;
                    String consulta = uri.getRawQuery();
                    return (sinContexto.isEmpty() ? "/" : sinContexto) + (consulta == null ? "" : "?" + consulta);
                }
            } catch (IllegalArgumentException ignorado) {
                // Referer inválido: se vuelve al inicio.
            }
        }
        return "/";
    }

    private static String ruta(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contexto = request.getContextPath();
        return uri.startsWith(contexto) ? uri.substring(contexto.length()) : uri;
    }
}
