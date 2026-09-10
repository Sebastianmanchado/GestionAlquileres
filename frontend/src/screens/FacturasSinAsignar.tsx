import { useState } from 'react';
import { api } from '../api';
import { useApp } from '../context';
import { c, s } from '../theme';
import { money, periodo as fmtPeriodo } from '../format';
import { useAsync } from '../hooks';
import { Loading, ErrorBox } from './Dashboard';
import { getApiRole } from '../api';

const GRID = '150px 1fr 130px 120px 140px 220px';

export function FacturasSinAsignar() {
  const { navigate, meta, role } = useApp();
  const [expanded, setExpanded] = useState<number | null>(null);
  const { data, loading, error, reload } = useAsync<any[]>(() => api.get('/invoices/unassigned'), [role]);

  async function assign(facturaId: number, contratoId: number) {
    console.log(getApiRole());
    try { await api.post(`/invoices/${facturaId}/assign?contratoId=${contratoId}`); setExpanded(null); reload(); }
    catch (e: any) { alert('No se pudo asignar: ' + (e.message ?? e)); }
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 14 }}>
        <h1 style={{ ...s.h1, flex: 1 }}>Facturas sin asignar</h1>
        <button style={s.btn} onClick={reload}>Actualizar</button>
        {meta.canEdit && <button style={s.btnPrimary} onClick={() => navigate({ screen: 'facturaDetalle', nueva: true, origin: 'facturas' })}>+ Crear factura</button>}
      </div>

      {loading && !data ? <Loading /> : error ? <ErrorBox msg={error} /> : (data && data.length === 0 ? (
        <div style={{ ...s.panel, padding: '60px 20px', textAlign: 'center' }}>
          <div style={{ width: 40, height: 40, border: '2px solid #c7c6c3', borderRadius: '50%', margin: '0 auto 14px' }} />
          <div style={{ fontWeight: 700, fontSize: 13.5 }}>No hay facturas sin asignar</div>
          <div style={{ fontSize: 12, color: c.muted2, marginTop: 6 }}>El RPA no encontró facturas sin coincidencia en este momento.</div>
        </div>
      ) : (
        <div style={{ ...s.panel, overflow: 'hidden' }}>
          <div style={{ display: 'grid', gridTemplateColumns: GRID, background: c.headerBg }}>
            <div style={s.th}>CUIT emisor</div><div style={s.th}>Razón social</div><div style={s.th}>Importe</div>
            <div style={s.th}>Período</div><div style={s.th}>Comprobante</div><div style={s.th}>Acción</div>
          </div>
          {data!.map((u) => (
            <div key={u.id}>
              <div style={{ display: 'grid', gridTemplateColumns: GRID, borderTop: `1px solid ${c.line}`, fontSize: 12.5, alignItems: 'center' }}>
                <div style={{ padding: '11px 12px' }}>{u.cuit}</div>
                <div style={{ padding: '11px 12px' }}>{u.razonSocial}</div>
                <div style={{ padding: '11px 12px' }}>{money(u.importe)}</div>
                <div style={{ padding: '11px 12px' }}>{fmtPeriodo(u.periodo)}</div>
                <div style={{ padding: '11px 12px' }}>
                  <span onClick={() => navigate({ screen: 'facturaDetalle', id: u.id, origin: 'facturas' })}
                    style={{ color: c.primary, textDecoration: 'underline', cursor: 'pointer', fontWeight: 600 }}>{u.comprobante}</span>
                </div>
                <div style={{ padding: '11px 12px' }}>
                  {meta.canEdit ? (
                    <button style={{ ...s.btn, padding: '6px 10px', fontSize: 12 }} onClick={() => setExpanded(expanded === u.id ? null : u.id)}>Asignar contrato</button>
                  ) : <span style={{ color: c.muted2 }}>Sólo lectura</span>}
                </div>
              </div>
              {expanded === u.id && (
                <div style={{ background: c.softBg, borderTop: `1px solid ${c.line}`, padding: '12px 16px', fontSize: 12.5 }}>
                  <div style={{ color: c.muted, marginBottom: 8 }}>Sugerencias por CUIT / NIS:</div>
                  <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                    {u.sugerencias.map((sug: any) => (
                      <div key={sug.contratoId} onClick={() => assign(u.id, sug.contratoId)} style={{
                        border: `1px solid ${c.border}`, borderRadius: 4, padding: '8px 12px', background: '#fff', cursor: 'pointer',
                        display: 'flex', flexDirection: 'column', gap: 3, minWidth: 150,
                      }}>
                        <div><span style={{ color: c.muted }}>NIS </span><span style={{ fontWeight: 600 }}>{sug.nis}</span></div>
                        <div><span style={{ color: c.muted }}>Sucursal </span><span style={{ fontWeight: 600 }}>{sug.sucursal}</span></div>
                        <div><span style={{ color: c.muted }}>Monto </span><span style={{ fontWeight: 600 }}>{money(sug.monto)}</span></div>
                      </div>
                    ))}
                    {u.sugerencias.length === 0 && <div style={{ color: c.muted2 }}>Sin sugerencias automáticas para este CUIT.</div>}
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
