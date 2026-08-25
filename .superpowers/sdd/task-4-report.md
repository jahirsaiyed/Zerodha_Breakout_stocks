STATUS: DONE_WITH_CONCERNS
COMMIT: a382928

FILES_CREATED:
- mobile/ (via create-expo-app@4.0.0 blank-typescript template)
- mobile/app.config.ts (replaces app.json; scheme: zbs, typedRoutes, expo-router + expo-secure-store + expo-notifications plugins)
- mobile/.env.development (EXPO_PUBLIC_API_URL=http://localhost:9006)
- mobile/.env.production (EXPO_PUBLIC_API_URL=https://your-domain.com)
- mobile/tailwind.config.js (nativewind/preset, content: app/** + components/**)
- mobile/babel.config.js (jsxImportSource: nativewind + nativewind/babel preset)
- mobile/global.css (@tailwind base/components/utilities)
- mobile/app/index.tsx (minimal Expo Router entry point, View + Text "Loading...")

DEPENDENCIES_INSTALLED:
- expo-router, expo-secure-store, expo-notifications, expo-web-browser, expo-linking (via npx expo install)
- @tanstack/react-query, axios, zustand (via npm --legacy-peer-deps)
- nativewind 4.x, tailwindcss 3.x (v3 required; nativewind/react-native-css-interop needs ~3)
- react-native-safe-area-context, react-native-screens (via npx expo install)
- jest-expo, @testing-library/react-native, @testing-library/jest-native (dev, --legacy-peer-deps)

TYPESCRIPT_VERIFICATION:
npx tsc --noEmit → clean (no output, exit 0)

CONCERNS:
1. tailwindcss downgraded from v4 to v3 — nativewind 4.x (react-native-css-interop 0.2.6) requires
   tailwindcss ~3. This is the correct and documented configuration for NativeWind v4 with Expo SDK 57.
   tailwindcss v4 was initially installed by npm install but was replaced with tailwindcss@3.4.19.

2. npm --legacy-peer-deps was required for @tanstack/react-query, zustand, and test libraries due to
   react-dom peer dep conflict from expo-router's radix-ui dependencies. This is a cosmetic conflict
   only (react-dom is not imported in the app); --legacy-peer-deps is the standard workaround.

3. .env.development and .env.production were force-added (git add -f) because the root .gitignore
   excludes .env* broadly. The values are non-sensitive (localhost URL and a placeholder). Future
   real production secrets must never be committed and .env.production should be removed from git
   tracking once a real deployment is set up.

4. npx expo start not verified (as instructed, skipped in favor of tsc --noEmit check). The scaffold
   is valid: app.config.ts resolves, tsconfig extends expo/tsconfig.base, entry app/index.tsx is
   present, all plugins referenced exist in node_modules.
