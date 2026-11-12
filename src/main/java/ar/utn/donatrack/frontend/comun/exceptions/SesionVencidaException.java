package ar.utn.donatrack.frontend.comun.exceptions;

/**
 * Un servicio rechazó el token de la sesión (401): venció o ya no es válido
 * (Entrega 6, los servicios validan el JWT). No es un ApiException a propósito:
 * los controllers que capturan ApiException para mostrar un aviso no la atrapan,
 * y SesionVencidaAdvice cierra la sesión y lleva a ingresar de nuevo.
 */
public class SesionVencidaException extends RuntimeException {

    public SesionVencidaException() {
        super("Tu sesión venció. Volvé a ingresar.");
    }
}
