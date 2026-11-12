package ar.utn.donatrack.frontend.donaciones.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.autenticacion.services.AuthClient;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.comun.util.Mapas;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;

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

/** Pantallas de la entidad que vienen de servicio-donaciones: campañas, necesidades, donaciones a recibir y registro. */
@Controller
@RequestMapping("/entidad")
public class EntidadDonacionesController {

    private final DonacionesApi api;
    private final AuthClient autenticacion;
    private static final String PERFIL_PENDIENTE = "perfilPendienteDeVincular";

    public EntidadDonacionesController(DonacionesApi api, AuthClient autenticacion) {
        this.autenticacion = autenticacion;
        this.api = api;
    }

    private SesionUsuario usuario(HttpSession s) {
        return (SesionUsuario) s.getAttribute(SesionUsuario.ATRIBUTO);
    }

    private UUID entidadId(HttpSession s) {
        SesionUsuario u = usuario(s);
        return u.getEntidadId();
    }

    @GetMapping("/campanias/nueva")
    public String nuevaCampania(HttpSession sesion, Model model) {
        if (entidadId(sesion) == null) {
            return "redirect:/entidad/registro";
        }
        model.addAttribute("seccion", "panel");
        return "donaciones/entidad/campania-nueva";
    }

    @PostMapping("/campanias")
    public String crearCampania(@RequestParam String descripcionGeneral, @RequestParam String fechaInicio,
                                @RequestParam String fechaFin, HttpSession sesion, RedirectAttributes flash) {
        UUID id = entidadId(sesion);
        if (id == null) {
            return "redirect:/entidad/registro";
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("descripcionGeneral", descripcionGeneral.trim());
        cuerpo.put("fechaInicio", fechaInicio);
        cuerpo.put("fechaFin", fechaFin);
        try {
            api.crearCampania(id, cuerpo);
        } catch (ApiException e) {
            return volverConError(flash, e, "/entidad/campanias/nueva");
        }
        return conExito(flash, "Campaña creada. Ahora podés sumarle necesidades.", "/entidad/panel");
    }

    @GetMapping("/campanias/{campaniaId}/necesidades/nueva")
    public String nuevaNecesidad(@PathVariable UUID campaniaId, HttpSession sesion, Model model) {
        if (entidadId(sesion) == null) {
            return "redirect:/entidad/registro";
        }
        model.addAttribute("campaniaId", campaniaId);
        model.addAttribute("seccion", "panel");
        return "donaciones/entidad/necesidad-nueva";
    }

    @PostMapping("/campanias/{campaniaId}/necesidades")
    public String crearNecesidad(@PathVariable UUID campaniaId, @RequestParam String nombre,
                                 @RequestParam(required = false) String descripcion,
                                 @RequestParam String subcategoria,
                                 @RequestParam(required = false) String subcategoriaOtra,
                                 @RequestParam int cantidadObjetivo, @RequestParam String tipo,
                                 @RequestParam(required = false) String periodo,
                                 HttpSession sesion, RedirectAttributes flash) {
        UUID id = entidadId(sesion);
        if (id == null) {
            return "redirect:/entidad/registro";
        }
        String volverAlFormulario = "/entidad/campanias/" + campaniaId + "/necesidades/nueva";
        // Mismos tipos que elige el donante: el back cruza la necesidad con la
        // donación comparando estas subcategorías.
        boolean esOtroTipo = "Otro".equals(subcategoria);
        if (esOtroTipo && (subcategoriaOtra == null || subcategoriaOtra.isBlank())) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", "Indicá qué tipo de cosa necesitás al elegir Otro.");
            return "redirect:" + volverAlFormulario;
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipo", "RECURRENTE".equals(tipo) ? "RECURRENTE" : "EXTRAORDINARIA");
        cuerpo.put("nombre", nombre.trim());
        cuerpo.put("descripcion", descripcion == null ? "" : descripcion.trim());
        cuerpo.put("subcategoria", esOtroTipo ? subcategoriaOtra.trim() : subcategoria.trim());
        cuerpo.put("cantidadObjetivo", cantidadObjetivo);
        if ("RECURRENTE".equals(tipo)) {
            cuerpo.put("periodo", periodo == null || periodo.isBlank() ? "MENSUAL" : periodo);
        }
        try {
            api.crearNecesidad(id, campaniaId, cuerpo);
        } catch (ApiException e) {
            return volverConError(flash, e, volverAlFormulario);
        }
        return conExito(flash, "Necesidad agregada a la campaña.", "/entidad/panel");
    }

    @PostMapping("/campanias/{campaniaId}/necesidades/{necesidadId}/eliminar")
    public String eliminarNecesidad(@PathVariable UUID campaniaId, @PathVariable UUID necesidadId,
                                    HttpSession sesion, RedirectAttributes flash) {
        UUID id = entidadId(sesion);
        if (id == null) {
            return "redirect:/entidad/registro";
        }
        try {
            api.eliminarNecesidad(id, campaniaId, necesidadId);
        } catch (ApiException e) {
            return volverConError(flash, e, "/entidad/panel");
        }
        return conExito(flash, "Eliminamos la necesidad.", "/entidad/panel");
    }

    @GetMapping("/recepciones")
    public String recepciones(HttpSession sesion, Model model) {
        UUID id = entidadId(sesion);
        if (id == null) {
            return "redirect:/entidad/registro";
        }
        model.addAttribute("donaciones", api.donacionesHaciaEntidad(id));
        model.addAttribute("seccion", "recepciones");
        return "donaciones/entidad/recepciones";
    }

    /**
     * Algoritmo del back que compara la subcategoría de la donación con las
     * necesidades abiertas de cada entidad. Es el único que mira qué necesita la
     * entidad: PRIORIDAD_SUBATENDIDOS le da puntaje a todas, así que si se lo
     * tuviera en cuenta a cualquier entidad le aparecería cualquier donación.
     */
    private static final String ALGORITMO_COMPATIBILIDAD = "COMPATIBILIDAD_SEMANTICA";

    /**
     * Donaciones en depósito para las que esta entidad tiene alguna necesidad
     * compatible. El back responde {@code rankings: [{algoritmo, entidades}]}.
     */
    @GetMapping("/disponibles")
    public String disponibles(HttpSession sesion, Model model) {
        UUID id = entidadId(sesion);
        if (id == null) {
            return "redirect:/entidad/registro";
        }
        List<Map<String, Object>> aptas = new ArrayList<>();
        for (Map<String, Object> d : api.donacionesEnDeposito()) {
            Map<String, Object> cand = api.candidatos(UUID.fromString(Mapas.texto(d, "id")));
            boolean candidata = Mapas.lista(cand.get("rankings")).stream()
                    .filter(r -> ALGORITMO_COMPATIBILIDAD.equals(Mapas.texto(r, "algoritmo")))
                    .flatMap(r -> Mapas.lista(r.get("entidades")).stream())
                    .anyMatch(e -> String.valueOf(id).equals(Mapas.texto(e, "id")));
            if (candidata) {
                aptas.add(d);
            }
        }
        model.addAttribute("donaciones", aptas);
        model.addAttribute("seccion", "disponibles");
        return "donaciones/entidad/disponibles";
    }

    @PostMapping("/disponibles/{donacionId}/aceptar")
    public String aceptar(@PathVariable UUID donacionId, HttpSession sesion, RedirectAttributes flash) {
        UUID id = entidadId(sesion);
        if (id == null) {
            return "redirect:/entidad/registro";
        }
        try {
            api.asignar(donacionId, id);
        } catch (ApiException e) {
            return volverConError(flash, e, "/entidad/disponibles");
        }
        return conExito(flash, "Aceptaste la donación. Logística coordinará la entrega.", "/entidad/recepciones");
    }

    @GetMapping("/registro")
    public String registro(HttpSession sesion, Model model) {
        if (entidadId(sesion) != null) {
            return "redirect:/entidad/panel";
        }
        model.addAttribute("seccion", "elegir");
        return "donaciones/entidad/registro";
    }

    @PostMapping("/registro")
    public String registrar(@RequestParam String razonSocial,
                            @RequestParam String representanteNombre, @RequestParam String representanteApellido,
                            @RequestParam String calle, @RequestParam int numero, @RequestParam String codigoPostal,
                            @RequestParam String localidad, @RequestParam String provincia,
                            @RequestParam(required = false) String telefono,
                            HttpSession sesion, RedirectAttributes flash) {
        SesionUsuario u = usuario(sesion);
        if (u.getEntidadId() != null) {
            return "redirect:/entidad/panel";
        }
        String clavePendiente = PERFIL_PENDIENTE + u.getRol() + u.getEmail();
        // El email de la cuenta queda como contacto y email del representante.
        List<Map<String, Object>> contactos = new ArrayList<>();
        contactos.add(Map.of("tipo", "EMAIL", "valor", u.getEmail()));
        if (telefono != null && !telefono.isBlank()) {
            contactos.add(Map.of("tipo", "TELEFONO", "valor", telefono.trim()));
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("razonSocial", razonSocial.trim());
        cuerpo.put("direccion", Map.of(
                "calle", calle.trim(), "numero", numero, "codigoPostal", codigoPostal.trim(),
                "localidad", Map.of("nombre", localidad.trim(), "provincia", Map.of("nombre", provincia.trim()))));
        cuerpo.put("contactos", contactos);
        cuerpo.put("representantes", List.of(Map.of(
                "nombre", representanteNombre.trim(), "apellido", representanteApellido.trim(), "email", u.getEmail())));
        try {
            autenticacion.exigirVinculacionConfigurada();
            UUID id = (UUID) sesion.getAttribute(clavePendiente);
            if (id == null) {
                id = api.crearEntidad(cuerpo);
                sesion.setAttribute(clavePendiente, id);
            }
            sesion.setAttribute(SesionUsuario.ATRIBUTO, autenticacion.vincularPerfil(u, id));
            sesion.removeAttribute(clavePendiente);
        } catch (ApiException e) {
            return volverConError(flash, e, "/entidad/registro");
        }
        return conExito(flash, "¡Listo! Registramos tu entidad.", "/entidad/panel");
    }

    private String volverConError(RedirectAttributes flash, ApiException e, String destino) {
        flash.addFlashAttribute("toastTipo", "error");
        flash.addFlashAttribute("toast", e.getMessage());
        return "redirect:" + destino;
    }

    private String conExito(RedirectAttributes flash, String mensaje, String destino) {
        flash.addFlashAttribute("toastTipo", "ok");
        flash.addFlashAttribute("toast", mensaje);
        return "redirect:" + destino;
    }
}
