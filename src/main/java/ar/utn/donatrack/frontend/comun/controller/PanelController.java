package ar.utn.donatrack.frontend.comun.controller;

import ar.utn.donatrack.frontend.autenticacion.services.AuthClient;
import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.comun.util.Mapas;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;
import ar.utn.donatrack.frontend.incentivos.services.IncentivosApi;
import ar.utn.donatrack.frontend.logistica.services.LogisticaApi;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final LogisticaApi logistica;
    private final AuthClient autenticacion;

    public PanelController(DonacionesApi donaciones, IncentivosApi incentivos,
                           LogisticaApi logistica, AuthClient autenticacion) {
        this.donaciones = donaciones;
        this.incentivos = incentivos;
        this.logistica = logistica;
        this.autenticacion = autenticacion;
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
            boolean personaHumana = !usuario(sesion).esDonanteJuridico();
            misiones = personaHumana ? incentivos.misiones(id) : List.of();
            insignias = personaHumana ? incentivos.insignias(id) : List.of();
        } catch (ApiException e) {
            logrosDisponibles = false;
            metricas = Map.of("totalDonacionesHistoricas", 0, "donacionesMesActual", 0,
                    "organizacionesAyudadas", 0, "posicionRanking", 0, "categoriaActual", "Sin categoría");
            misiones = List.of();
            insignias = List.of();
        }
        model.addAttribute("logrosDisponibles", logrosDisponibles);
        model.addAttribute("idPerfil", id);
        model.addAttribute("metricas", metricas);
        model.addAttribute("misiones", misiones);
        model.addAttribute("insignias", insignias);
        model.addAttribute("insigniasVisibles", insignias.stream()
                .filter(insignia -> Boolean.TRUE.equals(insignia.get("visible"))).toList());
        model.addAttribute("misionActiva", misiones.stream()
                .filter(mision -> Boolean.TRUE.equals(mision.get("activa"))).findFirst().orElse(null));
        cargarPodio(model, usuario(sesion));
        List<Map<String, Object>> propias = donaciones.donacionesDe(id);
        model.addAttribute("ultimas", propias.stream().limit(3).toList());
        model.addAttribute("seccion", "panel");
        return "comun/panel-donante";
    }

    @GetMapping("/perfiles/donantes/{id}")
    public String perfilPublico(@PathVariable UUID id, Model model) {
        Map<String, Object> donante = donaciones.donante(id);
        String nombre = donante.get("razonSocial") instanceof String razonSocial && !razonSocial.isBlank()
                ? razonSocial
                : (donante.getOrDefault("nombre", "Donante") + " " + donante.getOrDefault("apellido", "")).strip();
        Map<String, Object> perfil = incentivos.perfilPublico(id);
        model.addAttribute("nombrePerfil", nombre);
        model.addAttribute("categoriaPerfil", perfil.get("categoria"));
        model.addAttribute("insigniasVisibles", Mapas.lista(perfil.get("insignias")));
        return "incentivos/perfil-publico";
    }

    @PostMapping("/donante/insignias/{insigniaId}/visibilidad")
    public String cambiarVisibilidadInsignia(HttpSession sesion, @PathVariable long insigniaId,
                                             @RequestParam boolean visible, RedirectAttributes flash) {
        UUID id = donanteId(sesion);
        if (id == null) {
            return "redirect:/donante/registro";
        }
        try {
            incentivos.cambiarVisibilidad(id, insigniaId, visible);
            flash.addFlashAttribute("toast", visible ? "La insignia ahora aparece en tu perfil."
                    : "La insignia quedó oculta en tu perfil.");
        } catch (ApiException e) {
            flash.addFlashAttribute("toast", e.getMessage());
            flash.addFlashAttribute("toastTipo", "error");
        }
        return "redirect:/donante/panel#t-insignias";
    }

    private void cargarPodio(Model model, SesionUsuario usuario) {
        model.addAttribute("podioNoDisponible", false);
        try {
            Map<String, Object> ranking = incentivos.podio();
            Number mes = (Number) ranking.get("mes");
            String nombreMes = Month.of(mes.intValue()).getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-AR"));
            model.addAttribute("periodoPodio", nombreMes + " de " + ranking.get("anio"));
            model.addAttribute("podioProvisional", Boolean.TRUE.equals(ranking.get("provisional")));
            model.addAttribute("podio", Mapas.lista(ranking.get("puestos")).stream().map(puesto -> {
                Map<String, Object> fila = new LinkedHashMap<>(puesto);
                fila.put("nombre", nombreEnPodio(UUID.fromString(String.valueOf(puesto.get("donanteId"))), usuario));
                return fila;
            }).toList());
        } catch (ApiException e) {
            model.addAttribute("podioNoDisponible", true);
            model.addAttribute("podio", List.of());
        }
    }

    private String nombreEnPodio(UUID id, SesionUsuario usuario) {
        if (id.equals(usuario.getDonanteId())) {
            return usuario.getPrimerNombre() + " (vos)";
        }
        try {
            Map<String, Object> donante = donaciones.donante(id);
            if (donante.get("razonSocial") instanceof String razonSocial && !razonSocial.isBlank()) {
                return razonSocial;
            }
            if (donante.get("nombre") instanceof String nombre && !nombre.isBlank()) {
                if (donante.get("apellido") instanceof String apellido && !apellido.isBlank()) {
                    return nombre + " " + apellido.strip().charAt(0) + ".";
                }
                return nombre;
            }
        } catch (ApiException e) {
            return "Donante";
        }
        return "Donante";
    }

    @GetMapping("/admin/panel")
    public String panelAdministracion(HttpSession sesion, Model model) {
        model.addAttribute("seccion", "panel");
        model.addAttribute("area", "Administración");
        model.addAttribute("descripcionArea", "Área interna para la gestión de DonaTrack.");
        model.addAttribute("flotaNoDisponible", false);
        model.addAttribute("choferesNoDisponibles", false);
        List<Map<String, Object>> camiones = camionesParaAsignar(model);
        List<Map<String, Object>> choferes = choferesParaAsignar(sesion, model);
        model.addAttribute("camiones", camiones);
        model.addAttribute("choferes", conPatente(choferes, camiones));
        return "autenticacion/panel-interno";
    }

    @GetMapping({"/chofer/panel", "/transporte/panel"})
    public String panelTransporte(HttpSession sesion, Model model) {
        model.addAttribute("seccion", "panel");
        UUID camionId = usuario(sesion).getCamionId();
        model.addAttribute("camionAsignado", camionId);
        if (camionId != null) {
            try {
                model.addAttribute("patenteAsignada", logistica.camion(camionId).get("patente"));
            } catch (ApiException e) {
                model.addAttribute("patenteNoDisponible", true);
            }
        }
        model.addAttribute("area", "Transporte");
        model.addAttribute("descripcionArea", "Área del equipo que realiza las entregas.");
        return "autenticacion/panel-interno";
    }

    private List<Map<String, Object>> camionesParaAsignar(Model model) {
        try {
            return logistica.camiones();
        } catch (ApiException e) {
            model.addAttribute("flotaNoDisponible", true);
            return List.of();
        }
    }

    private List<Map<String, Object>> choferesParaAsignar(HttpSession sesion, Model model) {
        try {
            return autenticacion.choferes(usuario(sesion));
        } catch (ApiException e) {
            model.addAttribute("choferesNoDisponibles", true);
            return List.of();
        }
    }

    /** Cruza el camionId de la cuenta con la patente que conoce logística. */
    private List<Map<String, Object>> conPatente(List<Map<String, Object>> choferes,
                                                 List<Map<String, Object>> camiones) {
        Map<String, String> patentes = new HashMap<>();
        for (Map<String, Object> camion : camiones) {
            if (camion.get("id") != null && camion.get("patente") != null) {
                patentes.put(String.valueOf(camion.get("id")), String.valueOf(camion.get("patente")));
            }
        }
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Map<String, Object> chofer : choferes) {
            Map<String, Object> fila = new LinkedHashMap<>(chofer);
            Object camionId = chofer.get("camionId");
            if (camionId != null) {
                String patente = patentes.get(String.valueOf(camionId));
                fila.put("patente", patente);
                fila.put("camionPerdido", patente == null);
            }
            filas.add(fila);
        }
        return filas;
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
