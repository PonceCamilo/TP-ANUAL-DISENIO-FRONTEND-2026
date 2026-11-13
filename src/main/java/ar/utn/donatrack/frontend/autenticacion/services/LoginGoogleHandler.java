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

    public static final String TIPO_ELEGIDO = "tipoDonanteElegido";
    private final AuthClient authClient;

    public LoginGoogleHandler(AuthClient authClient) {
        this.authClient = authClient;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        HttpSession sesion = request.getSession();
        String rol = String.valueOf(sesion.getAttribute(ROL_ELEGIDO));
        if (!SesionUsuario.DONANTE.equals(rol) && !SesionUsuario.ENTIDAD_BENEFICIARIA.equals(rol)) {
            rol = SesionUsuario.DONANTE;
        }
        try {
            OidcUser google = (OidcUser) authentication.getPrincipal();
            String tipo = (String) sesion.getAttribute(TIPO_ELEGIDO);
            sesion.removeAttribute(ROL_ELEGIDO);
            sesion.removeAttribute(TIPO_ELEGIDO);
            SesionUsuario usuario = authClient.canjearGoogle(google.getIdToken().getTokenValue(), rol, tipo);
            sesion.setAttribute(SesionUsuario.ATRIBUTO, usuario);
            response.sendRedirect(ar.utn.donatrack.frontend.comun.seguridad.VolverA.destino(sesion, usuario));
        } catch (ApiException e) {
            sesion.setAttribute("errorLogin", e.getMessage());
            response.sendRedirect("/login?rol=" + rol);
        }
    }

    public static String destino(String rol) {
        return SesionUsuario.panelDe(rol);
    }
}
