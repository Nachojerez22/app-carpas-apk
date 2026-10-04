# Cuenta de Google: guardar los datos en tu Google Drive

Desde la versión 0.8.2, **Lugar → Cuenta de Google** guarda el diario, los datos manuales, el
equipo y los ajustes en una **carpeta privada de tu Google Drive** que solo ve CarpStrategy
(permiso `drive.appdata`: la app no puede ver ni tocar tus archivos de Drive).

- Inicias sesión **una vez**; la sesión queda abierta.
- Al abrir la app se baja lo último de Drive; cada cambio se sube a los ~15 s si hay conexión.
- Sin cobertura (el embalse) todo funciona igual con la copia del móvil y se sube al volver.
- En otro móvil, o tras reinstalar, inicias sesión y aparece todo.
- Si cambiaste cosas en dos móviles sin conexión, se **unen** sin duplicar sesiones.
- **Nunca** se suben la ubicación GPS en vivo ni claves (ajustes `local_…` del móvil).
- Cerrar sesión no borra nada: los datos quedan en el móvil y en Drive.

## Configurar Google Cloud (una vez, ~15 min)

Google solo deja iniciar sesión a apps registradas. Como es una app personal, lo registras tú.
**No hay que pasar ninguna clave a la app ni subir nada al repositorio**: la credencial de
Android se reconoce por el nombre del paquete y la huella SHA-1 de la firma.

1. Entra en <https://console.cloud.google.com> con tu cuenta y crea un proyecto
   («CarpStrategy»).
2. **APIs y servicios → Biblioteca** → busca **Google Drive API** → **Habilitar**.
3. **Pantalla de consentimiento de OAuth** (o «Google Auth Platform»):
   - Tipo de usuario: **Externo**. Nombre: CarpStrategy. Correo de asistencia y de
     contacto: el tuyo.
   - Permisos (scopes): añade `.../auth/drive.appdata`.
   - Usuarios de prueba: añade tu correo.
   - Si lo dejas en «Prueba», Google puede pedirte volver a dar permiso cada cierto tiempo;
     la app lo avisa («Volver a iniciar sesión»). Publicarlo («En producción») lo evita; para
     este permiso no debería hacer falta revisión de Google, pero compruébalo en la consola.
4. **Credenciales → Crear credenciales → ID de cliente de OAuth → Android**:
   - Paquete `com.nachojerez.carpstrategy` (producción).
   - **Huella SHA-1** que imprime la CI en el paso «Huella SHA-1 de la clave de firma»:
     `A9:71:06:03:EE:CA:27:4E:D8:57:5B:F9:3F:1E:86:17:F4:F0:7B:2B`. No es secreta.
   - El ID de cliente que muestra Google **no hace falta en la app** (no se copia en el código).
   - Opcional: otra credencial igual con el paquete `com.nachojerez.carpstrategy.prueba` si
     quieres iniciar sesión también en la versión de prueba (sin ella, en la de prueba no se
     puede).
5. Listo: en la app, **Lugar → Cuenta de Google → Iniciar sesión con Google**.

Si la app dice «Google no reconoce esta app», revisa el paquete y la huella SHA-1 de la
credencial (código 10 de Google Play Services).

## Formato

El archivo `carpstrategy-sync.json` (formato `carpstrategy-sync` v1) es la base de datos tal
cual: `registros`, `ajustes`, `sesiones` y `valoraciones`, cada fila identificada por su clave
natural (no por el id del móvil). La huella SHA-256 del contenido va en las propiedades del
archivo para saber, sin descargarlo, si cambió.

## Versiones nuevas

Cada versión de `main` se publica en **Releases** del repositorio con el APK
`CarpStrategy.apk` (descarga directa desde el móvil, sin zip). Al abrir la app de producción,
si hay una versión más nueva aparece un aviso con el botón **Descargar**: se instala **encima**
de la actual, sin desinstalar, y no se borra nada. Solo se consulta cuál es la última versión;
no se envía ningún dato.
