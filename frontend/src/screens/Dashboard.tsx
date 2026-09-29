import { useState } from 'react';
import { api } from '../api';
import { useApp } from '../context';
import { useAsync } from '../hooks';
import { c, s } from '../theme';
import { money, mesCorto, periodo } from '../format';

type Dash = {
  kpi: {
    contratosVigentes: number; contratosVencidos: number; contratosTotales: number;
    contratosProximos: number; vencen90: number; carteraPct: number; concConDiferencia: number; concProcesadas: number;
    periodo: string; facturasSinAsignar: number; montoMensual: number;
    ultimoIpc: number | null; ultimoIpcPeriodo: string | null;
    ipcActualizadoEn: string | null;
  };
  chart: { periodo: string; total: number }[];
  attention: string[];
};

export function Dashboard() {
  const { role, meta } = useApp();
  const { data, loading, error, reload } = useAsync<Dash>(() => api.get('/dashboard'), [role]);
  const [refreshing, setRefreshing] = useState(false);
  const [refreshError, setRefreshError] = useState('');

  async function actualizarIpc() {
    setRefreshing(true);
    setRefreshError('');
    try {
      const res = await api.post<{ errores?: string[] }>('/indices/sincronizar-y-aplicar');
      if (res.errores && res.errores.length > 0) {
        setRefreshError(res.errores.join(' '));
      }
      reload();
    } catch (e: any) {
      setRefreshError(e.message ?? 'No se pudo actualizar el IPC.');
    } finally {
      setRefreshing(false);
    }
  }

  if (loading && !data) return <Loading />;
  if (error || !data) return <ErrorBox msg={error} />;

  const k = data.kpi;
  const kpis: { label: string; value: string; sub: string; ipc?: boolean }[] = [
    { label: 'Contratos totales', value: `${k.contratosTotales}`, sub: 'en cartera' },
    { label: 'Contratos vigentes', value: `${k.contratosVigentes}`, sub: 'en estado vigente' },
    { label: 'Vencen en próx. 90 días', value: `${k.vencen90}`, sub: `${k.carteraPct}% de la cartera` },
    { label: 'Conciliaciones del mes con diferencia', value: `${k.concConDiferencia}`, sub: `de ${k.concProcesadas} procesadas` },
    { label: 'Facturas pendientes de matchear', value: `${k.facturasSinAsignar}`, sub: 'bandeja sin asignar' },
    { label: 'Monto mensual comprometido', value: money(k.montoMensual), sub: 'período actual' },
    { label: 'Último IPC', value: ipcPct(k.ultimoIpc), sub: k.ultimoIpcPeriodo ? periodo(k.ultimoIpcPeriodo) : 'variación mensual', ipc: true },
  ];

  const maxTotal = Math.max(1, ...data.chart.map((b) => Number(b.total)));

  return (
    <div>
      <h1 style={{ ...s.h1, marginBottom: 16 }}>Dashboard</h1>

      <div style={{ display: 'grid', gridTemplateColumns: '3fr 1fr', gap: 12, alignItems: 'stretch' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 12 }}>
          {kpis.map((kp, i) => (
            <div key={i} style={{ ...s.panel, padding: 14 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, marginBottom: 8 }}>
                <div style={{ fontSize: 11, color: c.muted }}>{kp.label}</div>
                {kp.ipc && meta.canEdit && (
                  <button
                    type="button"
                    onClick={actualizarIpc}
                    disabled={refreshing}
                    style={{ ...s.btn, padding: '4px 8px', fontSize: 11, opacity: refreshing ? 0.6 : 1 }}
                  >
                    {refreshing ? 'Actualizando…' : 'Actualizar'}
                  </button>
                )}
              </div>
              <div style={{ fontSize: 22, fontWeight: 700 }}>{kp.value}</div>
              <div style={{ fontSize: 11, color: c.muted2, marginTop: 4 }}>{kp.sub}</div>
              {kp.ipc && (
                <div style={{ fontSize: 11, color: c.muted, marginTop: 6 }}>{leyendaIpc(k.ipcActualizadoEn)}</div>
              )}
              {kp.ipc && refreshError && (
                <div style={{ fontSize: 11, color: c.danger, marginTop: 4 }}>{refreshError}</div>
              )}
            </div>
          ))}
        </div>

        <div style={{ ...s.panel, padding: 16 }}>
          <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 10 }}>Requiere tu atención</div>
          {data.attention.map((a, i) => (
            <div key={i} style={{
              padding: '9px 0', borderBottom: `1px solid ${c.line}`, display: 'flex', gap: 8, alignItems: 'flex-start',
            }}>
              <span style={{ width: 7, height: 7, border: '1.5px solid #6b6a67', transform: 'rotate(45deg)', marginTop: 4, flex: 'none' }} />
              <div style={{ fontSize: 12 }}>{a}</div>
            </div>
          ))}
          {data.attention.length === 0 && <div style={{ fontSize: 12, color: c.muted2 }}>Todo en orden.</div>}
        </div>
      </div>

      <div style={{ ...s.panel, padding: 16, marginTop: 20 }}>
        <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 14 }}>Evolución del gasto mensual</div>
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 10, height: 150, padding: '0 4px' }}>
          {data.chart.map((b, i) => (
            <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
              <div title={money(b.total)} style={{
                width: '100%', background: c.gold, borderRadius: '2px 2px 0 0',
                height: Math.round((Number(b.total) / maxTotal) * 120) + 10,
              }} />
              <div style={{ fontSize: 10, color: c.muted2 }}>{mesCorto(b.periodo)}</div>
            </div>
          ))}
          {data.chart.length === 0 && <div style={{ fontSize: 12, color: c.muted2 }}>Sin datos de facturación</div>}
        </div>
      </div>
    </div>
  );
}

function leyendaIpc(iso: string | null | undefined): string {
  if (!iso) return 'Sin actualización';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return 'Sin actualización';
  const partes = new Intl.DateTimeFormat('es-AR', {
    timeZone: 'America/Argentina/Buenos_Aires',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(d);
  const get = (tipo: Intl.DateTimeFormatPartTypes) => partes.find((p) => p.type === tipo)?.value ?? '';
  return `Actualizado el ${get('day')}/${get('month')}/${get('year')} ${get('hour')}:${get('minute')}`;
}

function ipcPct(n: number | null): string {
  if (n == null || Number.isNaN(Number(n))) return '—';
  const texto = new Intl.NumberFormat('es-AR', { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(Number(n));
  return texto + '%';
}

export function Loading() {
  return <div style={{ fontSize: 13, color: c.muted, padding: 8 }}>Cargando…</div>;
}

export function ErrorBox({ msg }: { msg?: string | null }) {
  return (
    <div style={{ ...s.panel, padding: 16, color: c.danger, fontSize: 13 }}>
      Error al cargar los datos{msg ? `: ${msg}` : ''}.
    </div>
  );
}
