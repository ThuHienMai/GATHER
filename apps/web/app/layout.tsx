import type { Metadata, Viewport } from 'next';
import './globals.css';
import { SessionProvider } from '../components/session';
export const metadata: Metadata = { title: 'Gather', description: 'Telegram-native coordination for spontaneous campus events.' };
export const viewport: Viewport = { width: 'device-width', initialScale: 1, viewportFit: 'cover' };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body><SessionProvider>{children}</SessionProvider></body></html>;
}
