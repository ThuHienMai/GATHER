'use client';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { API_URL,getToken,request } from '../../lib/api';
export function CalendarActions({eventId,cancelled=false}:{eventId:string;cancelled?:boolean}){
 const [error,setError]=useState('');
 const googleLink=useQuery({queryKey:['event',eventId,'google-calendar'],queryFn:()=>request<{url:string}>(`/api/v1/events/${eventId}/calendar/google`),enabled:!cancelled});
 async function download(){try{const response=await fetch(`${API_URL}/api/v1/events/${eventId}/calendar.ics`,{headers:{Authorization:`Bearer ${getToken()}`}});if(!response.ok)throw new Error('Calendar export is unavailable. Reopen Gather if your session expired.');const url=URL.createObjectURL(await response.blob());const a=document.createElement('a');a.href=url;a.download=`gather-${eventId}.ics`;a.click();setTimeout(()=>URL.revokeObjectURL(url),10000);}catch(e){setError((e as Error).message);}}

 return <section className="card mt-6"><h2 className="text-xl mb-4">Put it on your calendar</h2><div className="flex gap-3 flex-wrap">{googleLink.data&&!cancelled&&<a className="button" href={googleLink.data.url} target="_blank" rel="noreferrer">Google Calendar</a>}<button className="button" onClick={()=>void download()}>Download .ics</button></div><p className="text-sm mt-3">One-time export. Check Gather for later changes.</p>{error&&<p role="alert">{error}</p>}</section>;
}
