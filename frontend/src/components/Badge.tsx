import { badgeColor, c } from '../theme';
import type { BadgeTone } from '../theme';

export function Badge({ label, tone }: { label: string; tone: BadgeTone }) {
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 11.5, fontWeight: 600,
      padding: '4px 8px', border: `1px solid ${c.border}`, borderRadius: 3, background: c.softBg,
    }}>
      <span style={{ width: 6, height: 6, borderRadius: '50%', background: badgeColor[tone] }} />
      {label}
    </span>
  );
}
