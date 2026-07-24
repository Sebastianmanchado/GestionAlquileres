import type { BadgeTone } from './theme';

const nf0 = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 0 });
const nf2 = new Intl.NumberFormat('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

export function money(n: unknown): string {
  if (n === null || n === undefined || n === '') return '—';
  const v = Number(n);
  if (Number.isNaN(v)) return '—';
  return '$ ' + nf0.format(Math.round(v));
}

export function num(n: unknown, decimals = 0): string {
  if (n === null || n === undefined || n === '') return '—';
  const v = Number(n);
  if (Number.isNaN(v)) return String(n);
  return decimals === 0 ? nf0.format(v) : nf2.format(v);
}

export function m2(n: unknown): string {
  if (n === null || n === undefined || n === '') return '—';
  return nf0.format(Number(n)) + ' m²';
}

/** 'yyyy-MM-dd' -> 'dd/MM/yyyy' */
export function dateAr(iso: unknown): string {
  if (!iso) return '—';
  const s = String(iso).substring(0, 10);
  const parts = s.split('-');
  if (parts.length !== 3) return String(iso);
  return `${parts[2]}/${parts[1]}/${parts[0]}`;
}

const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];
const MESES_CORTOS = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];

/** 'yyyy-MM-dd' -> 'Junio 2026' */
export function periodo(iso: unknown): string {
  if (!iso) return '—';
  const s = String(iso).substring(0, 10);
  const parts = s.split('-');
  if (parts.length < 2) return String(iso);
  const mes = MESES[parseInt(parts[1], 10) - 1] ?? '';
  return `${mes} ${parts[0]}`;
}

export function mesCorto(iso: unknown): string {
  if (!iso) return '';
  const s = String(iso).substring(0, 10);
  const parts = s.split('-');
  if (parts.length < 2) return '';
  return MESES_CORTOS[parseInt(parts[1], 10) - 1] ?? '';
}

/** 'yyyy-MM-ddThh:mm:ss' -> 'dd/MM/yyyy hh:mm' */
export function dateTimeAr(iso: unknown): string {
  if (!iso) return '—';
  const s = String(iso);
  const [d, t] = s.split('T');
  const dd = dateAr(d);
  const hhmm = t ? t.substring(0, 5) : '';
  return hhmm ? `${dd} ${hhmm}` : dd;
}

type EstadoInfo = { label: string; tone: BadgeTone };

export function estadoContrato(codigo: string, nombre?: string): EstadoInfo {
  switch (codigo) {
    case 'VIGENTE': return { label: nombre ?? 'Vigente', tone: 'green' };
    case 'PROX_VENCER': return { label: nombre ?? 'Próximo a vencer', tone: 'amber' };
    case 'VENCIDO': return { label: nombre ?? 'Vencido', tone: 'red' };
    case 'RESCINDIDO': return { label: nombre ?? 'Rescindido', tone: 'neutral' };
    default: return { label: nombre ?? codigo, tone: 'neutral' };
  }
}

export function estadoFactura(codigo: string): EstadoInfo {
  switch (codigo) {
    case 'COINCIDE': return { label: 'Coincide', tone: 'green' };
    case 'CON_DIFERENCIA': return { label: 'Con diferencia', tone: 'red' };
    case 'SIN_ASIGNAR': return { label: 'Sin asignar', tone: 'neutral' };
    case 'PENDIENTE': return { label: 'Pendiente de validación', tone: 'amber' };
    case 'RECHAZADA': return { label: 'Rechazada', tone: 'red' };
    default: return { label: codigo, tone: 'neutral' };
  }
}

export function estadoConciliacion(codigo: string): EstadoInfo {
  switch (codigo) {
    case 'OK': return { label: 'OK', tone: 'green' };
    case 'OK_CON_DIF': return { label: 'OK con diferencia', tone: 'amber' };
    case 'CON_DIFERENCIA': return { label: 'Con diferencia', tone: 'red' };
    case 'SIN_FACTURA': return { label: 'Sin factura', tone: 'neutral' };
    default: return { label: codigo, tone: 'neutral' };
  }
}

export function estadoRpa(codigo: string): EstadoInfo {
  switch (codigo) {
    case 'COMPLETADO': return { label: 'Completado', tone: 'green' };
    case 'CON_ERRORES': return { label: 'Con errores', tone: 'red' };
    case 'EN_CURSO': return { label: 'En curso', tone: 'amber' };
    default: return { label: codigo, tone: 'neutral' };
  }
}

export function origenValor(codigo: string): string {
  switch (codigo) {
    case 'CONTRATO': return 'Contrato';
    case 'AJUSTE_INDICE': return 'Ajuste por índice';
    case 'ACUERDO': return 'Acuerdo';
    default: return codigo ?? '—';
  }
}

export function rolLabel(codigo: string): string {
  switch (codigo) {
    case 'ANALISTA': return 'Analista';
    case 'SUPERVISOR': return 'Supervisor regional';
    case 'AUDITOR': return 'Auditor';
    case 'SISTEMA': return 'Sistema';
    default: return codigo ?? '—';
  }
}
