## Task 6 Report: Auth Screens + Root Layout

**Status:** DONE

**Commit:** 138173f

**Files created:**
- `mobile/app/_layout.tsx` — root layout with QueryClientProvider (retry:1, staleTime:30s) and restoreSession auth gate; redirects to /(auth)/login when session restore returns false
- `mobile/app/(auth)/_layout.tsx` — auth group Stack layout with headerShown:false
- `mobile/app/(auth)/login.tsx` — email/password login screen; calls useAuthStore.login, navigates to /(tabs)/dashboard on success, shows Alert on failure
- `mobile/app/(auth)/zerodha-connect.tsx` — Zerodha OAuth screen; opens ${EXPO_PUBLIC_API_URL}/api/zerodha/login via WebBrowser.openBrowserAsync (FORM_SHEET), listens for zbs://zerodha-callback deep link, navigates to /(tabs)/dashboard on receipt; includes "Skip for now" fallback

**Concerns:** None. All files match the brief verbatim. The `mobile/app/index.tsx` placeholder was not modified.
