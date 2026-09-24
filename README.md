# SGA · Alquileres (Correo Argentino)

Prototipo funcional de gestión de alquileres. Stack: Spring Boot + Java 17 + MSSQL Server / React + Vite + TypeScript.

## Ambientes

| Ambiente | Cómo se activa | Base | Seed | Frontend |
|----------|----------------|------|------|----------|
| **local** (default) | `SPRING_PROFILES_ACTIVE=local` | Docker MSSQL `localhost:1433` | Sí (`db/seed`) | Vite proxy `/api` → `:8080` |
| **cloud** | `SPRING_PROFILES_ACTIVE=cloud` | Somee / MSSQL remoto vía env vars | Sí (POC) | `VITE_API_URL` apuntando al backend |

### Backend – variables

| Variable | Local | Cloud |
|----------|-------|-------|
| `SPRING_PROFILES_ACTIVE` | `local` (default) | `cloud` |
| `SGA_DB_URL` | default local | **obligatoria** (JDBC Somee) |
| `SGA_DB_USER` / `SGA_DB_PASSWORD` | defaults locales | **obligatorias** |
| `SGA_CORS_ORIGINS` | localhost Vite | URL del front (ej. `https://tu-app.vercel.app`) |
| `SGA_LOCAL_ROLE` | `ANALISTA` | opcional |
| `SGA_STORAGE_ROOT` | `./storage` | `/tmp/sga-storage` (efímero) |

### Frontend – variables

| Variable | Desarrollo | Producción |
|----------|------------|------------|
| `VITE_API_URL` | vacío → `/api` (proxy) | `https://tu-backend.onrender.com/api` |

## Arranque local

```bash
# 1) MSSQL (Docker)
docker start sga-alquileres-sqlserver-1   # o docker compose up -d

# 2) Backend
cd backend
mvn spring-boot:run                      # perfil local por defecto

# 3) Frontend
cd frontend
npm install
npm run dev                              # http://localhost:5173
```

Swagger: http://localhost:8080/swagger

## Roles MVP (sin Keycloak)

Header `X-Role`: `ANALISTA` | `SUPERVISOR` | `AUDITOR`. En la UI se cambia desde la barra superior.

## Estructura

```
backend/     Spring Boot API + Flyway (schema + seed)
frontend/    React SPA (Vite)
```

## Versionado

La versión se muestra en pequeño en el Sidebar, debajo del nombre del usuario, con el formato:

```
v1.000.001 - Gcia Data & IA
```

- **Solo varía la versión**; el sufijo `- Gcia Data & IA` es fijo.
- Único lugar a editar: [`frontend/src/version.ts`](./frontend/src/version.ts) (`APP_VERSION`).
- Formato `v<MAYOR>.<MENOR>.<PARCHE>` con MENOR y PARCHE de 3 dígitos:

| Componente | Cuándo se incrementa | Ejemplo |
|------------|----------------------|---------|
| MAYOR | Cambio grande / release mayor (rompe compatibilidad o rediseño) | `v1.000.001` → `v2.000.000` |
| MENOR | Funcionalidad nueva | `v1.000.001` → `v1.001.000` |
| PARCHE | Corrección de bugs o ajuste menor | `v1.000.001` → `v1.000.002` |

- Versión inicial: `v1.000.001`.
- Regla de trabajo: cada merge a `main` que llegue a producción actualiza `APP_VERSION` en el mismo PR.

> Esquema propuesto, pendiente de confirmación por el responsable del producto.

## Deploy cloud

Guía paso a paso (Somee compartida con MatrizPonderada + Render + Vercel): **[DEPLOY.md](./DEPLOY.md)**

Copiá `.env.example` → `.env` y completá `SGA_DB_PASSWORD` para probar contra Somee desde local con perfil `cloud`.
