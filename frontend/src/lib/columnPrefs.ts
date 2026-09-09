/**
 * Reads a visible-keys list from localStorage, falling back to defaults when the value is
 * missing, unreadable, or malformed. A stored value that parses to a recognized (even empty)
 * array of keys is trusted as-is — including "the user hid everything" — rather than being
 * treated as equivalent to no stored value at all.
 */
export function loadVisibleKeys<T extends string>(storageKey: string, allKeys: readonly T[], defaults: readonly T[]): T[] {
  try {
    const raw = localStorage.getItem(storageKey)
    if (!raw) return [...defaults]
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return [...defaults]
    // A stored empty array is a deliberate "hide everything" choice — trust it.
    // A non-empty stored array that filters down to zero valid keys is stale/corrupted
    // (e.g. an old app version's keys), not user intent — fall back to defaults instead.
    if (parsed.length === 0) return []
    const valid = parsed.filter((k): k is T => typeof k === 'string' && (allKeys as readonly string[]).includes(k))
    return valid.length > 0 ? [...new Set(valid)] : [...defaults]
  } catch {
    return [...defaults]
  }
}

export function saveVisibleKeys(storageKey: string, keys: readonly string[]): void {
  try {
    localStorage.setItem(storageKey, JSON.stringify(keys))
  } catch {
    // localStorage unavailable (private browsing, quota, etc.) — preference just won't persist
  }
}

/**
 * Reads a full column order (every key, none hidden) from localStorage. Unlike
 * `loadVisibleKeys`, a stored value that doesn't cover every known key is treated as stale
 * (e.g. a key was added in a later app version) and discarded in favor of defaults — a
 * reorder-only list has no "hide everything" concept to preserve.
 */
export function loadColumnOrder<T extends string>(storageKey: string, allKeys: readonly T[], defaults: readonly T[]): T[] {
  try {
    const raw = localStorage.getItem(storageKey)
    if (!raw) return [...defaults]
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return [...defaults]
    const valid = parsed.filter((k): k is T => typeof k === 'string' && (allKeys as readonly string[]).includes(k))
    const uniqueValid = [...new Set(valid)]
    return uniqueValid.length === allKeys.length ? uniqueValid : [...defaults]
  } catch {
    return [...defaults]
  }
}
