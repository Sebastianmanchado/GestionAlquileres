import { useEffect, useState, useRef } from 'react';
import type { ReactNode } from 'react';
import { api } from '../api';
import { useApp } from '../context';
import type { Route } from '../context';
import { c, s } from '../theme';
import { estadoFactura, } from '../format';
import { Loading } from './Dashboard';

export function DetalleFactura({
  id,
  nueva,
  origin,
}: {
  id?: number;
  nueva?: boolean;
  origin: Route['screen'];
}) {
  const { navigate, meta } = useApp();
  const esNueva = !!nueva;

  const [form, setForm] = useState<Record<string, any>>({});
  const [display, setDisplay] = useState<{
    estadoCodigo?: string;
    contrato?: string;
  }>({});

  const [ready, setReady] = useState(esNueva);
  const [saving, setSaving] = useState(false);

  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [errorModal, setErrorModal] = useState<string | null>(null);
  const [existingDocumentUrl, setExistingDocumentUrl] = useState<string | null>(null);
  const [documentExists, setDocumentExists] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!esNueva && id != null) {
      api.get(`/invoices/${id}`).then((d: any) => {
        setForm({
          cuit: d.cuit,
          razonSocial: d.razonSocial,
          comprobante: d.comprobante,
          importe: d.importe,
          periodo: d.periodo
            ? String(d.periodo).substring(0, 7)
            : '',
          fechaEmision: d.fechaEmision
            ? String(d.fechaEmision).substring(0, 10)
            : '',
          observaciones: d.observaciones ?? '',
        });

        setDisplay({
          estadoCodigo: d.estadoCodigo,
          contrato: d.contratoNis
            ? `${d.contratoNis} · ${d.contratoDenom}`
            : '(sin asignar)',
        });

        setReady(true);
      });
    }
  }, [id, esNueva]);

  useEffect(() => {
    if (!esNueva && id != null) {
      const url = `/api/documents/factura/${id}`;

      fetch(url, {
        headers: {
          'X-Role': 'ANALISTA',
        },
      })
        .then((response) => {
          if (response.status === 404) {
            setDocumentExists(false);
            setExistingDocumentUrl(null);
            return;
          }

          if (!response.ok) {
            throw new Error('No se pudo cargar el documento de la factura.');
          }

          setDocumentExists(true);
          setExistingDocumentUrl(url);
        })
        .catch((e) => {
          console.error(e);
          setDocumentExists(false);
          setExistingDocumentUrl(null);
        });
    }
  }, [id, esNueva]);

  useEffect(() => {
    return () => {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
      }
    };
  }, [previewUrl]);

  if (!ready) return <Loading />;

  const set = (k: string, v: any) =>
    setForm((f) => ({
      ...f,
      [k]: v,
    }));

  function handleFile(file: File) {
    if (file.type !== 'application/pdf') {
      setErrorModal('Solo se permiten archivos PDF.');
      return;
    }

    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }

    const url = URL.createObjectURL(file);

    setSelectedFile(file);
    setPreviewUrl(url);
  }

  async function removeDocument() {
    try {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
      }

      setSelectedFile(null);
      setPreviewUrl(null);
      setExistingDocumentUrl(null);
      setDocumentExists(false);

      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }

      if (!esNueva && id != null) {
        await api.del(`/documents/factura/${id}`);
      }
    } catch (e: any) {
      setErrorModal(
        e?.message ?? 'No se pudo quitar el documento.'
      );
    }
  }

  async function upload(id: number, file: File) {
    const formData = new FormData();

    formData.append('facturaId', String(id));
    formData.append('file', file);
    formData.append('tipoDocumento', 'FACTURA');

    console.log('a punto de guardar archivo');

    const result = await api.upload('/documents', formData);

    console.log(result);

    return result;
  }

  async function save() {
  setSaving(true);

  try {
    let facturaId = id;

    if (esNueva) {
      if (!selectedFile) {
        throw new Error(
          'Debe adjuntar el PDF de la factura.'
        );
      }

      const created = await api.post(
        '/invoices',
        form
      );

      facturaId = created?.id;

      if (!facturaId) {
        throw new Error(
          'La factura fue creada pero el backend no devolvió su ID.'
        );
      }
    } else {
      if (id == null) {
        throw new Error(
          'No se encontró el ID de la factura.'
        );
      }

      await api.put(
        `/invoices/${id}`,
        form
      );

      facturaId = id;
    }

    if (selectedFile && facturaId != null) {
      await upload(facturaId, selectedFile);
    } else {
      throw Error("Debe haber un documento para crear la factura.")
    }

    navigate({ screen: origin } as Route);

  } catch (e: any) {
    console.log(e);

    setErrorModal(
      e?.message ??
      'Ocurrió un error al guardar la factura.'
    );

  } finally {
    setSaving(false);
  }
}

  const estado = display.estadoCodigo
    ? estadoFactura(display.estadoCodigo).label
    : 'Sin asignar';

  return (
    <div>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          marginBottom: 16,
        }}
      >
        <button
          style={{
            ...s.btn,
            padding: '8px 10px',
          }}
          onClick={() =>
            navigate({ screen: origin } as Route)
          }
        >
          ‹ Volver
        </button>

        <h1 style={s.h1}>
          {esNueva
            ? 'Nueva factura'
            : `Factura ${form.comprobante ?? ''}`}
        </h1>
      </div>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          gap: 16,
        }}
      >
        {esNueva ? (
        <>
          <input
            ref={fileInputRef}
            type="file"
            accept="application/pdf,.pdf"
            style={{ display: 'none' }}
            onChange={(e) => {
              const file = e.target.files?.[0];

              if (file) {
                handleFile(file);
              }
            }}
          />

          <div
            style={{
              border: `1.5px dashed ${
                dragOver ? '#2563eb' : '#c7c6c3'
              }`,
              borderRadius: 4,
              height: 520,
              overflow: 'hidden',
              position: 'relative',
              background: '#f8f8f8',
            }}
            onDragOver={(e) => {
              e.preventDefault();
              setDragOver(true);
            }}
            onDragLeave={() => {
              setDragOver(false);
            }}
            onDrop={(e) => {
              e.preventDefault();
              setDragOver(false);

              const file = e.dataTransfer.files?.[0];

              if (file) {
                handleFile(file);
              }
            }}
          >
            {!selectedFile ? (
              <div
                onClick={() => fileInputRef.current?.click()}
                style={{
                  width: '100%',
                  height: '100%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  textAlign: 'center',
                  cursor: 'pointer',
                  color: c.muted2,
                  fontSize: 12.5,
                }}
              >
                <div>
                  <div style={{ fontSize: 28, marginBottom: 10 }}>
                    📄
                  </div>

                  <div>
                    Arrastrá el PDF de la factura aquí
                  </div>

                  <div style={{ marginTop: 4 }}>
                    o hacé clic para subir
                  </div>
                </div>
              </div>
            ) : (
              <>
                <iframe
                  src={previewUrl ?? undefined}
                  title="Vista previa de la factura"
                  style={{
                    width: '100%',
                    height: '100%',
                    border: 'none',
                    display: 'block',
                  }}
                />

                <div
                  style={{
                    position: 'absolute',
                    top: 10,
                    right: 10,
                    display: 'flex',
                    gap: 8,
                  }}
                >
                  <button
                    type="button"
                    style={{
                      ...s.btn,
                      background: '#fff',
                    }}
                    onClick={() => fileInputRef.current?.click()}
                  >
                    Cambiar PDF
                  </button>

                  <button
                    type="button"
                    style={{
                      ...s.btn,
                      background: '#fff',
                    }}
                    onClick={() => {
                      if (previewUrl) {
                        URL.revokeObjectURL(previewUrl);
                      }

                      setSelectedFile(null);
                      setPreviewUrl(null);

                      if (fileInputRef.current) {
                        fileInputRef.current.value = '';
                      }
                    }}
                  >
                    Quitar
                  </button>
                </div>
              </>
            )}
          </div>
        </>

        ) : (
          <div
  style={{
    ...s.panel,
    overflow: 'hidden',
  }}
>
  <input
    ref={fileInputRef}
    type="file"
    accept="application/pdf,.pdf"
    style={{ display: 'none' }}
    onChange={(e) => {
      const file = e.target.files?.[0];

      if (file) {
        handleFile(file);
      }
    }}
  />

  <div
    style={{
      border: `1.5px dashed ${
        dragOver ? '#2563eb' : '#c7c6c3'
      }`,
      borderRadius: 4,
      height: 520,
      overflow: 'hidden',
      position: 'relative',
      background: '#f8f8f8',
    }}
    onDragOver={(e) => {
      e.preventDefault();
      setDragOver(true);
    }}
    onDragLeave={() => {
      setDragOver(false);
    }}
    onDrop={(e) => {
      e.preventDefault();
      setDragOver(false);

      const file = e.dataTransfer.files?.[0];

      if (file) {
        handleFile(file);
      }
    }}
  >
    {!previewUrl && !existingDocumentUrl ? (
      <div
        onClick={() => fileInputRef.current?.click()}
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          textAlign: 'center',
          cursor: 'pointer',
          color: c.muted2,
          fontSize: 12.5,
        }}
      >
        <div>
          <div style={{ fontSize: 28, marginBottom: 10 }}>
            📄
          </div>

          <div>
            Arrastrá el PDF de la factura aquí
          </div>

          <div style={{ marginTop: 4 }}>
            o hacé clic para subir
          </div>
        </div>
      </div>
    ) : (
      <>
        <iframe
          src={previewUrl ?? existingDocumentUrl ?? undefined}
          title="Vista previa de la factura"
          style={{
            width: '100%',
            height: '100%',
            border: 'none',
            display: 'block',
          }}
        />

        <div
          style={{
            position: 'absolute',
            top: 10,
            right: 10,
            display: 'flex',
            gap: 8,
          }}
        >
          <button
            type="button"
            style={{
              ...s.btn,
              background: '#fff',
            }}
            onClick={() =>
              fileInputRef.current?.click()
            }
          >
            Cambiar PDF
          </button>

          <button
            type="button"
            style={{
              ...s.btn,
              background: '#fff',
            }}
            onClick={removeDocument}
          >
            Quitar
          </button>
        </div>
      </>
    )}
  </div>
</div>
        )}

        <div
          style={{
            ...s.panel,
            padding: 16,
          }}
        >
          <div
            style={{
              fontWeight: 700,
              fontSize: 13,
              marginBottom: 12,
            }}
          >
            Datos de la factura
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns:
                '1fr 1fr',
              gap: 12,
            }}
          >
            <Field label="CUIT emisor">
              <input
                style={s.input}
                value={form.cuit ?? ''}
                onChange={(e) =>
                  set('cuit', e.target.value)
                }
                disabled={!meta.canEdit}
              />
            </Field>

            <Field label="Razón social">
              <input
                style={s.input}
                value={form.razonSocial ?? ''}
                onChange={(e) =>
                  set(
                    'razonSocial',
                    e.target.value
                  )
                }
                disabled={!meta.canEdit}
              />
            </Field>

            <Field label="Número de comprobante">
              <input
                style={s.input}
                value={form.comprobante ?? ''}
                onChange={(e) =>
                  set(
                    'comprobante',
                    e.target.value
                  )
                }
                disabled={!meta.canEdit}
              />
            </Field>

            <Field label="Importe">
              <input
                style={s.input}
                value={form.importe ?? ''}
                onChange={(e) =>
                  set(
                    'importe',
                    e.target.value
                  )
                }
                disabled={!meta.canEdit}
              />
            </Field>

            <Field label="Período">
              <input
                type="month"
                style={s.input}
                value={form.periodo ?? ''}
                onChange={(e) =>
                  set(
                    'periodo',
                    e.target.value
                  )
                }
                disabled={!meta.canEdit}
              />
            </Field>

            <Field label="Fecha de emisión">
              <input
                type="date"
                style={s.input}
                value={
                  form.fechaEmision ?? ''
                }
                onChange={(e) =>
                  set(
                    'fechaEmision',
                    e.target.value
                  )
                }
                disabled={!meta.canEdit}
              />
            </Field>

            <Field label="Contrato asignado">
              <input
                style={{
                  ...s.input,
                  background: c.fieldBg,
                }}
                value={
                  display.contrato ??
                  '(sin asignar)'
                }
                disabled
              />
            </Field>

            <Field label="Estado">
              <input
                style={{
                  ...s.input,
                  background: c.fieldBg,
                }}
                value={estado}
                disabled
              />
            </Field>

            <Field
              label="Observaciones"
              span2
            >
              <textarea
                style={{
                  ...s.input,
                  minHeight: 60,
                  resize: 'vertical',
                }}
                value={
                  form.observaciones ?? ''
                }
                onChange={(e) =>
                  set(
                    'observaciones',
                    e.target.value
                  )
                }
                disabled={!meta.canEdit}
              />
            </Field>
          </div>

          {meta.canEdit ? (
            <div
              style={{
                display: 'flex',
                gap: 8,
                marginTop: 16,
              }}
            >
              <button
              style={s.btnPrimary}
              disabled={saving}
              onClick={save}
            >
              {saving ? 'Guardando…' : 'Guardar cambios'}
            </button>

              <button
                style={s.btn}
                onClick={() =>
                  navigate({
                    screen: origin,
                  } as Route)
                }
              >
                Cancelar
              </button>
            </div>
          ) : (
            <div
              style={{
                fontSize: 12,
                color: c.muted2,
                marginTop: 12,
              }}
            >
              Modo solo lectura — el rol
              Auditor no puede editar esta
              factura.
            </div>
          )}
        </div>
      </div>
      {errorModal && (
      <div style={s.modalOverlay}>
        <div style={s.modal}>
          <h3 style={s.modalTitle}>Error</h3>

          <p style={s.modalMessage}>
            {errorModal}
          </p>

          <div style={s.modalActions}>
            <button
              type="button"
              style={s.btnPrimary}
              onClick={() => setErrorModal(null)}
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

function Field({ label, children, span2 }: { label: string; children: ReactNode; span2?: boolean }) {
  return (
    <div style={{ gridColumn: span2 ? '1/-1' : undefined }}>
      <label style={s.label}>{label}</label>
      {children}
    </div>
  );
}
