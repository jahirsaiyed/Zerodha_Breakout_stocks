import { ExpoConfig, ConfigContext } from 'expo/config';

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: 'Zerodha Breakout',
  slug: 'zerodha-breakout',
  version: '1.0.0',
  scheme: 'zbs',
  platforms: ['ios', 'android'],
  ios: {
    supportsTablet: true,
    bundleIdentifier: 'com.trading.zerodhabreakout',
  },
  android: {
    package: 'com.trading.zerodhabreakout',
    adaptiveIcon: {
      backgroundColor: '#ffffff',
    },
  },
  plugins: [
    'expo-router',
    'expo-secure-store',
    ['expo-notifications', { icon: './assets/icon.png', color: '#ffffff' }],
  ],
  updates: {
    url: 'https://u.expo.dev/5cc1a048-c76f-4ecc-bdc0-16f20f32f90f',
  },
  runtimeVersion: {
    policy: 'appVersion',
  },
  experiments: {
    typedRoutes: true,
  },
  extra: {
    eas: {
      projectId: '5cc1a048-c76f-4ecc-bdc0-16f20f32f90f',
    },
  },
});
