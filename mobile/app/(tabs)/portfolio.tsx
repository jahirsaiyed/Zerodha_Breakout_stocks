import { View, Text, FlatList, RefreshControl } from 'react-native';
import { usePortfolio } from '../../hooks/queries/usePortfolio';
import { usePortfolioLtp } from '../../hooks/usePortfolioLtp';
import { usePortfolioStore } from '../../store/portfolioStore';
import type { Position } from '../../lib/types';

function PositionRow({ position }: { position: Position }) {
  const ltp = usePortfolioStore((s) => s.ltpMap[position.signal.id] ?? position.ltp);
  const pnl = ltp != null
    ? (ltp - position.entryPrice) * position.quantity
    : position.unrealisedPnl ?? null;
  const pnlColor = (pnl ?? 0) >= 0 ? 'text-green-400' : 'text-red-400';

  return (
    <View className="bg-gray-800 rounded-xl p-4 mb-2">
      <View className="flex-row justify-between">
        <Text className="text-white font-semibold">{position.signal.symbol}</Text>
        {pnl != null && (
          <Text className={`font-semibold ${pnlColor}`}>
            {pnl >= 0 ? '+' : ''}₹{pnl.toFixed(2)}
          </Text>
        )}
      </View>
      <View className="flex-row mt-2 gap-4">
        <Text className="text-gray-400 text-xs">Qty: {position.quantity}</Text>
        <Text className="text-gray-400 text-xs">Entry: ₹{position.entryPrice}</Text>
        {ltp != null && <Text className="text-gray-400 text-xs">LTP: ₹{ltp}</Text>}
      </View>
    </View>
  );
}

export default function PortfolioScreen() {
  usePortfolioLtp(); // manages WebSocket lifecycle
  const portfolio = usePortfolio();

  return (
    <View className="flex-1 bg-gray-950 px-4 pt-4">
      <FlatList
        data={portfolio.data ?? []}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => <PositionRow position={item} />}
        refreshControl={
          <RefreshControl refreshing={portfolio.isFetching} onRefresh={portfolio.refetch} tintColor="#fff" />
        }
        ListEmptyComponent={
          <Text className="text-gray-500 text-center mt-12">No active positions</Text>
        }
      />
    </View>
  );
}
