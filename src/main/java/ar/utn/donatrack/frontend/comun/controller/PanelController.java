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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
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
        // Entrega 6: métricas, misiones e insignias se piden por separado. Antes iban
        // juntas y, si fallaba una (por ejemplo las métricas de alguien sin actividad
        // todavía), desaparecían también las insignias.
        boolean logrosDisponibles = true;
        boolean misionesDisponibles = true;
        boolean insigniasDisponibles = true;
        boolean personaHumana = !usuario(sesion).esDonanteJuridico();
        Map<String, Object> metricas;
        List<Map<String, Object>> misiones = List.of();
        List<Map<String, Object>> insignias = List.of();
        try {
            metricas = incentivos.metricas(id);
        } catch (ApiException e) {
            logrosDisponibles = false;
            metricas = Map.of("totalDonacionesHistoricas", 0, "donacionesMesActual", 0,
                    "organizacionesAyudadas", 0, "posicionRanking", 0, "categoriaActual", "Sin categoría");
        }
        if (personaHumana) {
            try {
                misiones = incentivos.misiones(id);
            } catch (ApiException e) {
                misionesDisponibles = false;
            }
            try {
                insignias = incentivos.insignias(id);
            } catch (ApiException e) {
                insigniasDisponibles = false;
            }
        }
        model.addAttribute("misionesDisponibles", misionesDisponibles);
        model.addAttribute("insigniasDisponibles", insigniasDisponibles);
        model.addAttribute("logrosDisponibles", logrosDisponibles);
        model.addAttribute("idPerfil", id);
        model.addAttribute("metricas", metricas);
        model.addAttribute("misiones", misiones);
        model.addAttribute("insignias", insignias);
        model.addAttribute("hayInsigniasVisibles", insignias.stream()
                .anyMatch(insignia -> Boolean.TRUE.equals(insignia.get("visible"))));
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
        model.addAttribute("insigniasVisibles", IncentivosApi.sinRepetir(Mapas.lista(perfil.get("insignias"))));
        return "incentivos/perfil-publico";
    }

    @PostMapping(value = "/donante/insignias/{insigniaId}/visibilidad", headers = "!X-Actualizar-Insignia")
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

    @PostMapping(value = "/donante/insignias/{insigniaId}/visibilidad", headers = "X-Actualizar-Insignia")
    public ResponseEntity<?> actualizarVisibilidadInsignia(HttpSession sesion, @PathVariable long insigniaId,
                                                          @RequestParam boolean visible) {
        UUID id = donanteId(sesion);
        if (id == null) {
            return ResponseEntity.status(409).body(Map.of("mensaje", "Completá tu perfil de donante."));
        }
        try {
            incentivos.cambiarVisibilidad(id, insigniaId, visible);
            return ResponseEntity.noContent().build();
        } catch (ApiException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("mensaje", e.getMessage()));
        }
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
                fila.put("nombre", nombreEnPodio(UUID.fromString(String.valueOf(puesto.get("donanteId"))),
                        Mapas.texto(puesto, "nombreVisible"), usuario));
                return fila;
            }).toList());
        } catch (ApiException e) {
            model.addAttribute("podioNoDisponible", true);
            model.addAttribute("podio", List.of());
        }
    }

    /**
     * Entrega 6: el nombre lo devuelve Incentivos (nombre e inicial del apellido, o
     * razón social), que lo recibe de Donaciones. Ya no se pide el perfil de otras
     * personas a Donaciones: desde la Entrega 6 responde 403.
     */
    static String nombreEnPodio(UUID id, String nombreVisible, SesionUsuario usuario) {
        if (id.equals(usuario.getDonanteId())) {
            return usuario.getPrimerNombre() + " (vos)";
        }
        return nombreVisible == null || nombreVisible.isBlank() ? "Donante" : nombreVisible;
    }

    @GetMapping("/admin/panel")
    public String panelAdministracion(Model model) {
        model.addAttribute("seccion", "panel");
        model.addAttribute("area", "Administración");
        model.addAttribute("descripcionArea", "Área interna para la gestión de DonaTrack.");
        try {
            // Entrega 6: "pendientes de asignar" son todas las que están en depósito (antes
            // solo las del cálculo nocturno, y las recién cargadas no aparecían).
            List<Map<String, Object>> enDeposito = donaciones.donacionesEnDeposito();
            List<Map<String, Object>> pendientes = enDeposito;
            List<Map<String, Object>> camiones = logistica.camiones();
            long totalDonantes = donaciones.paginaDeDonantes(null, null, 1).getTotalElementos();

            model.addAttribute("totalEnDeposito", enDeposito.size());
            model.addAttribute("totalPendientes", pendientes.size());
            model.addAttribute("totalCamionesDisponibles",
                    camiones.stream().filter(c -> Mapas.igual(Mapas.texto(c, "estado"), "DISPONIBLE")).count());
            model.addAttribute("totalDonantes", totalDonantes);

            model.addAttribute("pendientesPreview", pendientes.stream().limit(3).map(p -> {
                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("idDonacion", Mapas.texto(p, "id"));
                fila.put("donacion", p);
                try {
                    Object idDonante = p.get("idDonante");
                    if (idDonante != null) {
                        fila.put("nombreDonante", nombreCorto(donaciones.donante(UUID.fromString(String.valueOf(idDonante)))));
                    }
                } catch (ApiException | IllegalArgumentException ignorado) {
                    // Sin el nombre de quien donó, la fila igual se muestra.
                }
                return fila;
            }).toList());
            model.addAttribute("flotaPreview", camiones.stream().limit(3).toList());
        } catch (ApiException e) {
            model.addAttribute("totalEnDeposito", 0);
            model.addAttribute("totalPendientes", 0);
            model.addAttribute("totalCamionesDisponibles", 0);
            model.addAttribute("totalDonantes", 0);
            model.addAttribute("pendientesPreview", List.of());
            model.addAttribute("flotaPreview", List.of());
            model.addAttribute("toast", e.getMessage());
            model.addAttribute("toastTipo", "error");
        }
        return "autenticacion/admin-panel";
    }

    private String nombreCorto(Map<String, Object> donante) {
        if (donante.get("razonSocial") instanceof String razonSocial && !razonSocial.isBlank()) {
            return razonSocial;
        }
        String nombre = Mapas.texto(donante, "nombre");
        String apellido = Mapas.texto(donante, "apellido");
        return (nombre + " " + apellido).strip();
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
