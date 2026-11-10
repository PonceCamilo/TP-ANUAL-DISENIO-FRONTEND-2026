package ar.utn.donatrack.frontend.donaciones.services;

import ar.utn.donatrack.frontend.comun.util.Mapas;

import java.util.List;
import java.util.Map;

/**
 * Una página de un listado de servicio-donaciones, tal como la devuelve el
 * servicio: {contenido, pagina, totalPaginas, totalElementos}. Las pantallas de
 * administración muestran una página por vez en lugar de traer todo.
 */
public final class Pagina {

    private final List<Map<String, Object>> contenido;
    private final int numero;
    private final int totalPaginas;
    private final long totalElementos;

    public Pagina(List<Map<String, Object>> contenido, int numero, int totalPaginas, long totalElementos) {
        this.contenido = contenido;
        this.numero = numero;
        this.totalPaginas = totalPaginas;
        this.totalElementos = totalElementos;
    }

    static Pagina desde(Map<String, Object> respuesta) {
        return new Pagina(
                Mapas.lista(respuesta.get("contenido")),
                entero(respuesta.get("pagina")),
                entero(respuesta.get("totalPaginas")),
                respuesta.get("totalElementos") instanceof Number n ? n.longValue() : 0);
    }

    private static int entero(Object valor) {
        return valor instanceof Number n ? n.intValue() : 0;
    }

    public List<Map<String, Object>> getContenido() { return contenido; }
    public int getNumero() { return numero; }
    public int getTotalPaginas() { return totalPaginas; }
    public long getTotalElementos() { return totalElementos; }

    public boolean isVacia() { return contenido.isEmpty(); }
    public boolean isHayAnterior() { return numero > 0; }
    public boolean isHaySiguiente() { return numero + 1 < totalPaginas; }
}
