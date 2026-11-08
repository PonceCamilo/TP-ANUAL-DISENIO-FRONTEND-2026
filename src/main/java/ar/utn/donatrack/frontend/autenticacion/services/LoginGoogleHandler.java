package ar.utn.donatrack.frontend.autenticacion.services;

import ar.utn.donatrack.frontend.comun.exceptions.ApiException;
import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Se ejecuta cuando Google confirmó la identidad. Canjea el ID token de Google
 * por el JWT de DonaTrack en el servicio de autenticación y lo guarda en sesión.
 */
@Component
public class LoginGoogleHandler implements AuthenticationSuccessHandler {

    public static final String ROL_ELEGIDO = "rolElegido";

    private final AuthClient authClient;

    public LoginGoogleHandler(AuthClient authClient) {
        this.authClient = authClient;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        HttpSession sesion = request.getSession();
        String rol = String.valueOf(sesion.getAttribute(ROL_ELEGIDO));
        if (!SesionUsuario.DONANTE.equals(rol) && !SesionUsuario.ENTIDAD.equals(rol)) {
            rol = SesionUsuario.DONANTE;
        }
        try {
            OidcUser google = (OidcUser) authentication.getPrincipal();
            SesionUsuario usuario = authClient.canjearGoogle(google.getIdToken().getTokenValue(), rol);
            sesion.setAttribute(SesionUsuario.ATRIBUTO, usuario);
            response.sendRedirect(destino(rol));
        } catch (ApiException e) {
            sesion.setAttribute("errorLogin", e.getMessage());
            response.sendRedirect("/login?rol=" + rol);
        }
    }

    public static String destino(String rol) {
        return SesionUsuario.ENTIDAD.equals(rol) ? "/entidad/panel" : "/donante/panel";
    }
}
