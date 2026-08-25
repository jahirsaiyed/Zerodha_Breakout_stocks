import { useState } from 'react';
import { View, Text, Switch, TouchableOpacity, TextInput, Alert, ScrollView } from 'react-native';
import { router } from 'expo-router';
import { useUserConfig } from '../../hooks/queries/useUserConfig';
import { useUpdateConfig } from '../../hooks/mutations/useUpdateConfig';
import { useConnectTelegram } from '../../hooks/mutations/useConnectTelegram';
import { useAuthStore } from '../../store/authStore';

export default function SettingsScreen() {
  const config = useUserConfig();
  const updateConfig = useUpdateConfig();
  const connectTelegram = useConnectTelegram();
  const logout = useAuthStore((s) => s.logout);

  const [botToken, setBotToken] = useState('');

  const toggle = async (field: 'tradingPaused' | 'syncPaused', value: boolean) => {
    try {
      await updateConfig.mutateAsync({ [field]: value });
    } catch {
      Alert.alert('Error', 'Could not update setting.');
    }
  };

  const handleConnectBot = async () => {
    if (!botToken.trim()) return;
    try {
      await connectTelegram.mutateAsync(botToken.trim());
      setBotToken('');
      Alert.alert('Success', 'Telegram bot connected.');
    } catch {
      Alert.alert('Error', 'Could not connect Telegram bot.');
    }
  };

  const handleLogout = async () => {
    await logout();
    router.replace('/(auth)/login');
  };

  const cfg = config.data;

  return (
    <ScrollView className="flex-1 bg-gray-950 px-4 pt-4">

      {/* Zerodha */}
      <Text className="text-gray-400 text-xs uppercase mb-2 mt-4">Zerodha</Text>
      <View className="bg-gray-800 rounded-xl p-4 mb-4 flex-row justify-between items-center">
        <Text className="text-white">
          {cfg?.zerodhaConnected ? 'Connected' : 'Not connected'}
        </Text>
        <TouchableOpacity
          className="bg-orange-500 px-4 py-2 rounded-lg"
          onPress={() => router.push('/(auth)/zerodha-connect')}
        >
          <Text className="text-white text-sm">{cfg?.zerodhaConnected ? 'Reconnect' : 'Connect'}</Text>
        </TouchableOpacity>
      </View>

      {/* Trading controls */}
      <Text className="text-gray-400 text-xs uppercase mb-2">Trading</Text>
      <View className="bg-gray-800 rounded-xl mb-4">
        {[
          { label: 'Pause Trading', field: 'tradingPaused' as const, value: cfg?.tradingPaused },
          { label: 'Pause Sync', field: 'syncPaused' as const, value: cfg?.syncPaused },
        ].map(({ label, field, value }) => (
          <View key={field} className="flex-row justify-between items-center px-4 py-4 border-b border-gray-700 last:border-0">
            <Text className="text-white">{label}</Text>
            <Switch
              value={value ?? false}
              onValueChange={(v) => toggle(field, v)}
              trackColor={{ true: '#3b82f6' }}
            />
          </View>
        ))}
      </View>

      {/* Telegram */}
      <Text className="text-gray-400 text-xs uppercase mb-2">Telegram</Text>
      <View className="bg-gray-800 rounded-xl p-4 mb-4">
        {cfg?.telegramChatId
          ? <Text className="text-green-400 mb-2">Bot connected</Text>
          : <Text className="text-gray-400 mb-2">No bot connected</Text>
        }
        <TextInput
          className="bg-gray-700 text-white rounded-lg px-3 py-2 mb-2"
          placeholder="Bot token"
          placeholderTextColor="#6b7280"
          value={botToken}
          onChangeText={setBotToken}
        />
        <TouchableOpacity
          className="bg-blue-600 rounded-lg py-3 items-center"
          onPress={handleConnectBot}
          disabled={connectTelegram.isPending}
        >
          <Text className="text-white text-sm">Connect Bot</Text>
        </TouchableOpacity>
      </View>

      {/* Logout */}
      <TouchableOpacity
        className="bg-red-700 rounded-xl py-4 items-center mb-8"
        onPress={handleLogout}
      >
        <Text className="text-white font-semibold">Sign Out</Text>
      </TouchableOpacity>

    </ScrollView>
  );
}
