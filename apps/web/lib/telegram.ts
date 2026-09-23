export interface TelegramApp {
  initData: string;
  initDataUnsafe?: { start_param?: string };
  ready(): void;
  expand(): void;
  close(): void;
  switchInlineQuery(query: string, chats?: string[]): void;
  openTelegramLink(url: string): void;
  themeParams: Record<string, string>;
  safeAreaInset?: { top: number; bottom: number; left: number; right: number };
  contentSafeAreaInset?: { top: number; bottom: number; left: number; right: number };
  onEvent(event: string, callback: () => void): void;
  offEvent(event: string, callback: () => void): void;
}
declare global { interface Window { Telegram?: { WebApp: TelegramApp } } }
