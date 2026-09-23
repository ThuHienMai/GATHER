import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, expect, test, vi } from 'vitest';
import { RsvpBar } from '../features/events/rsvp-bar';
import type { GatherEvent } from '../lib/types';
afterEach(() => vi.unstubAllGlobals());
test('waitlisted users see their status and can withdraw', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ goingCount: 1, maybeCount: 0, waitlistedCount: 1, myStatus: 'WAITLISTED', participants: [] }), { status: 200 })));
  render(<QueryClientProvider client={new QueryClient()}><RsvpBar event={{ id: 'one', status: 'OPEN', capacity: 1 } as GatherEvent} /></QueryClientProvider>);
  expect(await screen.findByText(/You’re on the waitlist/)).toBeVisible();
  expect(screen.getByRole('button', { name: 'Withdraw' })).toBeEnabled();
});
