package ar.utn.donatrack.frontend.donaciones.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.autenticacion.services.AuthClient;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.comun.util.Mapas;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;

import java.util.ArrayList;
import java.util.HashMap;
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

/** Pantallas del donante que vienen de servicio-donaciones: donar, mis donaciones, seguimiento y registro. */
@Controller
@RequestMapping("/donante")
public class DonanteDonacionesController {

    private final DonacionesApi api;
    private final AuthClient autenticacion;
    private static final String PERFIL_PENDIENTE = "perfilPendienteDeVincular";

    public DonanteDonacionesController(DonacionesApi api, AuthClient autenticacion) {
        this.autenticacion = autenticacion;
        this.api = api;
    }

    private SesionUsuario usuario(HttpSession s) {
        return (SesionUsuario) s.getAttribute(SesionUsuario.ATRIBUTO);
    }

    /** Devuelve el id del donante o null si todavía no completó su registro. */
    private UUID donanteId(HttpSession s) {
        SesionUsuario u = usuario(s);
        return u.getDonanteId();
    }

    @GetMapping("/donaciones")
    public String donaciones(@RequestParam(required = false) String estado, HttpSession sesion, Model model) {
        UUID id = donanteId(sesion);
        if (id == null) {
            return "redirect:/donante/registro";
        }
        List<Map<String, Object>> todas = api.donacionesDe(id);
        List<Map<String, Object>> filtradas = (estado == null || estado.isBlank())
                ? todas
                : todas.stream().filter(d -> estado.equals(Mapas.texto(d, "estado"))).toList();
        model.addAttribute("donaciones", filtradas);
        model.addAttribute("estadoFiltro", estado == null ? "" : estado);
        model.addAttribute("seccion", "donaciones");
        return "donaciones/donante/donaciones";
    }

    @GetMapping("/donaciones/{id}")
    public String detalle(@PathVariable UUID id, HttpSession sesion, Model model) {
        UUID donanteId = donanteId(sesion);
        if (donanteId == null) {
            return "redirect:/donante/registro";
        }
        Map<String, Object> donacion = api.donacion(id);
        if (!String.valueOf(donanteId).equals(Mapas.texto(donacion, "idDonante"))) {
            throw new ApiException("Esa donación no está entre las tuyas.", 404);
        }
        model.addAttribute("donacion", donacion);
        model.addAttribute("historial", Mapas.lista(donacion.get("historialEstados")));
        model.addAttribute("seccion", "donaciones");
        return "donaciones/donante/detalle";
    }

    @GetMapping("/donar")
    public String donar(HttpSession sesion, Model model) {
        if (donanteId(sesion) == null) {
            return "redirect:/donante/registro";
        }
        model.addAttribute("seccion", "donar");
        return "donaciones/donante/donar";
    }

    @PostMapping("/donar")
    public String enviarDonacion(@RequestParam String descripcion,
                                 @RequestParam String subcategoria,
                                 @RequestParam(required = false) String subcategoriaOtra,
                                 @RequestParam(required = false) String detalle,
                                 @RequestParam int cantidad,
                                 @RequestParam String unidad,
                                 @RequestParam(defaultValue = "true") boolean esNuevo,
                                 @RequestParam(required = false) String fechaVencimiento,
                                 HttpSession sesion, RedirectAttributes flash) {
        UUID id = donanteId(sesion);
        if (id == null) {
            return "redirect:/donante/registro";
        }
        // Validación de Lucía: "Otro" exige decir qué es. El cuerpo lo arma FormularioDonacion,
        // el mismo que usa el formulario de la administración.
        if ("Otro".equals(subcategoria) && (subcategoriaOtra == null || subcategoriaOtra.isBlank())) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", "Indicá qué tipo de donación es al elegir Otro.");
            return "redirect:/donante/donar";
        }
        Map<String, Object> cuerpo = FormularioDonacion.cuerpo(id, descripcion, subcategoria, subcategoriaOtra,
                detalle, cantidad, unidad, esNuevo, fechaVencimiento);
        try {
            api.crearDonacion(cuerpo);
        } catch (ApiException e) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", e.getMessage());
            return "redirect:/donante/donar";
        }
        flash.addFlashAttribute("toastTipo", "ok");
        flash.addFlashAttribute("toast", "¡Gracias! Registramos tu donación y te avisaremos cada vez que avance.");
        return "redirect:/donante/donaciones";
    }

    @GetMapping("/registro")
    public String registro(HttpSession sesion, Model model) {
        if (donanteId(sesion) != null) {
            return "redirect:/donante/panel";
        }
        model.addAttribute("seccion", "registro");
        return usuario(sesion).esDonanteJuridico() ? "autenticacion/registro-donante-juridico" : "donaciones/donante/registro";
    }

    @PostMapping("/registro")
    public String registrar(@RequestParam String nombre, @RequestParam String apellido,
                            @RequestParam String tipoDocumento, @RequestParam String numeroDocumento,
                            @RequestParam String fechaNacimiento, @RequestParam String genero,
                            @RequestParam String calle, @RequestParam int numero,
                            @RequestParam String codigoPostal, @RequestParam String localidad,
                            @RequestParam String provincia,
                            HttpSession sesion, RedirectAttributes flash) {
        SesionUsuario u = usuario(sesion);
        if (u.esDonanteJuridico() || u.getDonanteId() != null) {
            return "redirect:/donante/panel";
        }
        try {
            LocalDate.parse(fechaNacimiento);
        } catch (RuntimeException e) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", "La fecha de nacimiento no es válida.");
            return "redirect:/donante/registro";
        }
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("tipo", "HUMANA");
        cuerpo.put("nombre", nombre.trim());
        cuerpo.put("apellido", apellido.trim());
        cuerpo.put("tipoDocumento", tipoDocumento);
        cuerpo.put("numeroDocumento", numeroDocumento.trim());
        cuerpo.put("email", u.getEmail());
        cuerpo.put("fechaNacimiento", fechaNacimiento);
        cuerpo.put("genero", genero);
        cuerpo.put("direccion", Map.of(
                "calle", calle.trim(), "numero", numero, "codigoPostal", codigoPostal.trim(),
                "localidad", Map.of("nombre", localidad.trim(), "provincia", Map.of("nombre", provincia.trim()))));
        cuerpo.put("medioContactoPredeterminado", Map.of("tipo", "EMAIL", "valor", u.getEmail()));
        return completarRegistro(cuerpo, sesion, flash);
    }

    @PostMapping("/registro-juridico")
    public String registrarJuridica(@RequestParam String razonSocial, @RequestParam String rubro,
                                    @RequestParam String tipoOrganizacion, @RequestParam String numeroDocumento,
                                    @RequestParam String nombre, @RequestParam String apellido,
                                    @RequestParam String calle, @RequestParam int numero,
                                    @RequestParam String codigoPostal, @RequestParam String localidad,
                                    @RequestParam String provincia, HttpSession sesion, RedirectAttributes flash) {
        SesionUsuario u = usuario(sesion);
        if (!u.esDonanteJuridico() || u.getDonanteId() != null) {
            return "redirect:/donante/panel";
        }
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("tipo", "JURIDICA");
        cuerpo.put("razonSocial", razonSocial.trim());
        cuerpo.put("rubro", rubro.trim());
        cuerpo.put("tipoOrganizacion", tipoOrganizacion);
        cuerpo.put("tipoDocumento", "CUIT");
        cuerpo.put("numeroDocumento", numeroDocumento.trim());
        cuerpo.put("email", u.getEmail());
        cuerpo.put("direccion", Map.of("calle", calle.trim(), "numero", numero, "codigoPostal", codigoPostal.trim(),
                "localidad", Map.of("nombre", localidad.trim(), "provincia", Map.of("nombre", provincia.trim()))));
        cuerpo.put("medioContactoPredeterminado", Map.of("tipo", "EMAIL", "valor", u.getEmail()));
        cuerpo.put("representantes", List.of(Map.of("nombre", nombre.trim(), "apellido", apellido.trim(), "email", u.getEmail())));
        return completarRegistro(cuerpo, sesion, flash);
    }

    private String completarRegistro(Map<String, Object> cuerpo, HttpSession sesion, RedirectAttributes flash) {
        SesionUsuario u = usuario(sesion);
        String clavePendiente = PERFIL_PENDIENTE + u.getRol() + u.getEmail();
        try {
            autenticacion.exigirVinculacionConfigurada();
            UUID id = (UUID) sesion.getAttribute(clavePendiente);
            if (id == null) {
                id = api.registrarDonante(cuerpo);
                sesion.setAttribute(clavePendiente, id);
            }
            sesion.setAttribute(SesionUsuario.ATRIBUTO, autenticacion.vincularPerfil(u, id));
            sesion.removeAttribute(clavePendiente);
        } catch (ApiException e) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", e.getMessage());
            return "redirect:/donante/registro";
        }
        flash.addFlashAttribute("toastTipo", "ok");
        flash.addFlashAttribute("toast", "¡Listo, ya sos parte de DonaTrack!");
        return "redirect:/donante/panel";
    }
}
