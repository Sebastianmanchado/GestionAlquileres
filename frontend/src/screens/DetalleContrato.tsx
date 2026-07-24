import { useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { api } from '../api';
import { useApp } from '../context';
import { c, s } from '../theme';
import { Badge } from '../components/Badge';
import { money, dateAr, periodo as fmtPeriodo, origenValor, estadoContrato, estadoFactura, dateTimeAr } from '../format';
import { useAsync } from '../hooks';
import { Loading, ErrorBox } from './Dashboard';

type Detail = any;

const TABS: { key: string; label: string }[] = [
  { key: 'general', label: 'Datos generales' },
  { key: 'historial', label: 'Historial de valores' },
  { key: 'facturas', label: 'Facturas' },
  { key: 'docs', label: 'Documentos' },
  { key: 'cambios', label: 'Historial de cambios' },
];

export function DetalleContrato({ id }: { id: number }) {
  const { navigate, meta, role } = useApp();
  const [tab, setTab] = useState('general');
  const { data, loading, error, reload } = useAsync<Detail>(() => api.get(`/contracts/${id}`), [id, role]);

  if (loading) return <Loading />;
  if (error || !data) return <ErrorBox msg={error} />;
  const d = data;
  const est = estadoContrato(d.estadoCodigo, d.estadoNombre);
  const direccion = [d.direccion, d.localidad, d.provincia].filter(Boolean).join(', ');

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14, marginBottom: 16 }}>
        <button style={{ ...s.btn, padding: '8px 10px' }} onClick={() => navigate({ screen: 'listado' })}>‹ Volver</button>
        <div style={{ flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <h1 style={s.h1}>{d.nis} · {d.denom}</h1>
            <Badge label={est.label} tone={est.tone} />
          </div>
          <div style={{ fontSize: 12, color: c.muted, marginTop: 4 }}>{direccion}</div>
        </div>
        {meta.canEdit && <button style={s.btn} onClick={() => navigate({ screen: 'form', id })}>Editar contrato</button>}
      </div>

      {d.contratoAnteriorNumero && (
        <div style={{ background: c.fieldBg, border: `1px solid ${c.border}`, borderRadius: 4, padding: '10px 14px', fontSize: 12, marginBottom: 16 }}>
          Este contrato es <strong>prórroga / renovación</strong> del contrato <strong>{d.contratoAnteriorNumero}</strong>.
        </div>
      )}

      <div style={{ display: 'flex', gap: 4, borderBottom: `1px solid ${c.border}`, marginBottom: 18 }}>
        {TABS.map((t) => (
          <div key={t.key} onClick={() => setTab(t.key)} style={{
            padding: '10px 14px', fontSize: 12.5, cursor: 'pointer',
            borderBottom: `2px solid ${tab === t.key ? c.primary : 'transparent'}`,
            fontWeight: tab === t.key ? 700 : 400, color: tab === t.key ? c.text : c.muted,
          }}>{t.label}</div>
        ))}
      </div>

      {tab === 'general' && <General d={d} direccion={direccion} />}
      {tab === 'historial' && <Historial d={d} canEdit={meta.canEdit} />}
      {tab === 'facturas' && <Facturas d={d} />}
      {tab === 'docs' && <Documentos d={d} id={id} canEdit={meta.canEdit} onChange={reload} />}
      {tab === 'cambios' && <Cambios d={d} />}
    </div>
  );
}

function Panel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div style={{ ...s.panel, padding: 16 }}>
      <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 12 }}>{title}</div>
      {children}
    </div>
  );
}
function Field({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: `1px solid ${c.line}`, fontSize: 12.5, gap: 12 }}>
      <span style={{ color: c.muted }}>{label}</span>
      <span style={{ fontWeight: 600, textAlign: 'right' }}>{value}</span>
    </div>
  );
}

function General({ d, direccion }: { d: Detail; direccion: string }) {
  const est = estadoContrato(d.estadoCodigo, d.estadoNombre);
  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
      <Panel title="Inmueble">
        <Field label="NIS" value={d.nis} />
        <Field label="Unidad de negocio" value={d.denom} />
        <Field label="Región" value={d.region ?? '—'} />
        <Field label="Localidad / Provincia" value={`${d.localidad ?? '—'} / ${d.provincia ?? '—'}`} />
        <Field label="Destino / uso" value={d.destino ?? '—'} />
        <Field label="Superficie cubierta" value={d.supCubierta ? `${Number(d.supCubierta)} m²` : '—'} />
        <Field label="Responsable" value={d.responsable ?? '—'} />
        <Field label="Correo responsable" value={d.responsableEmail ?? '—'} />
        <Field label="Contacto responsable" value={d.responsableTelefono ?? '—'} />
      </Panel>
      <Panel title="Locador">
        <Field label="Razón social" value={d.locadorRazon} />
        <Field label="CUIT" value={d.locadorCuit} />
        <Field label="Acreedor SAP" value={d.acreedorSap ?? '—'} />
        <Field label="Contacto" value={d.locadorEmail ?? '—'} />
        <Field label="Teléfono contacto" value={d.locadorTelefono ?? '—'} />
      </Panel>
      <Panel title="Condiciones del contrato">
        <Field label="Tipo de contrato" value={d.tipo} />
        <Field label="Estado" value={<Badge label={est.label} tone={est.tone} />} />
        <Field label="Inicio" value={dateAr(d.inicio)} />
        <Field label="Vencimiento" value={dateAr(d.vencimiento)} />
        <Field label="Índice de ajuste" value={d.indiceCodigo ? `${d.indiceCodigo} · ${(d.periodicidad ?? '').toLowerCase()}` : '—'} />
        <Field label="Tolerancia de diferencia permitida" value={d.tolerancia != null ? `±${Number(d.tolerancia)}%` : '—'} />
      </Panel>
      <Panel title="Garantías">
        <Field label="Depósito de garantía" value={money(d.deposito)} />
        <Field label="Seguro de caución" value={d.seguro ? `Sí — Póliza ${d.seguro.poliza}` : 'No'} />
        <Field label="Suma asegurada" value={d.seguro ? money(d.seguro.suma) : '—'} />
        <Field label="Vigencia de póliza" value={d.seguro?.vigencia ? `hasta ${dateAr(d.seguro.vigencia)}` : '—'} />
      </Panel>
    </div>
  );
}

function Historial({ d, canEdit }: { d: Detail; canEdit: boolean }) {
  const grid = '120px 120px 150px 170px 140px';
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 10 }}>
        {canEdit && <button style={s.btnPrimary}>+ Registrar ajuste</button>}
      </div>
      <div style={{ ...s.panel, overflow: 'hidden' }}>
        <div style={{ display: 'grid', gridTemplateColumns: grid, background: c.headerBg }}>
          <div style={s.th}>Desde</div><div style={s.th}>Hasta</div><div style={s.th}>Importe</div><div style={s.th}>Origen</div><div style={s.th}>Coeficiente</div>
        </div>
        {d.valueHistory.map((v: any, i: number) => (
          <div key={i} style={{ display: 'grid', gridTemplateColumns: grid, borderTop: `1px solid ${c.line}`, fontSize: 12.5 }}>
            <div style={{ padding: '10px 12px' }}>{dateAr(v.desde)}</div>
            <div style={{ padding: '10px 12px' }}>{v.hasta ? dateAr(v.hasta) : 'Actual'}</div>
            <div style={{ padding: '10px 12px', fontWeight: 600 }}>{money(v.importe)}</div>
            <div style={{ padding: '10px 12px' }}>{origenValor(v.origen)}</div>
            <div style={{ padding: '10px 12px' }}>{v.indice && v.coeficiente ? `${v.indice} ${Number(v.coeficiente)}` : '—'}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

function Facturas({ d }: { d: Detail }) {
  const groups = new Map<string, any[]>();
  for (const f of d.facturas) {
    const key = String(f.periodo).substring(0, 7);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(f);
  }
  const ordered = Array.from(groups.entries()).sort((a, b) => (a[0] < b[0] ? 1 : -1));
  if (ordered.length === 0) return <div style={{ ...s.panel, padding: 16, fontSize: 12.5, color: c.muted2 }}>Este contrato aún no tiene facturas asociadas.</div>;
  return (
    <div>
      {ordered.map(([key, facturas]) => (
        <div key={key} style={{ marginBottom: 16 }}>
          <div style={{ fontWeight: 700, fontSize: 12.5, marginBottom: 6, color: c.muted }}>{fmtPeriodo(facturas[0].periodo)}</div>
          <div style={{ ...s.panel, overflow: 'hidden' }}>
            {facturas.map((f: any, i: number) => {
              const e = estadoFactura(f.estadoCodigo);
              return (
                <div key={i} style={{ display: 'grid', gridTemplateColumns: '110px 1fr 130px 160px', borderTop: i ? `1px solid ${c.line}` : 'none', fontSize: 12.5, alignItems: 'center' }}>
                  <div style={{ padding: '10px 12px' }}>{dateAr(f.fecha)}</div>
                  <div style={{ padding: '10px 12px' }}>Comprobante {f.numero}</div>
                  <div style={{ padding: '10px 12px' }}>{money(f.importe)}</div>
                  <div style={{ padding: '10px 12px' }}><Badge label={e.label} tone={e.tone} /></div>
                </div>
              );
            })}
          </div>
        </div>
      ))}
    </div>
  );
}

function Documentos({ d, id, canEdit, onChange }: { d: Detail; id: number; canEdit: boolean; onChange: () => void }) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [tipo, setTipo] = useState('Contrato firmado');
  const [busy, setBusy] = useState(false);

  async function upload(file: File) {
    setBusy(true);
    try {
      const form = new FormData();
      form.append('file', file);
      form.append('contratoId', String(id));
      form.append('tipoDocumento', tipo);
      await api.upload('/documents', form);
      onChange();
    } finally { setBusy(false); }
  }

  return (
    <div>
      {canEdit && (
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', marginBottom: 12 }}>
          <select style={s.select} value={tipo} onChange={(e) => setTipo(e.target.value)}>
            <option>Contrato firmado</option><option>Adenda</option><option>Póliza de caución</option><option>Otro</option>
          </select>
          <input ref={fileRef} type="file" style={{ display: 'none' }} onChange={(e) => { const f = e.target.files?.[0]; if (f) upload(f); }} />
          <button style={s.btn} disabled={busy} onClick={() => fileRef.current?.click()}>{busy ? 'Subiendo…' : 'Subir documento'}</button>
        </div>
      )}
      <div
        onClick={() => canEdit && fileRef.current?.click()}
        onDragOver={(e) => e.preventDefault()}
        onDrop={(e) => { e.preventDefault(); const f = e.dataTransfer.files?.[0]; if (f && canEdit) upload(f); }}
        style={{ border: `1.5px dashed #c7c6c3`, borderRadius: 4, padding: 24, textAlign: 'center', color: c.muted2, fontSize: 12.5, marginBottom: 16, cursor: canEdit ? 'pointer' : 'default' }}>
        {canEdit ? 'Arrastrá un archivo aquí o hacé clic para subir (contrato firmado, adenda, póliza de caución)' : 'Sólo lectura'}
      </div>
      <div style={{ ...s.panel, overflow: 'hidden' }}>
        {d.documents.length === 0 && <div style={{ padding: 16, fontSize: 12.5, color: c.muted2 }}>Sin documentos adjuntos.</div>}
        {d.documents.map((doc: any, i: number) => (
          <div key={i} style={{ display: 'grid', gridTemplateColumns: '28px 1fr 160px 120px', borderTop: i ? `1px solid ${c.line}` : 'none', fontSize: 12.5, alignItems: 'center' }}>
            <div style={{ padding: '10px 12px' }}><span style={{ width: 14, height: 16, border: '1.5px solid #8a8985', display: 'inline-block' }} /></div>
            <div style={{ padding: '10px 12px' }}><a href={`/api/documents/${doc.id}/download`} target="_blank" rel="noreferrer">{doc.nombre}</a></div>
            <div style={{ padding: '10px 12px', color: c.muted }}>{doc.tipo}</div>
            <div style={{ padding: '10px 12px', color: c.muted }}>{dateAr(doc.fecha)}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

function Cambios({ d }: { d: Detail }) {
  const grid = '150px 130px 160px 1fr 1fr';
  return (
    <div style={{ ...s.panel, overflow: 'hidden' }}>
      <div style={{ display: 'grid', gridTemplateColumns: grid, background: c.headerBg }}>
        <div style={s.th}>Fecha</div><div style={s.th}>Usuario</div><div style={s.th}>Campo</div><div style={s.th}>Valor anterior</div><div style={s.th}>Valor nuevo</div>
      </div>
      {d.changeLog.length === 0 && <div style={{ padding: 16, fontSize: 12.5, color: c.muted2 }}>Sin cambios registrados.</div>}
      {d.changeLog.map((ch: any, i: number) => (
        <div key={i} style={{ display: 'grid', gridTemplateColumns: grid, borderTop: `1px solid ${c.line}`, fontSize: 12.5 }}>
          <div style={{ padding: '10px 12px' }}>{dateTimeAr(ch.fecha)}</div>
          <div style={{ padding: '10px 12px' }}>{ch.usuario ?? '—'}</div>
          <div style={{ padding: '10px 12px' }}>{ch.campo}</div>
          <div style={{ padding: '10px 12px', color: c.danger }}>{ch.anterior ?? '—'}</div>
          <div style={{ padding: '10px 12px', color: c.green }}>{ch.nuevo ?? '—'}</div>
        </div>
      ))}
    </div>
  );
}
