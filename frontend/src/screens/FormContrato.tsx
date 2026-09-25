import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { api, qs } from '../api';
import { useApp } from '../context';
import { c, s } from '../theme';
import { Loading } from './Dashboard';

type Cat = any;

export function FormContrato({ id }: { id?: number }) {
  const { navigate, meta } = useApp();
  const editing = id != null;

  const [cat, setCat] = useState<Cat | null>(null);
  const [open, setOpen] = useState<string | null>('inmueble');
  const [form, setForm] = useState<Record<string, any>>({
    periodicidad: 'TRIMESTRAL',
    tipoContratoId: 1
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
      console.log(d)
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
        cantidad_facturas: d.cantidadFacturas ?? 1,
        facturas_planificadas: d.facturas_planificadas ?? [],
        locadorId: d.locadorId,
        acreedorSapCodigo: d.acreedorSap ?? '',
        cecoSap: d.cecoSap ?? '',
        divisionSap: d.divisionSap ?? '',
        cuentaGasto: d.cuentaGasto ?? '',
        indicadorImpuesto: d.indicadorImpuesto ?? '',
      });

      console.log("COMO VIENE EL CONTRATO")
      console.log(d)

      setModoIndice('existente');

      const indiceActual = (cat?.indices ?? []).find(
        (x: any) => String(x.id) === String(d.indiceId)
      );

      elegirIndice(d.indiceId)

      setModoInmueble('existente');
      setBusquedaInmueble(
        [d.nis, d.denom].filter(Boolean).join(' · ')
      );

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

async function save() {
  setSaving(true);

  try {
    console.log(form);

    const facturas = form.facturas_planificadas ?? [];

    const totalPorcentaje = facturas.reduce(
      (total: number, factura: { porcentaje: any }) =>
        total + Number(factura.porcentaje),
      0
    );

    if (totalPorcentaje !== 100) {
      setError('Los porcentajes de las facturas deben sumar 100%.');
      setSaving(false);
      return;
    }

    if (modoInmueble === 'existente' && !form.inmuebleId) {
      setError('Seleccioná un inmueble existente.');
      setSaving(false);
      return;
    }

    if (modoIndice === 'existente' && !form.indiceId) {
      setError('Seleccioná un índice de ajuste existente.');
      setSaving(false);
      return;
    }

    if (
      modoIndice === 'nuevo' &&
      (!form.indiceCodigo?.trim() || !form.indiceNombre?.trim())
    ) {
      setError(
        'Completá el código y el nombre del nuevo índice.'
      );
      setSaving(false);
      return;
    }

    setError('');

    const payload = { ...form };
    if (modoInmueble === 'nuevo') delete payload.inmuebleId;
    if (modoIndice === 'nuevo') delete payload.indiceId;
    console.log("=====================================")
    payload.modoIndice = modoIndice;
    console.log(payload)

    if (editing) {
      console.log("viene a put")
      await api.put(`/contracts/${id}`, payload);
      navigate({ screen: 'detalle', id: id! });
    } else {
      const res = await api.post<{ id: number }>(
        '/contracts',
        payload
      );

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
      key: 'locador',
      titulo: 'Locador',
      body: (
        <>
          <F label="Locador" span={2}>
            <select
              style={{ ...s.input }}
              value={form.locadorId ?? ''}
              onChange={(e) => set('locadorId', e.target.value)}
            >
              <option value="">Seleccionar locador existente</option>

              {cat.locadores.map((l: any) => (
                <option key={l.id} value={l.id}>
                  {l.razonSocial} · {l.cuit}
                </option>
              ))}
            </select>
          </F>

          <F label="Razón social (nuevo)">
            <input
              style={s.input}
              value={form.razonSocial ?? ''}
              onChange={(e) => set('razonSocial', e.target.value)}
              placeholder="Sólo si es un locador nuevo"
            />
          </F>

          <F label="CUIT (nuevo)">
            <input
              style={s.input}
              value={form.cuit ?? ''}
              onChange={(e) => set('cuit', e.target.value)}
              placeholder="30-XXXXXXXX-X"
            />
          </F>

          <F label="Acreedor SAP">
            <input
              style={s.input}
              value={form.acreedorSapCodigo ?? ''}
              onChange={(e) => set('acreedorSapCodigo', e.target.value)}
              placeholder="A00822"
            />
          </F>

          <F label="CeCo SAP">
            <input
              style={s.input}
              value={form.cecoSap ?? ''}
              onChange={(e) => set('cecoSap', e.target.value)}
              placeholder="53025952"
            />
          </F>

          <F label="División SAP">
            <input
              style={s.input}
              value={form.divisionSap ?? ''}
              onChange={(e) => set('divisionSap', e.target.value)}
              placeholder="4952"
            />
          </F>

          <F label="Cuenta de gasto">
            <input
              style={s.input}
              value={form.cuentaGasto ?? ''}
              onChange={(e) => set('cuentaGasto', e.target.value)}
              placeholder="510802"
            />
          </F>

          <F label="Indicador de impuestos">
            <input
              style={s.input}
              value={form.indicadorImpuesto ?? ''}
              onChange={(e) => set('indicadorImpuesto', e.target.value)}
              placeholder="C1"
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
              onChange={(e) => {
                const tipo = e.target.value;

                set('tipoFacturacion', tipo);

                if (tipo === 'mensual') {
                  const cantidad = calcularCantidadMeses(
                    form.fechaInicio,
                    form.fechaVencimiento
                  );

                  set('cantidad_facturas', cantidad);

                  set(
                    'facturas_planificadas',
                    generarPorcentajes(cantidad).map(
                      (porcentaje, index) => ({
                        numero: index + 1,
                        porcentaje,
                        importe:
                          (Number(form.importeTotal) || 0) *
                          (Number(porcentaje) || 0) /
                          100
                      })
                    )
                  );
                } else {
                  set('cantidad_facturas', 1);

                  set('facturas_planificadas', [
                    {
                      numero: 1,
                      porcentaje: 100,
                      importe: Number(form.importeTotal)
                    },
                  ]);
                }
              }}
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
                  onChange={(e) => {
                    const fechaInicio = e.target.value;

                    set('fechaInicio', fechaInicio);

                    const cantidad = calcularCantidadMeses(
                      fechaInicio,
                      form.fechaVencimiento
                    );

                    set('cantidad_facturas', cantidad);

                    set(
                      'facturas_planificadas',
                      generarPorcentajes(cantidad).map(
                        (porcentaje, index) => ({
                          numero: index + 1,
                          porcentaje,
                          importe:
                            (Number(form.importeTotal) || 0) *
                            (Number(porcentaje) || 0) /
                            100
                        })
                      )
                    );
                  }}
                />
              </F>

              <F label="Fecha de fin">
                <input
                  type="date"
                  style={s.input}
                  value={form.fechaVencimiento ?? ''}
                  onChange={(e) => {
                    const fechaFin = e.target.value;

                    set('fechaVencimiento', fechaFin);

                    const cantidad = calcularCantidadMeses(
                      form.fechaInicio,
                      fechaFin
                    );

                    set('cantidad_facturas', cantidad);

                    set(
                      'facturas_planificadas',
                      generarPorcentajes(cantidad).map(
                        (porcentaje, index) => ({
                          numero: index + 1,
                          porcentaje,
                          importe:
                            (Number(form.importeTotal) || 0) *
                            (Number(porcentaje) || 0) /
                            100
                        })
                      )
                    );
                  }}
                />
              </F>
            </>
          )}

          {form.tipoFacturacion === 'personalizado' && (
            <F label="Cantidad de facturas">
              <input
                type="number"
                style={s.input}
                value={form.cantidad_facturas ?? 0}
                onChange={(e) => {
                  const cantidad = Math.max(
                    1,
                    Number.parseInt(e.target.value || '1', 10)
                  );

                  set('cantidad_facturas', cantidad);

                  set(
                    'facturas_planificadas',
                    generarPorcentajes(cantidad).map(
                      (porcentaje, index) => ({
                        numero: index + 1,
                        porcentaje,
                        importe:
                          (Number(form.importeTotal) || 0) *
                          (Number(porcentaje) || 0) /
                          100
                      })
                    )
                  );
                }}
              />
            </F>
          )}

          {(form.facturas_planificadas ?? []).map((factura: any, index: number) => {
            const importe =
              (Number(form.importeTotal) || 0) *
              (Number(factura.porcentaje) || 0) /
              100;

            return (
              <F
                key={factura.numero}
                label={`Factura ${factura.numero}`}
                span={2}
              >
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10
                  }}
                >
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      flex: 1
                    }}
                  >
                    <input
                      type="number"
                      min="0"
                      max="100"
                      step="1"
                      style={{
                        ...s.input,
                        flex: 1
                      }}
                      value={factura.porcentaje}
                      onChange={(e) => {
                        const facturas = [...form.facturas_planificadas];

                        facturas[index] = {
                          ...facturas[index],
                          porcentaje: Number.parseInt(
                            e.target.value || '0',
                            10
                          ),
                        };

                        set('facturas_planificadas', facturas);
                      }}
                    />

                    <span
                      style={{
                        marginLeft: 6,
                        fontSize: 13,
                        color: c.muted
                      }}
                    >
                      %
                    </span>
                  </div>

                  <span style={{ fontSize: 13 }}>
                    ${importe.toFixed(2)}
                  </span>
                </div>
              </F>
            );
          })}
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
