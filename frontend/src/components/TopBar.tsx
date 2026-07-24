import { useEffect, useState } from 'react';
import { c } from '../theme';
import { useApp } from '../context';
import type { RoleCode } from '../api';
import { api } from '../api';

const BREADCRUMBS: Record<string, string> = {
  dashboard: 'Inicio',
  listado: 'Inicio / Inmuebles y contratos',
  detalle: 'Inicio / Inmuebles y contratos / Detalle',
  form: 'Inicio / Inmuebles y contratos / Nuevo contrato',
  concPeriodo: 'Inicio / Conciliación / Vista por período',
  concDiff: 'Inicio / Conciliación / Vista por período / Diferencia',
  facturas: 'Inicio / Conciliación / Facturas sin asignar',
  facturaDetalle: 'Inicio / Conciliación / Factura',
  rpa: 'Inicio / Monitor RPA',
  auditoria: 'Inicio / Auditoría',
};

const ROLES: { code: RoleCode; label: string }[] = [
  { code: 'ANALISTA', label: 'Analista de alquileres' },
  { code: 'SUPERVISOR', label: 'Supervisor regional' },
  { code: 'AUDITOR', label: 'Auditor' },
];

type Notif = { texto: string; tiempo: string };

export function TopBar() {
  const { route, meta, role, setRole } = useApp();
  const [notifOpen, setNotifOpen] = useState(false);
  const [roleOpen, setRoleOpen] = useState(false);
  const [notifs, setNotifs] = useState<Notif[]>([]);

  useEffect(() => {
    api.get<{ notifications: Notif[] }>('/dashboard')
      .then((d) => setNotifs(d.notifications ?? []))
      .catch(() => setNotifs([]));
  }, [role]);

  return (
    <div style={{
      height: 56, flex: 'none', background: '#fff', borderBottom: `1px solid ${c.border}`,
      display: 'flex', alignItems: 'center', padding: '0 20px', gap: 16,
    }}>
      <div style={{ fontSize: 12, color: c.muted, flex: 1 }}>{BREADCRUMBS[route.screen]}</div>

      <div style={{
        display: 'flex', alignItems: 'center', gap: 6, background: c.fieldBg,
        border: `1px solid ${c.border}`, borderRadius: 4, padding: '6px 10px', width: 280,
      }}>
        <span style={{ width: 12, height: 12, border: '1.5px solid #8a8985', borderRadius: '50%', flex: 'none' }} />
        <input placeholder="Buscar por NIS o unidad de negocio..." style={{
          border: 'none', background: 'transparent', outline: 'none', fontSize: 12.5, width: '100%', color: c.text,
        }} />
      </div>

      <div style={{ position: 'relative' }}>
        <div onClick={() => setNotifOpen((v) => !v)} style={{
          width: 32, height: 32, border: `1px solid ${c.border}`, borderRadius: 4, display: 'flex',
          alignItems: 'center', justifyContent: 'center', cursor: 'pointer', position: 'relative', background: '#fff',
        }}>
          <span style={{ width: 14, height: 14, border: '1.5px solid #6b6a67', borderRadius: '3px 3px 8px 8px' }} />
          {notifs.length > 0 && (
            <span style={{
              position: 'absolute', top: -4, right: -4, background: c.danger, color: '#fff',
              fontSize: 9, borderRadius: 8, padding: '1px 5px', fontWeight: 700,
            }}>{notifs.length}</span>
          )}
        </div>
        {notifOpen && (
          <div style={{
            position: 'absolute', right: 0, top: 38, width: 300, background: '#fff',
            border: `1px solid ${c.border}`, borderRadius: 4, boxShadow: '0 4px 14px rgba(0,0,0,.12)', zIndex: 20,
          }}>
            <div style={{ padding: '10px 14px', fontWeight: 700, borderBottom: `1px solid #eeece9`, fontSize: 12 }}>Notificaciones</div>
            {notifs.map((n, i) => (
              <div key={i} style={{ padding: '10px 14px', borderBottom: `1px solid ${c.line}`, display: 'flex', gap: 8 }}>
                <span style={{ width: 6, height: 6, borderRadius: '50%', background: '#8a8985', marginTop: 5, flex: 'none' }} />
                <div>
                  <div style={{ fontSize: 12 }}>{n.texto}</div>
                  <div style={{ fontSize: 10.5, color: c.muted2, marginTop: 2 }}>{n.tiempo}</div>
                </div>
              </div>
            ))}
            {notifs.length === 0 && <div style={{ padding: '10px 14px', fontSize: 12, color: c.muted2 }}>Sin notificaciones</div>}
          </div>
        )}
      </div>

      <div style={{ position: 'relative' }}>
        <div onClick={() => setRoleOpen((v) => !v)} title="Cambiar rol" style={{
          background: c.fieldBg, border: `1px solid ${c.border}`, borderRadius: 4, padding: '6px 12px',
          fontSize: 12, fontWeight: 600, color: '#3a3a38', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8,
        }}>
          {meta.roleLabel}
          <span style={{ width: 7, height: 7, borderRight: '2px solid #6b6a67', borderBottom: '2px solid #6b6a67', transform: 'rotate(45deg)', marginTop: -3 }} />
        </div>
        {roleOpen && (
          <div style={{
            position: 'absolute', right: 0, top: 36, width: 220, background: '#fff',
            border: `1px solid ${c.border}`, borderRadius: 4, boxShadow: '0 4px 14px rgba(0,0,0,.12)', zIndex: 20,
          }}>
            {ROLES.map((r) => (
              <div key={r.code} onClick={() => { setRole(r.code); setRoleOpen(false); }} style={{
                padding: '9px 14px', fontSize: 12, cursor: 'pointer',
                background: r.code === role ? c.fieldBg : '#fff',
                fontWeight: r.code === role ? 700 : 400,
                borderBottom: `1px solid ${c.line}`,
              }}>{r.label}</div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
