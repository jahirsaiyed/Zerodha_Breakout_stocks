import { View, Text, FlatList, RefreshControl } from 'react-native';
import { useHistory } from '../../hooks/queries/useHistory';
import type { ClosedTrade } from '../../lib/types';

function TradeRow({ trade }: { trade: ClosedTrade }) {
  const pnlColor = trade.realisedPnl >= 0 ? 'text-green-400' : 'text-red-400';
  return (
    <View className="bg-gray-800 rounded-xl p-4 mb-2">
      <View className="flex-row justify-between">
        <Text className="text-white font-semibold">{trade.symbol}</Text>
        <Text className={`font-semibold ${pnlColor}`}>
          {trade.realisedPnl >= 0 ? '+' : ''}₹{trade.realisedPnl.toFixed(2)}
        </Text>
      </View>
      <View className="flex-row mt-2 gap-4">
        <Text className="text-gray-400 text-xs">Qty: {trade.quantity}</Text>
        <Text className="text-gray-400 text-xs">Entry: ₹{trade.entryPrice}</Text>
        <Text className="text-gray-400 text-xs">Exit: ₹{trade.exitPrice}</Text>
      </View>
      <Text className="text-gray-500 text-xs mt-1">
        {new Date(trade.closedAt).toLocaleDateString('en-IN')}
      </Text>
    </View>
  );
}

export default function HistoryScreen() {
  const history = useHistory();

  const totalPnl = (history.data ?? []).reduce((sum, t) => sum + t.realisedPnl, 0);
  const totalColor = totalPnl >= 0 ? 'text-green-400' : 'text-red-400';

  return (
    <View className="flex-1 bg-gray-950 px-4 pt-4">
      {history.data && history.data.length > 0 && (
        <View className="bg-gray-800 rounded-xl p-4 mb-4 flex-row justify-between">
          <Text className="text-gray-400">Total Realised P&L</Text>
          <Text className={`font-bold ${totalColor}`}>
            {totalPnl >= 0 ? '+' : ''}₹{totalPnl.toFixed(2)}
          </Text>
        </View>
      )}
      <FlatList
        data={history.data ?? []}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => <TradeRow trade={item} />}
        refreshControl={
          <RefreshControl refreshing={history.isFetching} onRefresh={history.refetch} tintColor="#fff" />
        }
        ListEmptyComponent={
          <Text className="text-gray-500 text-center mt-12">No closed trades</Text>
        }
      />
    </View>
  );
}
