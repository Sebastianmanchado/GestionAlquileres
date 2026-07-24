import { useState } from 'react';
import { api } from '../api';
import { useApp } from '../context';
import { c, s } from '../theme';
import { Badge } from '../components/Badge';
import { dateTimeAr, periodo as fmtPeriodo, estadoRpa } from '../format';
import { useAsync } from '../hooks';
import { Loading, ErrorBox } from './Dashboard';

const GRID = '160px 150px 150px 110px 110px 100px 110px';

export function MonitorRpa() {
  const { role } = useApp();
  const [expanded, setExpanded] = useState<number | null>(null);
  const { data, loading, error } = useAsync<any[]>(() => api.get('/rpa/runs'), [role]);

  if (loading && !data) return <Loading />;
  if (error || !data) return <ErrorBox msg={error} />;

  return (
    <div>
      <h1 style={{ ...s.h1, marginBottom: 16 }}>Monitor de ejecuciones RPA</h1>
      <div style={{ ...s.panel, overflow: 'hidden' }}>
        <div style={{ display: 'grid', gridTemplateColumns: GRID, background: c.headerBg }}>
          <div style={s.th}>Fecha</div><div style={s.th}>Período solicitado</div><div style={s.th}>Estado</div>
          <div style={s.th}>Recibidas</div><div style={s.th}>Procesadas</div><div style={s.th}>Con error</div><div style={s.th}>Detalle</div>
        </div>
        {data.map((r) => {
          const info = estadoRpa(r.estadoCodigo);
          const isOpen = expanded === r.id;
          return (
            <div key={r.id}>
              <div style={{ display: 'grid', gridTemplateColumns: GRID, borderTop: `1px solid ${c.line}`, fontSize: 12.5, alignItems: 'center' }}>
                <div style={{ padding: '11px 12px' }}>{dateTimeAr(r.fecha)}</div>
                <div style={{ padding: '11px 12px' }}>{fmtPeriodo(r.periodoDesde)}</div>
                <div style={{ padding: '11px 12px' }}><Badge label={info.label} tone={info.tone} /></div>
                <div style={{ padding: '11px 12px' }}>{r.recibidas}</div>
                <div style={{ padding: '11px 12px' }}>{r.procesadas}</div>
                <div style={{ padding: '11px 12px' }}>{r.conError}</div>
                <div style={{ padding: '11px 12px' }}>
                  <a href="#" onClick={(e) => { e.preventDefault(); setExpanded(isOpen ? null : r.id); }}>{isOpen ? 'Ocultar' : 'Ver detalle'}</a>
                </div>
              </div>
              {isOpen && (
                <div style={{ background: c.softBg, borderTop: `1px solid ${c.line}`, padding: '12px 16px', fontSize: 12.5 }}>
                  {r.errores.length === 0 ? (
                    <div style={{ color: c.muted }}>Sin errores registrados en esta corrida.</div>
                  ) : r.errores.map((e: string, i: number) => (
                    <div key={i} style={{ padding: '5px 0', color: c.danger }}>• {e}</div>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
