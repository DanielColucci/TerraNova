# Interfaz TerraNova Sanctuary & Resort (100 % local)

Tema visual basado en `terranova_sanctuary_resort.html`: negro suave, champagne (#e2d5c3) y dorado (#c5a880),
títulos en serif, barra flotante de vidrio y tarjetas con bordes redondeados.
**No usa internet**: ni CDN, ni Google Fonts, ni Tailwind, ni imágenes remotas.

## Archivos (todo en `src/main/resources/static/`)

| Carpeta | Archivo | Para qué sirve |
|---|---|---|
| `css/` | `bootstrap.min.css` | Bootstrap 5 (ya era local) |
| `css/` | `fonts.css` | Declara las fuentes locales (`@font-face`) |
| `css/` | `icons.css` | Íconos `bi bi-nombre` como SVG incrustados (las plantillas no cambiaron) |
| `css/` | `terranova.css` | El tema: colores, sidebar, tarjetas, botones, login y portada del huésped |
| `fonts/` | `Poppins-*.ttf`, `Lora-*.ttf` | Poppins (texto) y Lora (títulos). Licencia SIL OFL |
| `img/` | `habitacion_*.jpg` | Fotos de habitaciones y portadas |
| `js/` | `bootstrap.bundle.min.js`, `terranova.js`, `asistente.js`, `auth.js` | Scripts |

Orden de carga (ya puesto en `fragments/layout.html`, `auth/acceso.html` y `auth/verificar.html`):
`bootstrap.min.css` → `fonts.css` → `icons.css` → `terranova.css`.

## Cómo cambiar cosas
- **Colores**: variables al inicio de `terranova.css` (`--tn-accent`, `--tn-champagne`, `--tn-dark`...).
- **Foto de portada**: reemplaza `img/habitacion_suite.jpg` (o cambia la ruta en `.landing-hero::before` y `.auth-hero::before`).
- **Nuevo ícono**: agrega una línea en `icons.css` con otro SVG (`.bi-nombre { --bi: url("data:image/svg+xml,..."); }`).
- **Nueva fuente**: copia el `.ttf`/`.woff2` a `fonts/` y decláralo en `fonts.css`.
- **Seguridad**: `/fonts/**` es público junto con `/css/**`, `/js/**` e `/img/**` (`SecurityConfig`), para que la pantalla de acceso también cargue las fuentes.

## Créditos de recursos
Poppins y Lora: SIL Open Font License 1.1. Íconos: Material Design Icons (Apache License 2.0).
