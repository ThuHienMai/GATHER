'use client';
import { Suspense,useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { useUser } from '../../../components/session';
import { request } from '../../../lib/api';
import { EventForm } from '../../../features/events/event-form';
function Create(){const user=useUser();const initial=useSearchParams().get('community');const [chosen,setChosen]=useState(initial??'');const q=useQuery({queryKey:['communities'],queryFn:()=>request<{id:string;name:string;timezone:string}[]>('/api/v1/communities'),enabled:!!user});const community=q.data?.find(c=>c.id===chosen)??q.data?.[0];return <main className="shell"><p className="eyebrow mb-3">Good company starts somewhere</p><h1 className="text-3xl font-semibold mb-8">Make a plan</h1>{!user?<p>Open Gather in Telegram to create a plan.</p>:community?<><label htmlFor="create-community">Community</label><select className="field mt-2 mb-6" id="create-community" value={community.id} onChange={e=>setChosen(e.target.value)}>{q.data?.map(c=><option key={c.id} value={c.id}>{c.name}</option>)}</select><EventForm key={community.id} community={community.id} timezone={community.timezone}/></>:<p role="status">{q.error?.message??(q.isPending?'Loading communities…':'Run /join in your registered Telegram group first.')}</p>}</main>;}
export default function Page(){return <Suspense fallback={<p>Loading…</p>}><Create/></Suspense>;}
