package ar.utn.donatrack.frontend.notificaciones.controller;

import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.comun.util.Mapas;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;
import ar.utn.donatrack.frontend.notificaciones.services.NotificacionesApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpSession;

/**
 * Avisos de donante y entidad. El servicio de notificaciones no filtra por persona:
 * acá se junta el email de la cuenta con los contactos cargados en donaciones.
 */
@Controller
public class NotificacionesController {

    private final NotificacionesApi notificaciones;
    private final DonacionesApi donaciones;

    public NotificacionesController(NotificacionesApi notificaciones, DonacionesApi donaciones) {
        this.notificaciones = notificaciones;
        this.donaciones = donaciones;
    }

    private SesionUsuario usuario(HttpSession s) {
        return (SesionUsuario) s.getAttribute(SesionUsuario.ATRIBUTO);
    }

    private UUID donanteId(HttpSession s) {
        SesionUsuario u = usuario(s);
        return u.getDonanteId();
    }

    private UUID entidadId(HttpSession s) {
        SesionUsuario u = usuario(s);
        return u.getEntidadId();
    }

    @GetMapping("/donante/notificaciones")
    public String notificacionesDonante(HttpSession sesion, Model model) {
        SesionUsuario u = usuario(sesion);
        UUID id = donanteId(sesion);
        if (id == null) {
            return "redirect:/donante/registro";
        }
        List<String> destinatarios = new ArrayList<>();
        destinatarios.add(u.getEmail());
        Map<String, Object> donante = donaciones.donante(id);
        Mapas.lista(donante.get("contactos")).forEach(c -> destinatarios.add(Mapas.texto(c, "valor")));
        model.addAttribute("notificaciones", notificaciones.notificacionesDe(destinatarios));
        model.addAttribute("seccion", "notificaciones");
        return "notificaciones/donante";
    }

    @GetMapping("/entidad/notificaciones")
    public String notificacionesEntidad(HttpSession sesion, Model model) {
        UUID id = entidadId(sesion);
        if (id == null) {
            return "redirect:/entidad/registro";
        }
        List<String> destinatarios = new ArrayList<>();
        destinatarios.add(usuario(sesion).getEmail());
        Mapas.lista(donaciones.entidad(id).get("contactos")).forEach(c -> destinatarios.add(Mapas.texto(c, "valor")));
        model.addAttribute("notificaciones", notificaciones.notificacionesDe(destinatarios));
        model.addAttribute("seccion", "notificaciones");
        return "notificaciones/entidad";
    }
}
