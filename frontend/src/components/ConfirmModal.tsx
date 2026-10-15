import { useEffect, type ReactNode } from 'react';
import { c, s } from '../theme';

export function ConfirmModal({
  open,
  titulo,
  children,
  confirmLabel = 'Confirmar',
  cancelLabel = 'Cancelar',
  peligro = false,
  cargando = false,
  onConfirm,
  onCancel,
}: {
  open: boolean;
  titulo: string;
  children: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  peligro?: boolean;
  cargando?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  // Cerrar con Escape (salvo mientras procesa)
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !cargando) onCancel();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, cargando, onCancel]);

  if (!open) return null;

  return (
    <div
      onClick={() => !cargando && onCancel()}
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0,0,0,0.4)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1000,
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-titulo"
        onClick={(e) => e.stopPropagation()}
        style={{
          ...s.panel,
          width: 'min(420px, calc(100vw - 32px))',
          padding: 20,
          background: '#fff',
          boxShadow: '0 10px 30px rgba(0,0,0,0.2)',
        }}
      >
        <div id="confirm-titulo" style={{ fontWeight: 700, fontSize: 15, marginBottom: 10 }}>
          {titulo}
        </div>

        <div style={{ fontSize: 13, color: c.muted, lineHeight: 1.5, marginBottom: 20 }}>
          {children}
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <button
            type="button"
            onClick={onCancel}
            disabled={cargando}
            style={{
              padding: '7px 14px',
              fontSize: 13,
              borderRadius: 6,
              border: `1px solid ${c.line}`,
              background: 'transparent',
              cursor: cargando ? 'not-allowed' : 'pointer',
            }}
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={cargando}
            autoFocus
            style={{
              padding: '7px 14px',
              fontSize: 13,
              borderRadius: 6,
              border: 'none',
              background: peligro ? '#b42318' : '#1f6feb',
              color: '#fff',
              cursor: cargando ? 'not-allowed' : 'pointer',
              opacity: cargando ? 0.7 : 1,
            }}
          >
            {cargando ? 'Procesando…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}