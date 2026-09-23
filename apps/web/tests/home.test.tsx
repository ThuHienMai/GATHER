import { render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import Home from '../components/landing';
afterEach(() => vi.unstubAllEnvs());
test('offers a Telegram entry point when configured', () => {
  vi.stubEnv('NEXT_PUBLIC_TELEGRAM_BOT_USERNAME', 'gather_test_bot');
  render(<Home />);
  expect(screen.getByRole('link', { name: 'Open in Telegram' })).toHaveAttribute('href', 'https://t.me/gather_test_bot/gather');
});
