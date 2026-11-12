package ar.utn.donatrack.frontend.donaciones.services;

import java.util.List;
import java.util.Map;

/**
 * Qué cambios de estado puede hacer a mano la persona administradora. Repite las
 * reglas de servicio-donaciones solo para ofrecer las opciones válidas en
 * pantalla: quien decide sigue siendo el servicio.
 *
 * Donaciones: a ASIGNACION_REALIZADA no se llega cambiando el estado sino
 * asignando una entidad, y a LISTA_PARA_ENTREGAR despachando a logística (los
 * dos registran datos además del estado), así que no aparecen como opción.
 */
public final class Transiciones {

    private static final Map<String, List<String>> DONACION = Map.of(
            "EN_DEPOSITO", List.of("VENCIDA"),
            "LISTA_PARA_ENTREGAR", List.of("EN_TRASLADO"),
            "EN_TRASLADO", List.of("ENTREGADA", "ENTREGA_FALLIDA"),
            "ENTREGA_FALLIDA", List.of("EN_TRASLADO", "EN_DEPOSITO"));

    private static final Map<String, List<String>> DONANTE = Map.of(
            "ACTIVO", List.of("INACTIVO", "BLOQUEADO"),
            "INACTIVO", List.of("ACTIVO"),
            "BLOQUEADO", List.of("ACTIVO"));

    private static final Map<String, String> ESTADOS_DONANTE = Map.of(
            "ACTIVO", "Activa",
            "INACTIVO", "Inactiva",
            "BLOQUEADO", "Bloqueada");

    private Transiciones() {}

    public static List<String> deDonacion(String estado) {
        return estado == null ? List.of() : DONACION.getOrDefault(estado, List.of());
    }

    public static List<String> deDonante(String estado) {
        return estado == null ? List.of() : DONANTE.getOrDefault(estado, List.of());
    }

    /** El servicio rechaza una entrega fallida o un bloqueo sin justificación. */
    public static boolean exigeJustificacion(String destino) {
        return "ENTREGA_FALLIDA".equals(destino) || "BLOQUEADO".equals(destino);
    }

    public static String estadoDonante(String codigo) {
        return codigo == null ? "" : ESTADOS_DONANTE.getOrDefault(codigo, codigo);
    }
}
