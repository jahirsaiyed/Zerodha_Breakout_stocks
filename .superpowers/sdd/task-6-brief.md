### Task 6: Mobile — Auth Screens + Root Layout

**Files:**
- Create: `mobile/app/_layout.tsx`
- Create: `mobile/app/(auth)/_layout.tsx`
- Create: `mobile/app/(auth)/login.tsx`
- Create: `mobile/app/(auth)/zerodha-connect.tsx`

**Interfaces:**
- Consumes: `useAuthStore.restoreSession`, `useAuthStore.login`
- Root layout redirects unauthenticated users to `/(auth)/login`

- [ ] **Step 1: Create root layout with auth gate**

```typescript
// mobile/app/_layout.tsx
import { useEffect } from 'react';
import { Stack, router } from 'expo-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '../global.css';
import { useAuthStore } from '../store/authStore';

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 30_000 } },
});

export default function RootLayout() {
  const restoreSession = useAuthStore((s) => s.restoreSession);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  useEffect(() => {
    restoreSession().then((ok) => {
      if (!ok) router.replace('/(auth)/login');
    });
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      <Stack screenOptions={{ headerShown: false }} />
    </QueryClientProvider>
  );
}
```

- [ ] **Step 2: Create auth group layout**

```typescript
// mobile/app/(auth)/_layout.tsx
import { Stack } from 'expo-router';

export default function AuthLayout() {
  return <Stack screenOptions={{ headerShown: false }} />;
}
```

- [ ] **Step 3: Create login screen**

```typescript
// mobile/app/(auth)/login.tsx
import { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, ActivityIndicator, Alert } from 'react-native';
import { router } from 'expo-router';
import { useAuthStore } from '../../store/authStore';

export default function LoginScreen() {
  const login = useAuthStore((s) => s.login);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const handleLogin = async () => {
    if (!email || !password) return;
    setLoading(true);
    try {
      await login(email, password);
      router.replace('/(tabs)/dashboard');
    } catch {
      Alert.alert('Login failed', 'Invalid email or password.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <View className="flex-1 justify-center px-6 bg-gray-950">
      <Text className="text-white text-3xl font-bold mb-8">Zerodha Breakout</Text>

      <TextInput
        className="bg-gray-800 text-white rounded-lg px-4 py-3 mb-4"
        placeholder="Email"
        placeholderTextColor="#6b7280"
        autoCapitalize="none"
        keyboardType="email-address"
        value={email}
        onChangeText={setEmail}
      />
      <TextInput
        className="bg-gray-800 text-white rounded-lg px-4 py-3 mb-6"
        placeholder="Password"
        placeholderTextColor="#6b7280"
        secureTextEntry
        value={password}
        onChangeText={setPassword}
      />

      <TouchableOpacity
        className="bg-blue-600 rounded-lg py-4 items-center"
        onPress={handleLogin}
        disabled={loading}
      >
        {loading
          ? <ActivityIndicator color="#fff" />
          : <Text className="text-white font-semibold text-base">Sign In</Text>
        }
      </TouchableOpacity>
    </View>
  );
}
```

- [ ] **Step 4: Create Zerodha connect screen**

```typescript
// mobile/app/(auth)/zerodha-connect.tsx
import { useEffect } from 'react';
import { View, Text, TouchableOpacity, ActivityIndicator } from 'react-native';
import * as WebBrowser from 'expo-web-browser';
import * as Linking from 'expo-linking';
import { router } from 'expo-router';
import { api } from '../../lib/api';

WebBrowser.maybeCompleteAuthSession();

export default function ZerodhaConnectScreen() {
  useEffect(() => {
    Linking.addEventListener('url', handleDeepLink);
    return () => Linking.removeAllListeners('url');
  }, []);

  const handleDeepLink = ({ url }: { url: string }) => {
    if (url.startsWith('zbs://zerodha-callback')) {
      router.replace('/(tabs)/dashboard');
    }
  };

  const startZerodhaOAuth = async () => {
    const apiUrl = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:9006';
    await WebBrowser.openBrowserAsync(`${apiUrl}/api/zerodha/login`, {
      presentationStyle: WebBrowser.WebBrowserPresentationStyle.FORM_SHEET,
    });
  };

  return (
    <View className="flex-1 justify-center px-6 bg-gray-950 items-center">
      <Text className="text-white text-2xl font-bold mb-4">Connect Zerodha</Text>
      <Text className="text-gray-400 text-center mb-8">
        Link your Zerodha account to start trading.
      </Text>
      <TouchableOpacity
        className="bg-orange-500 rounded-lg px-8 py-4"
        onPress={startZerodhaOAuth}
      >
        <Text className="text-white font-semibold text-base">Connect with Kite</Text>
      </TouchableOpacity>
      <TouchableOpacity className="mt-6" onPress={() => router.replace('/(tabs)/dashboard')}>
        <Text className="text-gray-500">Skip for now</Text>
      </TouchableOpacity>
    </View>
  );
}
```

- [ ] **Step 5: Commit**

```bash
git add mobile/app/_layout.tsx mobile/app/\(auth\)/
git commit -m "feat: add auth screens and root layout auth gate"
```

---

