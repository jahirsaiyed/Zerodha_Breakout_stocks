import { View, Text, ScrollView, TouchableOpacity, Alert, ActivityIndicator } from 'react-native';
import { useLocalSearchParams, router } from 'expo-router';
import { useSignal } from '../../hooks/queries/useSignal';
import { useCancelPending } from '../../hooks/mutations/useCancelPending';
import { useClosePosition } from '../../hooks/mutations/useClosePosition';

export default function SignalDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const signal = useSignal(Number(id));
  const cancel = useCancelPending();
  const close = useClosePosition();

  if (signal.isLoading) {
    return (
      <View className="flex-1 bg-gray-950 justify-center items-center">
        <ActivityIndicator color="#fff" />
      </View>
    );
  }
  if (!signal.data) {
    return (
      <View className="flex-1 bg-gray-950 justify-center items-center">
        <Text className="text-gray-400">Signal not found</Text>
      </View>
    );
  }

  const s = signal.data;

  const handleCancel = () => {
    Alert.alert('Cancel Order', `Cancel pending order for ${s.symbol}?`, [
      { text: 'No', style: 'cancel' },
      {
        text: 'Yes, Cancel',
        style: 'destructive',
        onPress: async () => {
          try {
            await cancel.mutateAsync(s.id);
            router.back();
          } catch {
            Alert.alert('Error', 'Could not cancel order.');
          }
        },
      },
    ]);
  };

  const handleClose = () => {
    Alert.alert('Close Position', `Market-sell ${s.symbol}?`, [
      { text: 'No', style: 'cancel' },
      {
        text: 'Yes, Close',
        style: 'destructive',
        onPress: async () => {
          try {
            await close.mutateAsync(s.id);
            router.back();
          } catch {
            Alert.alert('Error', 'Could not close position.');
          }
        },
      },
    ]);
  };

  return (
    <ScrollView className="flex-1 bg-gray-950 px-4 pt-4">
      <Text className="text-white text-2xl font-bold mb-2">{s.symbol}</Text>
      <Text className="text-gray-400 mb-6">{s.status}</Text>

      {[
        ['Entry Price', `₹${s.entryPrice}`],
        ['Target Price', `₹${s.targetPrice}`],
        ['Stop Loss', `₹${s.stopLossPrice}`],
        ['Source', s.source],
      ].map(([label, value]) => (
        <View key={label} className="flex-row justify-between py-3 border-b border-gray-800">
          <Text className="text-gray-400">{label}</Text>
          <Text className="text-white">{value}</Text>
        </View>
      ))}

      <View className="mt-8 gap-3">
        {s.status === 'PENDING' && (
          <TouchableOpacity
            className="bg-red-700 rounded-xl py-4 items-center"
            onPress={handleCancel}
            disabled={cancel.isPending}
          >
            <Text className="text-white font-semibold">Cancel Pending Order</Text>
          </TouchableOpacity>
        )}
        {s.status === 'ACTIVE' && (
          <TouchableOpacity
            className="bg-red-700 rounded-xl py-4 items-center"
            onPress={handleClose}
            disabled={close.isPending}
          >
            <Text className="text-white font-semibold">Close Active Position</Text>
          </TouchableOpacity>
        )}
      </View>
    </ScrollView>
  );
}
