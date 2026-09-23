import { useEffect, useMemo, useState } from 'react';
import { AppCtx, useApp } from './context';
import type { Meta, Route } from './context';
import { api, setApiRole } from './api';
import type { RoleCode } from './api';
import { c } from './theme';
import { Sidebar } from './components/Sidebar';
import { TopBar } from './components/TopBar';
import { Dashboard } from './screens/Dashboard';
import { Listado } from './screens/Listado';
import { DetalleContrato } from './screens/DetalleContrato';
import { FormContrato } from './screens/FormContrato';
import { FormInmueble } from './screens/FormInmueble';
import { ConciliacionPeriodo } from './screens/ConciliacionPeriodo';
import { ConciliacionDiff } from './screens/ConciliacionDiff';
import { FacturasSinAsignar } from './screens/FacturasSinAsignar';
import { DetalleFactura } from './screens/DetalleFactura';
import { MonitorRpa } from './screens/MonitorRpa';
import { Auditoria } from './screens/Auditoria';

const DEFAULT_META: Meta = {
  role: 'ANALISTA', roleLabel: 'Analista de alquileres', displayName: 'Juan Martínez',
  canEdit: true, canApprove: false, isReadOnly: false,
};

export function App() {
  const [route, setRoute] = useState<Route>({ screen: 'dashboard' });
  const [role, setRoleState] = useState<RoleCode>('ANALISTA');
  const [meta, setMeta] = useState<Meta>(DEFAULT_META);

  useEffect(() => {
    setApiRole(role);
    api.get<Meta>('/meta').then(setMeta).catch(() => setMeta({ ...DEFAULT_META, role }));
  }, [role]);

  const value = useMemo(() => ({
    route,
    navigate: (r: Route) => { setRoute(r); window.scrollTo(0, 0); },
    meta,
    role,
    setRole: (r: RoleCode) => setRoleState(r),
  }), [route, meta, role]);

  return (
    <AppCtx.Provider value={value}>
      <div style={{ display: 'flex', width: '100%', minHeight: '100vh', background: c.bg }}>
        <Sidebar />
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
          <TopBar />
          <div style={{ flex: 1, padding: 24, overflow: 'auto' }}>
            <RouteRenderer />
          </div>
        </div>
      </div>
    </AppCtx.Provider>
  );
}

function RouteRenderer() {
  const { route } = useApp();
  switch (route.screen) {
    case 'dashboard': return <Dashboard />;
    case 'listado': return <Listado />;
    case 'detalle': return <DetalleContrato id={route.id} />;
    case 'form': return <FormContrato id={route.id} />;
    case 'formInmueble': return <FormInmueble />;
    case 'concPeriodo': return <ConciliacionPeriodo />;
    case 'concDiff': return <ConciliacionDiff id={route.id} />;
    case 'facturas': return <FacturasSinAsignar />;
    case 'facturaDetalle': return <DetalleFactura id={route.id} nueva={route.nueva} origin={route.origin} />;
    case 'rpa': return <MonitorRpa />;
    case 'auditoria': return <Auditoria />;
    default: return <Dashboard />;
  }
}
