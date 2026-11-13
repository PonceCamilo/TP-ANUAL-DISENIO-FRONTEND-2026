package ar.utn.donatrack.frontend.donaciones.controller;

import ar.utn.donatrack.frontend.comun.clientes.ApiClient;
import ar.utn.donatrack.frontend.comun.config.FrontendProperties;
import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.donaciones.services.DonacionesApi;
import ar.utn.donatrack.frontend.donaciones.services.Pagina;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DonacionesApi sin red: devuelve lo que se le carga y anota cada acción.
 * Escrita a mano en lugar de un mock, para no depender de qué versiones de Java
 * soporta la librería de mocks.
 */
class DonacionesApiFalsa extends DonacionesApi {

    /** Cada acción que modifica algo, como "cambiarEstado VENCIDA". */
    final List<String> acciones = new ArrayList<>();
    Map<String, Object> filtrosPedidos;
    int paginaPedida = -1;
    Map<String, Object> cuerpoEnviado;

    /** Si no es null, las acciones fallan con esta excepción, como si el servicio la rechazara. */
    ApiException falla;

    Pagina pagina = new Pagina(List.of(), 1, 0, 0);
    Map<String, Object> donacion = donacion(UUID.randomUUID(), "EN_DEPOSITO");
    Map<String, Object> donante = Map.of("id", UUID.randomUUID().toString(), "email", "ana@example.com",
            "nombre", "Ana", "apellido", "Pérez", "estado", "ACTIVO");
    Map<String, Object> entidad = Map.of("id", UUID.randomUUID().toString(), "razonSocial", "Comedor Los Pibes");
    Map<String, Object> candidatos = Map.of("rankings", List.of(), "coincidencias", List.of());
    List<Map<String, Object>> pendientes = List.of();
    List<Map<String, Object>> enDeposito = List.of();
    List<Map<String, Object>> paraDespachar = List.of();
    List<Map<String, Object>> entidades = List.of();
    UUID donanteEncontrado;

    DonacionesApiFalsa() {
        super(new ApiClient(), new FrontendProperties(
                new FrontendProperties.Servicios("http://donaciones", null, null, null, null), false));
    }

    static Map<String, Object> donacion(UUID id, String estado) {
        Map<String, Object> d = new HashMap<>();
        d.put("id", id.toString());
        d.put("descripcion", "Camperas de invierno");
        d.put("subcategoria", "Abrigo");
        d.put("fechaDonacion", "2026-11-05T10:00:00");
        d.put("estado", estado);
        d.put("idDonante", UUID.randomUUID().toString());
        d.put("idEntidadBeneficiaria", null);
        d.put("proveedorLogistica", null);
        d.put("idSeguimientoLogistica", null);
        Map<String, Object> bien = new HashMap<>();
        bien.put("descripcion", "Campera talle M");
        bien.put("cantidad", 3);
        bien.put("unidad", "unidades");
        bien.put("esNuevo", true);
        bien.put("fechaVencimiento", null);
        d.put("bienes", List.of(bien));
        Map<String, Object> cambio = new HashMap<>();
        cambio.put("estado", "EN_DEPOSITO");
        cambio.put("fechaHora", "2026-11-05T10:00:00");
        cambio.put("justificacion", null);
        d.put("historialEstados", List.of(cambio));
        return d;
    }

    private void registrar(String accion) {
        if (falla != null) {
            throw falla;
        }
        acciones.add(accion);
    }

    @Override
    public Pagina paginaDeDonaciones(Map<String, Object> filtros, int numero) {
        filtrosPedidos = filtros;
        paginaPedida = numero;
        return pagina;
    }

    @Override
    public Pagina paginaDeDonantes(String estado, String email, int numero) {
        paginaPedida = numero;
        return pagina;
    }

    @Override
    public Pagina paginaDeEntidades(String email, int numero) {
        paginaPedida = numero;
        return pagina;
    }

    @Override
    public Map<String, Object> donacion(UUID id) { return donacion; }

    @Override
    public Map<String, Object> donante(UUID id) { return donante; }

    @Override
    public Map<String, Object> entidad(UUID id) { return entidad; }

    @Override
    public Map<String, Object> candidatos(UUID donacionId) { return candidatos; }

    @Override
    public List<Map<String, Object>> candidatosPendientes() { return pendientes; }

    @Override
    public List<Map<String, Object>> donacionesEnDeposito() { return enDeposito; }

    @Override
    public List<Map<String, Object>> donacionesParaDespachar() { return paraDespachar; }

    @Override
    public List<Map<String, Object>> entidades() { return entidades; }

    @Override
    public UUID buscarDonante(String email) { return donanteEncontrado; }

    // ── Entrega 6: "Mis donaciones" filtrada y datos públicos ──────────────────

    List<String> subcategoriasPedidas;
    String estadoPedido;
    List<String> catalogo = List.of("Abrigo", "Juguetes");
    Pagina paginaDeEntidadesPublicas = new Pagina(List.of(), 1, 0, 0);
    Map<String, Object> entregadas = Map.of("contenido", List.of(), "totalElementos", 0);

    @Override
    public Pagina paginaDeDonacionesDe(UUID donanteId, String estado, List<String> subcategorias, int numero) {
        estadoPedido = estado;
        subcategoriasPedidas = subcategorias;
        paginaPedida = numero;
        return pagina;
    }

    @Override
    public List<String> subcategorias() {
        if (falla != null) {
            throw falla;
        }
        return catalogo;
    }

    /** Si no es null, las lecturas públicas fallan con esta excepción (por ejemplo, un token vencido). */
    RuntimeException fallaDeLectura;
    Pagina paginaDeEntregadas = new Pagina(List.of(), 1, 0, 0);

    @Override
    public Pagina entidadesPublicas(int numero) {
        if (fallaDeLectura != null) {
            throw fallaDeLectura;
        }
        paginaPedida = numero;
        return paginaDeEntidadesPublicas;
    }

    @Override
    public Pagina paginaDeEntregadas(int numero, int tamanio) {
        paginaPedida = numero;
        return paginaDeEntregadas;
    }

    @Override
    public Map<String, Object> entregadasRecientes(int cantidad) {
        if (falla != null) {
            throw falla;
        }
        return entregadas;
    }

    @Override
    public void cambiarEstado(UUID donacionId, String estado, String justificacion) {
        registrar("cambiarEstado " + estado);
    }

    @Override
    public void despachar(List<UUID> donacionIds, String proveedor) {
        registrar("despachar " + donacionIds.size() + " " + proveedor);
    }

    @Override
    public void eliminarDonacion(UUID donacionId) { registrar("eliminarDonacion"); }

    @Override
    public void asignar(UUID donacionId, UUID entidadId) { registrar("asignar " + entidadId); }

    @Override
    public void crearDonacion(Map<String, Object> cuerpo) {
        cuerpoEnviado = cuerpo;
        registrar("crearDonacion");
    }

    /** Entrega 6: alta de entidad por la administración. */
    @Override
    public UUID crearEntidad(Map<String, Object> cuerpo) {
        cuerpoEnviado = cuerpo;
        registrar("crearEntidad");
        return UUID.randomUUID();
    }

    /** Desde la rama de Lucía, el alta devuelve el id de la persona creada (para vincular el perfil). */
    @Override
    public UUID registrarDonante(Map<String, Object> cuerpo) {
        cuerpoEnviado = cuerpo;
        registrar("registrarDonante");
        return UUID.randomUUID();
    }

    @Override
    public void cambiarEstadoDonante(UUID donanteId, String estado, String justificacion) {
        registrar("cambiarEstadoDonante " + estado);
    }

    @Override
    public void eliminarDonante(UUID donanteId) { registrar("eliminarDonante"); }

    @Override
    public void eliminarEntidad(UUID entidadId) { registrar("eliminarEntidad"); }
}
