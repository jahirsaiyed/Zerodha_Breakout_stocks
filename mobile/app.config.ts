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
  experiments: {
    typedRoutes: true,
  },
});
