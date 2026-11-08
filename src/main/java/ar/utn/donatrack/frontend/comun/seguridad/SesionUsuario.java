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
    public static final String ENTIDAD = "ENTIDAD";

    private final String email;
    private final String nombre;
    private final String rol;
    private final String token;
    private final Instant expiraEn;
    private UUID donanteId;
    private UUID entidadId;

    public SesionUsuario(String email, String nombre, String rol, String token, Instant expiraEn) {
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

    public String getPrimerNombre() {
        if (nombre == null || nombre.isBlank()) {
            return email;
        }
        return nombre.trim().split("\\s+")[0];
    }

    public String getEmail() { return email; }
    public String getNombre() { return nombre; }
    public String getRol() { return rol; }
    public String getToken() { return token; }
    public Instant getExpiraEn() { return expiraEn; }
    public UUID getDonanteId() { return donanteId; }
    public void setDonanteId(UUID donanteId) { this.donanteId = donanteId; }
    public UUID getEntidadId() { return entidadId; }
    public void setEntidadId(UUID entidadId) { this.entidadId = entidadId; }
}
