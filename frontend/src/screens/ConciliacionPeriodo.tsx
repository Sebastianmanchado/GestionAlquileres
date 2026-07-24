import { useState } from 'react';
import { api, qs } from '../api';
import { useApp } from '../context';
import { c, s } from '../theme';
import { Badge } from '../components/Badge';
import { money, periodo as fmtPeriodo, estadoConciliacion } from '../format';
import { useAsync } from '../hooks';
import { Loading, ErrorBox } from './Dashboard';

const GRID = '100px 1fr 150px 150px 130px 160px 170px';

export function ConciliacionPeriodo() {
  const { navigate, meta, role } = useApp();
  const [periodoSel, setPeriodoSel] = useState<string>('');
  const [estadoFiltro, setEstadoFiltro] = useState<string | null>(null);
  const [running, setRunning] = useState(false);

  const { data, loading, error, reload } = useAsync<any>(() => api.get('/reconciliations' + qs({ periodo: periodoSel })), [periodoSel, role]);

  async function run() {
    setRunning(true);
    try { await api.post('/reconciliations/run' + qs({ periodo: periodoSel || (data?.periodo ?? '') })); reload(); }
    catch (e: any) { alert('No se pudo ejecutar: ' + (e.message ?? e)); }
    finally { setRunning(false); }
  }

  if (loading && !data) return <Loading />;
  if (error || !data) return <ErrorBox msg={error} />;

  const rows = estadoFiltro ? data.rows.filter((r: any) => r.estadoCodigo === estadoFiltro) : data.rows;

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 14 }}>
        <h1 style={{ ...s.h1, flex: 1 }}>Conciliación · Vista por período</h1>
        <select style={{ ...s.select, padding: '7px 12px', fontSize: 12.5 }} value={periodoSel || String(data.periodo).substring(0, 10)} onChange={(e) => setPeriodoSel(e.target.value)}>
          {data.periodos.map((p: any) => <option key={String(p.periodo)} value={String(p.periodo).substring(0, 10)}>{fmtPeriodo(p.periodo)}</option>)}
        </select>
        {meta.canEdit && <button style={s.btnPrimary} disabled={running} onClick={run}>{running ? 'Ejecutando…' : 'Ejecutar conciliación del período'}</button>}
      </div>

      <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
        {data.filtros.map((f: any) => {
          const info = estadoConciliacion(f.codigo);
          const active = estadoFiltro === f.codigo;
          return (
            <div key={f.codigo} onClick={() => setEstadoFiltro(active ? null : f.codigo)} style={{
              padding: '6px 12px', border: `1px solid ${active ? c.primary : c.border}`, borderRadius: 14, fontSize: 12,
              background: active ? '#eef2f8' : '#fff', cursor: 'pointer', display: 'inline-flex', gap: 6, alignItems: 'center',
            }}>
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: badge(info.tone) }} />
              {info.label} <span style={{ color: c.muted2 }}>({f.count})</span>
            </div>
          );
        })}
      </div>

      <div style={{ ...s.panel, overflow: 'hidden' }}>
        <div style={{ display: 'grid', gridTemplateColumns: GRID, background: c.headerBg }}>
          <div style={s.th}>NIS</div><div style={s.th}>Contrato</div><div style={s.th}>Importe esperado</div>
          <div style={s.th}>Importe facturado</div><div style={s.th}>Diferencia</div><div style={s.th}>Comprobante asignado</div><div style={s.th}>Estado</div>
        </div>
        {rows.map((r: any) => {
          const info = estadoConciliacion(r.estadoCodigo);
          const puedeDiff = r.estadoCodigo === 'CON_DIFERENCIA' || r.estadoCodigo === 'OK_CON_DIF';
          return (
            <div key={r.id} onClick={() => puedeDiff && navigate({ screen: 'concDiff', id: r.id })}
              style={{ display: 'grid', gridTemplateColumns: GRID, borderTop: `1px solid ${c.line}`, fontSize: 12.5, cursor: puedeDiff ? 'pointer' : 'default' }}>
              <div style={{ padding: '11px 12px' }}>{r.nis}</div>
              <div style={{ padding: '11px 12px' }}>
                <span onClick={(e) => { e.stopPropagation(); navigate({ screen: 'detalle', id: r.contratoId }); }}
                  style={{ color: c.primary, textDecoration: 'underline', cursor: 'pointer', fontWeight: 600 }}>{r.denom}</span>
              </div>
              <div style={{ padding: '11px 12px' }}>{money(r.esperado)}</div>
              <div style={{ padding: '11px 12px' }}>{r.estadoCodigo === 'SIN_FACTURA' ? '—' : money(r.facturado)}</div>
              <div style={{ padding: '11px 12px', fontWeight: 600 }}>{money(r.diferencia)}</div>
              <div style={{ padding: '11px 12px' }}>{r.comprobante ?? '—'}</div>
              <div style={{ padding: '11px 12px' }}><Badge label={info.label} tone={info.tone} /></div>
            </div>
          );
        })}
        {rows.length === 0 && <div style={{ padding: 16, fontSize: 12.5, color: c.muted2 }}>No hay conciliaciones para este período/filtro.</div>}
      </div>
    </div>
  );
}

function badge(tone: string) {
  return tone === 'green' ? c.green : tone === 'red' ? c.danger : tone === 'amber' ? c.amber : '#8a8985';
}
