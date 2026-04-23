/** Vercel Geist design tokens — light mode, minimal grayscale + blue accent.
 *
 * Palette traced to Vercel's public Geist system (accents-1..8 neutrals,
 * geist-success/warning/error, geist-foreground/background, violet-600).
 * Aesthetic: pixel-precise, crisp 1px borders, no gradients, no glows.
 *
 *   bg          Pure white page
 *   surface     accents-1 — subtle neutral fill for primary cards
 *   surface-2   accents-1 deeper — inputs / nested surfaces
 *   surface-3   accents-2 — idle button fill (same value as line, on purpose)
 *   line        accents-2 — hairline 1px borders
 *   fg          geist-foreground — pure black
 *   muted       accents-5 — secondary text
 *   subtle      accents-3 — tertiary / placeholder
 *   accent      geist-blue (#0070F3) — primary CTA, active states, links
 *   accent-alt  geist-violet (#7928CA) — secondary emphasis
 *   accent-tint pale blue fill for active tab bg, info pills
 *   success     #00AA55 for PRIMARY tier / completed
 *   warning     #F5A623 amber for BACKUP tier
 *   error       #EE0000 red for SKIP tier / failures
 */
module.exports = {
  content: ['./src/**/*.{js,ts,jsx,tsx,mdx}'],
  theme: {
    extend: {
      colors: {
        bg: '#FFFFFF',
        surface: '#FAFAFA',
        'surface-2': '#F5F5F5',
        'surface-3': '#EAEAEA',
        line: '#EAEAEA',
        fg: '#000000',
        muted: '#666666',
        subtle: '#999999',
        accent: {
          DEFAULT: '#0070F3',
          soft: '#3291FF',
          tint: '#EFF6FF',
        },
        'accent-alt': {
          DEFAULT: '#7928CA',
          soft: '#8A3FD1',
          tint: '#F3E8FF',
        },
        success: {
          DEFAULT: '#00AA55',
          tint: '#D9F5E6',
        },
        warning: {
          DEFAULT: '#F5A623',
          tint: '#FFF3D6',
        },
        error: {
          DEFAULT: '#EE0000',
          tint: '#FFE4E4',
        },
      },
      fontFamily: {
        sans: ['var(--font-sans)', 'system-ui', 'sans-serif'],
        mono: ['var(--font-mono)', 'ui-monospace', 'monospace'],
      },
      borderRadius: {
        // Geist uses 5px consistently — Tailwind `rounded` is 4px, `rounded-md` is 6px.
        card: '0.3125rem',
      },
      boxShadow: {
        // Geist's signature soft elevation — used sparingly on interactive cards.
        geist: '0 1px 2px 0 rgba(0, 0, 0, 0.04), 0 4px 8px -2px rgba(0, 0, 0, 0.04)',
        'geist-md': '0 4px 8px 0 rgba(0, 0, 0, 0.06), 0 8px 16px -4px rgba(0, 0, 0, 0.06)',
      },
    },
  },
  plugins: [],
};
