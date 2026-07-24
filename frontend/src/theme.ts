import type { CSSProperties } from 'react';

/** Paleta tomada exactamente del wireframe. */
export const c = {
  navy: '#0f2b4c',
  navyLine: '#1c3f66',
  gold: '#f2b400',
  bg: '#ececea',
  text: '#1e1e1f',
  border: '#d6d5d2',
  panel: '#ffffff',
  headerBg: '#f6f5f3',
  primary: '#14355c',
  primaryDark: '#0c2440',
  danger: '#8a3b2e',
  green: '#2f6b3f',
  amber: '#8a7a2e',
  muted: '#6b6a67',
  muted2: '#8f8e8c',
  muted3: '#9d9c9a',
  sidebarText: '#c7d2e0',
  sidebarText2: '#c9c8c6',
  line: '#f1f0ee',
  softBg: '#fafaf9',
  fieldBg: '#f1f0ee',
  warnBg: '#f6efe6',
  warnBorder: '#d8c9a8',
  diffBg: '#fbf2ee',
} as const;

export const s = {
  panel: { background: c.panel, border: `1px solid ${c.border}`, borderRadius: 4 } as CSSProperties,
  h1: { fontSize: 19, margin: 0, fontWeight: 700 } as CSSProperties,
  btn: {
    border: `1px solid ${c.border}`, background: '#fff', borderRadius: 4,
    padding: '8px 12px', fontSize: 12.5, cursor: 'pointer', color: c.text,
  } as CSSProperties,
  btnPrimary: {
    border: 'none', background: c.primary, color: '#fff', borderRadius: 4,
    padding: '8px 14px', fontSize: 12.5, fontWeight: 600, cursor: 'pointer',
  } as CSSProperties,
  btnDanger: {
    border: 'none', background: c.danger, color: '#fff', borderRadius: 4,
    padding: '8px 14px', fontSize: 12.5, fontWeight: 600, cursor: 'pointer',
  } as CSSProperties,
  input: {
    width: '100%', border: `1px solid ${c.border}`, borderRadius: 4,
    padding: '8px 10px', fontSize: 12.5, background: '#fff', color: c.text,
  } as CSSProperties,
  select: {
    border: `1px solid ${c.border}`, borderRadius: 4, padding: '7px 10px',
    fontSize: 12, background: '#fff', color: c.text,
  } as CSSProperties,
  label: { display: 'block', fontSize: 11.5, color: c.muted, marginBottom: 5 } as CSSProperties,
  th: {
    padding: '10px 12px', fontSize: 11, fontWeight: 700, color: c.muted,
    textTransform: 'uppercase', letterSpacing: '.3px',
  } as CSSProperties,
};

/** Colores semánticos para las badges de estado. */
export const badgeColor = {
  green: c.green,
  red: c.danger,
  amber: c.amber,
  neutral: '#8a8985',
} as const;
export type BadgeTone = keyof typeof badgeColor;
