'use client';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Home,CalendarDays,Plus,Settings } from 'lucide-react';
export function Navigation(){
 const pathname=usePathname();
 return <nav aria-label="Main navigation" className="bottom-nav">{[{href:'/home',label:'Home',icon:Home},{href:'/my-events',label:'My Events',icon:CalendarDays},{href:'/events/new',label:'Create',icon:Plus},{href:'/settings',label:'Settings',icon:Settings}].map(({href,label,icon:Icon})=><Link key={href} href={href} aria-current={pathname===href?'page':undefined}><Icon size={21} aria-hidden="true"/><span>{label}</span></Link>)}</nav>;
}
