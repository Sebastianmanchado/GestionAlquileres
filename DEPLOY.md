# Deploy cloud (Somee + Render + Vercel)

Orden recomendado: **Somee → Render (backend) → Vercel (frontend)**.

Repo: https://github.com/Sebastianmanchado/GestionAlquileres

---

## 1) Base de datos en Somee

1. Entrá a tu panel de Somee y abrí la base MSSQL.
2. Anotá estos datos del connection string:
   - **Host** (ej. `workstation.somee.com` o similar)
   - **Database name**
   - **User Id**
   - **Password**
3. Traducí a JDBC (formato que usa Spring):

```
jdbc:sqlserver://<HOST>:1433;databaseName=<DB>;encrypt=true;trustServerCertificate=true
```

Ejemplo:

```
jdbc:sqlserver://workstation.somee.com:1433;databaseName=sga_poc;encrypt=true;trustServerCertificate=true
```

4. Verificá que Somee permita **conexiones remotas** desde internet (suele estar habilitado en el plan gratuito).

Al primer arranque del backend, **Flyway crea el schema y carga el seed** automáticamente.

---

## 2) Backend en Render (gratis)

### Opción A — Blueprint (recomendada)

1. https://dashboard.render.com/ → **New** → **Blueprint**
2. Conectá el repo `Sebastianmanchado/GestionAlquileres`
3. Render detecta `render.yaml` en la raíz
4. Cuando pida secretos, completá:

| Variable | Valor |
|----------|-------|
| `SGA_DB_URL` | JDBC de Somee (paso 1) |
| `SGA_DB_USER` | usuario Somee |
| `SGA_DB_PASSWORD` | contraseña Somee |

(`SPRING_PROFILES_ACTIVE`, `JAVA_TOOL_OPTIONS` y `SGA_CORS_ORIGINS` ya vienen en el blueprint.)

5. **Create Blueprint** y esperá el build Docker (~5–10 min la primera vez).
6. Copiá la URL pública, ej. `https://sga-alquileres-api.onrender.com`

### Opción B — Manual

1. **New** → **Web Service** → repo GitHub
2. **Root Directory:** `backend`
3. **Runtime:** Docker
4. **Plan:** Free
5. **Health Check Path:** `/api/meta`
6. Variables de entorno (Environment):

| Variable | Valor |
|----------|-------|
| `SPRING_PROFILES_ACTIVE` | `cloud` |
| `JAVA_TOOL_OPTIONS` | `-Xmx350m` |
| `SGA_DB_URL` | JDBC Somee |
| `SGA_DB_USER` | usuario Somee |
| `SGA_DB_PASSWORD` | contraseña Somee |
| `SGA_CORS_ORIGINS` | `https://*.vercel.app` |

### Verificar backend

```bash
curl https://<TU-SERVICIO>.onrender.com/api/meta
```

Respuesta esperada: JSON con `"role":"ANALISTA"`, etc.

Swagger: `https://<TU-SERVICIO>.onrender.com/swagger`

> **Nota:** el plan free de Render duerme tras ~15 min sin tráfico. El primer request puede tardar ~30–50 s.

---

## 3) Frontend en Vercel (gratis)

1. https://vercel.com/new → importá `Sebastianmanchado/GestionAlquileres`
2. Configuración del proyecto:

| Campo | Valor |
|-------|-------|
| **Root Directory** | `frontend` |
| **Framework Preset** | Vite |
| **Build Command** | `npm run build` |
| **Output Directory** | `dist` |

3. **Environment Variables** (Production):

| Variable | Valor |
|----------|-------|
| `VITE_API_URL` | `https://<TU-SERVICIO-RENDER>.onrender.com/api` |

(sin barra final; incluir `/api`)

4. **Deploy**

5. Abrí la URL de Vercel (ej. `https://gestion-alquileres.vercel.app`)

### CORS

El backend ya acepta `https://*.vercel.app` por defecto en cloud. No hace falta tocar CORS salvo que uses otro dominio (GitHub Pages, dominio propio, etc.).

---

## 4) Checklist post-deploy

- [ ] Dashboard carga KPIs reales (no ceros en todo)
- [ ] Listado muestra ~40 contratos
- [ ] Cambiar rol en la barra superior recarga datos
- [ ] Conciliación / facturas sin asignar responden
- [ ] Swagger del backend accesible

---

## Troubleshooting

### Backend no arranca en Render

- Revisá **Logs** en Render.
- Errores comunes:
  - `SGA_DB_URL` mal formada → revisá host, puerto 1433 y `databaseName`
  - Somee bloquea IP → verificá acceso remoto en el panel
  - Flyway falla → base vacía debería funcionar; si re-deployás sobre tablas existentes, puede haber conflicto

### Frontend en blanco o "Failed to fetch"

- `VITE_API_URL` mal seteada (falta `/api`, o http en vez de https)
- Backend dormido (Render free) → esperá y reintentá
- Re-deploy del front después de cambiar `VITE_API_URL` (Vite la embebe en build time)

### Mixed content

- Backend y front deben ser **HTTPS**. Render y Vercel lo dan por defecto.

---

## Variables de referencia

### Backend (Render)

```
SPRING_PROFILES_ACTIVE=cloud
SGA_DB_URL=jdbc:sqlserver://...
SGA_DB_USER=...
SGA_DB_PASSWORD=...
SGA_CORS_ORIGINS=https://*.vercel.app
JAVA_TOOL_OPTIONS=-Xmx350m
```

### Frontend (Vercel)

```
VITE_API_URL=https://sga-alquileres-api.onrender.com/api
```
