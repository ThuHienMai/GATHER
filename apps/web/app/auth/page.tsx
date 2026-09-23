'use client';
import { useUser } from '../../components/session';
import Home from '../../components/landing';
export default function Auth() {
  const user = useUser();
  return user ? <main className="shell"><h1 className="text-3xl">Welcome, {user.firstName}</h1><p>You’re signed in with Telegram.</p></main> : <Home />;
}
