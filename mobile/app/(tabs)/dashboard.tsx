import { View, Text, ScrollView, RefreshControl } from 'react-native';
import { useAccountSummary } from '../../hooks/queries/useAccountSummary';
import { usePortfolio } from '../../hooks/queries/usePortfolio';

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <View className="bg-gray-800 rounded-xl p-4 flex-1 mx-1">
      <Text className="text-gray-400 text-xs mb-1">{label}</Text>
      <Text className="text-white text-xl font-bold">{value}</Text>
    </View>
  );
}

export default function DashboardScreen() {
  const summary = useAccountSummary();
  const portfolio = usePortfolio();

  const refreshing = summary.isFetching || portfolio.isFetching;
  const onRefresh = () => {
    summary.refetch();
    portfolio.refetch();
  };

  const margin =
    summary.data?.availableMargin != null
      ? `₹${summary.data.availableMargin.toLocaleString('en-IN')}`
      : '—';

  const unrealisedPnl =
    portfolio.data?.reduce((sum, p) => sum + (p.unrealisedPnl ?? 0), 0) ?? 0;

  const pnlColor = unrealisedPnl >= 0 ? 'text-green-400' : 'text-red-400';

  return (
    <ScrollView
      className="flex-1 bg-gray-950 px-4 pt-4"
      refreshControl={
        <RefreshControl
          refreshing={refreshing}
          onRefresh={onRefresh}
          tintColor="#fff"
        />
      }
    >
      <Text className="text-gray-400 text-sm mb-4">
        {summary.data?.activePositions ?? 0} /{' '}
        {summary.data?.maxPositions ?? '—'} positions open
      </Text>

      <View className="flex-row mb-4">
        <StatCard label="Available Margin" value={margin} />
        <View className="bg-gray-800 rounded-xl p-4 flex-1 mx-1">
          <Text className="text-gray-400 text-xs mb-1">Unrealised P&amp;L</Text>
          <Text className={`text-xl font-bold ${pnlColor}`}>
            {unrealisedPnl >= 0 ? '+' : ''}₹{unrealisedPnl.toFixed(2)}
          </Text>
        </View>
      </View>
    </ScrollView>
  );
}
