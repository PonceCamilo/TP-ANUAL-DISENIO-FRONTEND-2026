package ar.utn.donatrack.frontend.donaciones.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;
import ar.utn.donatrack.frontend.donaciones.services.Pagina;

import java.util.ArrayList;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static ar.utn.donatrack.frontend.donaciones.controller.Administracion.error;
import static ar.utn.donatrack.frontend.donaciones.controller.Administracion.exito;

/**
 * Entidades beneficiarias para la persona administradora: listado con búsqueda
 * por email, alta (Entrega 6, con su ubicación para el mapa) y baja.
 */
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
                          @RequestParam(defaultValue = "1") int page,
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

    @GetMapping("/nueva")
    public String nueva(Model model) {
        model.addAttribute("pestania", "entidades");
        return "donaciones/admin/entidad-nueva";
    }

    /**
     * Alta de una entidad por la administración. Con latitud y longitud (opcionales,
     * las dos o ninguna) aparece en el mapa de la landing. El email queda como
     * contacto y como email de quien la representa.
     */
    @PostMapping("/nueva")
    public String crear(@RequestParam String razonSocial, @RequestParam String email,
                        @RequestParam String representanteNombre, @RequestParam String representanteApellido,
                        @RequestParam String calle, @RequestParam int numero, @RequestParam String codigoPostal,
                        @RequestParam String localidad, @RequestParam String provincia,
                        @RequestParam(required = false) String telefono,
                        @RequestParam(required = false) String latitud,
                        @RequestParam(required = false) String longitud,
                        RedirectAttributes flash) {
        String volver = LISTADO + "/nueva";
        Map<String, Object> ubicacion;
        try {
            ubicacion = Ubicacion.desde(latitud, longitud);
        } catch (IllegalArgumentException e) {
            return error(flash, e.getMessage(), volver);
        }
        List<Map<String, Object>> contactos = new ArrayList<>();
        contactos.add(Map.of("tipo", "EMAIL", "valor", email.trim()));
        if (telefono != null && !telefono.isBlank()) {
            contactos.add(Map.of("tipo", "TELEFONO", "valor", telefono.trim()));
        }
        Map<String, Object> direccion = new LinkedHashMap<>(Map.of(
                "calle", calle.trim(), "numero", numero, "codigoPostal", codigoPostal.trim(),
                "localidad", Map.of("nombre", localidad.trim(), "provincia", Map.of("nombre", provincia.trim()))));
        direccion.putAll(ubicacion);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("razonSocial", razonSocial.trim());
        cuerpo.put("direccion", direccion);
        cuerpo.put("contactos", contactos);
        cuerpo.put("representantes", List.of(Map.of(
                "nombre", representanteNombre.trim(), "apellido", representanteApellido.trim(), "email", email.trim())));
        try {
            api.crearEntidad(cuerpo);
        } catch (ApiException e) {
            return error(flash, e.getMessage(), volver);
        }
        return exito(flash, "Registramos la entidad " + razonSocial.trim() + ".", LISTADO);
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
