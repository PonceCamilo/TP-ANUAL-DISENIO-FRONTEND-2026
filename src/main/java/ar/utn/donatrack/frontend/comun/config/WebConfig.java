package ar.utn.donatrack.frontend.comun.config;

import ar.utn.donatrack.frontend.comun.seguridad.SesionInterceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SesionInterceptor sesionInterceptor;

    public WebConfig(SesionInterceptor sesionInterceptor) {
        this.sesionInterceptor = sesionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sesionInterceptor)
                // Entrega 6: la recepción y el mapa de una ruta también piden sesión.
                .addPathPatterns("/donante/**", "/entidad/**", "/admin/**", "/transporte/**", "/chofer/**",
                        "/recepcion", "/tracking/**", "/cuenta")
                .excludePathPatterns("/admin/ingreso", "/transporte/ingreso");
    }
}
