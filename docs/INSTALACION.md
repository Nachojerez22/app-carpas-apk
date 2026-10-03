# Instalar y actualizar CarpStrategy en Android

La app no está en Google Play: se instala desde el APK que genera GitHub Actions. Hay dos
versiones, que se pueden tener instaladas a la vez sin mezclar datos:

| Versión | Nombre en el móvil | Cuándo se genera | Para qué |
|---|---|---|---|
| **Producción** | CarpStrategy | Cada cambio en `main` (si existe la clave de firma) | Uso real: diario, mediciones |
| **Prueba** | CarpStrategy (prueba) | Cada ejecución de la CI, en cualquier rama | Probar cambios antes del merge |

Requisitos: Android 8.0 o superior.

## 1. Configuración única (secretos del repositorio)

En GitHub: **Settings → Secrets and variables → Actions → New repository secret**. Los
secretos nunca se ven en el código ni en los logs.

| Secreto | Valor |
|---|---|
| `AEMET_API_KEY` | Tu API key de AEMET OpenData. Sin ella la app funciona sin observaciones reales. |
| `CARP_KEYSTORE_BASE64` | La clave de firma en base64 (ver abajo) |
| `CARP_KEYSTORE_PASSWORD` | Contraseña del almacén de claves |
| `CARP_KEY_ALIAS` | Alias de la clave (p. ej. `carpstrategy`) |
| `CARP_KEY_PASSWORD` | Contraseña de la clave (puede ser la misma) |

### Crear la clave de firma (una sola vez en la vida de la app)

**Por qué importa:** Android solo deja actualizar una app si el APK nuevo está firmado con la
**misma clave**. Si cambia la clave hay que desinstalar, y desinstalar **borra todos tus datos**.
Guarda el archivo `.jks` y las contraseñas en un sitio seguro (gestor de contraseñas y una copia
fuera del ordenador). Nunca lo subas al repositorio (`.gitignore` ya lo impide).

Con Java instalado (o el `keytool` que trae Android Studio en `jbr/bin`):

```bash
keytool -genkeypair -v -keystore carpstrategy.jks -alias carpstrategy \
  -keyalg RSA -keysize 4096 -validity 10000
```

Pasarla a base64 para el secreto `CARP_KEYSTORE_BASE64`:

```bash
# Linux
base64 -w0 carpstrategy.jks
# macOS
base64 -i carpstrategy.jks | tr -d '\n'
```

```powershell
# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("carpstrategy.jks"))
```

Con los secretos creados, la siguiente ejecución de la CI en `main` (o **Actions → CI → Run
workflow** sobre `main`) genera el APK de producción.

## 2. Descargar e instalar

1. En GitHub: **Actions → CI →** la última ejecución en verde.
2. Abajo, en **Artifacts**: `CarpStrategy-N` (producción) o `CarpStrategy-prueba-N` (prueba).
   Se descarga un `.zip` con el `.apk` dentro (los artefactos se guardan 90 días).
3. Pasa el `.apk` al móvil (o descárgalo directamente desde el navegador del móvil con tu sesión
   de GitHub iniciada) y ábrelo.
4. Android pedirá permitir **instalar apps desconocidas** para el navegador o el gestor de
   archivos: acéptalo solo para esa app.
5. La primera vez que pulses «Usar mi ubicación» la app pedirá el permiso de ubicación. Solo se
   lee una vez, al pulsar, y no sale del móvil.

## 3. Actualizar sin perder datos

- Instala el APK nuevo **encima** del anterior (sin desinstalar). Con la misma clave de firma y
  un número de versión mayor (lo pone la CI), Android conserva todos los datos.
- La base de datos se migra sola y **nunca** de forma destructiva.
- Si Android dice que el paquete «entra en conflicto» con el instalado, es que la firma no
  coincide. **No desinstales** sin hacer antes las copias del apartado 4.
- APK de prueba firmados antes de crear la clave fija: usan una clave temporal distinta en cada
  ejecución. Para pasar a los firmados, desinstala solo **CarpStrategy (prueba)**.

## 4. Copias de seguridad

La app no usa la copia en la nube de Android: tus datos solo están en el móvil.

- **Diario → Exportar diario (JSON)**: todas las sesiones, con la valoración previa y los
  parámetros guardados ([formato](FORMATO_DIARIO.md)). **Restaurar una copia del diario** las
  recupera sin duplicar.
- **Datos → Exportar copia**: tus registros en formato `carpstrategy-datos`
  ([formato](FORMATO_DATOS.md)); se recuperan con **Importar JSON** (quedan marcados como
  importados).

Haz una copia de vez en cuando y siempre antes de desinstalar o cambiar de móvil.

## 5. Qué se guarda para el aprendizaje con tus datos (fase 7)

Desde la versión 0.7.0, cada valoración de la app y cada sesión guardan una **foto fija** de
todos los parámetros que vieron las reglas (`temp_agua_c`, `estacion`, `viento_24h_kmh`…), el
valor de cada nivel de la cadena, las reglas activadas con su factor y la huella de
`rules.json` con la que se calcularon. Con 20–40 sesiones permitirá comprobar qué variables
predicen de verdad con tus datos (CONOCIMIENTO.md §10) sin reconstruir nada a posteriori.

Para que una sesión tenga valoración previa, abre **Estrategia** antes de salir o pulsa
**Nueva sesión** al llegar al agua.
