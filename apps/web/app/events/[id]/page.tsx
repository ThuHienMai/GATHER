'use client';
import { useEventRealtime } from '../../../lib/use-event-realtime';
import { use, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useUser } from '../../../components/session';
import { request } from '../../../lib/api';
import type { GatherEvent } from '../../../lib/types';
import { EventCard } from '../../../features/events/event-card';
import { CalendarActions } from '../../../features/events/calendar-actions';
import { NotificationPreference } from '../../../features/events/notification-preference';
import { Scheduling } from '../../../features/events/scheduling';
import { Discussion } from '../../../features/events/discussion';
import { RsvpBar } from '../../../features/events/rsvp-bar';
import { EventForm } from '../../../features/events/event-form';
export default function Detail({params}:{params:Promise<{id:string}>}) {
  const {id}=use(params);const user=useUser();const cache=useQueryClient();const [editing,setEditing]=useState(false);const [error,setError]=useState('');
  useEventRealtime(id,!!user);
  const query=useQuery({queryKey:['event',id],queryFn:()=>request<GatherEvent>(`/api/v1/events/${id}`),enabled:!!user});
  const membership=useQuery({queryKey:['community',query.data?.communityId],queryFn:()=>request<{role:string}>(`/api/v1/communities/${query.data!.communityId}`),enabled:!!query.data});
  if(!user)return <main className="shell">Open this event in Telegram to continue.</main>;
  if(query.isPending)return <main className="shell" role="status">Opening the plan…</main>;
  if(query.error||!query.data)return <main className="shell" role="alert">{query.error?.message??'Event unavailable'}</main>;
  const event=query.data;
  const organizer=user.id===event.organizerId||membership.data?.role==='ADMIN';
  async function action(name:string){try{await request(`/api/v1/events/${id}/${name}`,{method:'POST',headers:{'If-Match':`"${event.version}"`}});await cache.invalidateQueries({queryKey:['event',id]});}catch(e){setError((e as Error).message);}}
  return <main className="shell"><a href="/home" className="block mb-6">← Your community</a><EventCard event={event}/><button className="button mb-4" onClick={()=>window.Telegram?.WebApp.switchInlineQuery(`event_${id}`,['groups','users'])}>Share to Telegram</button><p className="mb-6 whitespace-pre-wrap">{event.description}</p>{event.locationUrl&&<a className="button" href={event.locationUrl} target="_blank" rel="noreferrer">Location details</a>}
    <RsvpBar event={event}/>{event.schedulingMode==='FLEXIBLE'&&<Scheduling event={event} organizer={organizer}/> }<Discussion eventId={id} closed={['CANCELLED','COMPLETED'].includes(event.status)}/>{organizer&&!['CANCELLED','COMPLETED'].includes(event.status)&&<section className="card mt-6"><h2 className="text-xl mb-4">Your organizer controls</h2><div className="flex flex-wrap gap-3">{event.status==='OPEN'&&<><button className="button" onClick={()=>setEditing(!editing)}>Edit plan</button>{event.startAt&&<button className="button" onClick={()=>void action('lock')}>Lock planning</button>}</>}<button className="button" onClick={()=>{if(window.confirm('Cancel this event?'))void action('cancel');}}>Cancel event</button></div>{editing&&<EventForm community={event.communityId} event={event} onSaved={()=>{setEditing(false);void cache.invalidateQueries({queryKey:['event',id]});}}/>}</section>}
    {event.startAt&&<CalendarActions eventId={id} cancelled={event.status==='CANCELLED'}/>}<NotificationPreference eventId={id}/>{error&&<p role="alert" className="mt-4">{error}</p>}
  </main>;
}
