import { screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { renderWithRouter } from '../utils/test-utils';
import Support from './Support';

// Mock the userStore
vi.mock('../stores/userStore', () => ({
  default: {
    user: {
      id: 1,
      firstName: 'Test',
      lastName: 'User',
      loginId: 'testuser',
      active: true,
      userProfile: 'SUPPORT',
      version: 1,
      lastModifiedTimestamp: new Date().toISOString()
    },
    authorization: 'SUPPORT',
    isLoading: false,
    error: null
  }
}));

describe('Support Page', () => {
  it('renders the Support / Admin View heading', () => {
    renderWithRouter(<Support />);
    expect(screen.getByText(/Support/i)).toBeDefined();
  });
});
