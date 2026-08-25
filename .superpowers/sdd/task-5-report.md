# Task 5 Report: Mobile API Client + Types + Auth Store

## STATUS: DONE

## Commit

- Hash: `3f3367b`
- Message: `feat: add API client with Bearer auth, auto-refresh, and auth store`

## Files Created

- `mobile/lib/types.ts` — User, Signal, Position, ClosedTrade, UserConfig, AccountSummary, TokenResponse, ApiResponse<T>
- `mobile/lib/api.ts` — Axios instance with Bearer request interceptor, 401 auto-refresh with isRefreshing flag + pendingRequests queue
- `mobile/store/authStore.ts` — Zustand store with login, logout, restoreSession; tokens in expo-secure-store
- `mobile/lib/__tests__/api.test.ts` — Smoke test verifying api module loads without error

## Files Modified

- `mobile/babel.config.js` — Skip nativewind/babel preset in test environment to avoid react-native-worklets dependency
- `mobile/package.json` — Added jest config (jest-expo preset, node testEnvironment, empty setupFiles override) and devDependencies: jest@29, babel-preset-expo, @react-native/jest-preset@0.86

## Test Results

```
PASS lib/__tests__/api.test.ts (9.82 s)
  api client
    √ exports a base URL from EXPO_PUBLIC_API_URL (4701 ms)

Test Suites: 1 passed, 1 total
Tests:       1 passed, 1 total
```

## Bug Fix (b739c81)

**Problem:** When a token refresh failed, `pendingRequests = []` discarded all queued callbacks without settling their promises. Any concurrent request that entered the queue while a refresh was in-flight would stall indefinitely.

**Fix in `mobile/lib/api.ts`:**
- Changed `pendingRequests` element type from `(token: string) => void` to `{ resolve: (token: string) => void; reject: (err: unknown) => void }`.
- On success: `pendingRequests.forEach(({ resolve }) => resolve(newAccess))` — unchanged behaviour.
- On failure: `pendingRequests.forEach(({ reject }) => reject(refreshError))` — now rejects each waiting promise before clearing the queue.
- The `catch` clause now binds `refreshError` (was bare `catch {}`) so it can be forwarded to pending rejects.

**Tests:** 1 passed (smoke test, `lib/__tests__/api.test.ts`).

## Concerns

- `@react-native/jest-preset` must be pinned to `^0.86.x` (matching react-native 0.86) — 0.87 references `react-native/src/setup-env.js` which does not exist in RN 0.86.
- `babel.config.js` now conditionally skips `nativewind/babel` in test env; this is intentional and does not affect the Metro/Expo bundler.
