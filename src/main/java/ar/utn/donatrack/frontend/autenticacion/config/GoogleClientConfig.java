package ar.utn.donatrack.frontend.autenticacion.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Registra Google como proveedor solo si hay credenciales. Se hace a mano (y no
 * con spring.security.oauth2.client.registration.*) porque Spring Boot falla al
 * arrancar si el client-id está vacío, y queremos que el front levante igual y
 * ofrezca el acceso demo cuando todavía no se configuró Google.
 */
@Configuration
public class GoogleClientConfig {

    @Bean
    @ConditionalOnExpression("!'${donatrack.google.client-id:}'.isEmpty()")
    ClientRegistrationRepository google(@Value("${donatrack.google.client-id}") String id,
                                        @Value("${donatrack.google.client-secret:}") String secret) {
        ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(id)
                .clientSecret(secret)
                .scope("openid", "profile", "email")
                .build();
        return new InMemoryClientRegistrationRepository(google);
    }
}
