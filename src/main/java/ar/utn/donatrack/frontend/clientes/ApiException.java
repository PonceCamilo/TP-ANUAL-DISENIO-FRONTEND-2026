package ar.utn.donatrack.frontend.clientes;

/** Falló una llamada a un servicio. El mensaje ya está listo para mostrarle a la persona. */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(String mensaje, int status) {
        super(mensaje);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
