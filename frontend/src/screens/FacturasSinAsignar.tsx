import { useEffect, useState } from 'react';
import { api, qs } from '../api';
import { useApp } from '../context';
import { c, s } from '../theme';
import { money, periodo as fmtPeriodo } from '../format';
import { useAsync } from '../hooks';
import { Loading, ErrorBox } from './Dashboard';
import { getApiRole } from '../api';

const GRID = '150px 1fr 130px 120px 140px 220px';

type ContratoPick = {
  id: number;
  nis: string;
  unidad?: string;
  responsable?: string;
  cuit?: string;
  razon?: string;
  monto?: unknown;
};

function matchContrato(item: ContratoPick, q: string): boolean {
  const term = q.trim().toLowerCase();
  if (!term) return true;
  const digits = term.replace(/\D/g, '');
  const fields = [item.nis, item.unidad, item.responsable, item.cuit, item.razon];
  if (fields.some((v) => String(v ?? '').toLowerCase().includes(term))) return true;
  return digits.length >= 3 && String(item.cuit ?? '').replace(/\D/g, '').includes(digits);
}

function fromSugerencia(sug: any): ContratoPick {
  return {
    id: Number(sug.contratoId),
    nis: sug.nis,
    unidad: sug.sucursal,
    responsable: sug.responsable,
    cuit: sug.locadorCuit,
    razon: sug.locadorRazon,
    monto: sug.monto,
  };
}

function fromContrato(row: any): ContratoPick {
  return {
    id: Number(row.id),
    nis: row.nis,
    unidad: row.denom,
    responsable: row.responsable,
    cuit: row.locadorCuit,
    razon: row.propietario,
    monto: row.valorActual,
  };
}

function ContratoPickCard({ item, onPick }: { item: ContratoPick; onPick: () => void }) {
  return (
    <div
      onClick={onPick}
      style={{
        border: `1px solid ${c.border}`,
        borderRadius: 4,
        padding: '8px 12px',
        background: '#fff',
        cursor: 'pointer',
        display: 'flex',
        flexDirection: 'column',
        gap: 3,
        minWidth: 220,
        maxWidth: 280,
      }}
    >
      <PickLine label="NIS" value={item.nis} />
      <PickLine label="Unidad de negocio" value={item.unidad} />
      <PickLine label="Responsable" value={item.responsable} />
      <PickLine label="CUIT locador" value={item.cuit} />
      <PickLine label="Razón social" value={item.razon} />
      <PickLine label="Monto" value={money(item.monto)} />
    </div>
  );
}

function PickLine({ label, value }: { label: string; value?: unknown }) {
  const text = value == null || value === '' ? '—' : String(value);
  return (
    <div>
      <span style={{ color: c.muted }}>{label} </span>
      <span style={{ fontWeight: 600 }}>{text}</span>
    </div>
  );
}

export function FacturasSinAsignar() {
  const { navigate, meta, role } = useApp();
  const [expanded, setExpanded] = useState<number | null>(null);
  const { data, loading, error, reload } = useAsync<any[]>(() => api.get('/invoices/unassigned'), [role]);
  const [filtro, setFiltro] = useState('');
  const [searchResults, setSearchResults] = useState<ContratoPick[] | null>(null);
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    const q = filtro.trim();
    if (!q) {
      setSearchResults(null);
      setSearching(false);
      return;
    }
    const t = window.setTimeout(async () => {
      setSearching(true);
      try {
        const res = await api.get<any>('/contracts' + qs({ search: q, size: 40 }));
        setSearchResults((res.rows ?? []).map(fromContrato));
      } catch {
        setSearchResults([]);
      } finally {
        setSearching(false);
      }
    }, 250);
    return () => window.clearTimeout(t);
  }, [filtro]);

  function toggleAssign(id: number) {
    setExpanded((current) => {
      const next = current === id ? null : id;
      if (next !== current) {
        setFiltro('');
        setSearchResults(null);
      }
      return next;
    });
  }

  async function assign(facturaId: number, contratoId: number) {
    console.log(getApiRole());
    try {
      await api.post(`/invoices/${facturaId}/assign?contratoId=${contratoId}`);
      setExpanded(null);
      setFiltro('');
      setSearchResults(null);
      reload();
    } catch (e: any) {
      alert('No se pudo asignar: ' + (e.message ?? e));
    }
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
          {data!.map((u) => {
            const sugeridas = (u.sugerencias ?? []).map(fromSugerencia);
            const locales = sugeridas.filter((item: ContratoPick) => matchContrato(item, filtro));
            const q = filtro.trim();
            const items = q && searchResults ? searchResults : locales;
            return (
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
                    <button style={{ ...s.btn, padding: '6px 10px', fontSize: 12 }} onClick={() => toggleAssign(u.id)}>Asignar contrato</button>
                  ) : <span style={{ color: c.muted2 }}>Sólo lectura</span>}
                </div>
              </div>
              {expanded === u.id && (
                <div
                  style={{
                    background: c.softBg,
                    borderTop: `1px solid ${c.line}`,
                    padding: '12px 16px',
                    fontSize: 12.5
                  }}
                >
                  <div style={{ color: c.muted, marginBottom: 8 }}>
                    Buscá por NIS, unidad de negocio, responsable, CUIT o razón social del locador.
                  </div>

                  <input
                    style={{
                      ...s.input,
                      width: 420,
                      maxWidth: '100%',
                      marginBottom: 10,
                      fontSize: 12
                    }}
                    placeholder="NIS, unidad de negocio, responsable, CUIT o razón social"
                    value={filtro}
                    onChange={(e) => setFiltro(e.target.value)}
                  />

                  <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                    {searching && q && !searchResults && (
                      <div style={{ color: c.muted2 }}>Buscando contratos…</div>
                    )}

                    {items.map((item: ContratoPick) => (
                      <ContratoPickCard
                        key={item.id}
                        item={item}
                        onPick={() => assign(u.id, item.id)}
                      />
                    ))}

                    {!searching && items.length === 0 && (
                      <div style={{ color: c.muted2 }}>
                        {q
                          ? 'No hay contratos que coincidan con la búsqueda.'
                          : 'Sin sugerencias automáticas para este CUIT. Escribí para buscar en todos los contratos.'}
                      </div>
                    )}
                  </div>
                </div>
              )}

            </div>
            );
          })}
        </div>
      ))}
    </div>
  );
}
