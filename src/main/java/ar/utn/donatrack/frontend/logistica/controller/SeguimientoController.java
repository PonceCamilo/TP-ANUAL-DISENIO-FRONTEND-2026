package ar.utn.donatrack.frontend.logistica.controller;

import ar.utn.donatrack.frontend.logistica.services.LogisticaApi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Mapa de una ruta. Lo abre quien recibe el enlace del aviso: donante o entidad.
 * No cambia estados.
 */
@Controller
public class SeguimientoController {

    private final LogisticaApi api;

    public SeguimientoController(LogisticaApi api) {
        this.api = api;
    }

    @GetMapping("/tracking/ruta/{id}")
    public String ver(@PathVariable UUID id, Model model) {
        Map<String, Object> ruta = api.ruta(id);
        model.addAttribute("ruta", ruta);
        model.addAttribute("camion", camionDe(ruta));
        return "logistica/mapa";
    }

    @GetMapping("/tracking/ruta/{id}/posicion")
    @ResponseBody
    public Map<String, Object> posicion(@PathVariable UUID id) {
        Map<String, Object> ruta = api.ruta(id);
        Map<String, Object> camion = camionDe(ruta);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("estado", ruta.get("estado"));
        cuerpo.put("patente", camion == null ? null : camion.get("patente"));
        cuerpo.put("latitud", camion == null ? null : camion.get("latitud"));
        cuerpo.put("longitud", camion == null ? null : camion.get("longitud"));
        cuerpo.put("actualizada", camion == null ? null : hora(camion.get("ubicacionActualizadaEn")));
        return cuerpo;
    }

    private Map<String, Object> camionDe(Map<String, Object> ruta) {
        Object camionId = ruta.get("camionId");
        if (camionId == null) {
            return null;
        }
        return api.camion(UUID.fromString(String.valueOf(camionId)));
    }

    private static String hora(Object valor) {
        if (valor == null) {
            return null;
        }
        String texto = String.valueOf(valor);
        if (texto.length() >= 16) {
            return texto.substring(0, 16).replace('T', ' ');
        }
        return texto;
    }
}
