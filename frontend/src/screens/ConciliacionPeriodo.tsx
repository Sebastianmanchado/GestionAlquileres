import { useState } from 'react';
import { api, qs } from '../api';
import { useApp } from '../context';
import { c, s } from '../theme';
import { Badge } from '../components/Badge';
import { money, periodo as fmtPeriodo, estadoConciliacion } from '../format';
import { useAsync } from '../hooks';
import { Loading, ErrorBox } from './Dashboard';

const GRID = '30px 80px 1fr 130px 130px 80px 110px 140px 150px';

export function ConciliacionPeriodo() {
  const { navigate, meta, role } = useApp();

  const [periodoSel, setPeriodoSel] = useState<string>('');
  const [estadoFiltro, setEstadoFiltro] = useState<string | null>(null);
  const [running, setRunning] = useState(false);

  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [showSendModal, setShowSendModal] = useState(false);
  const [showSuccessModal, setShowSuccessModal] = useState(false);
  const [showSendMenu, setShowSendMenu] = useState(false);

  const [sendMode, setSendMode] = useState<
    'selected' | 'all' | 'ok' | 'okDiff'
  >('selected');

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

  function abrirModalEnvio(
    mode: 'selected' | 'all' | 'ok' | 'okDiff'
  ) {
    if (mode === 'selected' && selectedIds.length === 0) {
      return;
    }

    setSendMode(mode);
    setShowSendMenu(false);
    setShowSendModal(true);
  }

  if (loading && !data) return <Loading />;
  if (error || !data) return <ErrorBox msg={error} />;

  console.log(data)

  const rows = estadoFiltro
    ? data.rows.filter(
        (r: any) =>
          r.estadoCodigo === estadoFiltro
      )
    : data.rows;

  const rowsToSend = (() => {
    switch (sendMode) {
      case 'selected':
        return data.rows.filter((r: any) =>
          selectedIds.includes(Number(r.id))
        );

      case 'all':
        return data.rows.filter(
          (r: any) =>
            r.estadoCodigo === 'OK' ||
            r.estadoCodigo === 'OK_CON_DIF'
        );

      case 'ok':
        return data.rows.filter(
          (r: any) => r.estadoCodigo === 'OK'
        );

      case 'okDiff':
        return data.rows.filter(
          (r: any) => r.estadoCodigo === 'OK_CON_DIF'
        );

      default:
        return [];
    }
  })();

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
            <div style={{ position: 'relative' }}>
              <button
                style={{
                  ...s.btn,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8
                }}
                onClick={() =>
                  setShowSendMenu((current) => !current)
                }
              >
                Enviar
                <span style={{ fontSize: 10 }}>▼</span>
              </button>

              {showSendMenu && (
                <div
                  style={{
                    position: 'absolute',
                    top: 'calc(100% + 4px)',
                    right: 0,
                    minWidth: 230,
                    background: c.bg,
                    border: `1px solid ${c.line}`,
                    borderRadius: 6,
                    boxShadow:
                      '0 6px 18px rgba(0,0,0,0.15)',
                    zIndex: 1000,
                    overflow: 'hidden'
                  }}
                >
                  <button
                    style={{
                      width: '100%',
                      border: 'none',
                      background: 'transparent',
                      textAlign: 'left',
                      padding: '10px 12px',
                      fontSize: 12.5,
                      cursor:
                        selectedIds.length === 0
                          ? 'not-allowed'
                          : 'pointer',
                      color:
                        selectedIds.length === 0
                          ? c.muted2
                          : c.text
                    }}
                    disabled={selectedIds.length === 0}
                    onClick={() =>
                      abrirModalEnvio('selected')
                    }
                  >
                    Enviar seleccionados
                  </button>

                  <button
                    style={{
                      width: '100%',
                      border: 'none',
                      borderTop:
                        `1px solid ${c.line}`,
                      background: 'transparent',
                      textAlign: 'left',
                      padding: '10px 12px',
                      fontSize: 12.5,
                      cursor: 'pointer'
                    }}
                    onClick={() =>
                      abrirModalEnvio('all')
                    }
                  >
                    Enviar todo
                  </button>

                  <button
                    style={{
                      width: '100%',
                      border: 'none',
                      borderTop:
                        `1px solid ${c.line}`,
                      background: 'transparent',
                      textAlign: 'left',
                      padding: '10px 12px',
                      fontSize: 12.5,
                      cursor: 'pointer'
                    }}
                    onClick={() =>
                      abrirModalEnvio('ok')
                    }
                  >
                    Enviar todos los OK
                  </button>

                  <button
                    style={{
                      width: '100%',
                      border: 'none',
                      borderTop:
                        `1px solid ${c.line}`,
                      background: 'transparent',
                      textAlign: 'left',
                      padding: '10px 12px',
                      fontSize: 12.5,
                      cursor: 'pointer'
                    }}
                    onClick={() =>
                      abrirModalEnvio('okDiff')
                    }
                  >
                    Enviar todos los OK con diferencia
                  </button>
                </div>
              )}
            </div>
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

          const puedeEnviar =
            r.estadoCodigo === 'OK' || r.estadoCodigo === 'OK_CON_DIF';

          const rowId = Number(r.id);

          const selected =
            selectedIds.includes(rowId);

          return (
            <div
              key={r.id}
              style={{
                display: 'grid',
                gridTemplateColumns: GRID,
                justifyContent: "center",
                borderTop:
                  `1px solid ${c.line}`,
                fontSize: 12.5,
                cursor: 'default'
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
                  padding: '11px 12px',
                  alignContent: "center"
                }}
              >
                {r.nis}
              </div>

              {/* CONTRATO */}
              <div
                style={{
                  padding: '11px 12px',
                  alignContent: "center"
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
                    fontWeight: 600,
                    alignContent: "center"
                  }}
                >
                  {r.denom}
                </span>
              </div>

              {/* ESPERADO */}
              <div
                style={{
                  padding: '11px 12px',
                  alignContent: "center"
                }}
              >
                {money(r.esperado)}
              </div>

              {/* FACTURADO */}
              <div
                style={{
                  padding: '11px 12px',
                  alignContent: "center"
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
                  padding: '11px 12px',
                  alignContent: "center"
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
                  fontWeight: 600,
                  alignContent: "center"
                }}
              >
                {money(r.diferencia)}
              </div>

              {/* COMPROBANTE */}
              <div
                style={{
                  padding: '11px 12px',
                  alignContent: "center"
                }}
              >
                {r.facturas?.length > 0
                  ? r.facturas.map((f: any) => (
                      <span
                        key={f.id}
                        onClick={() =>
                          navigate({
                            screen: 'facturaDetalle',
                            id: f.id,
                            origin: 'concPeriodo',
                          })
                        }
                        style={{
                          display: 'block',
                          color: c.primary,
                          textDecoration: 'underline',
                          cursor: 'pointer',
                          fontWeight: 600,
                          marginBottom: 4,
                        }}
                      >
                        {f.comprobante ?? '—'}
                      </span>
                    ))
                  : '—'}
              </div>

              {/* ESTADO */}
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: '65% 35%',
                  padding: '11px 12px',
                  alignItems: 'center',
                  justifyContent: "center",
                  gap: 8,
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
                      fontSize: 11.5,
                      display: "flex",
                      justifyContent: "center"
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
              {rowsToSend.length === 0 ? (
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
                  {sendMode === 'selected' &&
                    'Se enviarán los siguientes contratos seleccionados:'}

                  {sendMode === 'all' &&
                    'Se enviarán todos los contratos en estado OK y OK con diferencia:'}

                  {sendMode === 'ok' &&
                    'Se enviarán todos los contratos en estado OK:'}

                  {sendMode === 'okDiff' &&
                    'Se enviarán todos los contratos en estado OK con diferencia:'}
                </div>


                <div
                  style={{
                    overflowY: 'auto',
                    maxHeight: 350,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 8
                  }}
                >
                  {rowsToSend.map((r: any) => {
                    const info = estadoConciliacion(r.estadoCodigo);
                    const isOk = r.estadoCodigo === 'OK';
                    const isOkDiff = r.estadoCodigo === 'OK_CON_DIF';
                    const bg = isOk
                      ? 'rgba(47, 107, 63, 0.14)'
                      : isOkDiff
                        ? 'rgba(217, 119, 6, 0.16)'
                        : c.softBg;
                    const border = isOk
                      ? 'rgba(47, 107, 63, 0.28)'
                      : isOkDiff
                        ? 'rgba(217, 119, 6, 0.32)'
                        : c.line;

                    return (
                      <div
                        key={r.id}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 12,
                          padding: '10px 12px',
                          borderRadius: 6,
                          background: bg,
                          border: `1px solid ${border}`,
                          fontSize: 13
                        }}
                      >
                        <Badge label={info.label} tone={info.tone} />
                        <div style={{ minWidth: 0, flex: 1 }}>
                          <div style={{ fontWeight: 700 }}>NIS {r.nis}</div>
                          <div style={{ fontSize: 12, color: c.muted, marginTop: 2 }}>
                            {r.denom}
                          </div>
                        </div>
                      </div>
                    );
                  })}
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
