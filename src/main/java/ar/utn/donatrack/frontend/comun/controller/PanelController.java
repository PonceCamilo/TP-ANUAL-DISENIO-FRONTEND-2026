package ar.utn.donatrack.frontend.comun.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.comun.util.Mapas;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;
import ar.utn.donatrack.frontend.incentivos.services.IncentivosApi;

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
 * Los dos paneles de inicio. Juntan datos de más de un servicio (donaciones e
 * incentivos), por eso no viven dentro de ninguno de ellos.
 */
@Controller
public class PanelController {

    private final DonacionesApi donaciones;
    private final IncentivosApi incentivos;

    public PanelController(DonacionesApi donaciones, IncentivosApi incentivos) {
        this.donaciones = donaciones;
        this.incentivos = incentivos;
    }

    private SesionUsuario usuario(HttpSession s) {
        return (SesionUsuario) s.getAttribute(SesionUsuario.ATRIBUTO);
    }

    private UUID donanteId(HttpSession s) {
        SesionUsuario u = usuario(s);
        donaciones.resolverIdentidad(u);
        return u.getDonanteId();
    }

    private UUID entidadId(HttpSession s) {
        SesionUsuario u = usuario(s);
        donaciones.resolverIdentidad(u);
        return u.getEntidadId();
    }

    @GetMapping("/donante/panel")
    public String panelDonante(HttpSession sesion, Model model) {
        UUID id = donanteId(sesion);
        if (id == null) {
            return "redirect:/donante/registro";
        }
        // Incentivos es un servicio aparte: si no responde (o el donante todavía no
        // tiene actividad allá), el panel igual se muestra, con un aviso.
        boolean logrosDisponibles = true;
        Map<String, Object> metricas;
        List<Map<String, Object>> misiones;
        List<Map<String, Object>> insignias;
        try {
            metricas = incentivos.metricas(id);
            misiones = incentivos.misiones(id);
            insignias = incentivos.insignias(id);
        } catch (ApiException e) {
            logrosDisponibles = false;
            metricas = Map.of("totalDonacionesHistoricas", 0, "donacionesMesActual", 0,
                    "organizacionesAyudadas", 0, "posicionRanking", 0, "categoriaActual", "Sin categoría");
            misiones = List.of();
            insignias = List.of();
        }
        model.addAttribute("logrosDisponibles", logrosDisponibles);
        model.addAttribute("metricas", metricas);
        model.addAttribute("misiones", misiones);
        model.addAttribute("insignias", insignias);
        List<Map<String, Object>> propias = donaciones.donacionesDe(id);
        model.addAttribute("ultimas", propias.stream().limit(3).toList());
        model.addAttribute("seccion", "panel");
        return "comun/panel-donante";
    }

    @GetMapping("/entidad/panel")
    public String panelEntidad(HttpSession sesion, Model model) {
        UUID id = entidadId(sesion);
        if (id == null) {
            return "redirect:/entidad/registro";
        }
        Map<String, Object> entidad = donaciones.entidad(id);
        List<Map<String, Object>> campanias = Mapas.lista(entidad.get("campanias"));
        int necesidades = 0;
        int satisfechas = 0;
        for (Map<String, Object> c : campanias) {
            for (Map<String, Object> n : Mapas.lista(c.get("necesidades"))) {
                necesidades++;
                if (Boolean.TRUE.equals(n.get("satisfecha"))) {
                    satisfechas++;
                }
            }
        }
        model.addAttribute("entidad", entidad);
        model.addAttribute("campanias", campanias);
        model.addAttribute("totalNecesidades", necesidades);
        model.addAttribute("necesidadesSatisfechas", satisfechas);
        model.addAttribute("recibidas", donaciones.donacionesHaciaEntidad(id).stream().limit(3).toList());
        model.addAttribute("seccion", "panel");
        return "comun/panel-entidad";
    }
}
