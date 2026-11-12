package ar.utn.donatrack.frontend.comun.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.comun.util.Mapas;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;
import ar.utn.donatrack.frontend.notificaciones.services.NotificacionesApi;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Datos comunes a todas las vistas y pantalla de error unificada. */
@ControllerAdvice
public class AvisosAdvice {

    private final NotificacionesApi notificaciones;
    private final DonacionesApi donaciones;

    public AvisosAdvice(NotificacionesApi notificaciones, DonacionesApi donaciones) {
        this.notificaciones = notificaciones;
        this.donaciones = donaciones;
    }

    @ModelAttribute("usuario")
    public SesionUsuario usuario(HttpSession sesion) {
        return sesion.getAttribute(SesionUsuario.ATRIBUTO) instanceof SesionUsuario u ? u : null;
    }

    @ModelAttribute("rutaActual")
    public String ruta(HttpServletRequest request) {
        return request.getRequestURI();
    }

    /**
     * Cantidad de notificaciones sin leer del usuario logueado, para el
     * numerito de la campanita. El administrador recibe avisos por su propio
     * email (ej: entrega no satisfactoria); el chofer todavía no tiene
     * notificaciones propias. Si algo falla acá, no tiene que romper el
     * resto de la página: se devuelve 0 y listo.
     */
    @ModelAttribute("noLeidas")
    public long noLeidas(HttpSession sesion) {
        SesionUsuario u = sesion.getAttribute(SesionUsuario.ATRIBUTO) instanceof SesionUsuario su ? su : null;
        if (u == null) {
            return 0L;
        }
        try {
            List<String> destinatarios = new ArrayList<>();
            destinatarios.add(u.getEmail());
            if (u.getDonanteId() != null) {
                Mapas.lista(donaciones.donante(u.getDonanteId()).get("contactos"))
                        .forEach(c -> destinatarios.add(Mapas.texto(c, "valor")));
            } else if (u.getEntidadId() != null) {
                Mapas.lista(donaciones.entidad(u.getEntidadId()).get("contactos"))
                        .forEach(c -> destinatarios.add(Mapas.texto(c, "valor")));
            } else if (!SesionUsuario.ADMINISTRADOR.equals(u.getRol())) {
                return 0L;
            }
            return notificaciones.notificacionesDe(destinatarios).stream()
                    .filter(n -> !Boolean.TRUE.equals(n.get("leida")))
                    .count();
        } catch (RuntimeException e) {
            return 0L;
        }
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
