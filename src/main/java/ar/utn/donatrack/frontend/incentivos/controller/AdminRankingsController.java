package ar.utn.donatrack.frontend.incentivos.controller;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.util.Mapas;
import ar.utn.donatrack.frontend.incentivos.services.IncentivosApi;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Ranking de la administración (Entrega 6, requisito 19): el podio del mes en
 * curso y el historial de los meses cerrados, paginado.
 *
 * Los datos los da servicio-incentivos (solo a la administración); el nombre de
 * cada persona viene en nombreVisible. La pantalla queda en /admin, así que
 * SesionInterceptor ya exige el rol.
 */
@Controller
public class AdminRankingsController {

    static final int PUESTOS_POR_PAGINA = 20;

    private final IncentivosApi incentivos;

    public AdminRankingsController(IncentivosApi incentivos) {
        this.incentivos = incentivos;
    }

    @GetMapping("/admin/rankings")
    public String rankings(@RequestParam(required = false) Integer anio, @RequestParam(required = false) Integer mes,
                           @RequestParam(defaultValue = "0") int pagina, Model model) {
        YearMonth actual = YearMonth.now();
        model.addAttribute("area", "Rankings");
        model.addAttribute("descripcionArea", "Las personas donantes que más misiones completaron: el mes en curso y el historial.");
        model.addAttribute("seccion", "rankings");
        cargarPodio(model);

        Set<String> periodos = new LinkedHashSet<>();
        Set<Integer> anios = new TreeSet<>(Comparator.reverseOrder());
        periodos.add(actual.getYear() + "-" + actual.getMonthValue());
        anios.add(actual.getYear());
        try {
            for (Map<String, Object> periodo : incentivos.rankingMesesDisponibles()) {
                int a = ((Number) periodo.get("anio")).intValue();
                int m = ((Number) periodo.get("mes")).intValue();
                periodos.add(a + "-" + m);
                anios.add(a);
            }
        } catch (ApiException e) {
            model.addAttribute("historialNoDisponible", true);
        }
        int anioSel = anio != null ? anio : actual.getYear();
        int mesSel = mes != null && mes >= 1 && mes <= 12 ? mes : actual.getMonthValue();
        model.addAttribute("anios", new ArrayList<>(anios));
        model.addAttribute("periodosDisponibles", periodos);
        model.addAttribute("anioSel", anioSel);
        model.addAttribute("mesSel", mesSel);
        cargarRanking(model, anioSel, mesSel, Math.max(0, pagina));
        return "incentivos/admin-rankings";
    }

    private void cargarPodio(Model model) {
        model.addAttribute("podioNoDisponible", false);
        try {
            Map<String, Object> podio = incentivos.podio();
            model.addAttribute("podioProvisional", Boolean.TRUE.equals(podio.get("provisional")));
            model.addAttribute("podio", conNombre(Mapas.lista(podio.get("puestos"))));
        } catch (ApiException e) {
            model.addAttribute("podioNoDisponible", true);
            model.addAttribute("podio", List.of());
        }
    }

    private void cargarRanking(Model model, int anio, int mes, int pagina) {
        model.addAttribute("rankingNoDisponible", false);
        model.addAttribute("rankingPagina", pagina);
        model.addAttribute("rankingTotalPaginas", 0);
        model.addAttribute("rankingTotalElementos", 0);
        try {
            Map<String, Object> ranking = incentivos.rankingDeMes(anio, mes, pagina, PUESTOS_POR_PAGINA);
            model.addAttribute("ranking", conNombre(Mapas.lista(ranking.get("contenido"))));
            model.addAttribute("rankingProvisional", Boolean.TRUE.equals(ranking.get("provisional")));
            model.addAttribute("rankingTotalPaginas", numero(ranking.get("totalPaginas")));
            model.addAttribute("rankingTotalElementos", numero(ranking.get("totalElementos")));
        } catch (ApiException e) {
            // 404: ese mes no tiene un ranking guardado; se muestra vacío.
            model.addAttribute("ranking", List.of());
            model.addAttribute("rankingNoDisponible", e.getStatus() != 404 && e.getStatus() != 400);
        }
    }

    /** Cada puesto con el nombre a mostrar: el nombre visible, o "Donante" si todavía no llegó. */
    static List<Map<String, Object>> conNombre(List<Map<String, Object>> puestos) {
        return puestos.stream().map(puesto -> {
            Map<String, Object> fila = new LinkedHashMap<>(puesto);
            String nombre = Mapas.texto(puesto, "nombreVisible");
            fila.put("nombre", nombre.isBlank() ? "Donante" : nombre);
            return (Map<String, Object>) fila;
        }).toList();
    }

    private static long numero(Object valor) {
        return valor instanceof Number n ? n.longValue() : 0L;
    }
}
