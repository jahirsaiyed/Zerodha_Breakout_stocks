import { useState } from 'react';
import { View, Text, FlatList, TouchableOpacity, RefreshControl, Alert } from 'react-native';
import { router } from 'expo-router';
import { useSignals } from '../../hooks/queries/useSignals';
import { useSyncSignals } from '../../hooks/mutations/useSyncSignals';
import type { Signal } from '../../lib/types';

const STATUS_FILTERS = ['ALL', 'PENDING', 'ACTIVE', 'CLOSED', 'CANCELLED'] as const;

const STATUS_COLOR: Record<string, string> = {
  PENDING: 'text-yellow-400',
  ACTIVE: 'text-green-400',
  CLOSED: 'text-gray-400',
  CANCELLED: 'text-red-400',
};

function SignalRow({ signal }: { signal: Signal }) {
  return (
    <TouchableOpacity
      className="bg-gray-800 rounded-xl p-4 mb-2"
      onPress={() => router.push(`/signals/${signal.id}`)}
    >
      <View className="flex-row justify-between items-center">
        <Text className="text-white font-semibold text-base">{signal.symbol}</Text>
        <Text className={STATUS_COLOR[signal.status] ?? 'text-gray-400'}>
          {signal.status}
        </Text>
      </View>
      <View className="flex-row mt-2 gap-4">
        <Text className="text-gray-400 text-xs">Entry: ₹{signal.entryPrice}</Text>
        <Text className="text-gray-400 text-xs">Target: ₹{signal.targetPrice}</Text>
        <Text className="text-gray-400 text-xs">SL: ₹{signal.stopLossPrice}</Text>
      </View>
    </TouchableOpacity>
  );
}

export default function SignalsScreen() {
  const [activeFilter, setActiveFilter] = useState<string>('ALL');
  const status = activeFilter === 'ALL' ? undefined : activeFilter;
  const signals = useSignals(status);
  const sync = useSyncSignals();

  const handleSync = async () => {
    try {
      await sync.mutateAsync();
    } catch {
      Alert.alert('Sync failed', 'Could not sync signals from Google Sheet.');
    }
  };

  return (
    <View className="flex-1 bg-gray-950 px-4 pt-4">
      {/* Filter tabs */}
      <View className="flex-row mb-4 gap-2 flex-wrap">
        {STATUS_FILTERS.map((f) => (
          <TouchableOpacity
            key={f}
            className={`px-3 py-1 rounded-full border ${
              activeFilter === f
                ? 'bg-blue-600 border-blue-600'
                : 'border-gray-600'
            }`}
            onPress={() => setActiveFilter(f)}
          >
            <Text className="text-white text-xs">{f}</Text>
          </TouchableOpacity>
        ))}
        <TouchableOpacity
          className="px-3 py-1 rounded-full bg-gray-700 ml-auto"
          onPress={handleSync}
          disabled={sync.isPending}
        >
          <Text className="text-white text-xs">{sync.isPending ? 'Syncing…' : 'Sync'}</Text>
        </TouchableOpacity>
      </View>

      <FlatList
        data={signals.data ?? []}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => <SignalRow signal={item} />}
        refreshControl={
          <RefreshControl refreshing={signals.isFetching} onRefresh={signals.refetch} tintColor="#fff" />
        }
        ListEmptyComponent={
          <Text className="text-gray-500 text-center mt-12">No signals</Text>
        }
      />
    </View>
  );
}
