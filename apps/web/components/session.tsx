'use client';
import Script from 'next/script';
import { Navigation } from './navigation';
import { useRouter } from 'next/navigation';
import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { request, setToken } from '../lib/api';
import '../lib/telegram';
import type { GatherUser as User } from '../lib/types';
function applyTelegramTheme() {
  const tg=window.Telegram?.WebApp;
  if(!tg)return;
  for(const [key,value] of Object.entries(tg.themeParams??{}))document.documentElement.style.setProperty(`--tg-theme-${key.replaceAll('_','-')}`,value);
  for(const edge of ['top','bottom','left','right'] as const){
    const inset=(tg.safeAreaInset?.[edge]??0)+(tg.contentSafeAreaInset?.[edge]??0);
    document.documentElement.style.setProperty(`--tg-safe-area-${edge}`,`${inset}px`);
  }
}
type Session = { token: string; expiresAt: string; user: User };
const Context = createContext<User | null>(null);
export function useUser() { return useContext(Context); }
export function SessionProvider({ children }: { children: React.ReactNode }) {
  const router=useRouter();
  const [client] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: 1 } } }));
  const [session, setSession] = useState<Session | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const initialize = useCallback(async () => {
    const tg = window.Telegram?.WebApp;
    if (!tg?.initData) { setLoading(false); return; }
    tg.ready(); tg.expand();
    applyTelegramTheme();
    try {
      const result = await request<Session>('/api/v1/auth/telegram', { method: 'POST', body: JSON.stringify({ initData: tg.initData }) });
      setToken(result.token); setSession(result);
      const start = new URLSearchParams(tg.initData).get('start_param');
      if(start && /^event_[0-9a-f-]{36}$/i.test(start))router.replace(`/events/${start.slice(6)}`);
    } catch (e) { setError(e instanceof Error ? e.message : 'Unable to sign in.'); }
    finally { setLoading(false); }
  }, [router]);
  useEffect(() => {
    const expire = () => { setToken(null); setSession(null); client.clear(); setError('Your session expired. Close and reopen Gather in Telegram.'); };
    window.addEventListener('gather-session-expired', expire);
    const timer = session ? setTimeout(expire, Math.max(0, Date.parse(session.expiresAt) - Date.now())) : undefined;
    return () => { window.removeEventListener('gather-session-expired', expire); clearTimeout(timer); };
  }, [session, client]);
  useEffect(()=>{
    const tg=window.Telegram?.WebApp;
    if(!session||!tg?.onEvent)return;
    const names=['themeChanged','safeAreaChanged','contentSafeAreaChanged'];
    names.forEach(name=>tg.onEvent(name,applyTelegramTheme));
    return()=>names.forEach(name=>tg.offEvent(name,applyTelegramTheme));
  },[session]);
  return <QueryClientProvider client={client}><Context.Provider value={session?.user ?? null}>
    <Script src="https://telegram.org/js/telegram-web-app.js" strategy="afterInteractive" onReady={() => { void initialize(); }} onError={() => { setLoading(false); setError('Telegram could not load. Check your connection and reopen Gather.'); }} />
    {loading ? <main className="shell" role="status">Opening Gather…</main> : error ? <main className="shell"><h1 className="text-2xl mb-4">Let’s reconnect</h1><p role="alert">{error}</p></main> : <>{children}{session&&<Navigation/>}</>}
  </Context.Provider></QueryClientProvider>;
}
