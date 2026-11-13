package ar.utn.donatrack.frontend.comun.config;

import ar.utn.donatrack.frontend.autenticacion.services.LoginGoogleHandler;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * Spring Security se usa para lo que hace bien: el flujo OIDC con Google, el
     * logout y la protección CSRF de los formularios (Thymeleaf agrega el token
     * solo en los th:action).
     *
     * El acceso a cada pantalla lo controla SesionInterceptor con el usuario
     * guardado en sesión, que es el que trae el JWT de DonaTrack.
     *
     * oauth2Login solo se activa si hay credenciales de Google configuradas;
     * sin ellas el front arranca igual y queda el ingreso con email y contraseña.
     */
    @Bean
    SecurityFilterChain filtros(HttpSecurity http,
                                ObjectProvider<ClientRegistrationRepository> clientes,
                                LoginGoogleHandler loginGoogle) throws Exception {
        http
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .logout(l -> l.logoutUrl("/salir").logoutSuccessUrl("/").invalidateHttpSession(true));

        if (clientes.getIfAvailable() != null) {
            http.oauth2Login(o -> o
                    .loginPage("/")
                    .successHandler(loginGoogle)
                    .failureUrl("/?error=google"));
        }
        return http.build();
    }
}
