package ar.utn.donatrack.frontend.donaciones.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Protege las pantallas de administración que vienen de servicio-donaciones. */
@Configuration
public class AdministracionConfig implements WebMvcConfigurer {

    static final String[] RUTAS = {"/admin/donaciones/**", "/admin/donantes/**", "/admin/entidades/**"};

    private final AdministradorInterceptor interceptor;

    public AdministracionConfig(AdministradorInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns(RUTAS);
    }
}
