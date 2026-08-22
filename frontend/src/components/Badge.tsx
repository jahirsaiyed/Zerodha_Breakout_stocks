type Variant = 'green' | 'red' | 'yellow' | 'blue' | 'gray' | 'indigo'

const styles: Record<Variant, string> = {
  green:  'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  red:    'bg-red-50 text-red-700 ring-red-600/20',
  yellow: 'bg-amber-50 text-amber-700 ring-amber-600/20',
  blue:   'bg-blue-50 text-blue-700 ring-blue-600/20',
  gray:   'bg-gray-100 text-gray-600 ring-gray-500/20',
  indigo: 'bg-indigo-50 text-indigo-700 ring-indigo-600/20',
}

export function Badge({ label, variant }: { label: string; variant: Variant }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${styles[variant]}`}>
      {label}
    </span>
  )
}

export function statusVariant(status: string): Variant {
  switch (status) {
    case 'ACTIVE':       return 'green'
    case 'PENDING_ENTRY': return 'blue'
    case 'CLOSED_TARGET': return 'green'
    case 'CLOSED_SL':    return 'red'
    case 'CLOSED_MANUAL': return 'gray'
    case 'CANCELLED':    return 'gray'
    case 'EXPIRED':      return 'gray'
    default:             return 'gray'
  }
}

export function statusLabel(status: string): string {
  switch (status) {
    case 'PENDING_ENTRY':  return 'Pending'
    case 'ACTIVE':         return 'Active'
    case 'CANCELLED':      return 'Cancelled'
    case 'CLOSED_TARGET':  return 'Target Hit'
    case 'CLOSED_SL':      return 'Stop Loss'
    case 'CLOSED_MANUAL':  return 'Manual Exit'
    case 'EXPIRED':        return 'Expired'
    default:               return status
  }
}
