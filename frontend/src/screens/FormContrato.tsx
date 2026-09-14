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
  const [form, setForm] = useState<Record<string, any>>({
    periodicidad: 'TRIMESTRAL',
    tipoContratoId: 1
  });
  const [saving, setSaving] = useState(false);
  const [ready, setReady] = useState(!editing);
  const [error, setError] = useState('');

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
    if (editing) {
      api.get(`/contracts/${id}`).then((d: any) => {
        setForm({
          nis: d.nis,
          denominacion: d.denom,
          direccion: d.direccion,
          importeTotal: d.valorActual,
          fechaInicio: str(d.inicio),
          fechaVencimiento: str(d.vencimiento),
          tolerancia: d.tolerancia,
          indiceId: undefined,
          periodicidad: d.periodicidad ?? 'TRIMESTRAL',
          deposito: d.deposito,
          tipoContratoId: 1,
        });

        setReady(true);
      });
    }
  }, [id]);

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

    const facturas = form.facturas ?? [];

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

    setError('');

    if (editing) {
      console.log("viene a put")
      await api.put(`/contracts/${id}`, form);
      navigate({ screen: 'detalle', id: id! });
    } else {
      const res = await api.post<{ id: number }>(
        '/contracts',
        form
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


  const sections: { key: string; titulo: string; body: ReactNode }[] = [
    {
      key: 'inmueble',
      titulo: 'Inmueble',
      body: (
        <>
          <F label="NIS">
            <input
              style={s.input}
              value={form.nis ?? ''}
              onChange={(e) => set('nis', e.target.value)}
              placeholder="B0501"
            />
          </F>

          <F label="Unidad de negocio">
            <input
              style={s.input}
              value={form.denominacion ?? ''}
              onChange={(e) => set('denominacion', e.target.value)}
              placeholder="Sucursal A"
            />
          </F>

          <F label="Región">
            <select
              style={{ ...s.input }}
              value={form.regionId ?? ''}
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
              style={{ ...s.input }}
              value={form.localidadId ?? ''}
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
              style={{ ...s.input }}
              value={form.destinoId ?? ''}
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
              style={s.input}
              value={form.direccion ?? ''}
              onChange={(e) => set('direccion', e.target.value)}
              placeholder="Av. Ejemplo 1234"
            />
          </F>

          <F label="Superficie cubierta (m²)">
            <input
              style={s.input}
              value={form.superficieCubierta ?? ''}
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
                    'facturas',
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

                  set('facturas', [
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
                      'facturas',
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
                      'facturas',
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
                    'facturas',
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

          {(form.facturas ?? []).map((factura: any, index: number) => {
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
                        const facturas = [...form.facturas];

                        facturas[index] = {
                          ...facturas[index],
                          porcentaje: Number.parseInt(
                            e.target.value || '0',
                            10
                          ),
                        };

                        set('facturas', facturas);
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
          <F label="Índice de ajuste">
            <select
              style={{ ...s.input }}
              value={form.indiceId ?? ''}
              onChange={(e) => set('indiceId', e.target.value)}
            >
              <option value="">Seleccionar índice</option>

              {cat.indices.map((x: any) => (
                <option key={x.id} value={x.id}>
                  {x.codigo} — {x.nombre}
                </option>
              ))}
            </select>
          </F>

          <F label="Frecuencia">
            <select
              style={{ ...s.input }}
              value={form.periodicidad ?? 'TRIMESTRAL'}
              onChange={(e) => set('periodicidad', e.target.value)}
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
              onChange={(e) => set('deposito', e.target.value)}
              placeholder="$ 0"
            />
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
