package ar.utn.donatrack.frontend.comun.controller;

import ar.utn.donatrack.frontend.autenticacion.services.LoginGoogleHandler;
import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.comun.util.Mapas;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpSession;

/**
 * Pantalla de inicio (landing) y páginas públicas de información. Si ya hay
 * sesión, la landing manda al panel que corresponde.
 *
 * Entrega 6 (requisitos 1 y 2): la landing muestra las donaciones entregadas en
 * el último mes, sus fotos y un mapa con las entidades que las recibieron, con
 * los datos públicos de servicio-donaciones (no piden login ni traen datos
 * personales). Si el servicio no responde, la landing se muestra igual.
 */
@Controller
public class InicioController {

    /** Entregadas que se piden para el mapa; las primeras se muestran como ejemplos. */
    static final int ENTREGADAS_DEL_MAPA = 100;
    static final int EJEMPLOS_DESTACADOS = 6;

    private final DonacionesApi donaciones;

    public InicioController(DonacionesApi donaciones) {
        this.donaciones = donaciones;
    }

    @GetMapping("/")
    public String inicio(HttpSession sesion, Model model) {
        if (sesion.getAttribute(SesionUsuario.ATRIBUTO) instanceof SesionUsuario u && !u.vencida()) {
            return "redirect:" + LoginGoogleHandler.destino(u.getRol());
        }
        List<Map<String, Object>> entregadas = List.of();
        long total = 0;
        boolean disponibles = true;
        try {
            Map<String, Object> pagina = donaciones.entregadasRecientes(ENTREGADAS_DEL_MAPA);
            entregadas = Mapas.lista(pagina.get("contenido"));
            total = pagina.get("totalElementos") instanceof Number n ? n.longValue() : entregadas.size();
        } catch (ApiException e) {
            disponibles = false;
        }
        model.addAttribute("entregadasDisponibles", disponibles);
        model.addAttribute("totalEntregadas", total);
        model.addAttribute("destacadas", entregadas.stream().limit(EJEMPLOS_DESTACADOS).toList());
        model.addAttribute("enElMapa", entregadas.stream().filter(InicioController::tieneUbicacion).toList());
        return "comun/inicio";
    }

    /** Información legal (requisito 1). */
    @GetMapping("/legal")
    public String legal() {
        return "comun/legal";
    }

    /** Política de privacidad (requisito 1). */
    @GetMapping("/privacidad")
    public String privacidad() {
        return "comun/privacidad";
    }

    private static boolean tieneUbicacion(Map<String, Object> entregada) {
        Map<String, Object> entidad = Mapas.mapa(entregada.get("entidad"));
        return entidad.get("latitud") instanceof Number && entidad.get("longitud") instanceof Number;
    }
}
