package ar.utn.donatrack.frontend.logistica.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;
import ar.utn.donatrack.frontend.logistica.services.LogisticaApi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Revisión de las entregas que el chofer marcó como no recibidas.
 * La hace la persona administradora, por eso cuelga de /admin.
 */
@Controller
@RequestMapping("/admin/entregas")
public class RevisionEntregasController {

    private static final Set<String> REPLANIFICABLES = Set.of(
            "ENTIDAD_AUSENTE", "DIRECCION_INCORRECTA", "RECHAZADA_POR_ENTIDAD");

    private static final Map<String, String> TEXTOS = Map.of(
            "ENTIDAD_AUSENTE", "La entidad no estaba. Se puede volver a rutear.",
            "DIRECCION_INCORRECTA", "Dirección incorrecta. Se puede volver a rutear.",
            "RECHAZADA_POR_ENTIDAD", "La entidad rechazó la entrega. Se puede volver a rutear.",
            "MERCADERIA_ROTA", "Mercadería rota. No vuelve a salir.",
            "MERCADERIA_PERDIDA", "Mercadería perdida. No vuelve a salir.",
            "ROBO", "Robo. No vuelve a salir.");

    private final LogisticaApi api;
    private final DonacionesApi donaciones;

    public RevisionEntregasController(LogisticaApi api, DonacionesApi donaciones) {
        this.api = api;
        this.donaciones = donaciones;
    }

    @GetMapping("/no-recibidas")
    public String listar(Model model) {
        model.addAttribute("seccion", "no-recibidas");
        model.addAttribute("pestania", "no-recibidas");
        List<Map<String, Object>> entregas = api.entregasPorEstado("NO_RECIBIDA");
        entregas.forEach(this::anotarMotivo);
        model.addAttribute("entregas", entregas);
        Set<String> yaListadas = new HashSet<>();
        for (Map<String, Object> entrega : entregas) {
            yaListadas.add(String.valueOf(entrega.get("idDonacion")));
        }
        List<Map<String, Object>> sinVolver = new ArrayList<>();
        try {
            for (Map<String, Object> donacion : donaciones.donacionesPorEstado("ENTREGA_FALLIDA")) {
                if (!yaListadas.contains(String.valueOf(donacion.get("id")))) {
                    sinVolver.add(donacion);
                }
            }
        } catch (ApiException e) {
            model.addAttribute("toast", e.getMessage());
            model.addAttribute("toastTipo", "error");
        }
        model.addAttribute("sinVolver", sinVolver);
        return "logistica/no-recibidas";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable UUID id, Model model) {
        Map<String, Object> entrega = api.entrega(id);
        model.addAttribute("seccion", "no-recibidas");
        model.addAttribute("pestania", "no-recibidas");
        anotarMotivo(entrega);
        model.addAttribute("entrega", entrega);
        return "logistica/entrega";
    }

    @PostMapping("/{id}/regreso-deposito")
    public String regresar(@PathVariable UUID id, RedirectAttributes flash) {
        String donacionId = null;
        try {
            donacionId = String.valueOf(api.entrega(id).get("idDonacion"));
            api.regresarADeposito(id);
            if (donacionId != null && !donacionId.isBlank() && !"null".equals(donacionId)) {
                donaciones.cambiarEstado(UUID.fromString(donacionId), "EN_DEPOSITO",
                        "La administración la devolvió al depósito para asignarla de nuevo.");
            }
        } catch (ApiException e) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", e.getMessage());
            return "redirect:/admin/entregas/" + id;
        }
        flash.addFlashAttribute("toastTipo", "ok");
        flash.addFlashAttribute("toast", "Volvió al depósito. La vas a ver en Donaciones, con el filtro En depósito.");
        return "redirect:/admin/entregas/no-recibidas";
    }

    /** La logística ya la sacó de No recibidas, pero Donaciones sigue en entrega fallida. */
    @PostMapping("/donacion/{id}/deposito")
    public String pasarDonacion(@PathVariable UUID id, RedirectAttributes flash) {
        try {
            donaciones.cambiarEstado(id, "EN_DEPOSITO",
                    "La administración la devolvió al depósito para asignarla de nuevo.");
        } catch (ApiException e) {
            flash.addFlashAttribute("toastTipo", "error");
            flash.addFlashAttribute("toast", e.getMessage());
            return "redirect:/admin/entregas/no-recibidas";
        }
        flash.addFlashAttribute("toastTipo", "ok");
        flash.addFlashAttribute("toast", "Quedó en depósito. La vas a ver en Donaciones, filtro En depósito.");
        return "redirect:/admin/donaciones?estado=EN_DEPOSITO";
    }

    private void anotarMotivo(Map<String, Object> entrega) {
        String motivo = ultimoMotivo(entrega.get("historial"));
        entrega.put("motivo", motivo);
        entrega.put("motivoTexto", motivo == null ? "Sin motivo informado." : TEXTOS.getOrDefault(motivo, motivo));
        entrega.put("replanificable", motivo != null && REPLANIFICABLES.contains(motivo));
    }

    private static String ultimoMotivo(Object historial) {
        if (!(historial instanceof List<?> cambios)) {
            return null;
        }
        String motivo = null;
        for (Object cambio : cambios) {
            if (cambio instanceof Map<?, ?> mapa
                    && "NO_RECIBIDA".equals(String.valueOf(mapa.get("estado")))
                    && mapa.get("motivoFallo") != null) {
                motivo = String.valueOf(mapa.get("motivoFallo"));
            }
        }
        return motivo;
    }
}
