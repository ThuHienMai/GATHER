'use client';
import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { API_URL, getToken } from './api';
export function useEventRealtime(eventId:string,enabled:boolean) {
 const cache=useQueryClient();
 useEffect(()=>{
  if(!enabled)return;
  let stopped=false;let attempt=0;let socket:WebSocket;let timer:ReturnType<typeof setTimeout>;
  function connect(){
   if(!getToken())return;
   socket=new WebSocket(API_URL.replace(/^http/,'ws')+'/ws');
   socket.onopen=()=>socket.send(JSON.stringify({type:'AUTH',token:getToken()}));
   socket.onmessage=message=>{
    const data=JSON.parse(message.data);
    if(data.type==='AUTH_OK'){attempt=0;socket.send(JSON.stringify({type:'SUBSCRIBE_EVENT',eventId}));}
    else if(data.type==='PING')socket.send(JSON.stringify({type:'PONG'}));
    else if(data.eventId===eventId){void cache.invalidateQueries({queryKey:['event',eventId]});void cache.invalidateQueries({queryKey:['events']});}
   };
   socket.onclose=()=>{if(!stopped&&getToken())timer=setTimeout(connect,Math.min(30000,1000*2**Math.min(attempt++,6)*(0.75+Math.random()*0.5)));};
  }
  connect();return()=>{stopped=true;clearTimeout(timer);socket?.close();};
 },[eventId,enabled,cache]);
}
