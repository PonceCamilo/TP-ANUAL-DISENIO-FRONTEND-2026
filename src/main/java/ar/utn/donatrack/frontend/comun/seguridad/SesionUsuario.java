package ar.utn.donatrack.frontend.comun.seguridad;


import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Lo que el front recuerda de la persona logueada: quién es, con qué rol entró
 * y el JWT que le dio el servicio de autenticación. Vive en la sesión HTTP.
 */
public class SesionUsuario implements Serializable {

    public static final String ATRIBUTO = "usuario";
    public static final String DONANTE = "DONANTE";
    public static final String ENTIDAD_BENEFICIARIA = "ENTIDAD_BENEFICIARIA";
    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String CHOFER = "CHOFER";

    public static String panelDe(String rol) {
        return switch (rol) {
            case DONANTE -> "/donante/panel";
            case ENTIDAD_BENEFICIARIA -> "/entidad/panel";
            case ADMINISTRADOR -> "/admin/panel";
            case CHOFER -> "/chofer/panel";
            default -> "/login";
        };
    }

    private final String email;
    private final String nombre;
    private final String rol;
    private final String tipoDonante;
    private final String token;
    private final Instant expiraEn;
    private UUID donanteId;
    private UUID entidadId;
    private UUID camionId;

    public SesionUsuario(String email, String nombre, String rol, String token, Instant expiraEn) {
        this(email, nombre, rol, token, expiraEn, "HUMANA");
    }

    public SesionUsuario(String email, String nombre, String rol, String token, Instant expiraEn, String tipoDonante) {
        this.tipoDonante = tipoDonante;
        this.email = email;
        this.nombre = nombre;
        this.rol = rol;
        this.token = token;
        this.expiraEn = expiraEn;
    }

    public boolean vencida() {
        return expiraEn != null && Instant.now().isAfter(expiraEn);
    }

    public boolean esDonante() {
        return DONANTE.equals(rol);
    }

    public boolean esDonanteJuridico() {
        return esDonante() && "JURIDICA".equals(tipoDonante);
    }

    public String getPrimerNombre() {
        if (nombre == null || nombre.isBlank()) {
            return email;
        }
        return nombre.trim().split("\\s+")[0];
    }

    /** Misma sesión, con el nombre y el token nuevos después de guardar Mis datos. */
    public SesionUsuario renovada(String nombreNuevo, String tokenNuevo, Instant expiraEnNuevo) {
        SesionUsuario nueva = new SesionUsuario(email, nombreNuevo, rol, tokenNuevo, expiraEnNuevo, tipoDonante);
        nueva.donanteId = donanteId;
        nueva.entidadId = entidadId;
        nueva.camionId = camionId;
        return nueva;
    }

    public String getEmail() { return email; }
    public String getNombre() { return nombre; }
    public String getRol() { return rol; }
    public String getToken() { return token; }
    public Instant getExpiraEn() { return expiraEn; }
    public UUID getDonanteId() { return donanteId; }
    public void setDonanteId(UUID donanteId) { this.donanteId = donanteId; }
    public UUID getCamionId() { return camionId; }
    public void setCamionId(UUID camionId) { this.camionId = camionId; }
    public UUID getEntidadId() { return entidadId; }
    public void setEntidadId(UUID entidadId) { this.entidadId = entidadId; }
}
