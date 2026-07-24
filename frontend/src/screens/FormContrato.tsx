import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { api } from '../api';
import { useApp } from '../context';
import { c, s } from '../theme';
import { Loading } from './Dashboard';

type Cat = any;

export function FormContrato({ id }: { id?: number }) {
  const { navigate, meta } = useApp();
  const editing = id != null;
  const [cat, setCat] = useState<Cat | null>(null);
  const [open, setOpen] = useState<string | null>('inmueble');
  const [form, setForm] = useState<Record<string, any>>({ periodicidad: 'TRIMESTRAL', tipoContratoId: 1 });
  const [saving, setSaving] = useState(false);
  const [ready, setReady] = useState(!editing);

  useEffect(() => { api.get<Cat>('/catalogs').then(setCat); }, []);
  useEffect(() => {
    if (editing) {
      api.get(`/contracts/${id}`).then((d: any) => {
        setForm({
          nis: d.nis, denominacion: d.denom, direccion: d.direccion,
          importeMensual: d.valorActual, fechaInicio: str(d.inicio), fechaVencimiento: str(d.vencimiento),
          tolerancia: d.tolerancia, indiceId: undefined, periodicidad: d.periodicidad ?? 'TRIMESTRAL',
          deposito: d.deposito, tipoContratoId: 1,
        });
        setReady(true);
      });
    }
  }, [id]);

  if (!meta.canEdit) {
    return <div style={{ ...s.panel, padding: 16, fontSize: 13, color: c.muted }}>El rol actual no tiene permisos para crear o editar contratos.</div>;
  }
  if (!ready || !cat) return <Loading />;

  const set = (k: string, v: any) => setForm((f) => ({ ...f, [k]: v }));

  async function save() {
    setSaving(true);
    try {
      if (editing) {
        await api.put(`/contracts/${id}`, form);
        navigate({ screen: 'detalle', id: id! });
      } else {
        const res = await api.post<{ id: number }>('/contracts', form);
        navigate({ screen: 'detalle', id: res.id });
      }
    } catch (e: any) {
      alert('No se pudo guardar: ' + (e.message ?? e));
    } finally { setSaving(false); }
  }

  const sections: { key: string; titulo: string; body: ReactNode }[] = [
    {
      key: 'inmueble', titulo: 'Inmueble', body: (
        <>
          <F label="NIS"><input style={s.input} value={form.nis ?? ''} onChange={(e) => set('nis', e.target.value)} placeholder="B0501" /></F>
          <F label="Unidad de negocio"><input style={s.input} value={form.denominacion ?? ''} onChange={(e) => set('denominacion', e.target.value)} placeholder="Sucursal A" /></F>
          <F label="Región"><select style={{ ...s.input }} value={form.regionId ?? ''} onChange={(e) => set('regionId', e.target.value)}><option value="">Seleccionar región</option>{cat.regiones.map((r: any) => <option key={r.id} value={r.id}>{r.nombre}</option>)}</select></F>
          <F label="Localidad"><select style={{ ...s.input }} value={form.localidadId ?? ''} onChange={(e) => set('localidadId', e.target.value)}><option value="">Seleccionar localidad</option>{cat.localidades.map((l: any) => <option key={l.id} value={l.id}>{l.nombre}</option>)}</select></F>
          <F label="Destino / uso"><select style={{ ...s.input }} value={form.destinoId ?? ''} onChange={(e) => set('destinoId', e.target.value)}><option value="">Seleccionar destino</option>{cat.destinos.map((x: any) => <option key={x.id} value={x.id}>{x.nombre}</option>)}</select></F>
          <F label="Dirección"><input style={s.input} value={form.direccion ?? ''} onChange={(e) => set('direccion', e.target.value)} placeholder="Av. Ejemplo 1234" /></F>
          <F label="Superficie cubierta (m²)"><input style={s.input} value={form.superficieCubierta ?? ''} onChange={(e) => set('superficieCubierta', e.target.value)} placeholder="120" /></F>
        </>
      ),
    },
    {
      key: 'locador', titulo: 'Locador', body: (
        <>
          <F label="Locador" span={2}><select style={{ ...s.input }} value={form.locadorId ?? ''} onChange={(e) => set('locadorId', e.target.value)}><option value="">Seleccionar locador existente</option>{cat.locadores.map((l: any) => <option key={l.id} value={l.id}>{l.razonSocial} · {l.cuit}</option>)}</select></F>
          <F label="Razón social (nuevo)"><input style={s.input} value={form.razonSocial ?? ''} onChange={(e) => set('razonSocial', e.target.value)} placeholder="Sólo si es un locador nuevo" /></F>
          <F label="CUIT (nuevo)"><input style={s.input} value={form.cuit ?? ''} onChange={(e) => set('cuit', e.target.value)} placeholder="30-XXXXXXXX-X" /></F>
        </>
      ),
    },
    {
      key: 'economicas', titulo: 'Condiciones económicas', body: (
        <>
          <F label="Importe mensual"><input style={s.input} value={form.importeMensual ?? ''} onChange={(e) => set('importeMensual', e.target.value)} placeholder="$ 0" /></F>
          <F label="Tipo de contrato"><select style={{ ...s.input }} value={form.tipoContratoId ?? 1} onChange={(e) => set('tipoContratoId', e.target.value)}>{cat.tiposContrato.map((x: any) => <option key={x.id} value={x.id}>{x.nombre}</option>)}</select></F>
          <F label="Fecha de inicio"><input type="date" style={s.input} value={form.fechaInicio ?? ''} onChange={(e) => set('fechaInicio', e.target.value)} /></F>
          <F label="Fecha de vencimiento"><input type="date" style={s.input} value={form.fechaVencimiento ?? ''} onChange={(e) => set('fechaVencimiento', e.target.value)} /></F>
          <F label="Tolerancia de diferencia (%)"><input style={s.input} value={form.tolerancia ?? ''} onChange={(e) => set('tolerancia', e.target.value)} placeholder="3" /></F>
        </>
      ),
    },
    {
      key: 'ajustes', titulo: 'Ajustes e índice', body: (
        <>
          <F label="Índice de ajuste"><select style={{ ...s.input }} value={form.indiceId ?? ''} onChange={(e) => set('indiceId', e.target.value)}><option value="">Seleccionar índice</option>{cat.indices.map((x: any) => <option key={x.id} value={x.id}>{x.codigo} — {x.nombre}</option>)}</select></F>
          <F label="Frecuencia"><select style={{ ...s.input }} value={form.periodicidad ?? 'TRIMESTRAL'} onChange={(e) => set('periodicidad', e.target.value)}><option value="TRIMESTRAL">Trimestral</option><option value="CUATRIMESTRAL">Cuatrimestral</option><option value="SEMESTRAL">Semestral</option></select></F>
        </>
      ),
    },
    {
      key: 'garantias', titulo: 'Garantías', body: (
        <>
          <F label="Depósito de garantía"><input style={s.input} value={form.deposito ?? ''} onChange={(e) => set('deposito', e.target.value)} placeholder="$ 0" /></F>
        </>
      ),
    },
    {
      key: 'documentos', titulo: 'Documentos', body: (
        <div style={{ gridColumn: '1/-1', fontSize: 12.5, color: c.muted }}>Podés adjuntar el contrato firmado y la póliza desde la pestaña <strong>Documentos</strong> del detalle una vez guardado.</div>
      ),
    },
  ];

  return (
    <div style={{ maxWidth: 900 }}>
      <h1 style={{ ...s.h1, marginBottom: 16 }}>{editing ? 'Editar contrato' : 'Nuevo contrato'}</h1>

      {!editing && (
        <div style={{ background: c.warnBg, border: `1px solid ${c.warnBorder}`, borderRadius: 4, padding: '10px 14px', fontSize: 12, marginBottom: 18, display: 'flex', gap: 8 }}>
          <span style={{ fontWeight: 700 }}>⚠</span>
          <div>Verificá que no exista un contrato vigente para el mismo inmueble en el período seleccionado antes de continuar.</div>
        </div>
      )}

      {sections.map((sec) => {
        const isOpen = open === sec.key;
        return (
          <div key={sec.key} style={{ ...s.panel, marginBottom: 10, overflow: 'hidden' }}>
            <div onClick={() => setOpen(isOpen ? null : sec.key)} style={{ padding: '14px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', cursor: 'pointer', background: c.softBg }}>
              <div style={{ fontWeight: 700, fontSize: 13.5 }}>{sec.titulo}</div>
              <span style={{ width: 8, height: 8, borderRight: '2px solid #6b6a67', borderBottom: '2px solid #6b6a67', transform: `rotate(${isOpen ? 225 : 45}deg)`, marginTop: isOpen ? 4 : -4 }} />
            </div>
            {isOpen && <div style={{ padding: 16, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14, borderTop: `1px solid ${c.line}` }}>{sec.body}</div>}
          </div>
        );
      })}

      <div style={{ position: 'sticky', bottom: 0, background: c.bg, padding: '14px 0', display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
        <button style={s.btn} onClick={() => navigate(editing ? { screen: 'detalle', id: id! } : { screen: 'listado' })}>Cancelar</button>
        <button style={s.btnPrimary} disabled={saving} onClick={save}>{saving ? 'Guardando…' : 'Guardar contrato'}</button>
      </div>
    </div>
  );
}

function F({ label, children, span }: { label: string; children: ReactNode; span?: number }) {
  return (
    <div style={{ gridColumn: span === 2 ? '1/-1' : undefined }}>
      <label style={s.label}>{label}</label>
      {children}
    </div>
  );
}

function str(v: any): string { return v == null ? '' : String(v).substring(0, 10); }
