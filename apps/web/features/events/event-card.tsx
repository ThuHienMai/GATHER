import Link from 'next/link';
import { DateTime } from 'luxon';
import { ArrowUpRight,MapPin,Clock3 } from 'lucide-react';
import type { GatherEvent } from '../../lib/types';
export function EventCard({event}:{event:GatherEvent}){
 const start=event.startAt??event.flexWindowStart;const date=start?DateTime.fromISO(start).setZone(event.timezone):null;
 return <article className="event-card"><div className="date-tile"><span>{date?.toFormat('LLL')??'SOON'}</span><strong>{date?.toFormat('d')??'?'}</strong></div><div className="min-w-0 flex-1"><div className="flex justify-between items-center gap-2"><span className={`status-pill ${event.status==='CANCELLED'?'cancelled':''}`}>{event.status==='OPEN'?(event.startAt?'Open invitation':'Finding a time'):event.status.toLowerCase()}</span><ArrowUpRight size={17} aria-hidden="true"/></div><h2 className="text-xl font-semibold mt-3 mb-3"><Link href={`/events/${event.id}`} className="event-title">{event.title}</Link></h2><p className="detail-line"><Clock3 size={14}/>{event.startAt?date?.toFormat('ccc · HH:mm'):'Time to be decided'}</p><p className="detail-line mt-2"><MapPin size={14}/>{event.locationText||'Location to be decided'}</p>{event.capacity&&<p className="text-xs muted mt-3">Room for {event.capacity}</p>}</div></article>;
}
