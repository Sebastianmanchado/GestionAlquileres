import { useEffect, useState } from 'react';
import type { ReactNode, CSSProperties } from 'react';
import { api, qs } from '../api';
import { useApp } from '../context';
import { c, s } from '../theme';
import { Badge } from '../components/Badge';
import { money, dateAr, m2 as fmtM2, estadoContrato } from '../format';
import { Loading, ErrorBox } from './Dashboard';

const CONTRACT_GRID = '70px 150px 110px 110px 100px 140px 70px 110px 90px 70px 170px 100px 110px 140px 150px 140px';
const INMUEBLE_GRID = '90px 180px 130px 140px 130px 220px 160px 110px';

type Row = {
  id: number; nis: string; denom: string; region: string; localidad: string; provincia: string;
  destino: string; m2: number; valorActual: number; indice: string; tipo: string;
  inicio: string; vencimiento: string; estadoCodigo: string; estadoNombre: string; propietario: string;
};

type InmuebleRow = {
  id: number; nis: string; denom: string; region: string; localidad: string; provincia: string;
  direccion: string; destino: string; superficie: number;
};

type Cat = {
  regiones: { id: number; nombre: string }[];
  estados: { id: number; codigo: string; nombre: string }[];
  indices: { id: number; codigo: string; nombre: string }[];
};

type Page<T> = { rows: T[]; total: number };

export function Listado() {
  const { navigate, meta, role, route } = useApp();
  const tab = route.screen === 'listado' && route.tab === 'inmuebles' ? 'inmuebles' : 'contratos';

  const [cat, setCat] = useState<Cat | null>(null);
  const [search, setSearch] = useState('');
  const [region, setRegion] = useState('');
  const [estado, setEstado] = useState('');
  const [indice, setIndice] = useState('');
  const [venc, setVenc] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(14);
  const [sort, setSort] = useState('nis');
  const [dir, setDir] = useState<'asc' | 'desc'>('asc');
  const [data, setData] = useState<Page<Row> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [delTarget, setDelTarget] = useState<Row | null>(null);

  const [iSearch, setISearch] = useState('');
  const [iRegion, setIRegion] = useState('');
  const [iPage, setIPage] = useState(0);
  const [iSize, setISize] = useState(14);
  const [iSort, setISort] = useState('nis');
  const [iDir, setIDir] = useState<'asc' | 'desc'>('asc');
  const [iData, setIData] = useState<Page<InmuebleRow> | null>(null);
  const [iLoading, setILoading] = useState(true);
  const [iError, setIError] = useState<string | null>(null);

  useEffect(() => { api.get<Cat>('/catalogs').then(setCat).catch(() => {}); }, []);

  function loadContracts() {
    setLoading(true);
    api.get<Page<Row>>('/contracts' + qs({ search, region, estado, indice, venc, page, size, sort, dir }))
      .then((d) => { setData(d); setLoading(false); })
      .catch((e) => { setError(e.message); setLoading(false); });
  }

  function loadInmuebles() {
    setILoading(true);
    api.get<Page<InmuebleRow>>('/inmuebles' + qs({ search: iSearch, region: iRegion, page: iPage, size: iSize, sort: iSort, dir: iDir }))
      .then((d) => { setIData(d); setILoading(false); })
      .catch((e) => { setIError(e.message); setILoading(false); });
  }

  useEffect(() => { loadContracts(); /* eslint-disable-next-line */ }, [search, region, estado, indice, venc, page, size, sort, dir, role]);
  useEffect(() => { loadInmuebles(); /* eslint-disable-next-line */ }, [iSearch, iRegion, iPage, iSize, iSort, iDir, role]);

  function toggleSort(column: string, current: string, currentDir: 'asc' | 'desc', setColumn: (v: string) => void, setDirection: (v: 'asc' | 'desc') => void, resetPage: () => void) {
    resetPage();
    if (current === column) setDirection(currentDir === 'asc' ? 'desc' : 'asc');
    else { setColumn(column); setDirection('asc'); }
  }

  async function confirmDelete() {
    if (!delTarget) return;
    await api.del(`/contracts/${delTarget.id}`);
    setDelTarget(null);
    loadContracts();
  }

  const active = tab === 'contratos'
    ? { total: data?.total ?? 0, page, size, setPage, setSize }
    : { total: iData?.total ?? 0, page: iPage, size: iSize, setPage: setIPage, setSize: setISize };
  const from = active.total === 0 ? 0 : active.page * active.size + 1;
  const to = Math.min(active.total, (active.page + 1) * active.size);
  const pages = Math.max(1, Math.ceil(active.total / active.size));
  const showing = tab === 'contratos' ? 'contratos' : 'inmuebles';

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: 14, gap: 12 }}>
        <h1 style={{ ...s.h1, flex: 1 }}>Inmuebles y contratos</h1>
        {tab === 'contratos' && <button style={s.btn}>Exportar a Excel</button>}
        {tab === 'contratos' && meta.canEdit && (
          <button style={s.btnPrimary} onClick={() => navigate({ screen: 'form' })}>+ Nuevo contrato</button>
        )}
        {tab === 'inmuebles' && meta.canEdit && (
          <button style={s.btnPrimary} onClick={() => navigate({ screen: 'formInmueble' })}>+ Nuevo inmueble</button>
        )}
      </div>

      <div style={{ display: 'flex', gap: 0, marginBottom: 14, borderBottom: `1px solid ${c.border}` }}>
        <TabButton active={tab === 'contratos'} onClick={() => navigate({ screen: 'listado', tab: 'contratos' })}>Contratos</TabButton>
        <TabButton active={tab === 'inmuebles'} onClick={() => navigate({ screen: 'listado', tab: 'inmuebles' })}>Inmuebles</TabButton>
      </div>

      {tab === 'contratos' ? (
        <>
          <div style={{ display: 'flex', gap: 8, marginBottom: 12, flexWrap: 'wrap', alignItems: 'center' }}>
            <SearchBox value={search} placeholder="Buscar NIS o unidad de negocio" onChange={(v) => { setPage(0); setSearch(v); }} />
            <select style={s.select} value={region} onChange={(e) => { setPage(0); setRegion(e.target.value); }}>
              <option value="">Región: Todas</option>
              {cat?.regiones.map((r) => <option key={r.id} value={r.nombre}>{r.nombre}</option>)}
            </select>
            <select style={s.select} value={estado} onChange={(e) => { setPage(0); setEstado(e.target.value); }}>
              <option value="">Estado: Todos</option>
              {cat?.estados.filter((x) => x.codigo !== 'RESCINDIDO' && x.codigo !== 'PROX_VENCER').map((x) => (
                <option key={x.id} value={x.codigo}>{x.nombre}</option>
              ))}
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
            <div style={{ ...s.panel, overflow: 'auto' }}>
              <div style={{ minWidth: 1720 }}>
                <div style={{ display: 'grid', gridTemplateColumns: CONTRACT_GRID, background: c.headerBg }}>
                  <SortTh sticky label="NIS" col="nis" sort={sort} dir={dir} onSort={(col) => toggleSort(col, sort, dir, setSort, setDir, () => setPage(0))} />
                  <SortTh label="Unidad de negocio" col="denom" sort={sort} dir={dir} onSort={(col) => toggleSort(col, sort, dir, setSort, setDir, () => setPage(0))} />
                  <SortTh label="Región" col="region" sort={sort} dir={dir} onSort={(col) => toggleSort(col, sort, dir, setSort, setDir, () => setPage(0))} />
                  <SortTh label="Localidad" col="localidad" sort={sort} dir={dir} onSort={(col) => toggleSort(col, sort, dir, setSort, setDir, () => setPage(0))} />
                  <SortTh label="Provincia" col="provincia" sort={sort} dir={dir} onSort={(col) => toggleSort(col, sort, dir, setSort, setDir, () => setPage(0))} />
                  <SortTh label="Destino / uso" col="destino" sort={sort} dir={dir} onSort={(col) => toggleSort(col, sort, dir, setSort, setDir, () => setPage(0))} />
                  <SortTh label="Valor actual" col="valorActual" sort={sort} dir={dir} onSort={(col) => toggleSort(col, sort, dir, setSort, setDir, () => setPage(0))} />
                  <SortTh label="Índice" col="indice" sort={sort} dir={dir} onSort={(col) => toggleSort(col, sort, dir, setSort, setDir, () => setPage(0))} />
                  <SortTh label="Tipo contrato" col="tipo" sort={sort} dir={dir} onSort={(col) => toggleSort(col, sort, dir, setSort, setDir, () => setPage(0))} />
                  <SortTh label="Inicio" col="inicio" sort={sort} dir={dir} onSort={(col) => toggleSort(col, sort, dir, setSort, setDir, () => setPage(0))} />
                  <SortTh label="Vencimiento" col="vencimiento" sort={sort} dir={dir} onSort={(col) => toggleSort(col, sort, dir, setSort, setDir, () => setPage(0))} />
                  <SortTh label="Estado" col="estado" sort={sort} dir={dir} onSort={(col) => toggleSort(col, sort, dir, setSort, setDir, () => setPage(0))} />
                  <SortTh label="Propietario" col="propietario" sort={sort} dir={dir} onSort={(col) => toggleSort(col, sort, dir, setSort, setDir, () => setPage(0))} />
                  <Th>Acciones</Th>
                </div>
                {data?.rows.map((r) => {
                  const est = estadoContrato(r.estadoCodigo, r.estadoNombre);
                  return (
                    <div key={r.id} onClick={() => navigate({ screen: 'detalle', id: r.id })}
                      style={{ display: 'grid', gridTemplateColumns: CONTRACT_GRID, borderTop: `1px solid ${c.line}`, fontSize: 12.5, cursor: 'pointer', background: '#fff' }}>
                      <CellSticky>{r.nis}</CellSticky>
                      <Cell>{r.denom}</Cell><Cell>{r.region}</Cell><Cell>{r.localidad}</Cell><Cell>{r.provincia}</Cell>
                      <Cell>{r.destino}</Cell><Cell>{money(r.valorActual)}</Cell><Cell>{r.indice}</Cell>
                      <Cell>{r.tipo}</Cell><Cell>{dateAr(r.inicio)}</Cell>
                      <Cell>{dateAr(r.vencimiento)}</Cell>
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
          )}
        </>
      ) : (
        <>
          <div style={{ display: 'flex', gap: 8, marginBottom: 12, flexWrap: 'wrap', alignItems: 'center' }}>
            <SearchBox value={iSearch} placeholder="Buscar NIS o unidad de negocio" onChange={(v) => { setIPage(0); setISearch(v); }} />
            <select style={s.select} value={iRegion} onChange={(e) => { setIPage(0); setIRegion(e.target.value); }}>
              <option value="">Región: Todas</option>
              {cat?.regiones.map((r) => <option key={r.id} value={r.nombre}>{r.nombre}</option>)}
            </select>
          </div>

          {iLoading && !iData ? <Loading /> : iError ? <ErrorBox msg={iError} /> : (
            <div style={{ ...s.panel, overflow: 'auto' }}>
              <div style={{ minWidth: 1160 }}>
                <div style={{ display: 'grid', gridTemplateColumns: INMUEBLE_GRID, background: c.headerBg }}>
                  <SortTh sticky label="NIS" col="nis" sort={iSort} dir={iDir} onSort={(col) => toggleSort(col, iSort, iDir, setISort, setIDir, () => setIPage(0))} />
                  <SortTh label="Unidad de negocio" col="denom" sort={iSort} dir={iDir} onSort={(col) => toggleSort(col, iSort, iDir, setISort, setIDir, () => setIPage(0))} />
                  <SortTh label="Región" col="region" sort={iSort} dir={iDir} onSort={(col) => toggleSort(col, iSort, iDir, setISort, setIDir, () => setIPage(0))} />
                  <SortTh label="Localidad" col="localidad" sort={iSort} dir={iDir} onSort={(col) => toggleSort(col, iSort, iDir, setISort, setIDir, () => setIPage(0))} />
                  <SortTh label="Provincia" col="provincia" sort={iSort} dir={iDir} onSort={(col) => toggleSort(col, iSort, iDir, setISort, setIDir, () => setIPage(0))} />
                  <SortTh label="Dirección" col="direccion" sort={iSort} dir={iDir} onSort={(col) => toggleSort(col, iSort, iDir, setISort, setIDir, () => setIPage(0))} />
                  <SortTh label="Destino / uso" col="destino" sort={iSort} dir={iDir} onSort={(col) => toggleSort(col, iSort, iDir, setISort, setIDir, () => setIPage(0))} />
                  <SortTh label="Superficie" col="superficie" sort={iSort} dir={iDir} onSort={(col) => toggleSort(col, iSort, iDir, setISort, setIDir, () => setIPage(0))} />
                </div>
                {iData?.rows.map((r) => (
                  <div key={r.id} style={{ display: 'grid', gridTemplateColumns: INMUEBLE_GRID, borderTop: `1px solid ${c.line}`, fontSize: 12.5, background: '#fff' }}>
                    <CellSticky>{r.nis}</CellSticky>
                    <Cell>{r.denom}</Cell><Cell>{r.region}</Cell><Cell>{r.localidad}</Cell><Cell>{r.provincia}</Cell>
                    <Cell>{r.direccion}</Cell><Cell>{r.destino}</Cell><Cell>{fmtM2(r.superficie)}</Cell>
                  </div>
                ))}
                {iData && iData.rows.length === 0 && <div style={{ padding: 16, fontSize: 12.5, color: c.muted2 }}>No hay inmuebles que coincidan con los filtros.</div>}
              </div>
            </div>
          )}
        </>
      )}

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 12, fontSize: 12, color: c.muted }}>
        <div>Mostrando {from}–{to} de {active.total} {showing}</div>
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          <select style={s.select} value={active.size} onChange={(e) => { active.setPage(0); active.setSize(Number(e.target.value)); }}>
            <option value={14}>14 filas</option><option value={25}>25 filas</option><option value={50}>50 filas</option>
          </select>
          <button style={s.btn} disabled={active.page === 0} onClick={() => active.setPage(Math.max(0, active.page - 1))}>‹ Anterior</button>
          <span style={{ padding: '5px 8px' }}>{active.page + 1} / {pages}</span>
          <button style={s.btn} disabled={active.page + 1 >= pages} onClick={() => active.setPage(Math.min(pages - 1, active.page + 1))}>Siguiente ›</button>
        </div>
      </div>

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

function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      onClick={onClick}
      style={{
        border: 'none',
        borderBottom: active ? `2px solid ${c.primary}` : '2px solid transparent',
        background: 'transparent',
        color: active ? c.primary : c.muted,
        fontWeight: 700,
        fontSize: 13,
        padding: '8px 14px',
        cursor: 'pointer',
      }}
    >
      {children}
    </button>
  );
}

function SearchBox({ value, placeholder, onChange }: { value: string; placeholder: string; onChange: (v: string) => void }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 6, background: '#fff', border: `1px solid ${c.border}`, borderRadius: 4, padding: '7px 10px', width: 260 }}>
      <span style={{ width: 11, height: 11, border: '1.5px solid #8a8985', borderRadius: '50%', flex: 'none' }} />
      <input value={value} onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder} style={{ border: 'none', outline: 'none', fontSize: 12, width: '100%' }} />
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
function SortTh({ label, col, sort, dir, onSort, sticky }: {
  label: string; col: string; sort: string; dir: 'asc' | 'desc'; onSort: (col: string) => void; sticky?: boolean;
}) {
  const active = sort === col;
  return (
    <div
      onClick={() => onSort(col)}
      style={{
        ...s.th,
        cursor: 'pointer',
        userSelect: 'none',
        ...(sticky ? { position: 'sticky', left: 0, background: c.headerBg, borderRight: `1px solid ${c.border}`, zIndex: 1 } : {}),
      }}
    >
      {label}{active ? (dir === 'asc' ? ' ↑' : ' ↓') : ''}
    </div>
  );
}
