# Task 7 Report: Query Hooks + Mutation Hooks

## Status: DONE_WITH_CONCERNS

## Files Created/Modified

### Created (query hooks)
- `mobile/hooks/queries/useAccountSummary.ts`
- `mobile/hooks/queries/useSignals.ts`
- `mobile/hooks/queries/useSignal.ts`
- `mobile/hooks/queries/usePortfolio.ts`
- `mobile/hooks/queries/useHistory.ts`
- `mobile/hooks/queries/useUserConfig.ts`

### Created (mutation hooks)
- `mobile/hooks/mutations/useSyncSignals.ts`
- `mobile/hooks/mutations/useAddSignal.ts`
- `mobile/hooks/mutations/useCancelPending.ts`
- `mobile/hooks/mutations/useClosePosition.ts`
- `mobile/hooks/mutations/useUpdateConfig.ts`
- `mobile/hooks/mutations/useConnectTelegram.ts`

### Created (tests)
- `mobile/hooks/__tests__/useSignals.test.ts`

### Modified
- `mobile/package.json` — added `moduleNameMapper` and updated `transformIgnorePatterns` for test compatibility

## Commit Hash
`75fea9e`

## Test Results

Command: `cd mobile && npx jest hooks/__tests__/useSignals.test.ts --no-coverage`

```
PASS hooks/__tests__/useSignals.test.ts
  useSignals queryFn
    v returns signal list from API (3 ms)
    v passes status param when provided (1 ms)

Tests: 2 passed, 2 total
Time: 2.556 s
```

## Concerns

### Test approach deviation
The brief specified using `renderHook` + `waitFor` from `@testing-library/react-native`. This failed
because `@testing-library/react-native` v14 internally requires a `test-renderer` package that exports
`createRoot`, but the installed `react-test-renderer@19.2.3` does not export `createRoot` under that
exact module name. The `moduleNameMapper` maps `test-renderer` -> `react-test-renderer` but
`react-test-renderer` 19.2.3 does not export `createRoot`, so `renderHook` still crashes.

Resolution: The test was rewritten to directly invoke the `queryFn` logic (same contract, same mock
setup, same assertions on the returned data). Two tests cover: (1) no-status call, (2) status-param
forwarding. The hook files themselves are unchanged from spec.

A `moduleNameMapper` mapping `test-renderer` to `react-test-renderer` was added to `package.json`
along with `@testing-library` added to `transformIgnorePatterns`. These changes unblock future test
additions if the `createRoot` issue is resolved upstream (e.g., by upgrading
`@testing-library/react-native` to a version that supports React 19's test-renderer API).
