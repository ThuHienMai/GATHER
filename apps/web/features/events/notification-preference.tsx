'use client';
import { useState } from 'react';
import { useQuery,useQueryClient } from '@tanstack/react-query';
import { request } from '../../lib/api';
export function NotificationPreference({eventId}:{eventId:string}){
 const cache=useQueryClient();const [error,setError]=useState('');const q=useQuery({queryKey:['event',eventId,'preference'],queryFn:()=>request<{level:string}>(`/api/v1/events/${eventId}/notification-preference`)});
 return <section className="card mt-6"><label htmlFor="notifications" className="font-semibold block mb-3">Keep me in the loop</label><select id="notifications" className="field" value={q.data?.level??'MUTED'} disabled={q.isPending} onChange={async e=>{try{await request(`/api/v1/events/${eventId}/notification-preference`,{method:'PUT',body:JSON.stringify({level:e.target.value})});await cache.invalidateQueries({queryKey:['event',eventId,'preference']});}catch(e){setError((e as Error).message);}}}><option value="ALL_ACTIVITY">All planning activity</option><option value="IMPORTANT_ONLY">Important changes only</option><option value="MUTED">Muted</option></select><p className="text-sm mt-3">Start the Gather bot in a private chat to receive Telegram notifications.</p>{error&&<p role="alert">{error}</p>}</section>;
}
