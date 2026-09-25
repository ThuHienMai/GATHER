import http from 'k6/http';
import ws from 'k6/ws';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import { check,sleep } from 'k6';
import { Trend,Rate,Counter } from 'k6/metrics';
const scenario=__ENV.CASE||'feed';const base=__ENV.API_URL||'http://localhost:8081';
if(!/^http:\/\/(localhost|127\.0\.0\.1):8081$/.test(base))throw new Error('These fixtures are restricted to the isolated local test API.');
const key='e2e-only-signing-secret-never-use-in-production';
function uuid(value){const h=crypto.md5(value,'hex');return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`;}
function token(user){const now=Math.floor(Date.now()/1000);const a=encoding.b64encode(JSON.stringify({alg:'HS256',typ:'JWT'}),'rawurl');const b=encoding.b64encode(JSON.stringify({sub:user,iat:now,exp:now+3600}),'rawurl');return `${a}.${b}.${crypto.hmac('sha256',key,`${a}.${b}`,'base64rawurl')}`;}
const latency=new Trend('operation_ms',true);const invalidation=new Trend('invalidation_ms',true);const errors=new Rate('server_errors');const subscribed=new Counter('subscriptions');
const config=scenario==='ws'?{executor:'per-vu-iterations',vus:500,iterations:1,maxDuration:'40s'}:scenario==='rsvp'?{executor:'per-vu-iterations',vus:100,iterations:5,maxDuration:'60s'}:scenario==='webhook'?{executor:'per-vu-iterations',vus:100,iterations:10,maxDuration:'30s'}:{executor:'constant-vus',vus:scenario==='schedule'?10:100,duration:'15s'};
export const options={scenarios:{[scenario]:config},thresholds:{server_errors:['rate<0.01'],checks:['rate>0.99'],...(scenario==='feed'?{operation_ms:['p(95)<200']}:scenario==='rsvp'?{operation_ms:['p(95)<300']}:scenario==='ws'?{invalidation_ms:['p(95)<500'],subscriptions:['count>=500']}:scenario==='schedule'?{operation_ms:['p(95)<100']}:{})}};
export function setup(){for(let attempt=0;attempt<30;attempt++){const ready=http.get(`${base}/actuator/health/readiness`);if(ready.status===200)return; sleep(1);}throw new Error('Test API did not become ready.');}
export default function(){
 const user=uuid(`load-user-${(__VU-1)%500+1}`);const params={headers:{Authorization:`Bearer ${token(user)}`,'Content-Type':'application/json'}};
 const event=uuid('load-event-1');let response;
 if(scenario==='feed'){response=http.get(`${base}/api/v1/events/${event}`,params);latency.add(response.timings.duration);check(response,{'event available':r=>r.status===200});const feed=http.get(`${base}/api/v1/communities/11111111-1111-1111-1111-111111111111/events`,params);check(feed,{'feed available':r=>r.status===200});errors.add(feed.status>=500);sleep(0.5);}
 else if(scenario==='rsvp'){response=http.put(`${base}/api/v1/events/${event}/rsvp`,JSON.stringify({status:__ITER%2===0?'GOING':'MAYBE'}),params);latency.add(response.timings.duration);check(response,{'rsvp accepted':r=>r.status===200});}
 else if(scenario==='schedule'){response=http.get(`${base}/api/v1/events/${uuid('load-flex')}/schedule-recommendations`,params);latency.add(response.timings.duration);check(response,{'ranked 500 attendees':r=>r.status===200&&r.json('slots.0.goingAvailable')===500});sleep(0.2);}
 else if(scenario==='webhook'){response=http.post(`${base}/api/v1/telegram/webhook`,JSON.stringify({update_id:900000001}),{headers:{'Content-Type':'application/json','X-Telegram-Bot-Api-Secret-Token':'e2e-only-webhook-secret'}});latency.add(response.timings.duration);check(response,{'duplicate acknowledged':r=>r.status===200});}
 else if(scenario==='ws'){
  const watched=uuid(`load-event-${(__VU-1)%25+1}`);
  const result=ws.connect(base.replace('http','ws')+'/ws',{headers:{Origin:'http://127.0.0.1:3000'}},socket=>{
   socket.on('open',()=>socket.send(JSON.stringify({type:'AUTH',token:token(user)})));
   socket.on('message',raw=>{const message=JSON.parse(raw);if(message.type==='AUTH_OK')socket.send(JSON.stringify({type:'SUBSCRIBE_EVENT',eventId:watched}));else if(message.type==='SUBSCRIBED'){subscribed.add(1);socket.setTimeout(()=>{const r=http.put(`${base}/api/v1/events/${watched}/rsvp`,JSON.stringify({status:'GOING'}),params);errors.add(r.status>=500);check(r,{'websocket stimulus accepted':r=>r.status===200});},2000);}else if(message.type==='PING')socket.send(JSON.stringify({type:'PONG'}));else if(message.type==='RSVP_UPDATED')invalidation.add(Math.max(0,Date.now()-Date.parse(message.occurredAt)));});
   socket.setTimeout(()=>socket.close(),10000);
  });check(result,{'socket upgraded':r=>r&&r.status===101});return;
 }
 if(__VU===1&&__ITER===0&&response.status!==200)console.error(`First response: ${response.status} ${response.body}`);errors.add(response.status>=500);
}
export function handleSummary(data){return {[`../results/${scenario}.json`]:JSON.stringify(data,null,2)};}
