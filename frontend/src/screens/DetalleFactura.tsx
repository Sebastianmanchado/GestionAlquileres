import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { api } from '../api';
import { useApp } from '../context';
import type { Route } from '../context';
import { c, s } from '../theme';
import { estadoFactura } from '../format';
import { Loading } from './Dashboard';

export function DetalleFactura({ id, nueva, origin }: { id?: number; nueva?: boolean; origin: Route['screen'] }) {
  const { navigate, meta } = useApp();
  const esNueva = !!nueva;
  const [form, setForm] = useState<Record<string, any>>({});
  const [display, setDisplay] = useState<{ estadoCodigo?: string; contrato?: string }>({});
  const [ready, setReady] = useState(esNueva);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!esNueva && id != null) {
      api.get(`/invoices/${id}`).then((d: any) => {
        setForm({
          cuit: d.cuit, razonSocial: d.razonSocial, comprobante: d.comprobante,
          importe: d.importe, periodo: d.periodo ? String(d.periodo).substring(0, 7) : '',
          fechaEmision: d.fechaEmision ? String(d.fechaEmision).substring(0, 10) : '',
          observaciones: d.observaciones ?? '',
        });
        setDisplay({ estadoCodigo: d.estadoCodigo, contrato: d.contratoNis ? `${d.contratoNis} · ${d.contratoDenom}` : '(sin asignar)' });
        setReady(true);
      });
    }
  }, [id, esNueva]);

  if (!ready) return <Loading />;
  const set = (k: string, v: any) => setForm((f) => ({ ...f, [k]: v }));

  async function save() {
    setSaving(true);
    try {
      if (esNueva) await api.post('/invoices', form);
      else await api.put(`/invoices/${id}`, form);
      navigate({ screen: origin } as Route);
    } catch (e: any) { alert('No se pudo guardar: ' + (e.message ?? e)); }
    finally { setSaving(false); }
  }

  const estado = display.estadoCodigo ? estadoFactura(display.estadoCodigo).label : 'Sin asignar';

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
        <button style={{ ...s.btn, padding: '8px 10px' }} onClick={() => navigate({ screen: origin } as Route)}>‹ Volver</button>
        <h1 style={s.h1}>{esNueva ? 'Nueva factura' : `Factura ${form.comprobante ?? ''}`}</h1>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        {esNueva ? (
          <div style={{ border: `1.5px dashed #c7c6c3`, borderRadius: 4, padding: 24, textAlign: 'center', color: c.muted2, fontSize: 12.5, height: 520, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            Arrastrá el PDF de la factura aquí o hacé clic para subir
          </div>
        ) : (
          <div style={{ ...s.panel, overflow: 'hidden' }}>
            <div style={{ padding: '10px 14px', borderBottom: `1px solid ${c.border}`, fontSize: 12, fontWeight: 700 }}>Comprobante {form.comprobante}.pdf</div>
            <div style={{ height: 520, background: 'repeating-linear-gradient(135deg,#f6f5f3,#f6f5f3 10px,#efeeec 10px,#efeeec 20px)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: c.muted2, fontSize: 12 }}>
              Vista previa del PDF de la factura
            </div>
          </div>
        )}

        <div style={{ ...s.panel, padding: 16 }}>
          <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 12 }}>Datos de la factura</div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Field label="CUIT emisor"><input style={s.input} value={form.cuit ?? ''} onChange={(e) => set('cuit', e.target.value)} disabled={!meta.canEdit} /></Field>
            <Field label="Razón social"><input style={s.input} value={form.razonSocial ?? ''} onChange={(e) => set('razonSocial', e.target.value)} disabled={!meta.canEdit} /></Field>
            <Field label="Número de comprobante"><input style={s.input} value={form.comprobante ?? ''} onChange={(e) => set('comprobante', e.target.value)} disabled={!meta.canEdit} /></Field>
            <Field label="Importe"><input style={s.input} value={form.importe ?? ''} onChange={(e) => set('importe', e.target.value)} disabled={!meta.canEdit} /></Field>
            <Field label="Período"><input type="month" style={s.input} value={form.periodo ?? ''} onChange={(e) => set('periodo', e.target.value)} disabled={!meta.canEdit} /></Field>
            <Field label="Fecha de emisión"><input type="date" style={s.input} value={form.fechaEmision ?? ''} onChange={(e) => set('fechaEmision', e.target.value)} disabled={!meta.canEdit} /></Field>
            <Field label="Contrato asignado"><input style={{ ...s.input, background: c.fieldBg }} value={display.contrato ?? '(sin asignar)'} disabled /></Field>
            <Field label="Estado"><input style={{ ...s.input, background: c.fieldBg }} value={estado} disabled /></Field>
            <Field label="Observaciones" span2><textarea style={{ ...s.input, minHeight: 60, resize: 'vertical' }} value={form.observaciones ?? ''} onChange={(e) => set('observaciones', e.target.value)} disabled={!meta.canEdit} /></Field>
          </div>

          {meta.canEdit ? (
            <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
              <button style={s.btnPrimary} disabled={saving} onClick={save}>{saving ? 'Guardando…' : 'Guardar cambios'}</button>
              <button style={s.btn} onClick={() => navigate({ screen: origin } as Route)}>Cancelar</button>
            </div>
          ) : (
            <div style={{ fontSize: 12, color: c.muted2, marginTop: 12 }}>Modo solo lectura — el rol Auditor no puede editar esta factura.</div>
          )}
        </div>
      </div>
    </div>
  );
}

function Field({ label, children, span2 }: { label: string; children: ReactNode; span2?: boolean }) {
  return (
    <div style={{ gridColumn: span2 ? '1/-1' : undefined }}>
      <label style={s.label}>{label}</label>
      {children}
    </div>
  );
}
