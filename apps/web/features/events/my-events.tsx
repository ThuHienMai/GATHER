'use client';
import Link from 'next/link';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { request } from '../../lib/api';
import { useUser } from '../../components/session';
import type { GatherEvent } from '../../lib/types';
import { EventCard } from './event-card';
export function MyEvents({past=false}:{past?:boolean}){
 const user=useUser();const [page,setPage]=useState(0);const q=useQuery({queryKey:['events','mine',past,page],queryFn:()=>request<GatherEvent[]>(`/api/v1/me/events?view=${past?'past':'upcoming'}&page=${page}`),enabled:!!user});
 return <main className="shell"><p className="eyebrow mb-4">Your calendar, with company</p><h1 className="text-3xl font-semibold mb-6">{past?'Good times, remembered.':'Your plans'}</h1><div className="flex gap-6 mb-6"><Link href="/my-events">Upcoming</Link><Link href="/past">Past events</Link></div>{!user?<p>Open Gather in Telegram to see your plans.</p>:q.isPending?<p role="status">Loading your plans…</p>:q.error?<p role="alert">{q.error.message}</p>:q.data?.length?q.data.map(e=><EventCard key={e.id} event={e}/>):<section className="card">Nothing here yet. Find your next plan on Home.</section>}<div className="flex justify-between mt-6"><button disabled={page===0} onClick={()=>setPage(page-1)}>Previous</button><button disabled={(q.data?.length??0)<30} onClick={()=>setPage(page+1)}>Next</button></div></main>;
}
