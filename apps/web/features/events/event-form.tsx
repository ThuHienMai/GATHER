'use client';
import { useState } from 'react';
import { useForm,useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { DateTime } from 'luxon';
import { localTimeToInstant } from '../../lib/local-time';
import { useRouter } from 'next/navigation';
import { request,ApiError } from '../../lib/api';
import type { GatherEvent } from '../../lib/types';
const schema = z.object({ title: z.string().trim().min(3).max(120), description: z.string().max(2000), locationText: z.string().max(250), locationUrl: z.string().max(500), start: z.string().min(1), end: z.string().min(1), startOffset: z.string().optional(), endOffset: z.string().optional(), timezone: z.string().min(1), schedulingMode: z.enum(['FIXED','FLEXIBLE']), durationMinutes: z.string(), capacity: z.string().regex(/^$|^[1-9]\d*$/, 'Enter a positive capacity') });
type Fields = z.infer<typeof schema>;
export function EventForm({ community, event, onSaved, timezone = 'Asia/Tokyo' }: { community: string; event?: GatherEvent; onSaved?: () => void; timezone?: string }) {
  const router = useRouter(); const [error,setError]=useState(''); const [initialVersion]=useState(event?.version); const [conflict,setConflict]=useState(false);
  const { register, handleSubmit, control, formState: { errors, isSubmitting } }=useForm<Fields>({ resolver:zodResolver(schema), defaultValues: { schedulingMode:event?.schedulingMode??'FIXED',durationMinutes:event?.durationMinutes?.toString()??'120', title:event?.title??'',description:event?.description??'',locationText:event?.locationText??'',locationUrl:event?.locationUrl??'',timezone:event?.timezone??timezone,capacity:event?.capacity?.toString()??'',start:(event?.startAt??event?.flexWindowStart)?DateTime.fromISO((event?.startAt??event?.flexWindowStart)!).setZone(event!.timezone).toFormat("yyyy-MM-dd'T'HH:mm"):'',end:(event?.endAt??event?.flexWindowEnd)?DateTime.fromISO((event?.endAt??event?.flexWindowEnd)!).setZone(event!.timezone).toFormat("yyyy-MM-dd'T'HH:mm"):'' } });
  const schedulingMode=useWatch({control,name:'schedulingMode'});
  const [start,end,selectedTimezone]=useWatch({control,name:['start','end','timezone']});
  const submit=handleSubmit(async values=>{
    setError('');
    try {
      const times=[localTimeToInstant(values.start,values.timezone,values.startOffset),localTimeToInstant(values.end,values.timezone,values.endOffset)];
      const result=await request<GatherEvent>(event?`/api/v1/events/${event.id}`:`/api/v1/communities/${community}/events`,{method:event?'PATCH':'POST',headers:event?{'If-Match':`"${initialVersion}"`}:undefined,body:JSON.stringify({...values,startAt:times[0],endAt:times[1],schedulingMode:values.schedulingMode,flexWindowStart:values.schedulingMode==='FLEXIBLE'?times[0]:null,flexWindowEnd:values.schedulingMode==='FLEXIBLE'?times[1]:null,durationMinutes:values.schedulingMode==='FLEXIBLE'?Number(values.durationMinutes):null,capacity:values.capacity?Number(values.capacity):null})});
      if(event&&onSaved)onSaved();else {router.push(`/events/${result.id}`);router.refresh();}
    }catch(e){setError(e instanceof Error?e.message:'Unable to save event.');if(e instanceof ApiError&&e.status===412)setConflict(true);}
  });
  return <form onSubmit={submit} className="grid gap-5"><label>Scheduling mode<select className="field" {...register('schedulingMode')}><option value="FIXED">Fixed time</option><option value="FLEXIBLE">Find a time together</option></select></label>{schedulingMode==='FLEXIBLE'&&<label>Duration in minutes<input className="field" type="number" min={30} max={480} {...register('durationMinutes')}/><span>Use Starts and Ends below for your earliest and latest possible times.</span></label>}
    {([['title','What’s the plan?','text'],['description','A little more detail','text'],['locationText','Where?','text'],['locationUrl','Location link','url'],['start','Starts','datetime-local'],['end','Ends','datetime-local'],['timezone','Timezone (IANA)','text'],['capacity','Capacity (optional)','number']] as const).map(([name,label,type])=><div key={name}><label className="block font-semibold mb-2" htmlFor={name}>{label}</label>{name==='description'?<textarea id={name} {...register(name)} className="field"/>:<input id={name} type={type} {...register(name)} className="field"/>}{errors[name]&&<p role="alert">{errors[name]?.message}</p>}</div>)}
    {(['start','end'] as const).map(name=>{const local=name==='start'?start:end;const possible=DateTime.fromISO(local||'',{zone:selectedTimezone}).getPossibleOffsets();return possible.length>1?<label key={name}>{name==='start'?'Start':'End'} UTC offset (required for repeated local time)<select className="field" {...register(name==='start'?'startOffset':'endOffset')} required defaultValue=""><option value="" disabled>Choose which occurrence</option>{possible.map(date=><option key={date.offset} value={date.toFormat('ZZ')}>{date.toFormat('ZZ')} · {date.offsetNameLong}</option>)}</select></label>:null;})}
    {error&&<p role="alert">{error}</p>}{conflict&&<section role="alertdialog" aria-label="Event changed" className="card"><p>The event changed while you were editing. Your draft is still shown above. Reload to discard it and review the latest plan before editing again.</p><button type="button" className="button mt-3" onClick={()=>window.location.reload()}>Discard draft and review latest</button></section>}<button className="button" disabled={isSubmitting}>{isSubmitting?'Saving…':event?'Save changes':'Create event'}</button>
  </form>;
}
