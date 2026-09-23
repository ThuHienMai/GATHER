'use client';
import Link from 'next/link';
import { Fragment,useState } from 'react';
import { DateTime } from 'luxon';
import { useQuery } from '@tanstack/react-query';
import { ArrowUpRight,Users } from 'lucide-react';
import { useUser } from '../../components/session';
import { request } from '../../lib/api';
import Landing from '../../components/landing';
import { EventCard } from '../../features/events/event-card';
import { Button } from '../../components/ui/button';
import type { GatherEvent } from '../../lib/types';
export default function HomeFeed(){
 const user=useUser();const [chosen,setChosen]=useState('');const [view,setView]=useState('upcoming');const [page,setPage]=useState(0);
 const communities=useQuery({queryKey:['communities'],queryFn:()=>request<{id:string;name:string;timezone:string;role:string}[]>('/api/v1/communities'),enabled:!!user});
 const community=communities.data?.find(c=>c.id===chosen)??communities.data?.[0];
 const feed=useQuery({queryKey:['events',community?.id,view,page],queryFn:()=>request<GatherEvent[]>(`/api/v1/communities/${community!.id}/events?view=${view}&page=${page}`),enabled:!!community});
 function sectionFor(event:GatherEvent){
  if(view==='past')return 'Past events';
  if(!event.startAt)return 'Finding a time';
  const now=DateTime.now().setZone(community?.timezone??event.timezone),start=DateTime.fromISO(event.startAt).setZone(now.zoneName!);
  if(start.toMillis()<=now.plus({hours:2}).toMillis())return 'Happening soon';
  if(start.hasSame(now,'day'))return 'Today';
  if(start<=now.endOf('week'))return 'This week';
  return 'Later';
 }
 if(!user)return <Landing/>;
 return <main className="shell"><header className="brand-row"><span className="brand">gather<span className="brand-dot">●</span></span><span className="avatar" aria-label={user.firstName}>{user.firstName.slice(0,1)}</span></header>
 <div className="eyebrow mt-8">A little less scrolling. A little more living.</div><h1 className="text-4xl font-semibold tracking-tight mt-3 mb-3">What’s the plan,<br/>{user.firstName}?</h1><p className="muted mb-8">Good company. Something to look forward to.</p>
 {communities.isPending?<p role="status">Finding your communities…</p>:communities.error?<p role="alert">{communities.error.message}</p>:!community?<section className="card"><Users className="mb-4"/><h2 className="text-xl mb-3">Your people are one step away.</h2><p>Run /join in your registered Telegram group, then reopen Gather.</p><Button className="mt-4" onClick={()=>void communities.refetch()}>Check membership</Button></section>:<>
 <label htmlFor="community" className="eyebrow">Your community</label><select id="community" className="field mt-2 mb-6" value={community.id} onChange={e=>{setChosen(e.target.value);setPage(0);}}>{communities.data?.map(c=><option key={c.id} value={c.id}>{c.name}</option>)}</select>
 <div className="plan-banner"><div><p className="font-semibold text-lg">Make room for a good time.</p><p className="text-sm mt-1">Dinner, a walk, or something spontaneous.</p></div><Link href={`/events/new?community=${community.id}`} aria-label="Create a plan" className="round-link"><ArrowUpRight/></Link></div>
 <div className="flex items-center justify-between my-7"><div className="flex gap-4"><button className="tab" aria-pressed={view==='upcoming'} onClick={()=>{setView('upcoming');setPage(0);}}>Coming up</button><button className="tab" aria-pressed={view==='past'} onClick={()=>{setView('past');setPage(0);}}>Past plans</button></div><span className="eyebrow">{community.timezone}</span></div>
 {feed.isPending?<p role="status">Finding plans…</p>:feed.error?<p role="alert">{feed.error.message}</p>:feed.data?.length?['Happening soon','Today','This week','Later','Finding a time','Past events'].map(section=>{const plans=feed.data.filter(event=>sectionFor(event)===section);return plans.length?<Fragment key={section}><h2 className="eyebrow mb-3 mt-6">{section}</h2>{plans.map(event=><EventCard key={event.id} event={event}/>)}</Fragment>:null;}):<section className="card text-center"><p className="text-xl mb-2">A little room for spontaneity.</p><p className="muted">No {view==='past'?'past':'upcoming'} plans here yet.</p></section>}
 <div className="flex justify-between mt-6"><button disabled={page===0} onClick={()=>setPage(page-1)}>Previous</button><button disabled={(feed.data?.length??0)<30} onClick={()=>setPage(page+1)}>Next</button></div>
 </>}
 </main>;
}
