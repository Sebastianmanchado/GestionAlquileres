import { useState } from 'react';
import { api } from '../api';
import { useApp } from '../context';
import { c, s } from '../theme';
import { money, dateTimeAr, rolLabel } from '../format';
import { useAsync } from '../hooks';
import { Loading, ErrorBox } from './Dashboard';

export function ConciliacionDiff({ id }: { id: number }) {
  const { navigate, meta, role } = useApp();
  const [comentario, setComentario] = useState('');
  const [busy, setBusy] = useState(false);
  const { data, loading, error, reload } = useAsync<any>(() => api.get(`/reconciliations/${id}`), [id, role]);

  if (loading && !data) return <Loading />;
  if (error || !data) return <ErrorBox msg={error} />;
  const d = data;

  async function doAction(action: string) {
    setBusy(true);
    try {
      await api.post(`/reconciliations/${id}/action`, { action, comentario });
      setComentario('');
      reload();
      alert('Acción registrada correctamente.');
    } catch (e: any) { alert('No se pudo completar: ' + (e.message ?? e)); }
    finally { setBusy(false); }
  }

  const isMoneyLabel = (label: string) => label === 'Importe';

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
        <button style={{ ...s.btn, padding: '8px 10px' }} onClick={() => navigate({ screen: 'concPeriodo' })}>‹ Volver</button>
        <h1 style={s.h1}>Diferencia · {d.nis} · {d.denom}</h1>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1.4fr 1fr', gap: 16 }}>
        <div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 0, ...s.panel, overflow: 'hidden' }}>
            <div style={{ padding: '12px 16px', fontWeight: 700, fontSize: 12.5, background: c.headerBg, borderBottom: `1px solid ${c.border}` }}>Según contrato</div>
            <div style={{ padding: '12px 16px', fontWeight: 700, fontSize: 12.5, background: c.headerBg, borderBottom: `1px solid ${c.border}`, borderLeft: `1px solid ${c.border}` }}>Según factura</div>
            {d.diffFields.map((f: any, i: number) => (
              <DiffRow key={i} f={f} money={isMoneyLabel(f.label)} />
            ))}
          </div>

          <div style={{ marginTop: 16, ...s.panel, padding: 16 }}>
            <div style={{ fontWeight: 700, fontSize: 12.5, marginBottom: 10 }}>Comentario y revisión</div>
            <textarea value={comentario} onChange={(e) => setComentario(e.target.value)} placeholder="Agregar comentario sobre esta diferencia..."
              style={{ ...s.input, minHeight: 60, resize: 'vertical' }} />
            {d.comentario && <div style={{ fontSize: 12, color: c.text, marginTop: 8 }}>Último comentario: {d.comentario}</div>}
            {d.revisadaPor && (
              <div style={{ fontSize: 11.5, color: c.muted2, marginTop: 8 }}>
                Revisado por {d.revisadaPor} ({rolLabel(d.revisadaRol)}) el {dateTimeAr(d.revisadaEn)}
              </div>
            )}
          </div>
        </div>

        <div>
          <div style={{ ...s.panel, overflow: 'hidden' }}>
            <div style={{ padding: '10px 14px', borderBottom: `1px solid ${c.border}`, fontSize: 12, fontWeight: 700 }}>
              {d.factura ? `Comprobante ${d.factura.numero}.pdf` : 'Sin comprobante asignado'}
            </div>
            <div style={{ height: 280, background: 'repeating-linear-gradient(135deg,#f6f5f3,#f6f5f3 10px,#efeeec 10px,#efeeec 20px)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: c.muted2, fontSize: 12, fontFamily: 'monospace' }}>
              visor de PDF
            </div>
          </div>

          <div style={{ ...s.panel, padding: 16, marginTop: 16 }}>
            <div style={{ fontWeight: 700, fontSize: 12.5, marginBottom: 12 }}>Acciones</div>
            {meta.canEdit && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <button style={s.btnPrimary} disabled={busy} onClick={() => doAction('aceptar')}>Aceptar diferencia (con justificación)</button>
                <button style={s.btn} disabled={busy} onClick={() => doAction('rechazar')}>Rechazar factura</button>
                <button style={s.btn} disabled={busy} onClick={() => doAction('reasignar')}>Reasignar a otro contrato</button>
                <button style={s.btn} disabled={busy} onClick={() => doAction('ajuste')}>Generar ajuste</button>
                <button style={s.btn} disabled={busy} onClick={() => doAction('agregar')}>Agregar factura</button>
              </div>
            )}
            {meta.canApprove && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <button style={s.btnPrimary} disabled={busy} onClick={() => doAction('aprobar')}>Aprobar resolución del analista</button>
                <button style={s.btn} disabled={busy} onClick={() => doAction('devolver')}>Devolver al analista</button>
              </div>
            )}
            {meta.isReadOnly && (
              <div style={{ fontSize: 12, color: c.muted2 }}>Modo solo lectura — el rol Auditor no puede tomar acciones sobre esta diferencia.</div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function DiffRow({ f, money: isMoney }: { f: any; money: boolean }) {
  const fmt = (v: any) => (isMoney && v && v !== '—' ? money(v) : v);
  return (
    <>
      <div style={{ padding: '12px 16px', borderTop: `1px solid ${c.line}` }}>
        <div style={{ fontSize: 11, color: c.muted }}>{f.label}</div>
        <div style={{ fontSize: 13, fontWeight: 600, marginTop: 2 }}>{fmt(f.contrato)}</div>
      </div>
      <div style={{ padding: '12px 16px', borderTop: `1px solid ${c.line}`, borderLeft: `1px solid ${c.border}`, display: 'flex', gap: 8, alignItems: 'flex-start', background: f.diff ? c.diffBg : undefined }}>
        <span style={{ width: 8, height: 8, ...(f.diff ? { border: `1.5px solid ${c.danger}`, transform: 'rotate(45deg)' } : {}), marginTop: 5, flex: 'none' }} />
        <div>
          <div style={{ fontSize: 11, color: c.muted }}>{f.label}</div>
          <div style={{ fontSize: 13, fontWeight: 600, marginTop: 2 }}>{fmt(f.factura)}</div>
        </div>
      </div>
    </>
  );
}
