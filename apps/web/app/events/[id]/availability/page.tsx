'use client';
import { use } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useUser } from '../../../../components/session';
import { request } from '../../../../lib/api';
import type { GatherEvent } from '../../../../lib/types';
import { Scheduling } from '../../../../features/events/scheduling';
export default function Availability({params}:{params:Promise<{id:string}>}){const {id}=use(params);const user=useUser();const q=useQuery({queryKey:['event',id],queryFn:()=>request<GatherEvent>(`/api/v1/events/${id}`),enabled:!!user});return <main className="shell"><h1 className="text-3xl">When works for you?</h1>{q.data&&user?<Scheduling event={q.data} organizer={q.data.organizerId===user.id}/>:<p role="status">{q.error?.message??'Open Gather in Telegram to add availability.'}</p>}</main>;}
