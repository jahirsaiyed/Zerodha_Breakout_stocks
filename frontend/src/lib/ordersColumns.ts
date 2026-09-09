export type OrderColumnKey = 'type' | 'kind' | 'qty' | 'price' | 'status' | 'zerodhaId' | 'placed'

export const ORDER_COLUMN_LABELS: Record<OrderColumnKey, string> = {
  type: 'Type',
  kind: 'Kind',
  qty: 'Qty',
  price: 'Price',
  status: 'Status',
  zerodhaId: 'Zerodha ID',
  placed: 'Placed',
}

export const DEFAULT_ORDER_COLUMNS: OrderColumnKey[] = ['type', 'kind', 'qty', 'price', 'status', 'zerodhaId', 'placed']

export const ORDER_COLUMNS_STORAGE_KEY = 'zbs.orders.columns'
