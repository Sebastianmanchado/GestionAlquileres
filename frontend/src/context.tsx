import { createContext, useContext } from 'react';
import type { RoleCode } from './api';

export type Route =
  | { screen: 'dashboard' }
  | { screen: 'listado'; tab?: 'contratos' | 'inmuebles' }
  | { screen: 'detalle'; id: number }
  | { screen: 'form'; id?: number }
  | { screen: 'formInmueble' }
  | { screen: 'concPeriodo' }
  | { screen: 'concDiff'; id: number }
  | { screen: 'facturas' }
  | { screen: 'facturaDetalle'; id?: number; nueva?: boolean; origin: Route['screen'] }
  | { screen: 'rpa' }
  | { screen: 'auditoria' };

export type Meta = {
  role: RoleCode;
  roleLabel: string;
  displayName: string;
  canEdit: boolean;
  canApprove: boolean;
  isReadOnly: boolean;
};

export type AppState = {
  route: Route;
  navigate: (route: Route) => void;
  meta: Meta;
  role: RoleCode;
  setRole: (role: RoleCode) => void;
};

export const AppCtx = createContext<AppState | null>(null);

export function useApp(): AppState {
  const ctx = useContext(AppCtx);
  if (!ctx) throw new Error('useApp fuera del proveedor');
  return ctx;
}
