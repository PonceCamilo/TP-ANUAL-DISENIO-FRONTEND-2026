package ar.utn.donatrack.frontend.comun.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Volver a donde iba la persona después de ingresar (Entrega 6).
 *
 * Los enlaces de los avisos (el mapa de la ruta, la recepción) piden sesión. Si
 * alguien llega sin sesión, se guarda la página que pidió y, al ingresar, se lo
 * lleva ahí en lugar de a su panel. Solo se guardan rutas internas pedidas con
 * GET, y solo se usan si el rol con el que ingresó puede verlas: nunca sirve para
 * mandar a alguien a otro sitio.
 */
public final class VolverA {
    public static final String ATRIBUTO = "volverA";

    private VolverA() {}

    /** Guarda la página pedida en la sesión (nueva) de quien todavía no ingresó. */
    public static void recordar(HttpServletRequest pedido) {
        if (!"GET".equalsIgnoreCase(pedido.getMethod())) {
            return;
        }
        String ruta = pedido.getRequestURI().substring(pedido.getContextPath().length());
        if (!esInterna(ruta) || ruta.endsWith("/posicion")) {
            return;
        }
        String consulta = pedido.getQueryString();
        pedido.getSession(true).setAttribute(ATRIBUTO, consulta == null ? ruta : ruta + "?" + consulta);
    }

    /** A dónde ir después de ingresar: la página guardada si el rol la puede ver; si no, su panel. */
    public static String destino(HttpSession sesion, SesionUsuario usuario) {
        Object guardada = sesion.getAttribute(ATRIBUTO);
        sesion.removeAttribute(ATRIBUTO);
        if (guardada instanceof String ruta && esInterna(ruta)
                && SesionInterceptor.permite(usuario.getRol(), ruta.split("\\?", 2)[0])) {
            return ruta;
        }
        return SesionUsuario.panelDe(usuario.getRol());
    }

    static boolean esInterna(String ruta) {
        return ruta != null && ruta.startsWith("/") && !ruta.startsWith("//") && !ruta.contains("\\")
                && !ruta.contains("://");
    }
}
