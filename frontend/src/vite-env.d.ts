/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** URL absoluta del backend en cloud, p.ej. https://sga.onrender.com/api. Vacío = /api (proxy local). */
  readonly VITE_API_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
