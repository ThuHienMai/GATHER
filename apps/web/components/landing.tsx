export default function Home() {
  const shortName = process.env.NEXT_PUBLIC_TELEGRAM_MINI_APP_SHORT_NAME || 'gather';
  const bot = process.env.NEXT_PUBLIC_TELEGRAM_BOT_USERNAME;
  return <main className="shell">
    <p className="text-sm font-semibold tracking-widest uppercase mb-16">Gather</p>
    <section className="card">
      <p className="text-sm font-semibold text-emerald-800 mb-4">Less planning. More being there.</p>
      <h1 className="text-4xl font-semibold tracking-tight mb-5">Good plans start<br />with your people.</h1>
      <p className="text-lg leading-relaxed mb-8">Find campus hangouts, pick a time together, and know who’s coming. Right inside Telegram.</p>
      {bot ? <a className="button" href={`https://t.me/${bot}/${shortName}`}>Open in Telegram</a>
        : <p role="status">Gather’s Telegram connection is being set up.</p>}
    </section>
  </main>;
}
