import { test,expect,type BrowserContext } from '@playwright/test';
import { createHmac } from 'node:crypto';
import { DateTime } from 'luxon';
const enabled=process.env.GATHER_E2E_INTEGRATION==='1';
async function telegram(context:BrowserContext,id:number,name:string){
 const date=Math.floor(Date.now()/1000);const user=JSON.stringify({id,first_name:name});
 const key=createHmac('sha256','WebAppData').update('123:e2e-only').digest();
 const hash=createHmac('sha256',key).update(`auth_date=${date}\nuser=${user}`).digest('hex');
 const initData=new URLSearchParams({auth_date:String(date),user,hash}).toString();
 await context.route('https://telegram.org/js/telegram-web-app.js',route=>route.fulfill({contentType:'application/javascript',body:`window.Telegram={WebApp:{initData:${JSON.stringify(initData)},themeParams:{},ready(){},expand(){},onEvent(){},offEvent(){},switchInlineQuery(){}}};`}));
}
test.describe('real PostgreSQL and API workflow',()=>{
 test.skip(!enabled,'Run scripts/e2e-stack.sh and set GATHER_E2E_INTEGRATION=1');
 test('create, RSVP race, realtime promotion, discussion, cancel, and past view',async({browser})=>{
  const a=await browser.newContext();const b=await browser.newContext();await telegram(a,1001,'Alice');await telegram(b,1002,'Bob');const failures:string[]=[];for(const context of [a,b])context.on('response',response=>{if(response.url().includes('/api/')&&response.status()>=500)failures.push(response.url());});const alice=await a.newPage(),bob=await b.newPage();
  await alice.goto('/events/new?community=11111111-1111-1111-1111-111111111111');
  await alice.getByRole('combobox',{name:'Timezone',exact:true}).selectOption('Europe/Berlin');
  const title=`Dinner ${Date.now()}`;await alice.getByLabel('What’s the plan?').fill(title);
  const start=DateTime.now().setZone('Europe/Berlin').plus({days:1}).startOf('hour');
  await alice.getByLabel('Starts',{exact:true}).fill(start.toFormat("yyyy-MM-dd'T'HH:mm"));await alice.getByLabel('Ends',{exact:true}).fill(start.plus({hours:2}).toFormat("yyyy-MM-dd'T'HH:mm"));await alice.getByLabel('Capacity (optional)').fill('1');
  await alice.getByRole('button',{name:'Create event',exact:true}).click();await expect(alice).toHaveURL(/\/events\/[0-9a-f-]{36}$/);
  await bob.goto(alice.url());await expect(bob.getByRole('heading',{name:title})).toBeVisible();
  await alice.getByRole('button',{name:'Going',exact:true}).click();await expect(alice.getByText('1 / 1 going · 0 maybe · 0 waiting')).toBeVisible();
  await bob.getByRole('button',{name:'Going',exact:true}).click();await expect(bob.getByText(/You’re on the waitlist/)).toBeVisible();
  await alice.getByRole('button',{name:'Withdraw',exact:true}).click();await expect(bob.getByRole('button',{name:'Going',exact:true})).toHaveAttribute('aria-pressed','true');
  await expect(alice.getByLabel('Discussion sections')).toHaveCount(0);
  await alice.getByLabel('Add to the conversation').fill('See you by the station.');await alice.getByRole('button',{name:'Post comment'}).click();await expect(bob.getByText('See you by the station.')).toBeVisible();
  alice.once('dialog',dialog=>dialog.accept());await alice.getByRole('button',{name:'Cancel event',exact:true}).click();await expect(bob.getByText('cancelled',{exact:true})).toBeVisible();
  await alice.goto('/past');await expect(alice.getByRole('heading',{name:title})).toBeVisible();expect(failures).toEqual([]);await a.close();await b.close();
 });
 test('manual availability ranks and finalizes a flexible plan',async({browser})=>{
  const context=await browser.newContext();await telegram(context,1001,'Alice');const page=await context.newPage();await page.goto('/events/new?community=11111111-1111-1111-1111-111111111111');
  await page.getByLabel('What’s the plan?').fill('Karaoke after class');await page.getByLabel('Scheduling mode').selectOption('FLEXIBLE');
  const start=DateTime.now().setZone('Asia/Tokyo').plus({days:2}).startOf('hour');await page.getByLabel('Starts',{exact:true}).fill(start.toFormat("yyyy-MM-dd'T'HH:mm"));await page.getByLabel('Ends',{exact:true}).fill(start.plus({hours:4}).toFormat("yyyy-MM-dd'T'HH:mm"));
  await page.getByRole('button',{name:'Create event',exact:true}).click();await expect(page).toHaveURL(/\/events\/[0-9a-f-]{36}$/);
  await page.getByRole('button',{name:'Going',exact:true}).click();for(let i=0;i<4;i++){const cell=page.getByRole('checkbox').nth(i);await cell.focus();await page.keyboard.press('Space');await expect(cell).toBeChecked();}const dragStart=await page.getByRole('checkbox').nth(4).boundingBox(),dragEnd=await page.getByRole('checkbox').nth(5).boundingBox();await page.mouse.move(dragStart!.x+10,dragStart!.y+10);await page.mouse.down();await page.mouse.move(dragEnd!.x+10,dragEnd!.y+10,{steps:5});await page.mouse.up();await expect(page.getByRole('checkbox').nth(4)).toBeChecked();await expect(page.getByRole('checkbox').nth(5)).toBeChecked();await page.getByRole('button',{name:'Save availability'}).click();await expect(page.getByText('1 going + 0 maybe available').first()).toBeVisible();
  await page.getByRole('button',{name:'Finalize this time'}).first().click();await expect(page.getByText('locked',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Download .ics'})).toBeVisible();await page.setViewportSize({width:390,height:844});await page.screenshot({path:'test-results/gather-event-mobile.png',fullPage:true});await page.goto('/home');await expect(page.getByRole('heading',{level:1})).toContainText('Alice');await expect(page.getByRole('heading',{name:'Karaoke after class'})).toBeVisible();await page.screenshot({path:'test-results/gather-home-mobile.png',fullPage:true});await context.close();
 });
 test('stale edits preserve drafts and reconnect refetches current state',async({browser})=>{
  const a=await browser.newContext(),b=await browser.newContext();await telegram(a,1001,'Alice');await telegram(b,1001,'Alice');
  const first=await a.newPage(),second=await b.newPage();await first.goto('/events/new?community=11111111-1111-1111-1111-111111111111');
  await first.getByLabel('What’s the plan?').fill(`Walk ${Date.now()}`);
  const start=DateTime.now().setZone('Asia/Tokyo').plus({days:3}).startOf('hour');
  await first.getByLabel('Starts',{exact:true}).fill(start.toFormat("yyyy-MM-dd'T'HH:mm"));await first.getByLabel('Ends',{exact:true}).fill(start.plus({hours:1}).toFormat("yyyy-MM-dd'T'HH:mm"));
  await first.getByRole('button',{name:'Create event',exact:true}).click();await expect(first).toHaveURL(/\/events\/[0-9a-f-]{36}$/);await second.goto(first.url());
  await first.getByRole('button',{name:'Edit plan',exact:true}).click();await second.getByRole('button',{name:'Edit plan',exact:true}).click();
  await first.getByLabel('What’s the plan?').fill('Latest walk plan');await first.getByRole('button',{name:'Save changes',exact:true}).click();await expect(first.getByLabel('What’s the plan?')).toHaveCount(0);
  await second.getByLabel('What’s the plan?').fill('My unsaved draft');await second.getByRole('button',{name:'Save changes',exact:true}).click();await expect(second.getByRole('alertdialog',{name:'Event changed'})).toBeVisible();await expect(second.getByLabel('What’s the plan?')).toHaveValue('My unsaved draft');
  await second.getByRole('button',{name:'Discard draft and review latest'}).click();await expect(second.getByRole('heading',{name:'Latest walk plan'})).toBeVisible();
  await b.setOffline(true);await first.getByRole('button',{name:'Going',exact:true}).click();await expect(first.getByText('1 going · 0 maybe · 0 waiting')).toBeVisible();await b.setOffline(false);await expect(second.getByText('1 going · 0 maybe · 0 waiting')).toBeVisible({timeout:15000});
  await a.close();await b.close();
 });

});
