package ar.utn.donatrack.frontend.donaciones.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;
import ar.utn.donatrack.frontend.donaciones.services.Necesidades;
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

        Map<String, Long> abiertasPorEntidad = new LinkedHashMap<>();
        for (Map<String, Object> entidad : pagina.getContenido()) {
            abiertasPorEntidad.put(String.valueOf(entidad.get("id")), Necesidades.de(entidad).stream()
                    .filter(n -> !Boolean.TRUE.equals(n.get("satisfecha"))).count());
        }
        model.addAttribute("pagina", pagina);
        model.addAttribute("abiertasPorEntidad", abiertasPorEntidad);
        model.addAttribute("filtros", filtros);
        model.addAttribute("hayFiltros", !Administracion.vacio(email));
        model.addAttribute("urlAnterior", Administracion.url(LISTADO, filtros, pagina.getNumero() - 1));
        model.addAttribute("urlSiguiente", Administracion.url(LISTADO, filtros, pagina.getNumero() + 1));
        model.addAttribute("pestania", "entidades");
        return "donaciones/admin/entidades";
    }

    /**
     * Entrega 6: todas las necesidades de todas las entidades, para que la
     * administración vea qué falta. Por defecto solo las abiertas.
     */
    @GetMapping("/necesidades")
    public String necesidades(@RequestParam(defaultValue = "false") boolean todas,
                              @RequestParam(required = false) String subcategoria, Model model) {
        List<Map<String, Object>> filas = new ArrayList<>();
        java.util.Set<String> subcategorias = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Map<String, Object> entidad : api.entidades()) {
            for (Map<String, Object> fila : Necesidades.de(entidad)) {
                subcategorias.add(String.valueOf(fila.get("subcategoria")));
                boolean abierta = !Boolean.TRUE.equals(fila.get("satisfecha"));
                boolean coincide = Administracion.vacio(subcategoria)
                        || subcategoria.strip().equalsIgnoreCase(String.valueOf(fila.get("subcategoria")));
                if ((todas || abierta) && coincide) {
                    filas.add(fila);
                }
            }
        }
        filas.sort(java.util.Comparator
                .comparing((Map<String, Object> f) -> (Boolean) f.get("satisfecha"))
                .thenComparing(f -> -((Integer) f.get("falta")))
                .thenComparing(f -> String.valueOf(f.get("razonSocial")).toLowerCase()));
        subcategorias.remove("");
        model.addAttribute("necesidades", filas);
        model.addAttribute("todas", todas);
        model.addAttribute("subcategoria", subcategoria == null ? "" : subcategoria.strip());
        model.addAttribute("subcategorias", subcategorias);
        model.addAttribute("pestania", "entidades");
        return "donaciones/admin/necesidades";
    }

    /** Entrega 6: el detalle de una entidad, con su ubicación, campañas y necesidades. */
    @GetMapping("/{id}")
    public String detalle(@PathVariable UUID id, Model model) {
        Map<String, Object> entidad = api.entidad(id);
        Map<String, Object> direccion = ar.utn.donatrack.frontend.comun.util.Mapas.mapa(entidad.get("direccion"));
        Map<String, Object> localidad = ar.utn.donatrack.frontend.comun.util.Mapas.mapa(direccion.get("localidad"));
        List<Map<String, Object>> campanias = new ArrayList<>();
        for (Map<String, Object> campania : ar.utn.donatrack.frontend.comun.util.Mapas.lista(entidad.get("campanias"))) {
            Map<String, Object> conNecesidades = new LinkedHashMap<>(campania);
            List<Map<String, Object>> necesidades = new ArrayList<>();
            for (Map<String, Object> necesidad : ar.utn.donatrack.frontend.comun.util.Mapas.lista(campania.get("necesidades"))) {
                necesidades.add(Necesidades.fila(entidad, campania, necesidad));
            }
            conNecesidades.put("filas", necesidades);
            campanias.add(conNecesidades);
        }
        model.addAttribute("entidad", entidad);
        model.addAttribute("direccion", direccion);
        model.addAttribute("localidad", ar.utn.donatrack.frontend.comun.util.Mapas.texto(localidad, "nombre"));
        model.addAttribute("provincia", ar.utn.donatrack.frontend.comun.util.Mapas.texto(
                ar.utn.donatrack.frontend.comun.util.Mapas.mapa(localidad.get("provincia")), "nombre"));
        model.addAttribute("contactos", ar.utn.donatrack.frontend.comun.util.Mapas.lista(entidad.get("contactos")));
        model.addAttribute("representantes", ar.utn.donatrack.frontend.comun.util.Mapas.lista(entidad.get("representantes")));
        model.addAttribute("campanias", campanias);
        model.addAttribute("abiertas", campanias.stream()
                .flatMap(c -> ((List<?>) c.get("filas")).stream())
                .filter(f -> !Boolean.TRUE.equals(((Map<?, ?>) f).get("satisfecha"))).count());
        model.addAttribute("pestania", "entidades");
        return "donaciones/admin/entidad";
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
