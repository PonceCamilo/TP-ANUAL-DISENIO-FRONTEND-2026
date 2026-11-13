package ar.utn.donatrack.frontend.donaciones.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.autenticacion.services.AuthClient;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.comun.util.Mapas;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;
import ar.utn.donatrack.frontend.donaciones.services.Pagina;
import ar.utn.donatrack.frontend.logistica.services.LogisticaApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpSession;

/** Pantallas del donante que vienen de servicio-donaciones: donar, mis donaciones, seguimiento, entidades y registro. */
@Controller
@RequestMapping("/donante")
public class DonanteDonacionesController {

    private final DonacionesApi api;
    private final AuthClient autenticacion;
    private final LogisticaApi logistica;
    private static final String PERFIL_PENDIENTE = "perfilPendienteDeVincular";

    public DonanteDonacionesController(DonacionesApi api, AuthClient autenticacion, LogisticaApi logistica) {
        this.autenticacion = autenticacion;
        this.logistica = logistica;
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

    /**
     * "Mis donaciones" (requisito 5): filtra por estado y por una o más
     * subcategorías en el servicio, una página por vez. Antes traía todas y
     * filtraba solo por estado en memoria.
     */
    @GetMapping("/donaciones")
    public String donaciones(@RequestParam(required = false) String estado,
                             @RequestParam(required = false) List<String> subcategoria,
                             @RequestParam(defaultValue = "1") int page,
                             HttpSession sesion, Model model) {
        UUID id = donanteId(sesion);
        if (id == null) {
            return "redirect:/donante/registro";
        }
        String estadoElegido = estado == null || estado.isBlank() ? "" : estado.strip();
        List<String> elegidas = subcategoria == null ? List.of() : subcategoria.stream()
                .filter(s -> s != null && !s.isBlank()).map(String::strip).distinct().toList();
        Pagina pagina = api.paginaDeDonacionesDe(id, estadoElegido, elegidas, page);
        model.addAttribute("pagina", pagina);
        model.addAttribute("donaciones", pagina.getContenido());
        model.addAttribute("estadoFiltro", estadoElegido);
        model.addAttribute("subcategoriasElegidas", elegidas);
        model.addAttribute("catalogo", catalogoCon(elegidas));
        model.addAttribute("hayFiltros", !estadoElegido.isEmpty() || !elegidas.isEmpty());
        model.addAttribute("urlAnterior", urlDeMisDonaciones(estadoElegido, elegidas, pagina.getNumero() - 1));
        model.addAttribute("urlSiguiente", urlDeMisDonaciones(estadoElegido, elegidas, pagina.getNumero() + 1));
        model.addAttribute("seccion", "donaciones");
        return "donaciones/donante/donaciones";
    }

    /**
     * Las entidades beneficiarias con lo que necesitan hoy (requisito 6). Son los
     * datos públicos del servicio: nombre, zona y necesidades, sin contactos.
     */
    @GetMapping("/entidades")
    public String entidades(@RequestParam(defaultValue = "1") int page, Model model) {
        Pagina pagina = api.entidadesPublicas(page);
        model.addAttribute("pagina", pagina);
        model.addAttribute("entidades", pagina.getContenido());
        model.addAttribute("urlAnterior", urlDePagina("/donante/entidades", pagina.getNumero() - 1));
        model.addAttribute("urlSiguiente", urlDePagina("/donante/entidades", pagina.getNumero() + 1));
        model.addAttribute("seccion", "entidades");
        return "donaciones/donante/entidades";
    }

    /** El catálogo del servicio más las elegidas, por si alguna ya no figura. Si el catálogo falla, solo las elegidas. */
    private List<String> catalogoCon(List<String> elegidas) {
        java.util.LinkedHashSet<String> opciones = new java.util.LinkedHashSet<>();
        try {
            opciones.addAll(api.subcategorias());
        } catch (ApiException e) {
            // El filtro por subcategoría es accesorio: la lista se muestra igual.
        }
        opciones.addAll(elegidas);
        return List.copyOf(opciones);
    }

    /** Link a otra página de "Mis donaciones" conservando los filtros, con los valores bien codificados. */
    static String urlDeMisDonaciones(String estado, List<String> subcategorias, int pagina) {
        org.springframework.web.util.UriComponentsBuilder url =
                org.springframework.web.util.UriComponentsBuilder.fromPath("/donante/donaciones");
        Map<String, Object> valores = new java.util.LinkedHashMap<>();
        if (estado != null && !estado.isBlank()) {
            url.queryParam("estado", "{estado}");
            valores.put("estado", estado);
        }
        for (int i = 0; i < subcategorias.size(); i++) {
            url.queryParam("subcategoria", "{subcategoria" + i + "}");
            valores.put("subcategoria" + i, subcategorias.get(i));
        }
        if (pagina > DonacionesApi.PRIMERA_PAGINA) {
            url.queryParam("page", pagina);
        }
        return url.encode().buildAndExpand(valores).toUriString();
    }

    private static String urlDePagina(String ruta, int pagina) {
        return pagina > DonacionesApi.PRIMERA_PAGINA ? ruta + "?page=" + pagina : ruta;
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
        // Entrega 6 (requisito 9): lista para salir o en camino, se puede seguir en el mapa.
        String estado = Mapas.texto(donacion, "estado");
        if ("LISTA_PARA_ENTREGAR".equals(estado) || "EN_TRASLADO".equals(estado)) {
            // Donaciones guarda la ruta cuando el camión sale; antes de eso se le pregunta a Logística.
            String ruta = Mapas.texto(donacion, "idRutaLogistica");
            model.addAttribute("rutaId", ruta.isBlank() ? rutaDe(id) : ruta);
        }
        return "donaciones/donante/detalle";
    }

    /** La ruta de la entrega en Logística. Si la lleva el proveedor externo o Logística no responde, no hay mapa. */
    private String rutaDe(UUID donacion) {
        try {
            Object ruta = logistica.entregaPorDonacion(donacion).get("rutaId");
            return ruta == null ? null : String.valueOf(ruta);
        } catch (ApiException e) {
            return null;
        }
    }

    @GetMapping("/donar")
    public String donar(HttpSession sesion, Model model) {
        if (donanteId(sesion) == null) {
            return "redirect:/donante/registro";
        }
        if (!model.containsAttribute("carga")) {
            model.addAttribute("carga", CargaDeDonacion.vacia());
        }
        model.addAttribute("seccion", "donar");
        return "donaciones/donante/donar";
    }

    @PostMapping("/donar")
    public String enviarDonacion(@ModelAttribute("carga") CargaDeDonacion carga,
                                 HttpSession sesion, Model model, RedirectAttributes flash) {
        UUID id = donanteId(sesion);
        if (id == null) {
            return "redirect:/donante/registro";
        }
        if (carga.getBienes() == null) {
            carga.setBienes(new ArrayList<>());
        }
        String error = FormularioDonacion.validar(carga);
        if (error != null) {
            return donarConError(carga, error, model);
        }
        try {
            api.crearDonacion(FormularioDonacion.cuerpo(id, carga));
        } catch (ApiException e) {
            return donarConError(carga, e.getMessage(), model);
        }
        flash.addFlashAttribute("toastTipo", "ok");
        flash.addFlashAttribute("toast", "¡Gracias! Registramos tu donación. Si los bienes son de distinto tipo, los vas a ver separados en el listado.");
        return "redirect:/donante/donaciones";
    }

    /** Vuelve a mostrar el formulario con lo escrito y el mensaje, sin llamar al servicio. */
    private String donarConError(CargaDeDonacion carga, String error, Model model) {
        if (carga.getBienes().isEmpty()) {
            carga.getBienes().add(new CargaDeDonacion.Bien());
        }
        model.addAttribute("carga", carga);
        model.addAttribute("toast", error);
        model.addAttribute("toastTipo", "error");
        model.addAttribute("seccion", "donar");
        return "donaciones/donante/donar";
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
