'use client';
import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { request } from '../../lib/api';
import type { GatherEvent } from '../../lib/types';
import type { RsvpSummary as Summary } from '../../lib/types';
export function RsvpBar({event}:{event:GatherEvent}){
 const cache=useQueryClient();const [error,setError]=useState('');const [busy,setBusy]=useState(false);
 const q=useQuery({queryKey:['event',event.id,'rsvps'],queryFn:()=>request<Summary>(`/api/v1/events/${event.id}/rsvps`)});
 async function update(status:string|null){setBusy(true);setError('');try{await request(`/api/v1/events/${event.id}/rsvp`,{method:status?'PUT':'DELETE',body:status?JSON.stringify({status}):undefined});await cache.invalidateQueries({queryKey:['event',event.id]});}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
 const closed=['CANCELLED','COMPLETED'].includes(event.status);
 return <section className="card mt-6"><h2 className="text-xl mb-3">Are you in?</h2>{q.data&&<><p aria-live="polite" className="mb-4">{q.data.goingCount}{event.capacity?` / ${event.capacity}`:''} going · {q.data.maybeCount} maybe · {q.data.waitlistedCount} waiting</p>{q.data.myStatus==='WAITLISTED'&&<p className="mb-3">You’re on the waitlist. We’ll let you know when a spot opens.</p>}<div className="flex gap-3 flex-wrap"><button className="button" disabled={busy||closed} aria-pressed={q.data.myStatus==='GOING'} onClick={()=>void update('GOING')}>Going</button><button className="button" disabled={busy||closed} aria-pressed={q.data.myStatus==='MAYBE'} onClick={()=>void update('MAYBE')}>Maybe</button>{q.data.myStatus&&<button className="button" disabled={busy||closed} onClick={()=>void update(null)}>Withdraw</button>}</div><p className="mt-4">{q.data.participants.filter(p=>p.status==='GOING').map(p=>p.firstName).join(', ')}</p></>}{(error||q.error)&&<p role="alert">{error||q.error?.message}</p>}</section>;
}
