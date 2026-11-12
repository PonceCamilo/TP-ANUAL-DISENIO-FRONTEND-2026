package ar.utn.donatrack.frontend.donaciones.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;
import ar.utn.donatrack.frontend.comun.util.Mapas;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;
import ar.utn.donatrack.frontend.donaciones.services.ImportacionCsvClient;
import ar.utn.donatrack.frontend.donaciones.services.Pagina;
import ar.utn.donatrack.frontend.donaciones.services.Transiciones;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static ar.utn.donatrack.frontend.donaciones.controller.Administracion.error;
import static ar.utn.donatrack.frontend.donaciones.controller.Administracion.exito;

/**
 * Pantallas de la persona administradora sobre las personas donantes: listado,
 * alta, cambio de estado, baja e importación desde un CSV (requerimientos 15 y 20).
 */
@Controller
@RequestMapping("/admin/donantes")
public class AdminDonantesController {

    static final String LISTADO = "/admin/donantes";
    /** El CSV se lee entero en memoria antes de subirlo: un límite razonable para un listado de personas. */
    static final long TAMANIO_MAXIMO_CSV = 5L * 1024 * 1024;

    private final DonacionesApi api;
    private final ImportacionCsvClient importacion;

    public AdminDonantesController(DonacionesApi api, ImportacionCsvClient importacion) {
        this.api = api;
        this.importacion = importacion;
    }

    @GetMapping
    public String listado(@RequestParam(required = false) String estado,
                          @RequestParam(required = false) String email,
                          @RequestParam(defaultValue = "1") int page,
                          Model model) {
        Pagina pagina = api.paginaDeDonantes(estado, email, page);
        Map<String, Object> filtros = new LinkedHashMap<>();
        filtros.put("estado", estado);
        filtros.put("email", email);

        model.addAttribute("pagina", pagina);
        List<Map<String, Object>> donantes = new ArrayList<>();
        for (Map<String, Object> donante : Administracion.conNombre(pagina.getContenido())) {
            donante.put("destinos", Transiciones.deDonante(Mapas.texto(donante, "estado")));
            donantes.add(donante);
        }
        model.addAttribute("donantes", donantes);
        model.addAttribute("filtros", filtros);
        model.addAttribute("hayFiltros", !Administracion.vacio(estado) || !Administracion.vacio(email));
        model.addAttribute("urlAnterior", Administracion.url(LISTADO, filtros, pagina.getNumero() - 1));
        model.addAttribute("urlSiguiente", Administracion.url(LISTADO, filtros, pagina.getNumero() + 1));
        model.addAttribute("pestania", "donantes");
        return "donaciones/admin/donantes";
    }

    @PostMapping("/{id}/estado")
    public String cambiarEstado(@PathVariable UUID id, @RequestParam String estado,
                                @RequestParam(required = false) String justificacion, RedirectAttributes flash) {
        if (Transiciones.exigeJustificacion(estado) && Administracion.vacio(justificacion)) {
            return error(flash, "Para bloquear a una persona hay que indicar el motivo.", LISTADO);
        }
        try {
            api.cambiarEstadoDonante(id, estado, justificacion);
        } catch (ApiException e) {
            return error(flash, e.getMessage(), LISTADO);
        }
        return exito(flash, "La persona quedó " + Transiciones.estadoDonante(estado).toLowerCase() + ".", LISTADO);
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable UUID id, RedirectAttributes flash) {
        try {
            api.eliminarDonante(id);
        } catch (ApiException e) {
            return error(flash, e.getMessage(), LISTADO);
        }
        return exito(flash, "Eliminamos a la persona donante.", LISTADO);
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("pestania", "donantes");
        return "donaciones/admin/donante-nuevo";
    }

    /** Alta de una persona humana u organización. El contacto predeterminado es su email. */
    @PostMapping("/nuevo")
    public String registrar(@RequestParam String tipo, @RequestParam String email,
                            @RequestParam String tipoDocumento, @RequestParam String numeroDocumento,
                            @RequestParam(required = false) String nombre, @RequestParam(required = false) String apellido,
                            @RequestParam(required = false) String fechaNacimiento,
                            @RequestParam(required = false) String genero,
                            @RequestParam(required = false) String razonSocial, @RequestParam(required = false) String rubro,
                            @RequestParam(required = false) String tipoOrganizacion,
                            @RequestParam String calle, @RequestParam int numero,
                            @RequestParam String codigoPostal, @RequestParam String localidad,
                            @RequestParam String provincia,
                            RedirectAttributes flash) {
        String volver = LISTADO + "/nuevo";
        boolean juridica = "JURIDICA".equals(tipo);
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("tipo", juridica ? "JURIDICA" : "HUMANA");
        cuerpo.put("email", email.strip());
        cuerpo.put("tipoDocumento", tipoDocumento);
        cuerpo.put("numeroDocumento", numeroDocumento.strip());
        if (juridica) {
            if (Administracion.vacio(razonSocial) || Administracion.vacio(rubro) || Administracion.vacio(tipoOrganizacion)) {
                return error(flash, "Para una organización completá razón social, rubro y tipo.", volver);
            }
            cuerpo.put("razonSocial", razonSocial.strip());
            cuerpo.put("rubro", rubro.strip());
            cuerpo.put("tipoOrganizacion", tipoOrganizacion);
        } else {
            if (Administracion.vacio(nombre) || Administracion.vacio(apellido) || Administracion.fecha(fechaNacimiento) == null) {
                return error(flash, "Para una persona completá nombre, apellido y una fecha de nacimiento válida.", volver);
            }
            cuerpo.put("nombre", nombre.strip());
            cuerpo.put("apellido", apellido.strip());
            cuerpo.put("fechaNacimiento", fechaNacimiento.strip());
            cuerpo.put("genero", Administracion.vacio(genero) ? "PREFIERO_NO_DECIR" : genero);
        }
        cuerpo.put("direccion", Map.of(
                "calle", calle.strip(), "numero", numero, "codigoPostal", codigoPostal.strip(),
                "localidad", Map.of("nombre", localidad.strip(), "provincia", Map.of("nombre", provincia.strip()))));
        cuerpo.put("medioContactoPredeterminado", Map.of("tipo", "EMAIL", "valor", email.strip()));
        try {
            api.registrarDonante(cuerpo);
        } catch (ApiException e) {
            return error(flash, e.getMessage(), volver);
        }
        return exito(flash, "Registramos a " + email.strip() + ".", LISTADO);
    }

    @GetMapping("/importar")
    public String importar(Model model) {
        model.addAttribute("pestania", "donantes");
        return "donaciones/admin/importar";
    }

    /** Sube el CSV y vuelve a la misma pantalla con el resumen: cuántas se crearon, actualizaron y qué filas fallaron. */
    @PostMapping("/importar")
    public String subir(@RequestParam("archivo") MultipartFile archivo, HttpSession sesion, RedirectAttributes flash) {
        String volver = LISTADO + "/importar";
        if (archivo == null || archivo.isEmpty()) {
            return error(flash, "Elegí un archivo CSV para importar.", volver);
        }
        if (archivo.getSize() > TAMANIO_MAXIMO_CSV) {
            return error(flash, "El archivo supera los 5 MB.", volver);
        }
        String token = sesion.getAttribute(SesionUsuario.ATRIBUTO) instanceof SesionUsuario u ? u.getToken() : null;
        try {
            Map<String, Object> resumen = importacion.importarDonantes(archivo.getOriginalFilename(), archivo.getBytes(), token);
            flash.addFlashAttribute("resumen", new LinkedHashMap<>(resumen));
        } catch (ApiException e) {
            return error(flash, e.getMessage(), volver);
        } catch (IOException e) {
            return error(flash, "No pudimos leer el archivo. Probá de nuevo.", volver);
        }
        return exito(flash, "Procesamos el archivo.", volver);
    }
}
