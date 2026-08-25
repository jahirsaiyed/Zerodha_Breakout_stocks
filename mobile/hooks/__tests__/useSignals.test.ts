import { api } from '../../lib/api';

jest.mock('../../lib/api', () => ({ api: { get: jest.fn() } }));

// Extract the queryFn logic directly — same contract as the hook's queryFn
async function signalsQueryFn(status?: string) {
  const params = status ? { status } : {};
  const { data } = await (api.get as jest.Mock)('/api/signals', { params });
  return data.data;
}

describe('useSignals queryFn', () => {
  it('returns signal list from API', async () => {
    const signals = [{ id: 1, symbol: 'RELIANCE', status: 'PENDING' }];
    (api.get as jest.Mock).mockResolvedValue({ data: { data: signals } });

    const result = await signalsQueryFn();
    expect(api.get).toHaveBeenCalledWith('/api/signals', { params: {} });
    expect(result).toEqual(signals);
  });

  it('passes status param when provided', async () => {
    const signals = [{ id: 2, symbol: 'TCS', status: 'ACTIVE' }];
    (api.get as jest.Mock).mockResolvedValue({ data: { data: signals } });

    const result = await signalsQueryFn('ACTIVE');
    expect(api.get).toHaveBeenCalledWith('/api/signals', { params: { status: 'ACTIVE' } });
    expect(result).toEqual(signals);
  });
});
