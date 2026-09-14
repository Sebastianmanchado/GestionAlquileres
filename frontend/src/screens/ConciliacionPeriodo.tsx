import { useState } from 'react';
import { api, qs } from '../api';
import { useApp } from '../context';
import { c, s } from '../theme';
import { Badge } from '../components/Badge';
import { money, periodo as fmtPeriodo, estadoConciliacion } from '../format';
import { useAsync } from '../hooks';
import { Loading, ErrorBox } from './Dashboard';

const GRID = '40px 100px 1fr 150px 150px 100px 130px 160px 170px';

export function ConciliacionPeriodo() {
  const { navigate, meta, role } = useApp();

  const [periodoSel, setPeriodoSel] = useState<string>('');
  const [estadoFiltro, setEstadoFiltro] = useState<string | null>(null);
  const [running, setRunning] = useState(false);

  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [showSendModal, setShowSendModal] = useState(false);
  const [showSuccessModal, setShowSuccessModal] = useState(false);

  const { data, loading, error, reload } = useAsync<any>(
    () =>
      api.get(
        '/reconciliations' +
          qs({ periodo: periodoSel })
      ),
    [periodoSel, role]
  );

  async function run() {
    setRunning(true);

    try {
      await api.post(
        '/reconciliations/run' +
          qs({
            periodo:
              periodoSel ||
              (data?.periodo ?? '')
          })
      );

      setSelectedIds([]);
      reload();

    } catch (e: any) {
      alert(
        'No se pudo ejecutar: ' +
          (e.message ?? e)
      );
    } finally {
      setRunning(false);
    }
  }

  function toggleSelected(id: number) {
    setSelectedIds((current) =>
      current.includes(id)
        ? current.filter((x) => x !== id)
        : [...current, id]
    );
  }

  function abrirModalEnvio() {
    if (selectedIds.length === 0) {
      return;
    }

    setShowSendModal(true);
  }

  if (loading && !data) return <Loading />;
  if (error || !data) return <ErrorBox msg={error} />;

  const rows = estadoFiltro
    ? data.rows.filter(
        (r: any) =>
          r.estadoCodigo === estadoFiltro
      )
    : data.rows;

  const selectedRows = rows.filter(
    (r: any) =>
      selectedIds.includes(Number(r.id))
  );

  return (
    <div>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          marginBottom: 14
        }}
      >
        <h1
          style={{
            ...s.h1,
            flex: 1
          }}
        >
          Conciliación · Vista por período
        </h1>

        <select
          style={{
            ...s.select,
            padding: '7px 12px',
            fontSize: 12.5
          }}
          value={
            periodoSel ||
            String(data.periodo).substring(0, 10)
          }
          onChange={(e) => {
            setPeriodoSel(e.target.value);
            setSelectedIds([]);
          }}
        >
          {data.periodos.map((p: any) => (
            <option
              key={String(p.periodo)}
              value={String(p.periodo).substring(0, 10)}
            >
              {fmtPeriodo(p.periodo)}
            </option>
          ))}
        </select>

        {meta.canEdit && (
          <>
            <button
              style={s.btn}
              disabled={
                selectedIds.length === 0
              }
              onClick={abrirModalEnvio}
            >
              Enviar seleccionados
            </button>

            <button
              style={s.btnPrimary}
              disabled={running}
              onClick={run}
            >
              {running
                ? 'Ejecutando…'
                : 'Ejecutar conciliación del período'}
            </button>
          </>
        )}
      </div>

      <div
        style={{
          display: 'flex',
          gap: 8,
          marginBottom: 12
        }}
      >
        {data.filtros.map((f: any) => {
          const info = estadoConciliacion(
            f.codigo
          );

          const active =
            estadoFiltro === f.codigo;

          return (
            <div
              key={f.codigo}
              onClick={() =>
                setEstadoFiltro(
                  active ? null : f.codigo
                )
              }
              style={{
                padding: '6px 12px',
                border: `1px solid ${
                  active
                    ? c.primary
                    : c.border
                }`,
                borderRadius: 14,
                fontSize: 12,
                background: active
                  ? '#eef2f8'
                  : '#fff',
                cursor: 'pointer',
                display: 'inline-flex',
                gap: 6,
                alignItems: 'center'
              }}
            >
              <span
                style={{
                  width: 6,
                  height: 6,
                  borderRadius: '50%',
                  background: badge(
                    info.tone
                  )
                }}
              />

              {info.label}

              <span
                style={{
                  color: c.muted2
                }}
              >
                ({f.count})
              </span>
            </div>
          );
        })}
      </div>

      <div
        style={{
          ...s.panel,
          overflow: 'hidden'
        }}
      >
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: GRID,
            background: c.headerBg
          }}
        >
          <div style={s.th}></div>
          <div style={s.th}>NIS</div>
          <div style={s.th}>Contrato</div>
          <div style={s.th}>Importe esperado</div>
          <div style={s.th}>Importe facturado</div>
          <div style={s.th}>Facturas</div>
          <div style={s.th}>Diferencia</div>
          <div style={s.th}>
            Comprobante asignado
          </div>
          <div style={s.th}>Estado</div>
        </div>

        {rows.map((r: any) => {
          const info = estadoConciliacion(
            r.estadoCodigo
          );

          const puedeDiff =
            r.estadoCodigo ===
              'CON_DIFERENCIA' ||
            r.estadoCodigo ===
              'OK_CON_DIF';

          const puedeEnviar =
            r.estadoCodigo === 'OK';

          const rowId = Number(r.id);

          const selected =
            selectedIds.includes(rowId);

          return (
            <div
              key={r.id}
              onClick={() =>
                puedeDiff &&
                navigate({
                  screen: 'concDiff',
                  id: r.id
                })
              }
              style={{
                display: 'grid',
                gridTemplateColumns: GRID,
                borderTop:
                  `1px solid ${c.line}`,
                fontSize: 12.5,
                cursor: puedeDiff
                  ? 'pointer'
                  : 'default'
              }}
            >
              {/* CHECKBOX */}
              <div
                style={{
                  padding: '11px 8px',
                  display: 'flex',
                  justifyContent:
                    'center',
                  alignItems: 'center'
                }}
              >
                {puedeEnviar && (
                  <input
                    type="checkbox"
                    checked={selected}
                    onClick={(e) =>
                      e.stopPropagation()
                    }
                    onChange={() =>
                      toggleSelected(rowId)
                    }
                  />
                )}
              </div>

              {/* NIS */}
              <div
                style={{
                  padding: '11px 12px'
                }}
              >
                {r.nis}
              </div>

              {/* CONTRATO */}
              <div
                style={{
                  padding: '11px 12px'
                }}
              >
                <span
                  onClick={(e) => {
                    e.stopPropagation();

                    navigate({
                      screen: 'detalle',
                      id: r.contratoId
                    });
                  }}
                  style={{
                    color: c.primary,
                    textDecoration:
                      'underline',
                    cursor: 'pointer',
                    fontWeight: 600
                  }}
                >
                  {r.denom}
                </span>
              </div>

              {/* ESPERADO */}
              <div
                style={{
                  padding: '11px 12px'
                }}
              >
                {money(r.esperado)}
              </div>

              {/* FACTURADO */}
              <div
                style={{
                  padding: '11px 12px'
                }}
              >
                {r.estadoCodigo ===
                'SIN_FACTURA'
                  ? '—'
                  : money(r.facturado)}
              </div>

              {/* FACTURAS */}
              <div
                style={{
                  padding: '11px 12px'
                }}
              >
                {r.facturas_existentes +
                  '/' +
                  r.cantidad_facturas}
              </div>

              {/* DIFERENCIA */}
              <div
                style={{
                  padding: '11px 12px',
                  fontWeight: 600
                }}
              >
                {money(r.diferencia)}
              </div>

              {/* COMPROBANTE */}
              <div
                style={{
                  padding: '11px 12px'
                }}
              >
                {r.comprobante ?? '—'}
              </div>

              {/* ESTADO */}
              <div
                style={{
                  padding: '11px 12px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8
                }}
              >
                <Badge
                  label={info.label}
                  tone={info.tone}
                />

                {puedeEnviar && (
                  <button
                    style={{
                      ...s.btnPrimary,
                      padding:
                        '5px 10px',
                      fontSize: 11.5
                    }}
                    onClick={(e) => {
                      e.stopPropagation();

                      setSelectedIds([
                        rowId
                      ]);

                      setShowSendModal(true);
                    }}
                  >
                    Enviar
                  </button>
                )}
              </div>
            </div>
          );
        })}

        {rows.length === 0 && (
          <div
            style={{
              padding: 16,
              fontSize: 12.5,
              color: c.muted2
            }}
          >
            No hay conciliaciones para este
            período/filtro.
          </div>
        )}
      </div>

      {/* MODAL ENVÍO */}
      {showSendModal && (
        <div
          onClick={() =>
            setShowSendModal(false)
          }
          style={{
            position: 'fixed',
            inset: 0,
            background:
              'rgba(0, 0, 0, 0.45)',
            display: 'flex',
            alignItems: 'center',
            justifyContent:
              'center',
            zIndex: 9999,
            padding: 20
          }}
        >
          <div
            onClick={(e) =>
              e.stopPropagation()
            }
            style={{
              width: '100%',
              maxWidth: 520,
              background: c.bg,
              borderRadius: 8,
              boxShadow:
                '0 8px 30px rgba(0,0,0,0.25)',
              overflow: 'hidden'
            }}
          >
            <div
              style={{
                padding:
                  '14px 18px',
                borderBottom:
                  `1px solid ${c.line}`,
                fontWeight: 700,
                fontSize: 15
              }}
            >
              Enviar contratos a SAP
            </div>

            <div
              style={{
                padding: 18
              }}
            >
              {selectedRows.length === 0 ? (
                <div
                  style={{
                    fontSize: 13,
                    color: c.muted
                  }}
                >
                  No hay contratos
                  seleccionados.
                </div>
              ) : (
              <>

                <div
                  style={{
                    fontSize: 13,
                    marginBottom: 12
                  }}
                >
                  Se enviarán los siguientes contratos:
                </div>

                <div
                  style={{
                    border: `1px solid ${c.line}`,
                    borderRadius: 6,
                    overflowY: 'auto',
                    maxHeight: 350
                  }}
                >
                  {selectedRows.map(
                    (r: any) => (
                      <div
                        key={r.id}
                        style={{
                          display: 'grid',
                          gridTemplateColumns: '120px 1fr',
                          borderTop: `1px solid ${c.line}`,
                          padding: '10px 12px',
                          fontSize: 13
                        }}
                      >
                        <div
                          style={{
                            fontWeight: 600
                          }}
                        >
                          NIS
                        </div>

                        <div>
                          {r.nis}
                        </div>

                        <div
                          style={{
                            fontWeight: 600
                          }}
                        >
                          Contrato
                        </div>

                        <div>
                          {r.denom}
                        </div>
                      </div>
                    )
                  )}
                </div>

              </>

              )}
            </div>

            <div
              style={{
                padding:
                  '12px 18px',
                borderTop:
                  `1px solid ${c.line}`,
                display: 'flex',
                justifyContent:
                  'flex-end',
                gap: 8
              }}
            >
              <button
                style={s.btn}
                onClick={() =>
                  setShowSendModal(false)
                }
              >
                Cancelar
              </button>

              <button
                style={s.btnPrimary}
                onClick={() => {
                  setShowSendModal(false);
                  setSelectedIds([]);
                  setShowSuccessModal(true);
                }}
              >
                Enviar
              </button>

            </div>
          </div>
        </div>
      )}
      {showSuccessModal && (
        <div
          onClick={() => setShowSuccessModal(false)}
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0, 0, 0, 0.45)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 10000,
            padding: 20
          }}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              width: '100%',
              maxWidth: 420,
              background: c.bg,
              borderRadius: 8,
              boxShadow:
                '0 8px 30px rgba(0,0,0,0.25)',
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
              Envío a SAP
            </div>

            <div
              style={{
                padding: 24,
                textAlign: 'center'
              }}
            >
              <div
                style={{
                  fontSize: 32,
                  marginBottom: 12
                }}
              >
                ✓
              </div>

              <div
                style={{
                  fontSize: 15,
                  fontWeight: 600,
                  marginBottom: 8
                }}
              >
                Contratos enviados a SAP
              </div>

              <div
                style={{
                  fontSize: 13,
                  color: c.muted
                }}
              >
                Los contratos seleccionados fueron enviados
                correctamente.
              </div>
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
                onClick={() => setShowSuccessModal(false)}
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

function badge(tone: string) {
  return tone === 'green'
    ? c.green
    : tone === 'red'
    ? c.danger
    : tone === 'amber'
    ? c.amber
    : '#8a8985';
}
