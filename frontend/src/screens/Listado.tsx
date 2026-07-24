import { useEffect, useState } from 'react';
import type { ReactNode, CSSProperties } from 'react';
import { api, qs } from '../api';
import { useApp } from '../context';
import { c, s } from '../theme';
import { Badge } from '../components/Badge';
import { money, dateAr, m2 as fmtM2, estadoContrato } from '../format';
import { Loading, ErrorBox } from './Dashboard';

const GRID = '70px 150px 110px 110px 100px 140px 70px 110px 90px 70px 170px 100px 110px 140px 150px 140px';

type Row = {
  id: number; nis: string; denom: string; region: string; localidad: string; provincia: string;
  destino: string; m2: number; valorActual: number; indice: string; tipo: string;
  inicio: string; vencimiento: string; estadoCodigo: string; estadoNombre: string; propietario: string;
};

type Cat = {
  regiones: { id: number; nombre: string }[];
  estados: { id: number; codigo: string; nombre: string }[];
  indices: { id: number; codigo: string; nombre: string }[];
};

export function Listado() {
  const { navigate, meta, role } = useApp();
  const [cat, setCat] = useState<Cat | null>(null);
  const [search, setSearch] = useState('');
  const [region, setRegion] = useState('');
  const [estado, setEstado] = useState('');
  const [indice, setIndice] = useState('');
  const [venc, setVenc] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(14);

  const [data, setData] = useState<{ rows: Row[]; total: number } | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [delTarget, setDelTarget] = useState<Row | null>(null);

  useEffect(() => { api.get<Cat>('/catalogs').then(setCat).catch(() => {}); }, []);

  function load() {
    setLoading(true);
    api.get<{ rows: Row[]; total: number }>('/contracts' + qs({ search, region, estado, indice, venc, page, size }))
      .then((d) => { setData(d); setLoading(false); })
      .catch((e) => { setError(e.message); setLoading(false); });
  }
  // recargar cuando cambian filtros / paginación / rol
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [search, region, estado, indice, venc, page, size, role]);

  async function confirmDelete() {
    if (!delTarget) return;
    await api.del(`/contracts/${delTarget.id}`);
    setDelTarget(null);
    load();
  }

  const total = data?.total ?? 0;
  const from = total === 0 ? 0 : page * size + 1;
  const to = Math.min(total, (page + 1) * size);
  const pages = Math.max(1, Math.ceil(total / size));

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: 14, gap: 12 }}>
        <h1 style={{ ...s.h1, flex: 1 }}>Inmuebles y contratos</h1>
        <button style={s.btn}>Exportar a Excel</button>
        {meta.canEdit && <button style={s.btnPrimary} onClick={() => navigate({ screen: 'form' })}>+ Nuevo contrato</button>}
      </div>

      <div style={{ display: 'flex', gap: 8, marginBottom: 12, flexWrap: 'wrap', alignItems: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, background: '#fff', border: `1px solid ${c.border}`, borderRadius: 4, padding: '7px 10px', width: 260 }}>
          <span style={{ width: 11, height: 11, border: '1.5px solid #8a8985', borderRadius: '50%', flex: 'none' }} />
          <input value={search} onChange={(e) => { setPage(0); setSearch(e.target.value); }}
            placeholder="Buscar NIS o unidad de negocio" style={{ border: 'none', outline: 'none', fontSize: 12, width: '100%' }} />
        </div>
        <select style={s.select} value={region} onChange={(e) => { setPage(0); setRegion(e.target.value); }}>
          <option value="">Región: Todas</option>
          {cat?.regiones.map((r) => <option key={r.id} value={r.nombre}>{r.nombre}</option>)}
        </select>
        <select style={s.select} value={estado} onChange={(e) => { setPage(0); setEstado(e.target.value); }}>
          <option value="">Estado: Todos</option>
          {cat?.estados.filter((x) => x.codigo !== 'RESCINDIDO').map((x) => <option key={x.id} value={x.codigo}>{x.nombre}</option>)}
        </select>
        <select style={s.select} value={indice} onChange={(e) => { setPage(0); setIndice(e.target.value); }}>
          <option value="">Índice: Todos</option>
          {cat?.indices.map((x) => <option key={x.id} value={x.codigo}>{x.codigo}</option>)}
        </select>
        <select style={s.select} value={venc} onChange={(e) => { setPage(0); setVenc(e.target.value); }}>
          <option value="">Vencimiento: cualquier fecha</option>
          <option value="30">Próx. 30 días</option>
          <option value="90">Próx. 90 días</option>
          <option value="vencidos">Vencidos</option>
        </select>
      </div>

      {loading && !data ? <Loading /> : error ? <ErrorBox msg={error} /> : (
        <>
          <div style={{ ...s.panel, overflow: 'auto' }}>
            <div style={{ minWidth: 1720 }}>
              <div style={{ display: 'grid', gridTemplateColumns: GRID, background: c.headerBg }}>
                <Th sticky>NIS</Th><Th>Unidad de negocio</Th><Th>Región</Th><Th>Localidad</Th><Th>Provincia</Th>
                <Th>Destino / uso</Th><Th>m²</Th><Th>Valor actual</Th><Th>Valor / m²</Th><Th>Índice</Th>
                <Th>Tipo contrato</Th><Th>Inicio</Th><Th>Vencimiento</Th><Th>Estado</Th><Th>Propietario</Th><Th>Acciones</Th>
              </div>
              {data?.rows.map((r) => {
                const est = estadoContrato(r.estadoCodigo, r.estadoNombre);
                const valorM2 = r.m2 ? Number(r.valorActual) / Number(r.m2) : 0;
                return (
                  <div key={r.id} onClick={() => navigate({ screen: 'detalle', id: r.id })}
                    style={{ display: 'grid', gridTemplateColumns: GRID, borderTop: `1px solid ${c.line}`, fontSize: 12.5, cursor: 'pointer', background: '#fff' }}>
                    <CellSticky>{r.nis}</CellSticky>
                    <Cell>{r.denom}</Cell><Cell>{r.region}</Cell><Cell>{r.localidad}</Cell><Cell>{r.provincia}</Cell>
                    <Cell>{r.destino}</Cell><Cell>{fmtM2(r.m2)}</Cell><Cell>{money(r.valorActual)}</Cell><Cell>{money(valorM2)}</Cell><Cell>{r.indice}</Cell>
                    <Cell>{r.tipo}</Cell><Cell>{dateAr(r.inicio)}</Cell>
                    <Cell><span style={{ textDecoration: r.estadoCodigo === 'PROX_VENCER' ? 'underline' : 'none', fontWeight: r.estadoCodigo === 'PROX_VENCER' ? 700 : 400 }}>{dateAr(r.vencimiento)}</span></Cell>
                    <Cell><Badge label={est.label} tone={est.tone} /></Cell>
                    <Cell>{r.propietario}</Cell>
                    <div style={{ ...cellStyle, display: 'flex', gap: 8 }} onClick={(e) => e.stopPropagation()}>
                      <a href="#" onClick={(e) => { e.preventDefault(); navigate({ screen: 'detalle', id: r.id }); }}>Ver</a>
                      {meta.canEdit && <a href="#" onClick={(e) => { e.preventDefault(); navigate({ screen: 'form', id: r.id }); }}>Editar</a>}
                      {meta.canEdit && <a href="#" onClick={(e) => { e.preventDefault(); setDelTarget(r); }} style={{ color: c.danger }}>Eliminar</a>}
                    </div>
                  </div>
                );
              })}
              {data && data.rows.length === 0 && <div style={{ padding: 16, fontSize: 12.5, color: c.muted2 }}>No hay contratos que coincidan con los filtros.</div>}
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 12, fontSize: 12, color: c.muted }}>
            <div>Mostrando {from}–{to} de {total} contratos</div>
            <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
              <select style={s.select} value={size} onChange={(e) => { setPage(0); setSize(Number(e.target.value)); }}>
                <option value={14}>14 filas</option><option value={25}>25 filas</option><option value={50}>50 filas</option>
              </select>
              <button style={s.btn} disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>‹ Anterior</button>
              <span style={{ padding: '5px 8px' }}>{page + 1} / {pages}</span>
              <button style={s.btn} disabled={page + 1 >= pages} onClick={() => setPage((p) => Math.min(pages - 1, p + 1))}>Siguiente ›</button>
            </div>
          </div>
        </>
      )}

      {delTarget && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(20,20,20,.45)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50 }}>
          <div style={{ background: '#fff', borderRadius: 6, width: 380, padding: 22 }}>
            <div style={{ fontWeight: 700, fontSize: 14.5, marginBottom: 8 }}>Eliminar contrato {delTarget.nis}</div>
            <div style={{ fontSize: 12.5, color: c.muted, lineHeight: 1.5 }}>
              Esta acción no se puede deshacer. El contrato y su historial de valores dejarán de estar disponibles en el listado principal.
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 20 }}>
              <button style={s.btn} onClick={() => setDelTarget(null)}>Cancelar</button>
              <button style={s.btnDanger} onClick={confirmDelete}>Eliminar</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

const cellStyle: CSSProperties = { padding: '9px 10px', borderRight: `1px solid ${c.line}`, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' };
function Cell({ children }: { children: ReactNode }) { return <div style={cellStyle}>{children}</div>; }
function CellSticky({ children }: { children: ReactNode }) {
  return <div style={{ position: 'sticky', left: 0, background: '#fff', padding: '9px 10px', borderRight: `1px solid ${c.border}`, fontWeight: 700, zIndex: 1 }}>{children}</div>;
}
function Th({ children, sticky }: { children: ReactNode; sticky?: boolean }) {
  return <div style={{ ...s.th, ...(sticky ? { position: 'sticky', left: 0, background: c.headerBg, borderRight: `1px solid ${c.border}`, zIndex: 1 } : {}) }}>{children}</div>;
}
