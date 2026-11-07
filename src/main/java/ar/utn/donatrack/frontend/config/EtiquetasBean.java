package ar.utn.donatrack.frontend.config;

import java.util.List;

import ar.utn.donatrack.frontend.util.Etiquetas;
import org.springframework.stereotype.Component;

/** Wrapper para usar Etiquetas desde Thymeleaf como ${@etiquetas.estado(x)}. */
@Component("etiquetas")
public class EtiquetasBean {
    public String estado(String c) { return Etiquetas.estado(c); }
    public String variante(String c) { return Etiquetas.variante(c); }
    public String icono(String c) { return Etiquetas.icono(c); }
    public List<Etiquetas.Paso> recorrido(String c) { return Etiquetas.recorrido(c); }
}
