# Deploy cloud (Somee + Render + Vercel)

Orden recomendado: **Render (backend) → Vercel (frontend)**. La base Somee ya está configurada (misma que MatrizPonderada).

Repo: https://github.com/Sebastianmanchado/GestionAlquileres

---

## 1) Base de datos Somee (ya configurada)

Misma instancia que **MatrizPonderada** (`MatrizPonderada/.env.example`):

| Campo | Valor |
|-------|-------|
| **Host** | `GIA_DB_PoC.mssql.somee.com` |
| **Puerto** | `1433` |
| **Base** | `GIA_DB_PoC` |
| **Usuario** | `jppirra_SQLLogin_1` |
| **Password** | La misma que usás en MatrizPonderada (`DATABASE_PASSWORD`) |

**JDBC (Spring):**

```
jdbc:sqlserver://GIA_DB_PoC.mssql.somee.com:1433;databaseName=GIA_DB_PoC;encrypt=true;trustServerCertificate=true;packetSize=4096
```

> SGA y MatrizPonderada comparten el **mismo catálogo** en Somee. Flyway crea tablas propias de alquileres (`contrato`, `inmueble`, etc.) sin pisar las de la matriz (`iniciativa`, `evaluacion`, etc.).

Al primer arranque del backend en cloud, **Flyway crea el schema SGA y carga el seed** automáticamente.

### Probar conexión desde local (opcional)

```bash
cp .env.example .env
# Completar SGA_DB_PASSWORD con la misma pass de Somee
# Descomentar o setear: SPRING_PROFILES_ACTIVE=cloud
cd backend && mvn spring-boot:run
```

---

## 2) Backend en Render (gratis)

### Opción A — Blueprint (recomendada)

1. https://dashboard.render.com/ → **New** → **Blueprint**
2. Conectá el repo `Sebastianmanchado/GestionAlquileres`
3. Render detecta `render.yaml` (URL y usuario Somee ya vienen preconfigurados)
4. Solo tenés que completar el secreto:

| Variable | Valor |
|----------|-------|
| `SGA_DB_PASSWORD` | misma password que MatrizPonderada / Somee |

5. **Create Blueprint** y esperá el build Docker (~5–10 min la primera vez).
6. URL del servicio: `https://sga-alquileres-api.onrender.com`

Si Render te pide renombrar el servicio, actualizá la URL en `frontend/vercel.json` (rewrite `/api`).

### Opción B — Manual

1. **New** → **Web Service** → repo GitHub
2. **Root Directory:** `backend`
3. **Runtime:** Docker · **Plan:** Free
4. **Health Check Path:** `/api/meta`
5. Variables de entorno:

| Variable | Valor |
|----------|-------|
| `SPRING_PROFILES_ACTIVE` | `cloud` |
| `JAVA_TOOL_OPTIONS` | `-Xmx350m` |
| `SGA_DB_URL` | JDBC de arriba |
| `SGA_DB_USER` | `jppirra_SQLLogin_1` |
| `SGA_DB_PASSWORD` | password Somee |
| `SGA_CORS_ORIGINS` | `https://*.vercel.app` |

### Verificar backend

```bash
curl https://sga-alquileres-api.onrender.com/api/meta
```

Swagger: `https://sga-alquileres-api.onrender.com/swagger`

> El plan free de Render duerme tras ~15 min sin tráfico. El primer request puede tardar ~30–50 s.

---

## 3) Frontend en Vercel (gratis)

1. https://vercel.com/new → importá `Sebastianmanchado/GestionAlquileres`
2. **Root Directory:** `frontend`
3. Elegí **modo A o B**:

| Modo | Vercel | Render |
|------|--------|--------|
| **A — Proxy** (recomendado) | Root = `frontend`. **No** setear `VITE_API_URL`. `vercel.json` reescribe `/api/*` → Render. | Solo password Somee. |
| **B — Directo** | `VITE_API_URL=https://sga-alquileres-api.onrender.com/api` | `SGA_CORS_ORIGINS=https://*.vercel.app` (ya default) |

4. **Deploy**

### Si el front carga pero no trae datos

1. **Vercel → Environment Variables:** si existe `VITE_API_URL=http://localhost:8080`, borrala o corregila. Requiere **redeploy**.
2. **Root Directory** debe ser `frontend`.
3. Probá backend directo: `https://sga-alquileres-api.onrender.com/api/contracts` (puede tardar si estaba dormido).
4. En DevTools → Network: si falla CORS, usá modo A (proxy) sin `VITE_API_URL`.

---

## 4) Checklist post-deploy

- [ ] `GET /api/meta` responde JSON
- [ ] Dashboard con KPIs (40 contratos en seed)
- [ ] Listado de contratos carga
- [ ] Cambio de rol en la UI funciona

---

## Variables de referencia

### Backend (Render)

```
SPRING_PROFILES_ACTIVE=cloud
SGA_DB_URL=jdbc:sqlserver://GIA_DB_PoC.mssql.somee.com:1433;databaseName=GIA_DB_PoC;encrypt=true;trustServerCertificate=true;packetSize=4096
SGA_DB_USER=jppirra_SQLLogin_1
SGA_DB_PASSWORD=<misma que MatrizPonderada>
SGA_CORS_ORIGINS=https://*.vercel.app
JAVA_TOOL_OPTIONS=-Xmx350m
```

### Frontend (Vercel) — solo modo B

```
VITE_API_URL=https://sga-alquileres-api.onrender.com/api
```
