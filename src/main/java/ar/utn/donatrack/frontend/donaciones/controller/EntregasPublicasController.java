package ar.utn.donatrack.frontend.donaciones.controller;

import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;
import ar.utn.donatrack.frontend.donaciones.services.Pagina;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Fotos de las donaciones entregadas, sin login (Entrega 6, requisito 1). Usa
 * los datos públicos de servicio-donaciones: qué se entregó, cuándo y a qué
 * entidad, nunca quién lo donó. /entregas no está en SesionInterceptor: es
 * pública como la landing.
 */
@Controller
public class EntregasPublicasController {

    static final int FOTOS_POR_PAGINA = 24;

    private final DonacionesApi api;

    public EntregasPublicasController(DonacionesApi api) {
        this.api = api;
    }

    @GetMapping("/entregas")
    public String entregas(@RequestParam(defaultValue = "1") int page, Model model) {
        Pagina pagina = api.paginaDeEntregadas(page, FOTOS_POR_PAGINA);
        model.addAttribute("pagina", pagina);
        model.addAttribute("entregadas", pagina.getContenido());
        model.addAttribute("urlAnterior", pagina.getNumero() - 1 > DonacionesApi.PRIMERA_PAGINA
                ? "/entregas?page=" + (pagina.getNumero() - 1) : "/entregas");
        model.addAttribute("urlSiguiente", "/entregas?page=" + (pagina.getNumero() + 1));
        return "donaciones/publico/entregas";
    }
}
