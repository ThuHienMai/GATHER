import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, expect, test, vi } from 'vitest';
import { EventForm } from '../features/events/event-form';
import { Discussion } from '../features/events/discussion';
import { request } from '../lib/api';
vi.mock('next/navigation', () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));
vi.mock('../components/session', () => ({ useUser: () => ({ id: 'me' }) }));
vi.mock('../lib/api', async importOriginal => ({ ...await importOriginal<object>(), request: vi.fn() }));
afterEach(() => { cleanup(); vi.resetAllMocks(); });
test.each([
  ['San Francisco', 'America/Los_Angeles', '2099-01-02T20:00:00.000Z'],
  ['Tokyo', 'Asia/Tokyo', '2099-01-02T03:00:00.000Z'],
  ['Buenos Aires', 'America/Argentina/Buenos_Aires', '2099-01-02T15:00:00.000Z'],
  ['Berlin', 'Europe/Berlin', '2099-01-02T11:00:00.000Z'],
])('creates a plan in %s using a timezone dropdown', async (city, zone, instant) => {
  vi.mocked(request).mockResolvedValue({ id: 'event' });
  render(<EventForm community="community" />);
  const user = userEvent.setup();
  await user.selectOptions(screen.getByRole('combobox', { name: 'Timezone' }), screen.getByRole('option', { name: city }));
  await user.type(screen.getByLabelText('What’s the plan?'), 'Lunch together');
  const { fireEvent } = await import('@testing-library/react');
  fireEvent.change(screen.getByLabelText('Starts'), { target: { value: '2099-01-02T12:00' } });
  fireEvent.change(screen.getByLabelText('Ends'), { target: { value: '2099-01-02T13:00' } });
  await user.click(screen.getByRole('button', { name: 'Create event' }));
  const options = vi.mocked(request).mock.calls[0][1];
  expect(JSON.parse(options!.body as string)).toMatchObject({ timezone: zone, startAt: instant });
  expect(screen.queryByText(/IANA/)).not.toBeInTheDocument();
});
test('one discussion shows legacy comments and preserves reply compatibility', async () => {
  const comments = [
    { id: 'time', section: 'TIME', body: 'What time?', authorName: 'Alice', authorId: 'other', parentCommentId: null },
    { id: 'place', section: 'LOCATION', body: 'At the station', authorName: 'Bob', authorId: 'other', parentCommentId: null },
  ];
  vi.mocked(request).mockImplementation(async (_path, options) => options?.method ? {} : comments);
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><Discussion eventId="event" closed={false}/></QueryClientProvider>);
  expect(await screen.findByText('What time?')).toBeVisible();
  expect(screen.getByText('At the station')).toBeVisible();
  expect(screen.queryByLabelText('Discussion sections')).not.toBeInTheDocument();
  expect(request).toHaveBeenCalledWith('/api/v1/events/event/comments?section=ALL&page=0');
  const user = userEvent.setup();
  await user.click(screen.getAllByRole('button', { name: 'Reply' })[0]);
  await user.type(screen.getByLabelText('Add to the conversation'), 'Noon works');
  await user.click(screen.getByRole('button', { name: 'Post comment' }));
  const post = vi.mocked(request).mock.calls.find(([, options]) => options?.method === 'POST');
  expect(JSON.parse(post![1]!.body as string)).toMatchObject({ section: 'TIME', parentCommentId: 'time', body: 'Noon works' });
});
