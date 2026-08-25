import { useEffect } from 'react';
import { View, Text, TouchableOpacity, ActivityIndicator } from 'react-native';
import * as WebBrowser from 'expo-web-browser';
import * as Linking from 'expo-linking';
import { router } from 'expo-router';
import { api } from '../../lib/api';

WebBrowser.maybeCompleteAuthSession();

export default function ZerodhaConnectScreen() {
  useEffect(() => {
    const sub = Linking.addEventListener('url', handleDeepLink);
    return () => sub.remove();
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
