package ar.utn.donatrack.frontend.donaciones.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.util.Mapas;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;

import java.util.LinkedHashMap;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Pantallas de administración sobre servicio-donaciones: Depósito (registrar
 * donaciones y vencerlas), Asignación (confirmar destino a partir del ranking
 * de los algoritmos) y Donantes (alta manual e importación por CSV).
 */
@Controller
@RequestMapping("/admin")
public class AdminDonacionesController {

    private final DonacionesApi donaciones;

    public AdminDonacionesController(DonacionesApi donaciones) {
        this.donaciones = donaciones;
    }

    // ---------------------------------------------------------------- Depósito

    @GetMapping("/deposito")
    public String deposito(Model model) {
        model.addAttribute("area", "Depósito");
        model.addAttribute("descripcionArea", "Donaciones en depósito: registrá lo que llega y marcá lo vencido.");
        model.addAttribute("seccion", "deposito");
        try {
            model.addAttribute("donaciones", donaciones.donacionesEnDeposito());
            model.addAttribute("donantes", donaciones.donantes());
        } catch (ApiException e) {
            model.addAttribute("donaciones", List.of());
            model.addAttribute("donantes", List.of());
            model.addAttribute("toast", e.getMessage());
            model.addAttribute("toastTipo", "error");
        }
        return "autenticacion/admin-deposito";
    }

    @PostMapping("/deposito")
    public String registrarDonacion(
            @RequestParam UUID idDonante,
            @RequestParam String descripcion,
            @RequestParam String subcategoria,
            @RequestParam(required = false) String bienDescripcion,
            @RequestParam Integer cantidad,
            @RequestParam String unidad,
            @RequestParam(required = false) String fechaVencimiento,
            @RequestParam(required = false) Boolean esNuevo,
            RedirectAttributes flash) {
        try {
            Map<String, Object> bien = new LinkedHashMap<>();
            bien.put("subcategoria", subcategoria);
            bien.put("descripcion", bienDescripcion);
            bien.put("cantidad", cantidad);
            bien.put("unidad", unidad);
            if (fechaVencimiento != null && !fechaVencimiento.isBlank()) {
                bien.put("tipo", "PERECIBLE");
                bien.put("fechaVencimiento", fechaVencimiento);
            } else if (esNuevo != null) {
                bien.put("tipo", "CON_ESTADO");
                bien.put("esNuevo", esNuevo);
            } else {
                bien.put("tipo", "GENERICO");
            }
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("idDonante", idDonante);
            cuerpo.put("descripcion", descripcion);
            cuerpo.put("bienes", List.of(bien));
            donaciones.crearDonacion(cuerpo);
            flash.addFlashAttribute("toast", "Donación registrada.");
        } catch (ApiException e) {
            flash.addFlashAttribute("toast", e.getMessage());
            flash.addFlashAttribute("toastTipo", "error");
        }
        return "redirect:/admin/deposito";
    }

    @PostMapping("/deposito/{id}/vencer")
    public String vencerDonacion(@PathVariable UUID id,
                                  @RequestParam(required = false) String justificacion,
                                  RedirectAttributes flash) {
        try {
            donaciones.cambiarEstadoDonacion(id, "VENCIDA", justificacion);
            flash.addFlashAttribute("toast", "Donación marcada como vencida.");
        } catch (ApiException e) {
            flash.addFlashAttribute("toast", e.getMessage());
            flash.addFlashAttribute("toastTipo", "error");
        }
        return "redirect:/admin/deposito";
    }

    // ------------------------------------------------------------- Asignación

    @GetMapping("/asignacion")
    public String asignacion(Model model) {
        model.addAttribute("area", "Asignación");
        model.addAttribute("descripcionArea", "Donaciones pendientes de confirmar destino, según los algoritmos.");
        model.addAttribute("seccion", "asignacion");
        try {
            List<Map<String, Object>> pendientes = donaciones.candidatosPendientes();
            model.addAttribute("pendientes", pendientes.stream().map(p -> {
                Map<String, Object> fila = new LinkedHashMap<>(p);
                try {
                    fila.put("donacion", donaciones.donacion(UUID.fromString(Mapas.texto(p, "idDonacion"))));
                } catch (ApiException | IllegalArgumentException ignorado) {
                    fila.put("donacion", Map.of());
                }
                return fila;
            }).toList());
        } catch (ApiException e) {
            model.addAttribute("pendientes", List.of());
            model.addAttribute("toast", e.getMessage());
            model.addAttribute("toastTipo", "error");
        }
        return "autenticacion/admin-asignacion";
    }

    @GetMapping("/asignacion/{id}")
    public String asignacionDetalle(@PathVariable UUID id, Model model, RedirectAttributes flash) {
        model.addAttribute("area", "Asignación");
        model.addAttribute("seccion", "asignacion");
        try {
            model.addAttribute("donacion", donaciones.donacion(id));
            model.addAttribute("candidatos", donaciones.candidatos(id));
        } catch (ApiException e) {
            flash.addFlashAttribute("toast", e.getMessage());
            flash.addFlashAttribute("toastTipo", "error");
            return "redirect:/admin/asignacion";
        }
        return "autenticacion/admin-asignacion-detalle";
    }

    @PostMapping("/asignacion/{id}/asignar")
    public String confirmarAsignacion(@PathVariable UUID id,
                                       @RequestParam UUID idEntidadBeneficiaria,
                                       RedirectAttributes flash) {
        try {
            donaciones.asignar(id, idEntidadBeneficiaria);
            flash.addFlashAttribute("toast", "Donación asignada a la entidad elegida.");
        } catch (ApiException e) {
            flash.addFlashAttribute("toast", e.getMessage());
            flash.addFlashAttribute("toastTipo", "error");
        }
        return "redirect:/admin/asignacion";
    }

    // --------------------------------------------------------------- Donantes

    @GetMapping("/donantes")
    public String donantesLista(Model model) {
        model.addAttribute("area", "Donantes");
        model.addAttribute("descripcionArea", "Alta manual e importación masiva de personas donantes.");
        model.addAttribute("seccion", "donantes");
        try {
            model.addAttribute("donantes", donaciones.donantes());
        } catch (ApiException e) {
            model.addAttribute("donantes", List.of());
            model.addAttribute("toast", e.getMessage());
            model.addAttribute("toastTipo", "error");
        }
        return "autenticacion/admin-donantes";
    }

    @PostMapping("/donantes")
    public String altaDonante(
            @RequestParam String tipoDocumento,
            @RequestParam String numeroDocumento,
            @RequestParam String email,
            @RequestParam String nombre,
            @RequestParam String apellido,
            @RequestParam String fechaNacimiento,
            @RequestParam String genero,
            @RequestParam String calle,
            @RequestParam(required = false) Integer numero,
            @RequestParam String localidad,
            @RequestParam String provincia,
            @RequestParam(required = false) String codigoPostal,
            RedirectAttributes flash) {
        try {
            Map<String, Object> provinciaMap = new LinkedHashMap<>();
            provinciaMap.put("nombre", provincia);

            Map<String, Object> localidadMap = new LinkedHashMap<>();
            localidadMap.put("nombre", localidad);
            localidadMap.put("provincia", provinciaMap);

            Map<String, Object> direccion = new LinkedHashMap<>();
            direccion.put("calle", calle);
            direccion.put("numero", numero == null ? 0 : numero);
            direccion.put("codigoPostal", codigoPostal == null ? "" : codigoPostal);
            direccion.put("localidad", localidadMap);

            Map<String, Object> medioContacto = new LinkedHashMap<>();
            medioContacto.put("tipo", "EMAIL");
            medioContacto.put("valor", email);

            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("tipo", "HUMANA");
            cuerpo.put("tipoDocumento", tipoDocumento);
            cuerpo.put("numeroDocumento", numeroDocumento);
            cuerpo.put("email", email);
            cuerpo.put("direccion", direccion);
            cuerpo.put("medioContactoPredeterminado", medioContacto);
            cuerpo.put("nombre", nombre);
            cuerpo.put("apellido", apellido);
            cuerpo.put("fechaNacimiento", fechaNacimiento);
            cuerpo.put("genero", genero);
            donaciones.registrarDonante(cuerpo);
            flash.addFlashAttribute("toast", "Donante registrado.");
        } catch (ApiException e) {
            flash.addFlashAttribute("toast", e.getMessage());
            flash.addFlashAttribute("toastTipo", "error");
        }
        return "redirect:/admin/donantes";
    }

    @PostMapping("/donantes/import")
    public String importarCsv(@RequestParam("archivo") MultipartFile archivo, RedirectAttributes flash) {
        try {
            Map<String, Object> resumen = donaciones.importarDonantesCsv(archivo);
            flash.addFlashAttribute("resumenImportacion", resumen);
        } catch (ApiException e) {
            flash.addFlashAttribute("toast", e.getMessage());
            flash.addFlashAttribute("toastTipo", "error");
        }
        return "redirect:/admin/donantes";
    }
}
