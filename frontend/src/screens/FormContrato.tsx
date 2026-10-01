import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { api, qs } from '../api';
import { useApp } from '../context';
import { c, s } from '../theme';
import { Loading } from './Dashboard';

type Cat = any;

type LocadorForm = {
  uid: string;                // id interno de la tarjeta (no se envía al backend)
  numero: number;
  locadorId: string;          // id de un locador existente ('' si es nuevo)
  razonSocial: string;        // solo si es nuevo
  cuit: string;               // solo si es nuevo
  email: string;
  telefono: string;
  cbu: string;
  porcentaje: number;
  acreedorSapCodigo: string;
  cecoSap: string;
  divisionSap: string;
  cuentaGasto: string;
  indicadorImpuesto: string;
};

let locadorSeq = 0;

function nuevoLocador(numero: number, porcentaje: number): LocadorForm {
  return {
    uid: `loc-${++locadorSeq}`,
    numero,
    locadorId: '',
    razonSocial: '',
    cuit: '',
    email: '',
    telefono: '',
    cbu: '',
    porcentaje,
    acreedorSapCodigo: '',
    cecoSap: '',
    divisionSap: '',
    cuentaGasto: '',
    indicadorImpuesto: '',
  };
}

/** Campos del locador que vienen de la base y se bloquean cuando es existente. */
const CAMPOS_LOCADOR_VACIOS = {
  razonSocial: '',
  cuit: '',
  email: '',
  telefono: '',
  cbu: '',
  acreedorSapCodigo: '',
  cecoSap: '',
  divisionSap: '',
  cuentaGasto: '',
  indicadorImpuesto: '',
};

/** Convierte un locador del catálogo (o del detalle) en los campos del form. */
function datosDeLocador(l: any): Partial<LocadorForm> {
  const txt = (v: any) => (v == null ? '' : String(v).trim());
  return {
    razonSocial: txt(l?.razonSocial ?? l?.razon_social),
    cuit: txt(l?.cuit),
    email: txt(l?.email),
    telefono: txt(l?.telefono),
    cbu: txt(l?.cbu),
    acreedorSapCodigo: txt(l?.acreedorSap ?? l?.codigoSap ?? l?.codigo_sap),
    cecoSap: txt(l?.cecoSap ?? l?.ceco_sap),
    divisionSap: txt(l?.divisionSap ?? l?.division_sap),
    cuentaGasto: txt(l?.cuentaGasto ?? l?.cuenta_gasto),
    indicadorImpuesto: txt(l?.indicadorImpuesto ?? l?.indicador_impuesto),
  };
}

export function FormContrato({ id }: { id?: number }) {
  const { navigate, meta } = useApp();
  const editing = id != null;

  const [cat, setCat] = useState<Cat | null>(null);
  const [open, setOpen] = useState<string | null>('inmueble');
    const [form, setForm] = useState<Record<string, any>>({
    periodicidad: 'TRIMESTRAL',
    tipoContratoId: 1,
    tipoFacturacion: 'mensual',
    locadores: [nuevoLocador(1, 100)],
  });
  const [saving, setSaving] = useState(false);
  const [ready, setReady] = useState(!editing);
  const [error, setError] = useState('');
  const [modoInmueble, setModoInmueble] = useState<'nuevo' | 'existente'>('nuevo');
  const [busquedaInmueble, setBusquedaInmueble] = useState('');
  const [opcionesInmueble, setOpcionesInmueble] = useState<any[]>([]);

  const [modoIndice, setModoIndice] =
    useState<'nuevo' | 'existente'>('existente');

  const [busquedaIndice, setBusquedaIndice] = useState('');
  const [opcionesIndice, setOpcionesIndice] = useState<any[]>([]);

  function calcularCantidadMeses(
    fechaInicio?: string,
    fechaFin?: string
  ): number {
    if (!fechaInicio || !fechaFin) return 0;

    const inicio = new Date(`${fechaInicio}T00:00:00`);
    const fin = new Date(`${fechaFin}T00:00:00`);

    if (inicio > fin) return 0;

    return (
      (fin.getFullYear() - inicio.getFullYear()) * 12 +
      (fin.getMonth() - inicio.getMonth()) +
      1
    );
  }

  function generarPorcentajes(cantidad: number): number[] {
    if (cantidad <= 0) return [];

    const base = Math.floor(100 / cantidad);
    const resto = 100 % cantidad;

    return Array.from(
      { length: cantidad },
      (_, i) => base + (i < resto ? 1 : 0)
    );
  }

  useEffect(() => {
    api.get<Cat>('/catalogs').then(setCat);
  }, []);

  useEffect(() => {
    if (!editing) return;

    api.get(`/contracts/${id}`).then((d: any) => {
      const plan: any[] = d.facturas_planificadas ?? [];

      const locs: LocadorForm[] = (d.locadores ?? []).map((l: any, index: number) => {
        const fp = plan.find((p) => String(p.locadorId) === String(l.id));
        return {
          ...nuevoLocador(index + 1, Number(fp?.porcentaje ?? 0)),
          ...datosDeLocador(l),
          locadorId: String(l.id),
        };
      });

      const locadores = locs.length > 0 ? locs : [nuevoLocador(1, 100)];

      setForm({
        inmuebleId: d.inmuebleId,
        nis: d.nis,
        denominacion: d.denom,
        direccion: d.direccion,
        regionId: d.regionId,
        localidadId: d.localidadId,
        destinoId: d.destinoId,
        superficieCubierta: d.supCubierta ?? '',
        importeTotal: d.valorActual,
        fechaInicio: str(d.inicio),
        fechaVencimiento: str(d.vencimiento),
        tolerancia: d.tolerancia,
        indiceId: d.indiceId,
        periodicidad: d.periodicidad ?? 'TRIMESTRAL',
        deposito: d.deposito,
        tipoContratoId: d.tipoContratoId ?? 1,
        tipoFacturacion: d.tipoFacturacion ?? 'mensual',
        cantidad_facturas: locadores.length,
        locadores
      });

      setModoIndice('existente');
      elegirIndice(d.indiceId);

      setModoInmueble('existente');
      setBusquedaInmueble([d.nis, d.denom].filter(Boolean).join(' · '));

      setReady(true);
    });

  }, [editing, id]);

  useEffect(() => {
    if (modoIndice !== 'existente') return;

    const q = busquedaIndice.trim();

    if (!q) {
      setOpcionesIndice([]);
      return;
    }

    const t = window.setTimeout(() => {
      api.get<any>(
        '/indices' + qs({
          search: q,
          size: 8,
        })
      )
        .then((res) => {
          setOpcionesIndice(res.rows ?? []);
        })
        .catch(() => {
          setOpcionesIndice([]);
        });
    }, 250);

    return () => window.clearTimeout(t);
  }, [busquedaIndice, modoIndice]);

  useEffect(() => {
    if (modoInmueble !== 'existente') return;

    const q = busquedaInmueble.trim();

    if (!q) {
      setOpcionesInmueble([]);
      return;
    }

    const t = window.setTimeout(() => {
      api.get<any>(
        '/inmuebles' + qs({
          search: q,
          size: 8,
        })
      )
        .then((res) => {
          setOpcionesInmueble(res.rows ?? []);
        })
        .catch(() => {
          setOpcionesInmueble([]);
        });
    }, 250);

    return () => window.clearTimeout(t);
  }, [busquedaInmueble, modoInmueble]);

  async function elegirIndice(indiceId: number) {
    const d = await api.get<any>(`/indices/${indiceId}`);

    setForm((f) => ({
      ...f,
      indiceId: d.id,
      indiceCodigo: d.codigo ?? '',
      indiceNombre: d.nombre ?? '',
      indiceFuente: d.fuente ?? '',
    }));

    setBusquedaIndice(`${d.codigo} — ${d.nombre}`);
    setOpcionesIndice([]);
  }

  async function elegirInmueble(inmuebleId: number) {
    const d = await api.get<any>(`/inmuebles/${inmuebleId}`);
    setForm((f) => ({
      ...f,
      inmuebleId: d.id,
      nis: d.nis ?? '',
      denominacion: d.denominacion ?? '',
      direccion: d.direccion ?? '',
      regionId: d.regionId ?? '',
      localidadId: d.localidadId ?? '',
      destinoId: d.destinoId ?? '',
      superficieCubierta: d.superficieCubierta ?? '',
    }));
    setBusquedaInmueble(`${d.nis} · ${d.denominacion}`);
    setOpcionesInmueble([]);
  }

  if (!meta.canEdit) {
    return (
      <div
        style={{
          ...s.panel,
          padding: 16,
          fontSize: 13,
          color: c.muted
        }}
      >
        El rol actual no tiene permisos para crear o editar contratos.
      </div>
    );
  }

  if (!ready || !cat) return <Loading />;

  const set = (k: string, v: any) => {
    setForm((f) => ({
      ...f,
      [k]: v,
    }));
  };

  const setLocador = (index: number, cambios: Partial<LocadorForm>) => {
    setForm((f) => {
      const locs: LocadorForm[] = [...(f.locadores ?? [])];
      locs[index] = { ...locs[index], ...cambios };
      return { ...f, locadores: locs };
    });
  };

  /** Renumera las tarjetas y reparte los porcentajes (1 → 100, 2 → 50/50, 3 → 34/33/33). */
  const repartir = (locs: LocadorForm[]): LocadorForm[] => {
    const pcts = generarPorcentajes(locs.length);
    return locs.map((l, i) => ({ ...l, numero: i + 1, porcentaje: pcts[i] }));
  };

  const agregarLocador = () => {
    setForm((f) => ({
      ...f,
      locadores: repartir([...(f.locadores ?? []), nuevoLocador(0, 0)]),
    }));
  };

  const quitarLocador = (index: number) => {
    setForm((f) => ({
      ...f,
      locadores: repartir((f.locadores ?? []).filter((_: LocadorForm, i: number) => i !== index)),
    }));
  };


async function save() {
  setSaving(true);

  try {
    const locadores: LocadorForm[] = form.locadores ?? [];
    const importeTotal = Number(form.importeTotal) || 0;

    if (locadores.length === 0) {
      setError('Cargá al menos un locador.');
      return;
    }

    const totalPorcentaje = locadores.reduce((t, l) => t + (Number(l.porcentaje) || 0), 0);
    if (totalPorcentaje !== 100) {
      setError('Los porcentajes de los locadores deben sumar 100%.');
      return;
    }

    const incompleto = locadores.find(
      (l) => !l.locadorId && (!l.razonSocial?.trim() || !l.cuit?.trim())
    );
    if (incompleto) {
      setError(`Locador ${incompleto.numero}: seleccioná uno existente o completá razón social y CUIT.`);
      return;
    }

    const ids = locadores.filter((l) => l.locadorId).map((l) => String(l.locadorId));
    if (new Set(ids).size !== ids.length) {
      setError('No se puede repetir el mismo locador en el contrato.');
      return;
    }

    if (modoInmueble === 'existente' && !form.inmuebleId) {
      setError('Seleccioná un inmueble existente.');
      return;
    }

    if (modoIndice === 'existente' && !form.indiceId) {
      setError('Seleccioná un índice de ajuste existente.');
      return;
    }

    if (modoIndice === 'nuevo' && (!form.indiceCodigo?.trim() || !form.indiceNombre?.trim())) {
      setError('Completá el código y el nombre del nuevo índice.');
      return;
    }

    setError('');

    const payload: Record<string, any> = { ...form };
    if (modoInmueble === 'nuevo') delete payload.inmuebleId;
    if (modoIndice === 'nuevo') delete payload.indiceId;
    payload.modoIndice = modoIndice;

    const tolerancia = String(form.tolerancia ?? '').trim();
    payload.tolerancia = tolerancia === '' ? 3 : tolerancia;

    // una factura planificada por locador
    payload.cantidad_facturas = locadores.length;

    // los datos SAP ahora viajan dentro de cada locador
    delete payload.acreedorSapCodigo;
    delete payload.cecoSap;
    delete payload.divisionSap;
    delete payload.cuentaGasto;
    delete payload.indicadorImpuesto;

    payload.locadores = locadores.map((l) => ({
      locadorId: l.locadorId ? Number(l.locadorId) : null,
      razonSocial: l.locadorId ? null : l.razonSocial.trim(),
      cuit: l.locadorId ? null : l.cuit.trim(),
      email: l.email?.trim() || null,
      telefono: l.telefono?.trim() || null,
      cbu: l.cbu?.trim() || null,
      porcentaje: Number(l.porcentaje),
      importe: Math.round(importeTotal * Number(l.porcentaje)) / 100,
      acreedorSapCodigo: l.acreedorSapCodigo?.trim() || null,
      cecoSap: l.cecoSap?.trim() || null,
      divisionSap: l.divisionSap?.trim() || null,
      cuentaGasto: l.cuentaGasto?.trim() || null,
      indicadorImpuesto: l.indicadorImpuesto?.trim() || null,
    }));

    payload.facturas_planificadas = payload.locadores.map((l: any, i: number) => ({
      numero: i + 1,
      locadorId: l.locadorId,
      porcentaje: l.porcentaje,
      importe: l.importe,
    }));

    console.log(payload);

    if (editing) {
      await api.put(`/contracts/${id}`, payload);
      navigate({ screen: 'detalle', id: id! });
    } else {
      const res = await api.post<{ id: number }>('/contracts', payload);
      navigate({ screen: 'detalle', id: res.id });
    }
  } catch (e: any) {
    console.error('Error al guardar contrato:', e);

    const backendMessage =
      e?.response?.data?.message ??
      e?.data?.message ??
      e?.message ??
      'Ocurrió un error al guardar el contrato.';

    setError(String(backendMessage));
  } finally {
    setSaving(false);
  }
}


  const inmuebleBloqueado = modoInmueble === 'existente';
  const indiceBloqueado = modoIndice === 'existente';
  const lockedInput = { background: c.fieldBg, cursor: 'not-allowed' as const };

  const sections: { key: string; titulo: string; body: ReactNode }[] = [
    {
      key: 'inmueble',
      titulo: 'Inmueble',
      body: (
        <>
          <div
            style={{
              gridColumn: '1 / -1',
              display: 'flex',
              gap: 8,
            }}
          >
            <button
              type="button"
              style={
                modoInmueble === 'existente'
                  ? s.btnPrimary
                  : s.btn
              }
              onClick={() => {
                setModoInmueble('existente');

                /*
                * Conservamos el inmueble actual mientras el usuario
                * todavía no haya elegido otro.
                */
                setBusquedaInmueble(
                  form.inmuebleId
                    ? [form.nis, form.denominacion]
                        .filter(Boolean)
                        .join(' · ')
                    : ''
                );

                setOpcionesInmueble([]);
              }}
            >
              Inmueble existente
            </button>

            <button
              type="button"
              style={
                modoInmueble === 'nuevo'
                  ? s.btnPrimary
                  : s.btn
              }
              onClick={() => {
                setModoInmueble('nuevo');

                setForm((f) => ({
                  ...f,
                  inmuebleId: undefined,
                  nis: '',
                  denominacion: '',
                  direccion: '',
                  regionId: '',
                  localidadId: '',
                  destinoId: '',
                  superficieCubierta: '',
                }));

                setBusquedaInmueble('');
                setOpcionesInmueble([]);
              }}
            >
              Cargar nuevo
            </button>
          </div>

          {modoInmueble === 'existente' && (
            <div style={{ gridColumn: '1 / -1', position: 'relative' }}>
              <label style={s.label}>Buscar inmueble</label>
              <input
                style={s.input}
                value={busquedaInmueble}
                onChange={(e) => setBusquedaInmueble(e.target.value)}
                placeholder="NIS, unidad de negocio o región"
              />
              {opcionesInmueble.length > 0 && (
                <div style={{ ...s.panel, marginTop: 6, overflow: 'hidden' }}>
                  {opcionesInmueble.map((op) => (
                    <div
                      key={op.id}
                      onClick={() => elegirInmueble(op.id)}
                      style={{ padding: '8px 12px', cursor: 'pointer', borderTop: `1px solid ${c.line}`, fontSize: 12.5 }}
                    >
                      <b>{op.nis}</b> · {op.denom}{op.region ? ` · ${op.region}` : ''}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          <F label="NIS">
            <input
              style={{ ...s.input, ...(inmuebleBloqueado ? lockedInput : {}) }}
              value={form.nis ?? ''}
              disabled={inmuebleBloqueado}
              onChange={(e) => set('nis', e.target.value)}
              placeholder="B0501"
            />
          </F>

          <F label="Unidad de negocio">
            <input
              style={{ ...s.input, ...(inmuebleBloqueado ? lockedInput : {}) }}
              value={form.denominacion ?? ''}
              disabled={inmuebleBloqueado}
              onChange={(e) => set('denominacion', e.target.value)}
              placeholder="Sucursal A"
            />
          </F>

          <F label="Región">
            <select
              style={{ ...s.input, ...(inmuebleBloqueado ? lockedInput : {}) }}
              value={form.regionId ?? ''}
              disabled={inmuebleBloqueado}
              onChange={(e) => set('regionId', e.target.value)}
            >
              <option value="">Seleccionar región</option>

              {cat.regiones.map((r: any) => (
                <option key={r.id} value={r.id}>
                  {r.nombre}
                </option>
              ))}
            </select>
          </F>

          <F label="Localidad">
            <select
              style={{ ...s.input, ...(inmuebleBloqueado ? lockedInput : {}) }}
              value={form.localidadId ?? ''}
              disabled={inmuebleBloqueado}
              onChange={(e) => set('localidadId', e.target.value)}
            >
              <option value="">Seleccionar localidad</option>

              {cat.localidades.map((l: any) => (
                <option key={l.id} value={l.id}>
                  {l.nombre}
                </option>
              ))}
            </select>
          </F>

          <F label="Destino / uso">
            <select
              style={{ ...s.input, ...(inmuebleBloqueado ? lockedInput : {}) }}
              value={form.destinoId ?? ''}
              disabled={inmuebleBloqueado}
              onChange={(e) => set('destinoId', e.target.value)}
            >
              <option value="">Seleccionar destino</option>

              {cat.destinos.map((x: any) => (
                <option key={x.id} value={x.id}>
                  {x.nombre}
                </option>
              ))}
            </select>
          </F>

          <F label="Dirección">
            <input
              style={{ ...s.input, ...(inmuebleBloqueado ? lockedInput : {}) }}
              value={form.direccion ?? ''}
              disabled={inmuebleBloqueado}
              onChange={(e) => set('direccion', e.target.value)}
              placeholder="Av. Ejemplo 1234"
            />
          </F>

          <F label="Superficie cubierta (m²)">
            <input
              style={{ ...s.input, ...(inmuebleBloqueado ? lockedInput : {}) }}
              value={form.superficieCubierta ?? ''}
              disabled={inmuebleBloqueado}
              onChange={(e) => set('superficieCubierta', e.target.value)}
              placeholder="120"
            />
          </F>
        </>
      ),
    },
        {
      key: 'economicas',
      titulo: 'Condiciones económicas',
      body: (
        <>
          <F label="Importe total">
            <input
              style={s.input}
              value={form.importeTotal ?? ''}
              onChange={(e) => set('importeTotal', e.target.value)}
              placeholder="$ 0"
            />
          </F>

          <F label="Tipo de contrato">
            <select
              style={{ ...s.input }}
              value={form.tipoContratoId ?? 1}
              onChange={(e) => set('tipoContratoId', e.target.value)}
            >
              {cat.tiposContrato.map((x: any) => (
                <option key={x.id} value={x.id}>
                  {x.nombre}
                </option>
              ))}
            </select>
          </F>

          <F label="Tolerancia de diferencia (%)">
            <input
              style={s.input}
              value={form.tolerancia ?? ''}
              onChange={(e) => set('tolerancia', e.target.value)}
              placeholder="3"
            />
          </F>

          <F label="Tipo de facturación">
            <select
              style={{ ...s.input }}
              value={form.tipoFacturacion ?? 'mensual'}
              onChange={(e) => set('tipoFacturacion', e.target.value)}
            >
              <option value="mensual">Mensual</option>
              <option value="personalizado">Personalizado</option>
            </select>
          </F>

          {form.tipoFacturacion === 'mensual' && (
            <>
              <F label="Fecha de inicio">
                <input
                  type="date"
                  style={s.input}
                  value={form.fechaInicio ?? ''}
                  onChange={(e) => set('fechaInicio', e.target.value)}
                />
              </F>

              <F label="Fecha de fin">
                <input
                  type="date"
                  style={s.input}
                  value={form.fechaVencimiento ?? ''}
                  onChange={(e) => set('fechaVencimiento', e.target.value)}
                />
              </F>
            </>
          )}
        </>
      ),
    },
        {
      key: 'locador',
      titulo: 'Locador',
      body: (
        <>
          {(form.locadores ?? []).map((loc: LocadorForm, index: number) => {
            const importe =
              (Number(form.importeTotal) || 0) *
              (Number(loc.porcentaje) || 0) /
              100;

            const existente = !!loc.locadorId;
            const unico = (form.locadores ?? []).length <= 1;
            const estilo = { ...s.input, ...(existente ? lockedInput : {}) };

            return (
              <div
                key={loc.uid}
                style={{
                  gridColumn: '1 / -1',
                  border: `1px solid ${c.line}`,
                  borderRadius: 4,
                  padding: 12,
                  display: 'grid',
                  gridTemplateColumns: '1fr 1fr',
                  gap: 12,
                }}
              >
                <div
                  style={{
                    gridColumn: '1 / -1',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}
                >
                  <span style={{ fontWeight: 700, fontSize: 12.5 }}>
                    Locador {loc.numero}
                    {existente && (
                      <span style={{ fontWeight: 400, color: c.muted, marginLeft: 8 }}>
                        · existente (solo lectura)
                      </span>
                    )}
                  </span>

                  <button
                    type="button"
                    style={{
                      ...s.btn,
                      padding: '4px 10px',
                      fontSize: 12,
                      opacity: unico ? 0.5 : 1,
                      cursor: unico ? 'not-allowed' : 'pointer',
                    }}
                    disabled={unico}
                    title={unico ? 'El contrato debe tener al menos un locador' : 'Quitar este locador'}
                    onClick={() => quitarLocador(index)}
                  >
                    ✕ Cancelar
                  </button>
                </div>

                <F label="Locador existente" span={2}>
                  <select
                    style={{ ...s.input }}
                    value={loc.locadorId ?? ''}
                    onChange={(e) => {
                      const value = e.target.value;

                      // sin selección: se vacía y se habilita para cargar uno nuevo
                      if (!value) {
                        setLocador(index, { locadorId: '', ...CAMPOS_LOCADOR_VACIOS });
                        return;
                      }

                      const cl = cat.locadores.find((x: any) => String(x.id) === value);
                      setLocador(index, {
                        ...CAMPOS_LOCADOR_VACIOS,
                        ...datosDeLocador(cl),
                        locadorId: value,
                      });
                    }}
                  >
                    <option value="">Seleccionar locador existente</option>

                    {cat.locadores.map((l: any) => {
                      const usadoEnOtro = (form.locadores ?? []).some(
                        (o: LocadorForm, j: number) =>
                          j !== index && String(o.locadorId) === String(l.id)
                      );

                      return (
                        <option key={l.id} value={l.id} disabled={usadoEnOtro}>
                          {l.razonSocial} · {String(l.cuit ?? '').trim()}
                        </option>
                      );
                    })}
                  </select>
                </F>

                <F label="Razón social">
                  <input
                    style={estilo}
                    value={loc.razonSocial ?? ''}
                    disabled={existente}
                    onChange={(e) => setLocador(index, { razonSocial: e.target.value })}
                    placeholder="Razón social del locador nuevo"
                  />
                </F>

                <F label="CUIT">
                  <input
                    style={estilo}
                    value={loc.cuit ?? ''}
                    disabled={existente}
                    onChange={(e) => setLocador(index, { cuit: e.target.value })}
                    placeholder="30-XXXXXXXX-X"
                  />
                </F>

                <F label="Contacto (mail)">
                  <input
                    type="email"
                    style={estilo}
                    value={loc.email ?? ''}
                    disabled={existente}
                    onChange={(e) => setLocador(index, { email: e.target.value })}
                    placeholder="contacto@empresa.com"
                  />
                </F>

                <F label="Teléfono contacto">
                  <input
                    style={estilo}
                    value={loc.telefono ?? ''}
                    disabled={existente}
                    onChange={(e) => setLocador(index, { telefono: e.target.value })}
                    placeholder="011 4555-2310"
                  />
                </F>

                <F label="CBU" span={2}>
                  <input
                    style={estilo}
                    value={loc.cbu ?? ''}
                    maxLength={22}
                    disabled={existente}
                    onChange={(e) =>
                      setLocador(index, { cbu: e.target.value.replace(/\D/g, '') })
                    }
                    placeholder="22 dígitos"
                  />
                </F>

                <F label="Acreedor SAP">
                  <input
                    style={estilo}
                    value={loc.acreedorSapCodigo ?? ''}
                    disabled={existente}
                    onChange={(e) => setLocador(index, { acreedorSapCodigo: e.target.value })}
                    placeholder="A00822"
                  />
                </F>

                <F label="CeCo SAP">
                  <input
                    style={estilo}
                    value={loc.cecoSap ?? ''}
                    disabled={existente}
                    onChange={(e) => setLocador(index, { cecoSap: e.target.value })}
                    placeholder="53025952"
                  />
                </F>

                <F label="División SAP">
                  <input
                    style={estilo}
                    value={loc.divisionSap ?? ''}
                    disabled={existente}
                    onChange={(e) => setLocador(index, { divisionSap: e.target.value })}
                    placeholder="4952"
                  />
                </F>

                <F label="Cuenta de gasto">
                  <input
                    style={estilo}
                    value={loc.cuentaGasto ?? ''}
                    disabled={existente}
                    onChange={(e) => setLocador(index, { cuentaGasto: e.target.value })}
                    placeholder="510802"
                  />
                </F>

                <F label="Indicador de impuestos">
                  <input
                    style={estilo}
                    value={loc.indicadorImpuesto ?? ''}
                    disabled={existente}
                    onChange={(e) => setLocador(index, { indicadorImpuesto: e.target.value })}
                    placeholder="C1"
                  />
                </F>

                <F label="Porcentaje de factura">
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <div style={{ display: 'flex', alignItems: 'center', flex: 1 }}>
                      <input
                        type="number"
                        min="0"
                        max="100"
                        step="1"
                        style={{ ...s.input, flex: 1 }}
                        value={loc.porcentaje}
                        onChange={(e) =>
                          setLocador(index, {
                            porcentaje: Number.parseInt(e.target.value || '0', 10),
                          })
                        }
                      />
                      <span style={{ marginLeft: 6, fontSize: 13, color: c.muted }}>%</span>
                    </div>

                    <span style={{ fontSize: 13 }}>${importe.toFixed(2)}</span>
                  </div>
                </F>
              </div>
            );
          })}
          
          <div style={{ gridColumn: '1 / -1', display: 'flex', justifyContent: 'center' }}>
            <button
              type="button"
              style={{
                ...s.btn,
                width: '10%',
                borderStyle: 'dashed',
                fontSize: 18,
                fontWeight: 700,
                padding: '6px 0',
                color:'white',
                backgroundColor: "#008CBA"
              }}
              title="Agregar locador"
              onClick={agregarLocador}
            >
              +
            </button>
          </div>
        </>
      ),
    },
    {
      key: 'ajustes',
      titulo: 'Ajustes e índice',
      body: (
        <>
          <div
            style={{
              gridColumn: '1 / -1',
              display: 'flex',
              gap: 8,
            }}
          >
            <button
              type="button"
              style={
                modoIndice === 'existente'
                  ? s.btnPrimary
                  : s.btn
              }
              onClick={() => {
                setModoIndice('existente');

                setBusquedaIndice(
                  form.indiceId
                    ? [form.indiceCodigo, form.indiceNombre]
                        .filter(Boolean)
                        .join(' — ')
                    : ''
                );

                setOpcionesIndice([]);
              }}
            >
              Índice existente
            </button>

            <button
              type="button"
              style={
                modoIndice === 'nuevo'
                  ? s.btnPrimary
                  : s.btn
              }
              onClick={() => {
                setModoIndice('nuevo');

                setForm((f) => ({
                  ...f,
                  indiceId: undefined,
                  indiceCodigo: '',
                  indiceNombre: '',
                  indiceFuente: '',
                }));

                setBusquedaIndice('');
                setOpcionesIndice([]);
              }}
            >
              Cargar nuevo
            </button>
          </div>

          {modoIndice === 'existente' && (
            <div
              style={{
                gridColumn: '1 / -1',
                position: 'relative',
              }}
            >
              <label style={s.label}>Buscar índice</label>

              <input
                style={s.input}
                value={busquedaIndice}
                onChange={(e) =>
                  setBusquedaIndice(e.target.value)
                }
                placeholder="Código, nombre o fuente"
              />

              {opcionesIndice.length > 0 && (
                <div
                  style={{
                    ...s.panel,
                    marginTop: 6,
                    overflow: 'hidden',
                  }}
                >
                  {opcionesIndice.map((op) => (
                    <div
                      key={op.id}
                      onClick={() => elegirIndice(op.id)}
                      style={{
                        padding: '8px 12px',
                        cursor: 'pointer',
                        borderTop: `1px solid ${c.line}`,
                        fontSize: 12.5,
                      }}
                    >
                      <b>{op.codigo}</b> — {op.nombre}
                      {op.fuente ? ` · ${op.fuente}` : ''}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          <F label="Código del índice">
            <input
              style={{
                ...s.input,
                ...(indiceBloqueado ? lockedInput : {}),
              }}
              value={form.indiceCodigo ?? ''}
              disabled={indiceBloqueado}
              onChange={(e) =>
                set('indiceCodigo', e.target.value)
              }
              placeholder="ICL"
            />
          </F>

          <F label="Nombre del índice">
            <input
              style={{
                ...s.input,
                ...(indiceBloqueado ? lockedInput : {}),
              }}
              value={form.indiceNombre ?? ''}
              disabled={indiceBloqueado}
              onChange={(e) =>
                set('indiceNombre', e.target.value)
              }
              placeholder="Índice para Contratos de Locación"
            />
          </F>

          <F label="Fuente" span={2}>
            <input
              style={{
                ...s.input,
                ...(indiceBloqueado ? lockedInput : {}),
              }}
              value={form.indiceFuente ?? ''}
              disabled={indiceBloqueado}
              onChange={(e) =>
                set('indiceFuente', e.target.value)
              }
              placeholder="BCRA"
            />
          </F>

          <F label="Frecuencia">
            <select
              style={{ ...s.input }}
              value={form.periodicidad ?? 'TRIMESTRAL'}
              onChange={(e) =>
                set('periodicidad', e.target.value)
              }
            >
              <option value="TRIMESTRAL">Trimestral</option>
              <option value="CUATRIMESTRAL">Cuatrimestral</option>
              <option value="SEMESTRAL">Semestral</option>
            </select>
          </F>
        </>
      ),
    },
    {
      key: 'garantias',
      titulo: 'Garantías',
      body: (
        <>
          <F label="Depósito de garantía">
            <input
              style={s.input}
              value={form.deposito ?? ''}
              onChange={(e) =>
                set('deposito', e.target.value)
              }
              placeholder="$ 0"
            />
          </F>

          <F label="Tipo de moneda">
            <select
              style={s.input}
              value={form.monedaDeposito ?? 'ARS'}
              onChange={(e) =>
                set('monedaDeposito', e.target.value)
              }
            >
              <option value="ARS">ARS</option>
              <option value="USD">USD</option>
            </select>
          </F>
        </>
      ),
    },

  ];

  return (
    <div style={{ maxWidth: 900 }}>
      <h1 style={{ ...s.h1, marginBottom: 16 }}>
        {editing ? 'Editar contrato' : 'Nuevo contrato'}
      </h1>

      {!editing && (
        <div
          style={{
            background: c.warnBg,
            border: `1px solid ${c.warnBorder}`,
            borderRadius: 4,
            padding: '10px 14px',
            fontSize: 12,
            marginBottom: 18,
            display: 'flex',
            gap: 8
          }}
        >
          <span style={{ fontWeight: 700 }}>⚠</span>

          <div>
            Verificá que no exista un contrato vigente para el mismo inmueble
            en el período seleccionado antes de continuar.
          </div>
        </div>
      )}

      {sections.map((sec) => {
        const isOpen = open === sec.key;

        return (
          <div
            key={sec.key}
            style={{
              ...s.panel,
              marginBottom: 10,
              overflow: 'hidden'
            }}
          >
            <div
              onClick={() => setOpen(isOpen ? null : sec.key)}
              style={{
                padding: '14px 16px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                cursor: 'pointer',
                background: c.softBg
              }}
            >
              <div style={{ fontWeight: 700, fontSize: 13.5 }}>
                {sec.titulo}
              </div>

              <span
                style={{
                  width: 8,
                  height: 8,
                  borderRight: '2px solid #6b6a67',
                  borderBottom: '2px solid #6b6a67',
                  transform: `rotate(${isOpen ? 225 : 45}deg)`,
                  marginTop: isOpen ? 4 : -4
                }}
              />
            </div>

            {isOpen && (
              <div
                style={{
                  padding: 16,
                  display: 'grid',
                  gridTemplateColumns: '1fr 1fr',
                  gap: 14,
                  borderTop: `1px solid ${c.line}`
                }}
              >
                {sec.body}
              </div>
            )}
          </div>
        );
      })}

      <div
        style={{
          position: 'sticky',
          bottom: 0,
          background: c.bg,
          padding: '14px 0',
          display: 'flex',
          justifyContent: 'flex-end',
          gap: 8
        }}
      >
        <button
          style={s.btn}
          onClick={() =>
            navigate(
              editing
                ? { screen: 'detalle', id: id! }
                : { screen: 'listado' }
            )
          }
        >
          Cancelar
        </button>

        <button
          style={s.btnPrimary}
          disabled={saving}
          onClick={save}
        >
          {saving ? 'Guardando…' : 'Guardar contrato'}
        </button>
      </div>

      {/* Modal de error */}
      {error && (
        <div
          onClick={() => setError('')}
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0, 0, 0, 0.45)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 9999,
            padding: 20
          }}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              width: '100%',
              maxWidth: 460,
              background: c.bg,
              borderRadius: 8,
              boxShadow: '0 8px 30px rgba(0,0,0,0.25)',
              overflow: 'hidden'
            }}
          >
            <div
              style={{
                padding: '14px 18px',
                borderBottom: `1px solid ${c.line}`,
                fontWeight: 700,
                fontSize: 15
              }}
            >
              Error al guardar
            </div>

            <div
              style={{
                padding: '18px',
                fontSize: 13.5,
                lineHeight: 1.5,
                color: c.text
              }}
            >
              {error}
            </div>

            <div
              style={{
                padding: '12px 18px',
                borderTop: `1px solid ${c.line}`,
                display: 'flex',
                justifyContent: 'flex-end'
              }}
            >
              <button
                style={s.btnPrimary}
                onClick={() => setError('')}
              >
                Aceptar
              </button>
            </div>
          </div>
        </div>
      )}
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