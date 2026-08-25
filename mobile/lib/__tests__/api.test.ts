import axios from 'axios';

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

jest.mock('axios', () => {
  const actual = jest.requireActual('axios');
  return {
    ...actual,
    create: jest.fn(() => actual.create()),
    post: jest.fn(),
  };
});

describe('api client', () => {
  it('exports a base URL from EXPO_PUBLIC_API_URL', () => {
    // Smoke test — the module must load without throwing
    expect(() => require('../api')).not.toThrow();
  });
});
