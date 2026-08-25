import { create } from 'zustand';

interface PortfolioState {
  ltpMap: Record<number, number>; // signalId → ltp
  setLtp: (signalId: number, ltp: number) => void;
  clearLtp: () => void;
}

export const usePortfolioStore = create<PortfolioState>((set) => ({
  ltpMap: {},
  setLtp: (signalId, ltp) =>
    set((s) => ({ ltpMap: { ...s.ltpMap, [signalId]: ltp } })),
  clearLtp: () => set({ ltpMap: {} }),
}));
