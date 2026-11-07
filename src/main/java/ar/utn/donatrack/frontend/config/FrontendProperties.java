package ar.utn.donatrack.frontend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Direcciones de los servicios y opciones del front. Todo configurable: el
 * cliente no tiene ninguna URL escrita en el código.
 */
@ConfigurationProperties(prefix = "donatrack")
public record FrontendProperties(Servicios servicios, boolean demoEnabled) {

    public record Servicios(
            String donaciones,
            String incentivos,
            String notificaciones,
            String logistica,
            String autenticacion
    ) {}
}
