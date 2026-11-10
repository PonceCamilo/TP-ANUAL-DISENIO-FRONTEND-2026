package ar.utn.donatrack.frontend.donaciones.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;
import ar.utn.donatrack.frontend.donaciones.services.Pagina;

import java.util.LinkedHashMap;
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

import static ar.utn.donatrack.frontend.donaciones.controller.Administracion.error;
import static ar.utn.donatrack.frontend.donaciones.controller.Administracion.exito;

/** Listado de entidades beneficiarias para la persona administradora, con búsqueda por email y baja. */
@Controller
@RequestMapping("/admin/entidades")
public class AdminEntidadesController {

    static final String LISTADO = "/admin/entidades";

    private final DonacionesApi api;

    public AdminEntidadesController(DonacionesApi api) {
        this.api = api;
    }

    @GetMapping
    public String listado(@RequestParam(required = false) String email,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {
        Pagina pagina = api.paginaDeEntidades(email, page);
        Map<String, Object> filtros = new LinkedHashMap<>();
        filtros.put("email", email);

        model.addAttribute("pagina", pagina);
        model.addAttribute("filtros", filtros);
        model.addAttribute("hayFiltros", !Administracion.vacio(email));
        model.addAttribute("urlAnterior", Administracion.url(LISTADO, filtros, pagina.getNumero() - 1));
        model.addAttribute("urlSiguiente", Administracion.url(LISTADO, filtros, pagina.getNumero() + 1));
        model.addAttribute("pestania", "entidades");
        return "donaciones/admin/entidades";
    }

    /** El servicio rechaza la baja si la entidad ya recibió donaciones; el motivo se muestra tal cual. */
    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable UUID id, RedirectAttributes flash) {
        try {
            api.eliminarEntidad(id);
        } catch (ApiException e) {
            return error(flash, e.getMessage(), LISTADO);
        }
        return exito(flash, "Eliminamos la entidad.", LISTADO);
    }
}
