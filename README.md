# DonaTrack · Front

Cliente liviano de DonaTrack: Spring Boot + Thymeleaf. No tiene base de datos ni lógica de negocio: renderiza HTML a partir de las APIs REST del back y guarda en sesión quién ingresó y su JWT.

## Cómo se conecta con el back
- Es un repo aparte; solo conoce las URLs de los servicios (`SERVICIOS_*_URL`).
- Cuentas e ingreso: `servicio-autenticacion` (`/api/autenticacion/registro`, `/api/autenticacion/ingreso`, `/api/autenticacion/ingreso-social` para Google).
- Cada pedido a los demás servicios lleva `Authorization: Bearer <JWT>`.
- Rutas `/donante/**` y `/entidad/**` se protegen en el front según el rol de la sesión.

## Cómo correrlo
Con el back levantado (`docker compose up -d` en el repo del back):
```bash
cp .env.example .env      # y completá lo que corresponda
docker compose up --build
```
Abrí http://localhost:8080 (Codespaces: puerto 8080 en la pestaña *Puertos*). Sin Docker: `mvn spring-boot:run`.

## Cuentas e ingreso
- **Dos tipos de cuenta:** donante y entidad. En `/login` y `/crear-cuenta` hay un selector arriba.
- **Crear cuenta:** nombre, email y contraseña (mínimo 8 caracteres, se guarda con BCrypt en `autenticacion_db`). El mismo email puede tener una cuenta de donante y otra de entidad.
- **Primer ingreso:** el donante completa un registro corto; la entidad registra sus datos en `/entidad/registro`.
- **Ingresar:** email y contraseña. Si algo falla el mensaje es siempre el mismo (no dice si el email existe).
- **Google (opcional):** botón visible solo si hay `GOOGLE_CLIENT_ID`.

## Configuración
| Variable | Para qué | Por defecto |
|---|---|---|
| `SERVICIOS_DONACIONES_URL` | donaciones | `…:8081` |
| `SERVICIOS_INCENTIVOS_URL` | incentivos | `…:8083` |
| `SERVICIOS_NOTIFICACIONES_URL` | notificaciones | `…:8084` |
| `SERVICIOS_LOGISTICA_URL` | logística | `…:8085` |
| `SERVICIOS_AUTENTICACION_URL` | autenticación | `…:8088` |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | login con Google | vacío (el botón no aparece) |
| `DONATRACK_DEMO` | acceso de prueba sin contraseña (solo desarrollo) | `false` |

**Credenciales:** van siempre en `.env` o variables de entorno. `.env` está en `.gitignore`: nunca lo subas.

### Login con Google
1. Google Cloud Console → *Credenciales* → *ID de cliente de OAuth* (aplicación web).
2. URI de redirección: `<URL pública del front>/login/oauth2/code/google` (Codespaces: `https://<nombre>-8080.app.github.dev/...`).
3. `GOOGLE_CLIENT_ID` y `GOOGLE_CLIENT_SECRET` en el `.env` del front y el **mismo** `GOOGLE_CLIENT_ID` en el del back.

## Pantallas
| Rol | Pantallas |
|---|---|
| Todos | Inicio · Ingresar · Crear cuenta · Error |
| Donante | Inicio · Donar · Donaciones → Seguimiento · Avisos · Registro |
| Entidad | Mi entidad (campañas y necesidades) → Nueva campaña / necesidad · Disponibles · Recepciones · Avisos · Registro de la entidad |

## Diseño
Tokens en `static/css/tokens.css` (colores, tipografías Bricolage Grotesque + Instrument Sans, espacios, objetivo táctil mínimo de 48 px). Contraste AA, foco visible, navegación inferior en celular, loader si un pedido pasa los 300 ms, toasts, banners y modales.

## Estructura
Igual que el back: una carpeta por servicio, y cada una con su `controller/` y `services/`
(el cliente REST de ese servicio). Lo que usan todos va en `comun/`.
```
src/main/java/ar/utn/donatrack/frontend/
├── autenticacion/   controller/ (login, crear cuenta, Google) · services/ (AuthClient, LoginGoogleHandler) · config/
├── donaciones/      controller/ (donante y entidad) · services/DonacionesApi
├── notificaciones/  controller/ (avisos de donante y entidad) · services/NotificacionesApi
├── incentivos/      services/IncentivosApi   (las métricas se ven en el panel del donante)
├── logistica/       todavía sin pantallas (package-info explica dónde va lo nuevo)
└── comun/           config/ · seguridad/ (sesión) · clientes/ApiClient · exceptions/ · util/ · controller/ (inicio, paneles, errores)

src/main/resources/templates/   la misma división: autenticacion/ · donaciones/{donante,entidad}/ · notificaciones/ · comun/ (+ fragmentos/)
src/main/resources/static/      css/ y js/ compartidos (tokens.css = diseño unificado)
src/test/java/…                 espeja los paquetes
```
**Regla para trabajar por separado:** cada integrante toca solo la carpeta de su servicio (Java y plantillas). Si necesita algo de otro servicio, lo pide por su `…Api`. Los dos paneles de inicio están en `comun/` porque juntan datos de donaciones e incentivos.

## Límites conocidos
- No se compiló ni corrió en el entorno donde se escribió: es el primer build real.
- Los servicios del back todavía no validan el JWT.
- No hay recuperación de contraseña ni bloqueo por intentos fallidos.
- Auditoría de accesibilidad (Lighthouse/axe, lector de pantalla) pendiente.
