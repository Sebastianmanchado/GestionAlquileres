import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { api } from '../api';
import { useApp } from '../context';
import { c, s } from '../theme';
import { Loading } from './Dashboard';

type Cat = {
  regiones: { id: number; nombre: string }[];
  localidades: { id: number; nombre: string }[];
  destinos: { id: number; nombre: string }[];
};

export function FormInmueble() {
  const { navigate, meta } = useApp();
  const [cat, setCat] = useState<Cat | null>(null);
  const [form, setForm] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => { api.get<Cat>('/catalogs').then(setCat).catch(() => {}); }, []);

  const set = (k: string, v: string) => setForm((f) => ({ ...f, [k]: v }));

  async function save() {
    setSaving(true);
    setError('');
    try {
      await api.post('/inmuebles', {
        nis: form.nis,
        denominacion: form.denominacion,
        regionId: form.regionId || null,
        localidadId: form.localidadId || null,
        destinoId: form.destinoId || null,
        direccion: form.direccion,
        superficieCubierta: form.superficieCubierta || null,
      });
      navigate({ screen: 'listado', tab: 'inmuebles' });
    } catch (e: any) {
      setError(e?.message ?? 'Ocurrió un error al guardar el inmueble.');
    } finally {
      setSaving(false);
    }
  }

  if (!meta.canEdit) {
    return <div style={{ ...s.panel, padding: 16, fontSize: 13, color: c.muted }}>El rol actual no tiene permisos para crear inmuebles.</div>;
  }
  if (!cat) return <Loading />;

  return (
    <div style={{ maxWidth: 900 }}>
      <h1 style={{ ...s.h1, marginBottom: 16 }}>Nuevo inmueble</h1>
      <div style={{ ...s.panel, padding: 16, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
        <Field label="NIS">
          <input style={s.input} value={form.nis ?? ''} onChange={(e) => set('nis', e.target.value)} placeholder="B0501" />
        </Field>
        <Field label="Unidad de negocio">
          <input style={s.input} value={form.denominacion ?? ''} onChange={(e) => set('denominacion', e.target.value)} placeholder="Sucursal A" />
        </Field>
        <Field label="Región">
          <select style={s.input} value={form.regionId ?? ''} onChange={(e) => set('regionId', e.target.value)}>
            <option value="">Seleccionar región</option>
            {cat.regiones.map((r) => <option key={r.id} value={r.id}>{r.nombre}</option>)}
          </select>
        </Field>
        <Field label="Localidad">
          <select style={s.input} value={form.localidadId ?? ''} onChange={(e) => set('localidadId', e.target.value)}>
            <option value="">Seleccionar localidad</option>
            {cat.localidades.map((l) => <option key={l.id} value={l.id}>{l.nombre}</option>)}
          </select>
        </Field>
        <Field label="Destino / uso">
          <select style={s.input} value={form.destinoId ?? ''} onChange={(e) => set('destinoId', e.target.value)}>
            <option value="">Seleccionar destino</option>
            {cat.destinos.map((x) => <option key={x.id} value={x.id}>{x.nombre}</option>)}
          </select>
        </Field>
        <Field label="Dirección">
          <input style={s.input} value={form.direccion ?? ''} onChange={(e) => set('direccion', e.target.value)} placeholder="Av. Ejemplo 1234" />
        </Field>
        <Field label="Superficie cubierta (m²)">
          <input style={s.input} value={form.superficieCubierta ?? ''} onChange={(e) => set('superficieCubierta', e.target.value)} placeholder="120" />
        </Field>
      </div>

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
        <button style={s.btn} onClick={() => navigate({ screen: 'listado', tab: 'inmuebles' })}>Cancelar</button>
        <button style={s.btnPrimary} disabled={saving} onClick={save}>{saving ? 'Guardando…' : 'Guardar inmueble'}</button>
      </div>

      {error && (
        <div style={{ ...s.panel, marginTop: 12, padding: 14, color: c.danger, fontSize: 13 }}>{error}</div>
      )}
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <label style={s.label}>{label}</label>
      {children}
    </div>
  );
}
