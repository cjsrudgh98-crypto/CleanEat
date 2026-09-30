interface BrandProps {
  size?: number
  showWordmark?: boolean
}

export function LogoMark({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 96 96" aria-hidden="true">
      <rect x="4" y="4" width="88" height="88" rx="20" fill="#0E1F17" />
      <path
        d="M64 30 A22 22 0 1 0 64 66"
        fill="none"
        stroke="#0EA875"
        strokeWidth="10"
        strokeLinecap="round"
      />
      <path
        d="M42 46 L50 56 L66 36"
        fill="none"
        stroke="#ffffff"
        strokeWidth="7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

export function Brand({ size = 28, showWordmark = true }: BrandProps) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem' }}>
      <LogoMark size={size} />
      {showWordmark && (
        <span style={{ fontWeight: 800, fontSize: '1.1rem', letterSpacing: '-0.02em' }}>
          Clean<span style={{ color: 'var(--brand)' }}>Eat</span>
        </span>
      )}
    </span>
  )
}
