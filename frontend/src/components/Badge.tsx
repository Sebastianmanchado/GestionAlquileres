import { badgeColor, c } from '../theme';
import type { BadgeTone } from '../theme';

export function Badge({ label, tone, minWidth }: { label: string; tone: BadgeTone; minWidth?: number }) {
  return (
    <span style={{
      display: minWidth ? 'inline-flex' : 'grid',
      gridTemplateColumns: minWidth ? undefined : '10% 90%',
      alignItems: 'center', gap: 6, fontSize: 11.5, fontWeight: 600,
      padding: '4px 8px', border: `1px solid ${c.border}`, borderRadius: 3, background: c.softBg,
      minWidth, boxSizing: 'border-box',
    }}>
      <span style={{ width: 6, height: 6, borderRadius: '50%', background: badgeColor[tone] }} />
      {label}
    </span>
  );
}
