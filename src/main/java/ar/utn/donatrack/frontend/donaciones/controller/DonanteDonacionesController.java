package ar.utn.donatrack.frontend.donaciones.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
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

/** Pantallas del donante que vienen de servicio-donaciones: donar, mis donaciones, seguimiento y registro. */
@Controller
@RequestMapping("/donante")
public class DonanteDonacionesController {

    private final DonacionesApi api;

    public DonanteDonacionesController(DonacionesApi api) {
        this.api = api;
    }

    private SesionUsuario usuario(HttpSession s) {
        return (SesionUsuario) s.getAttribute(SesionUsuario.ATRIBUTO);
    }

    /** Devuelve el id del donante o null si todavía no completó su registro. */
    private UUID donanteId(HttpSession s) {
        SesionUsuario u = usuario(s);
        api.resolverIdentidad(u);
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
        String tipo = "Otro".equals(subcategoria) && subcategoriaOtra != null && !subcategoriaOtra.isBlank()
                ? subcategoriaOtra.trim()
                : subcategoria.trim();
        Map<String, Object> bien = new LinkedHashMap<>();
        bien.put("subcategoria", tipo);
        bien.put("descripcion", detalle == null || detalle.isBlank() ? tipo : detalle.trim());
        bien.put("cantidad", cantidad);
        bien.put("unidad", unidad.trim());
        bien.put("esNuevo", esNuevo);
        if (fechaVencimiento != null && !fechaVencimiento.isBlank()) {
            bien.put("fechaVencimiento", fechaVencimiento);
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("idDonante", id);
        cuerpo.put("descripcion", descripcion.trim());
        cuerpo.put("bienes", List.of(bien));
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
        return "donaciones/donante/registro";
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
        try {
            api.registrarDonante(cuerpo);
            u.setDonanteId(null);
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
