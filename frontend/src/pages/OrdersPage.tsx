import { useState } from 'react'
import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import api from '../lib/api'
import type { Order } from '../lib/types'
import { CustomizePanel } from '../components/CustomizePanel'
import { loadColumnOrder, saveVisibleKeys } from '../lib/columnPrefs'
import {
  DEFAULT_ORDER_COLUMNS,
  ORDER_COLUMNS_STORAGE_KEY,
  ORDER_COLUMN_LABELS,
  type OrderColumnKey,
} from '../lib/ordersColumns'

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
  ADD:         'Add Quantity',
}

const ORDER_COLUMN_OPTIONS = DEFAULT_ORDER_COLUMNS.map(key => ({ key, label: ORDER_COLUMN_LABELS[key] }))

export function OrdersPage() {
  const [columns, setColumnsState] = useState<OrderColumnKey[]>(() =>
    loadColumnOrder(ORDER_COLUMNS_STORAGE_KEY, DEFAULT_ORDER_COLUMNS, DEFAULT_ORDER_COLUMNS))

  const handleColumnsChange = (keys: string[]) => {
    const next = keys as OrderColumnKey[]
    setColumnsState(next)
    saveVisibleKeys(ORDER_COLUMNS_STORAGE_KEY, next)
  }

  const { data: orders = [], isLoading } = useQuery<Order[]>({
    queryKey: ['orders'],
    queryFn: () => api.get('/portfolio/orders').then(r => r.data),
    refetchInterval: 30_000,
  })

  const columnCells: Record<OrderColumnKey, (o: Order) => ReactNode> = {
    type: o => TYPE_LABEL[o.type] ?? o.type,
    kind: o => <span className="text-gray-500 text-xs">{o.orderKind}</span>,
    qty: o => o.quantity,
    price: o => o.price != null ? `₹${Number(o.price).toFixed(2)}` : '—',
    status: o => (
      <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_COLOR[o.status] ?? 'bg-gray-100 text-gray-500'}`}>
        {o.status}
      </span>
    ),
    zerodhaId: o => <span className="font-mono text-xs text-gray-400">{o.zerodhaOrderId ?? '—'}</span>,
    placed: o => (
      <span className="text-xs text-gray-400">
        {new Date(o.placedAt).toLocaleString('en-IN', { dateStyle: 'short', timeStyle: 'short' })}
      </span>
    ),
  }

  return (
    <div className="p-4 sm:p-8">
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-gray-950">Orders</h1>
          <p className="text-sm text-gray-500">All entry and exit orders placed via Zerodha</p>
        </div>
        <CustomizePanel
          label="Customize columns"
          options={ORDER_COLUMN_OPTIONS}
          visibleKeys={columns}
          onChange={handleColumnsChange}
          defaultKeys={DEFAULT_ORDER_COLUMNS}
          reorderOnly
        />
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
                <th className="px-5 py-3 text-xs font-medium text-gray-400 whitespace-nowrap">Symbol</th>
                {columns.map(key => (
                  <th key={key} className="px-5 py-3 text-xs font-medium text-gray-400 whitespace-nowrap">
                    {ORDER_COLUMN_LABELS[key]}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {orders.map(o => (
                <tr key={o.id} className="border-b border-gray-50 last:border-0 hover:bg-gray-50/50">
                  <td className="px-5 py-3.5 font-medium text-gray-900">{o.symbol}</td>
                  {columns.map(key => (
                    <td key={key} className="px-5 py-3.5 whitespace-nowrap">{columnCells[key](o)}</td>
                  ))}
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
