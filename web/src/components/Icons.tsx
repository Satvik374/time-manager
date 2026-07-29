/* Inline SVG icons — a handful of 24px strokes rather than an icon dependency. */

interface IconProps {
  size?: number
  className?: string
  strokeWidth?: number
}

function Svg({
  size = 20,
  className,
  strokeWidth = 1.8,
  children,
}: IconProps & { children: React.ReactNode }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
      focusable="false"
    >
      {children}
    </svg>
  )
}

export const IconToday = (props: IconProps) => (
  <Svg {...props}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7v5l3.2 1.9" />
  </Svg>
)

export const IconCalendar = (props: IconProps) => (
  <Svg {...props}>
    <rect x="3" y="4.5" width="18" height="16" rx="2.5" />
    <path d="M3 9.5h18M8 3v3M16 3v3" />
  </Svg>
)

export const IconSparkle = (props: IconProps) => (
  <Svg {...props}>
    <path d="M12 3l1.9 5.2L19 10l-5.1 1.8L12 17l-1.9-5.2L5 10l5.1-1.8L12 3z" />
    <path d="M18.5 15.5l.7 1.9 1.8.6-1.8.7-.7 1.8-.7-1.8-1.8-.7 1.8-.6.7-1.9z" />
  </Svg>
)

export const IconInsights = (props: IconProps) => (
  <Svg {...props}>
    <path d="M4 20V10M10 20V5M16 20v-7M22 20H2" />
  </Svg>
)

export const IconTrophy = (props: IconProps) => (
  <Svg {...props}>
    <path d="M7 4h10v5a5 5 0 0 1-10 0V4z" />
    <path d="M7 6H4.5a2.5 2.5 0 0 0 2.5 4M17 6h2.5a2.5 2.5 0 0 1-2.5 4" />
    <path d="M12 14v3.5M9 21h6M10 17.5h4" />
  </Svg>
)

export const IconSettings = (props: IconProps) => (
  <Svg {...props}>
    <circle cx="12" cy="12" r="3" />
    <path d="M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-1.8-.3 1.6 1.6 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1A1.6 1.6 0 0 0 9 19.4a1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0 .3-1.8 1.6 1.6 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1A1.6 1.6 0 0 0 4.6 9a1.6 1.6 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 1.8.3H9a1.6 1.6 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 1 1.5 1.6 1.6 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8V9a1.6 1.6 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1z" />
  </Svg>
)

export const IconPlus = (props: IconProps) => (
  <Svg {...props}>
    <path d="M12 5v14M5 12h14" />
  </Svg>
)

export const IconCheck = (props: IconProps) => (
  <Svg {...props} strokeWidth={props.strokeWidth ?? 2.6}>
    <path d="M20 6.5L9.5 17 4 11.5" />
  </Svg>
)

export const IconChevronLeft = (props: IconProps) => (
  <Svg {...props}>
    <path d="M15 5l-7 7 7 7" />
  </Svg>
)

export const IconChevronRight = (props: IconProps) => (
  <Svg {...props}>
    <path d="M9 5l7 7-7 7" />
  </Svg>
)

export const IconChevronDown = (props: IconProps) => (
  <Svg {...props}>
    <path d="M5 9l7 7 7-7" />
  </Svg>
)

export const IconClose = (props: IconProps) => (
  <Svg {...props}>
    <path d="M18 6L6 18M6 6l12 12" />
  </Svg>
)

export const IconSend = (props: IconProps) => (
  <Svg {...props}>
    <path d="M21 3L10.5 13.5M21 3l-6.8 18-3.7-7.5L3 9.8 21 3z" />
  </Svg>
)

export const IconTrash = (props: IconProps) => (
  <Svg {...props}>
    <path d="M4 7h16M9 7V4.5h6V7M6.5 7l.8 12.2a1.5 1.5 0 0 0 1.5 1.3h6.4a1.5 1.5 0 0 0 1.5-1.3L17.5 7" />
  </Svg>
)

export const IconTool = (props: IconProps) => (
  <Svg {...props}>
    <path d="M14.7 6.3a4 4 0 0 0 5.3 5.2l-8 8a2.8 2.8 0 1 1-4-4l6.7-9.2z" />
    <path d="M14.7 6.3L17 4a4 4 0 0 1 3 6.5" />
  </Svg>
)

export const IconLock = (props: IconProps) => (
  <Svg {...props}>
    <rect x="4.5" y="10.5" width="15" height="10" rx="2.5" />
    <path d="M8 10.5V7.5a4 4 0 0 1 8 0v3" />
  </Svg>
)

export const IconFire = (props: IconProps) => (
  <Svg {...props}>
    <path d="M12 3s5 4.2 5 9a5 5 0 0 1-10 0c0-1.7.8-3.2 1.7-4.3.3 1 1 1.8 1.8 2.1C11 8 12 5.5 12 3z" />
  </Svg>
)

export const IconCoin = (props: IconProps) => (
  <Svg {...props}>
    <circle cx="12" cy="12" r="8.5" />
    <path d="M12 7.5v9M14.5 9.8c-.6-.9-1.6-1.3-2.6-1.3-1.3 0-2.4.7-2.4 1.9 0 2.6 5 1.3 5 3.9 0 1.2-1.1 1.9-2.5 1.9-1.1 0-2.1-.4-2.7-1.3" />
  </Svg>
)

export const IconTimer = (props: IconProps) => (
  <Svg {...props}>
    <circle cx="12" cy="13.5" r="7.5" />
    <path d="M12 10v3.5l2.4 1.4M9.5 2.5h5" />
  </Svg>
)

export const IconWarning = (props: IconProps) => (
  <Svg {...props}>
    <path d="M12 4l9 15.5H3L12 4z" />
    <path d="M12 10v4M12 17.2v.1" />
  </Svg>
)

export const IconTrendUp = (props: IconProps) => (
  <Svg {...props}>
    <path d="M3 17l6-6 4 4 8-8" />
    <path d="M15 7h6v6" />
  </Svg>
)

export const IconTrendDown = (props: IconProps) => (
  <Svg {...props}>
    <path d="M3 7l6 6 4-4 8 8" />
    <path d="M15 17h6v-6" />
  </Svg>
)

export const IconTrendFlat = (props: IconProps) => (
  <Svg {...props}>
    <path d="M3 12h14" />
    <path d="M17 8l4 4-4 4" />
  </Svg>
)

export const IconList = (props: IconProps) => (
  <Svg {...props}>
    <path d="M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01" />
  </Svg>
)

export const IconGrid = (props: IconProps) => (
  <Svg {...props}>
    <rect x="3.5" y="3.5" width="7" height="7" rx="1.5" />
    <rect x="13.5" y="3.5" width="7" height="7" rx="1.5" />
    <rect x="3.5" y="13.5" width="7" height="7" rx="1.5" />
    <rect x="13.5" y="13.5" width="7" height="7" rx="1.5" />
  </Svg>
)

export const IconSun = (props: IconProps) => (
  <Svg {...props}>
    <circle cx="12" cy="12" r="4" />
    <path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.2 5.2l1.4 1.4M17.4 17.4l1.4 1.4M18.8 5.2l-1.4 1.4M6.6 17.4l-1.4 1.4" />
  </Svg>
)

export const IconMoon = (props: IconProps) => (
  <Svg {...props}>
    <path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5z" />
  </Svg>
)

export const IconArrowLeft = (props: IconProps) => (
  <Svg {...props}>
    <path d="M19 12H5M11 6l-6 6 6 6" />
  </Svg>
)

export const IconEye = (props: IconProps) => (
  <Svg {...props}>
    <path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z" />
    <circle cx="12" cy="12" r="3" />
  </Svg>
)

export const IconEyeOff = (props: IconProps) => (
  <Svg {...props}>
    <path d="M9.9 5.7A9.5 9.5 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a16 16 0 0 1-2.6 3.4M6.2 6.7A16 16 0 0 0 2.5 12S6 18.5 12 18.5c1.2 0 2.2-.2 3.2-.6" />
    <path d="M3 3l18 18M10 10a3 3 0 0 0 4 4" />
  </Svg>
)
