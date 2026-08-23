import { useQuery } from '@tanstack/react-query'
import api from '../lib/api'
import type { Order } from '../lib/types'

const STATUS_COLOR: Record<string, string> = {
  PENDING:   'bg-amber-50 text-amber-700',
  FILLED:    'bg-emerald-50 text-emerald-700',
  CANCELLED: 'bg-gray-100 text-gray-500',
  REJECTED:  'bg-red-50 text-red-700',
}

const TYPE_LABEL: Record<string, string> = {
  ENTRY:       'Entry',
  EXIT_TARGET: 'Exit (Target)',
  EXIT_SL:     'Exit (SL)',
  EXIT_MANUAL: 'Exit (Manual)',
}

export function OrdersPage() {
  const { data: orders = [], isLoading } = useQuery<Order[]>({
    queryKey: ['orders'],
    queryFn: () => api.get('/portfolio/orders').then(r => r.data),
    refetchInterval: 30_000,
  })

  return (
    <div className="p-4 sm:p-8">
      <div className="mb-6">
        <h1 className="text-xl font-semibold text-gray-950">Orders</h1>
        <p className="text-sm text-gray-500">All entry and exit orders placed via Zerodha</p>
      </div>

      <div className="rounded-xl border border-gray-200 bg-white">
        {isLoading ? (
          <div className="py-12 text-center text-sm text-gray-400">Loading…</div>
        ) : orders.length === 0 ? (
          <div className="py-12 text-center text-sm text-gray-400">No orders yet</div>
        ) : (
          <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 text-left">
                {['Symbol','Type','Kind','Qty','Price','Status','Zerodha ID','Placed'].map(h => (
                  <th key={h} className="px-5 py-3 text-xs font-medium text-gray-400">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {orders.map(o => (
                <tr key={o.id} className="border-b border-gray-50 last:border-0 hover:bg-gray-50/50">
                  <td className="px-5 py-3.5 font-medium text-gray-900">{o.symbol}</td>
                  <td className="px-5 py-3.5 text-gray-600">{TYPE_LABEL[o.type] ?? o.type}</td>
                  <td className="px-5 py-3.5 text-gray-500 text-xs">{o.orderKind}</td>
                  <td className="px-5 py-3.5 text-gray-600">{o.quantity}</td>
                  <td className="px-5 py-3.5 text-gray-600">
                    {o.price != null ? `₹${Number(o.price).toFixed(2)}` : '—'}
                  </td>
                  <td className="px-5 py-3.5">
                    <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_COLOR[o.status] ?? 'bg-gray-100 text-gray-500'}`}>
                      {o.status}
                    </span>
                  </td>
                  <td className="px-5 py-3.5 font-mono text-xs text-gray-400">
                    {o.zerodhaOrderId ?? '—'}
                  </td>
                  <td className="px-5 py-3.5 text-xs text-gray-400">
                    {new Date(o.placedAt).toLocaleString('en-IN', { dateStyle: 'short', timeStyle: 'short' })}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        )}
      </div>
    </div>
  )
}
