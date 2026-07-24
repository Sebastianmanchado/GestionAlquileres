import { c } from '../theme';
import { useApp } from '../context';
import type { Route } from '../context';

type Item = { key: string; label: string; screens: Route['screen'][]; target: Route };

const groups: { title?: string; items: Item[] }[] = [
  {
    items: [
      { key: 'dashboard', label: 'Dashboard', screens: ['dashboard'], target: { screen: 'dashboard' } },
      { key: 'listado', label: 'Inmuebles y contratos', screens: ['listado', 'detalle', 'form'], target: { screen: 'listado' } },
    ],
  },
  {
    title: 'Conciliación',
    items: [
      { key: 'concPeriodo', label: 'Vista por período', screens: ['concPeriodo', 'concDiff'], target: { screen: 'concPeriodo' } },
      { key: 'facturas', label: 'Facturas sin asignar', screens: ['facturas', 'facturaDetalle'], target: { screen: 'facturas' } },
    ],
  },
  {
    title: 'Operación',
    items: [
      { key: 'rpa', label: 'Monitor RPA', screens: ['rpa'], target: { screen: 'rpa' } },
      { key: 'auditoria', label: 'Auditoría', screens: ['auditoria'], target: { screen: 'auditoria' } },
    ],
  },
];

export function Sidebar() {
  const { route, navigate, meta } = useApp();

  return (
    <div style={{
      width: 216, flex: 'none', background: c.navy, color: c.sidebarText,
      display: 'flex', flexDirection: 'column', minHeight: '100vh',
    }}>
      <div style={{ padding: '16px 16px 14px', borderBottom: `1px solid ${c.navyLine}` }}>
        <img src="/correo-argentino.webp" alt="Correo Argentino"
          style={{ width: '100%', display: 'block', marginBottom: 10 }} />
        <div style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
          <div style={{ fontWeight: 700, fontSize: 15, color: '#fff', letterSpacing: '.3px' }}>SGA · Alquileres</div>
        </div>
      </div>

      <div style={{ padding: '12px 8px', flex: 1 }}>
        {groups.map((g, gi) => (
          <div key={gi}>
            {g.title && (
              <div style={{
                marginTop: 14, padding: '6px 10px', fontSize: 10.5, textTransform: 'uppercase',
                letterSpacing: '.5px', color: '#777674',
              }}>{g.title}</div>
            )}
            {g.items.map((it) => {
              const active = it.screens.includes(route.screen);
              return (
                <div key={it.key} onClick={() => navigate(it.target)} style={{
                  display: 'flex', alignItems: 'center', gap: 9, padding: '9px 10px', borderRadius: 4,
                  fontSize: 12.5, cursor: 'pointer', marginBottom: 2,
                  background: active ? c.gold : 'transparent',
                  color: active ? c.navy : c.sidebarText2,
                  fontWeight: active ? 700 : 400,
                }}>
                  <span style={{
                    width: 6, height: 6, borderRadius: '50%', flex: 'none',
                    background: active ? '#fff' : '#77767a',
                  }} />
                  {it.label}
                </div>
              );
            })}
          </div>
        ))}
      </div>

      <div style={{ padding: '14px 16px', borderTop: `1px solid ${c.navyLine}`, fontSize: 11.5, color: c.muted3 }}>
        <div style={{ fontWeight: 600, color: '#e5e4e2' }}>{meta.roleLabel}</div>
        <div style={{ marginTop: 2 }}>{meta.displayName}</div>
      </div>
    </div>
  );
}
