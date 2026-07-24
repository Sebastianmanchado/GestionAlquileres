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
