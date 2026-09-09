export type AdminColumnKey = 'email' | 'role' | 'status' | 'joined'

export const ADMIN_COLUMN_LABELS: Record<AdminColumnKey, string> = {
  email: 'Email',
  role: 'Role',
  status: 'Status',
  joined: 'Joined',
}

export const DEFAULT_ADMIN_COLUMNS: AdminColumnKey[] = ['email', 'role', 'status', 'joined']

export const ADMIN_COLUMNS_STORAGE_KEY = 'zbs.admin.columns'
