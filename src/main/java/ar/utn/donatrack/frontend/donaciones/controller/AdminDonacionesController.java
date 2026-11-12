package ar.utn.donatrack.frontend.donaciones.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.util.Etiquetas;
import ar.utn.donatrack.frontend.comun.util.Mapas;
import ar.utn.donatrack.frontend.donaciones.services.Candidatos;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;
import ar.utn.donatrack.frontend.donaciones.services.Pagina;
import ar.utn.donatrack.frontend.donaciones.services.Transiciones;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
 * Pantallas de la persona administradora sobre las donaciones: listado con
 * filtros, detalle con cambios de estado y baja, elección de la entidad según
 * los algoritmos, despacho a logística y alta de una donación a nombre de un
 * donante (requerimientos 15, 16 y 17).
 */
@Controller
@RequestMapping("/admin/donaciones")
public class AdminDonacionesController {

    static final String LISTADO = "/admin/donaciones";

    private final DonacionesApi api;

    public AdminDonacionesController(DonacionesApi api) {
        this.api = api;
    }

    @GetMapping
    public String listado(@RequestParam(required = false) String estado,
                          @RequestParam(required = false) String subcategoria,
                          @RequestParam(required = false) String desde,
                          @RequestParam(required = false) String hasta,
                          @RequestParam(defaultValue = "false") boolean vencidas,
                          @RequestParam(defaultValue = "1") int page,
                          Model model) {
        LocalDate fechaDesde = Administracion.fecha(desde);
        LocalDate fechaHasta = Administracion.fecha(hasta);
        String aviso = null;
        if (!Administracion.vacio(desde) && fechaDesde == null || !Administracion.vacio(hasta) && fechaHasta == null) {
            aviso = "Una de las fechas no es válida: la lista se muestra sin ese filtro.";
        } else if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            aviso = "La fecha \"desde\" es posterior a \"hasta\": la lista se muestra sin rango de fechas.";
            fechaDesde = null;
            fechaHasta = null;
        }

        Map<String, Object> filtros = new LinkedHashMap<>();
        filtros.put("estado", estado);
        filtros.put("subcategoria", subcategoria);
        filtros.put("desde", fechaDesde);
        filtros.put("hasta", fechaHasta);
        filtros.put("vencidas", vencidas ? Boolean.TRUE : null);
        Pagina pagina = api.paginaDeDonaciones(filtros, page);

        model.addAttribute("pagina", pagina);
        model.addAttribute("filtros", filtros);
        model.addAttribute("aviso", aviso);
        model.addAttribute("hayFiltros", filtros.values().stream().anyMatch(v -> v != null && !String.valueOf(v).isBlank()));
        model.addAttribute("urlAnterior", Administracion.url(LISTADO, filtros, pagina.getNumero() - 1));
        model.addAttribute("urlSiguiente", Administracion.url(LISTADO, filtros, pagina.getNumero() + 1));
        model.addAttribute("pestania", "donaciones");
        return "donaciones/admin/donaciones";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable UUID id, Model model) {
        Map<String, Object> donacion = api.donacion(id);
        String idDonante = Mapas.texto(donacion, "idDonante");
        String idEntidad = Mapas.texto(donacion, "idEntidadBeneficiaria");
        Map<String, Object> donante = idDonante.isBlank() ? null
                : Administracion.siExiste(() -> api.donante(UUID.fromString(idDonante)));
        Map<String, Object> entidad = idEntidad.isBlank() ? null
                : Administracion.siExiste(() -> api.entidad(UUID.fromString(idEntidad)));
        String estado = Mapas.texto(donacion, "estado");

        model.addAttribute("donacion", donacion);
        model.addAttribute("historial", Mapas.lista(donacion.get("historialEstados")));
        model.addAttribute("donante", donante);
        model.addAttribute("nombreDonante", Administracion.nombreDeDonante(donante));
        model.addAttribute("entidad", entidad);
        model.addAttribute("destinos", Transiciones.deDonacion(estado));
        model.addAttribute("pestania", "donaciones");
        return "donaciones/admin/detalle";
    }

    @PostMapping("/{id}/estado")
    public String cambiarEstado(@PathVariable UUID id, @RequestParam String estado,
                                @RequestParam(required = false) String justificacion, RedirectAttributes flash) {
        String volver = LISTADO + "/" + id;
        if (Transiciones.exigeJustificacion(estado) && Administracion.vacio(justificacion)) {
            return error(flash, "Para registrar una entrega fallida hay que contar qué pasó.", volver);
        }
        try {
            api.cambiarEstado(id, estado, justificacion);
        } catch (ApiException e) {
            return error(flash, e.getMessage(), volver);
        }
        return exito(flash, "La donación pasó a \"" + Etiquetas.estado(estado) + "\".", volver);
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable UUID id, RedirectAttributes flash) {
        try {
            api.eliminarDonacion(id);
        } catch (ApiException e) {
            return error(flash, e.getMessage(), LISTADO + "/" + id);
        }
        return exito(flash, "Dimos de baja la donación. Queda guardada con su historial.", LISTADO);
    }

    /** Las entidades que recomienda cada algoritmo, para elegir a cuál asignarla. */
    @GetMapping("/{id}/candidatos")
    public String candidatos(@PathVariable UUID id, Model model) {
        Map<String, Object> donacion = api.donacion(id);
        Map<String, Object> respuesta = api.candidatos(id);
        List<Map<String, Object>> rankings = new ArrayList<>();
        for (Map<String, Object> ranking : Mapas.lista(respuesta.get("rankings"))) {
            Map<String, Object> conNombre = new LinkedHashMap<>(ranking);
            conNombre.put("nombre", Candidatos.nombreDelAlgoritmo(Mapas.texto(ranking, "algoritmo")));
            rankings.add(conNombre);
        }
        model.addAttribute("donacion", donacion);
        model.addAttribute("rankings", rankings);
        model.addAttribute("coincidencias", Mapas.lista(respuesta.get("coincidencias")));
        model.addAttribute("asignable", "EN_DEPOSITO".equals(Mapas.texto(donacion, "estado")));
        model.addAttribute("pestania", "por-asignar");
        return "donaciones/admin/candidatos";
    }

    @PostMapping("/{id}/asignar")
    public String asignar(@PathVariable UUID id, @RequestParam UUID entidadId,
                          @RequestParam(required = false) String razonSocial, RedirectAttributes flash) {
        try {
            api.asignar(id, entidadId);
        } catch (ApiException e) {
            return error(flash, e.getMessage(), LISTADO + "/" + id + "/candidatos");
        }
        String destino = Administracion.vacio(razonSocial) ? "la entidad elegida" : razonSocial.strip();
        return exito(flash, "Asignamos la donación a " + destino + ". Ya se puede despachar a logística.",
                LISTADO + "/" + id);
    }

    /** Bandeja del matchmaking nocturno: donaciones en depósito con sus entidades sugeridas. */
    @GetMapping("/por-asignar")
    public String porAsignar(Model model) {
        Map<String, Map<String, Object>> enDeposito = porId(api.donacionesEnDeposito());
        List<Map<String, Object>> pendientes = new ArrayList<>();
        for (Map<String, Object> pendiente : api.candidatosPendientes()) {
            Map<String, Object> donacion = enDeposito.get(Mapas.texto(pendiente, "idDonacion"));
            if (donacion == null) {
                continue; // ya se asignó o se dio de baja después del cálculo
            }
            Map<String, Object> fila = new LinkedHashMap<>(pendiente);
            fila.put("donacion", donacion);
            fila.put("cantidadDeCandidatas", Candidatos.idsDeEntidades(pendiente).size());
            pendientes.add(fila);
        }
        model.addAttribute("pendientes", pendientes);
        model.addAttribute("pestania", "por-asignar");
        return "donaciones/admin/por-asignar";
    }

    @GetMapping("/despachos")
    public String despachos(Model model) {
        Map<String, Map<String, Object>> entidades = porId(api.entidades());
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Map<String, Object> donacion : api.donacionesParaDespachar()) {
            Map<String, Object> fila = new LinkedHashMap<>(donacion);
            Map<String, Object> entidad = entidades.get(Mapas.texto(donacion, "idEntidadBeneficiaria"));
            fila.put("razonSocial", entidad == null ? "" : Mapas.texto(entidad, "razonSocial"));
            filas.add(fila);
        }
        model.addAttribute("donaciones", filas);
        model.addAttribute("pestania", "despachos");
        return "donaciones/admin/despachos";
    }

    @PostMapping("/despachos")
    public String despachar(@RequestParam(name = "ids", required = false) List<UUID> ids,
                            @RequestParam(required = false) String proveedor, RedirectAttributes flash) {
        String volver = LISTADO + "/despachos";
        if (ids == null || ids.isEmpty()) {
            return error(flash, "Marcá al menos una donación para despachar.", volver);
        }
        try {
            api.despachar(ids, proveedor);
        } catch (ApiException e) {
            return error(flash, e.getMessage(), volver);
        }
        String cuantas = ids.size() == 1 ? "la donación" : "las " + ids.size() + " donaciones";
        return exito(flash, "Mandamos " + cuantas + " a logística. Ya están listas para entregar.", volver);
    }

    @GetMapping("/nueva")
    public String nueva(Model model) {
        model.addAttribute("pestania", "donaciones");
        return "donaciones/admin/donacion-nueva";
    }

    @PostMapping("/nueva")
    public String registrar(@RequestParam String emailDonante,
                            @RequestParam String descripcion,
                            @RequestParam String subcategoria,
                            @RequestParam(required = false) String subcategoriaOtra,
                            @RequestParam(required = false) String detalle,
                            @RequestParam int cantidad,
                            @RequestParam String unidad,
                            @RequestParam(defaultValue = "true") boolean esNuevo,
                            @RequestParam(required = false) String fechaVencimiento,
                            @RequestParam(required = false) String foto,
                            RedirectAttributes flash) {
        String volver = LISTADO + "/nueva";
        try {
            UUID idDonante = api.buscarDonante(emailDonante.strip());
            if (idDonante == null) {
                return error(flash, "No hay ninguna persona donante con el email " + emailDonante.strip()
                        + ". Registrala primero en Donantes.", volver);
            }
            api.crearDonacion(FormularioDonacion.cuerpo(idDonante, descripcion, subcategoria, subcategoriaOtra,
                    detalle, cantidad, unidad, esNuevo, fechaVencimiento, foto));
        } catch (ApiException e) {
            return error(flash, e.getMessage(), volver);
        }
        return exito(flash, "Registramos la donación a nombre de " + emailDonante.strip() + ".", LISTADO);
    }

    private static Map<String, Map<String, Object>> porId(List<Map<String, Object>> elementos) {
        return elementos.stream().collect(Collectors.toMap(
                e -> Mapas.texto(e, "id"), Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }
}
