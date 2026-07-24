import { useState } from 'react';
import type { ReactNode } from 'react';
import { api, qs } from '../api';
import { useApp } from '../context';
import { c, s } from '../theme';
import { dateTimeAr, rolLabel } from '../format';
import { useAsync } from '../hooks';
import { Loading, ErrorBox } from './Dashboard';

const GRID = '160px 140px 160px 170px 1fr';

export function Auditoria() {
  const { role } = useApp();
  const [f, setF] = useState<Record<string, string>>({ desde: '', hasta: '', usuario: '', rol: '', contrato: '', accion: '' });
  const set = (k: string, v: string) => setF((p) => ({ ...p, [k]: v }));
  const { data, loading, error } = useAsync<any>(() => api.get('/audit' + qs(f)), [f.desde, f.hasta, f.usuario, f.rol, f.contrato, f.accion, role]);

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: 14, gap: 12 }}>
        <h1 style={{ ...s.h1, flex: 1 }}>Auditoría</h1>
        <button style={s.btn}>Exportar a Excel</button>
      </div>

      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 14, alignItems: 'flex-end' }}>
        <FilterField label="Desde"><input type="date" style={s.select} value={f.desde} onChange={(e) => set('desde', e.target.value)} /></FilterField>
        <FilterField label="Hasta"><input type="date" style={s.select} value={f.hasta} onChange={(e) => set('hasta', e.target.value)} /></FilterField>
        <FilterField label="Usuario"><Select value={f.usuario} onChange={(v) => set('usuario', v)} options={data?.options?.usuarios ?? []} /></FilterField>
        <FilterField label="Rol"><Select value={f.rol} onChange={(v) => set('rol', v)} options={data?.options?.roles ?? []} render={rolLabel} /></FilterField>
        <FilterField label="Contrato"><Select value={f.contrato} onChange={(v) => set('contrato', v)} options={data?.options?.contratos ?? []} /></FilterField>
        <FilterField label="Acción"><Select value={f.accion} onChange={(v) => set('accion', v)} options={data?.options?.acciones ?? []} /></FilterField>
      </div>

      {loading && !data ? <Loading /> : error ? <ErrorBox msg={error} /> : (
        <div style={{ ...s.panel, overflow: 'hidden' }}>
          <div style={{ display: 'grid', gridTemplateColumns: GRID, background: c.headerBg }}>
            <div style={s.th}>Fecha</div><div style={s.th}>Usuario</div><div style={s.th}>Rol</div><div style={s.th}>Contrato</div><div style={s.th}>Acción</div>
          </div>
          {data.rows.map((a: any, i: number) => (
            <div key={i} style={{ display: 'grid', gridTemplateColumns: GRID, borderTop: `1px solid ${c.line}`, fontSize: 12.5 }}>
              <div style={{ padding: '10px 12px' }}>{dateTimeAr(a.fecha)}</div>
              <div style={{ padding: '10px 12px' }}>{a.usuario ?? '—'}</div>
              <div style={{ padding: '10px 12px' }}>{a.rol ? rolLabel(a.rol) : '—'}</div>
              <div style={{ padding: '10px 12px' }}>{a.contrato ?? '—'}</div>
              <div style={{ padding: '10px 12px' }}>{a.accion}</div>
            </div>
          ))}
          {data.rows.length === 0 && <div style={{ padding: 16, fontSize: 12.5, color: c.muted2 }}>No hay eventos de auditoría para los filtros seleccionados.</div>}
        </div>
      )}
    </div>
  );
}

function FilterField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <label style={{ display: 'block', fontSize: 10.5, color: c.muted2, marginBottom: 4 }}>{label}</label>
      {children}
    </div>
  );
}

function Select({ value, onChange, options, render }: { value: string; onChange: (v: string) => void; options: string[]; render?: (v: string) => string }) {
  return (
    <select style={{ ...s.select, minWidth: 150 }} value={value} onChange={(e) => onChange(e.target.value)}>
      <option value="">Todos</option>
      {options.map((o) => <option key={o} value={o}>{render ? render(o) : o}</option>)}
    </select>
  );
}
