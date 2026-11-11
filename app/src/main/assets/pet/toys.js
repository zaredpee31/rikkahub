// 玩具箱、房间里会撞来撞去的东西、崽崽自己找事干、眼睛先转
(function(){
const A=window.PetApp;if(!A||!A.body)return;
const THREE=A.THREE,P=A.P,body=A.body,root=A.root,pet=A.pet,scene=A.scene,cam=A.cam,M=A.M,mesh=A.mesh;
const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
const pick=a=>a[Math.floor(Math.random()*a.length)];
const $=s=>document.querySelector(s);
const cv=document.getElementById('c');
const V3=THREE.Vector3;
const ray=new THREE.Raycaster(),ndc=new THREE.Vector2(),tv=new V3(),tv2=new V3();
const X0=-2.5,X1=2.5,Z0=-2.3,Z1=1.9;
function setRay(x,y){ndc.set(x/innerWidth*2-1,-(y/innerHeight)*2+1);ray.setFromCamera(ndc,cam)}
const plane=new THREE.Plane(new V3(0,1,0),0);
function floorAt(x,y,h){setRay(x,y);plane.constant=-(h||0);const o=new V3();return ray.ray.intersectPlane(plane,o)?o:null}
function under(o,r){while(o){if(o===r)return true;o=o.parent}return false}

const T={
  fetch:['啦！','给你！','再扔一次','球球回来啦'],
  chase:['等等我！','球球别跑','我来啦'],
  tired:['好累，想歇一会儿','玩不动了……'],
  startle:['吓我一跳！','什么声音！','杯子倒了！'],
  fix:['扶好啦','放回去了','嗯，摆正了'],
  oops:['……','不是我干的','它自己倒的'],
  nap:['在垫子上眯一会儿','垫子软软的'],
  wake:['醒啦','刚才梦到奶酪了'],
  bat:['拨一下','嘿！','球球过来'],
  pop:['抓到了！','啪！','又一个','泡泡破啦'],
  bubble:['泡泡！','好多泡泡','我要夹住它'],
  hide:['我要藏起来啦','不许偷看'],
  found:['被你找到啦！','妈妈好厉害','嘿嘿，在这儿'],
  miss:['不在这儿～','猜错啦'],
  here:['我在这儿呢','这边这边'],
  crouch:['……','盯——'],
  caught:['抓到了！','嗷呜！','是我的了'],
  nocatch:['差一点！','它跑了','再来'],
  teaser:['那是什么','羽毛！']
};

// ---------- 房间里的球、杯子、垫子 ----------
const ballT=(()=>{const c=document.createElement('canvas');c.width=128;c.height=64;const g=c.getContext('2d');['#F4A261','#FFFFFF','#7FC4E0','#FFFFFF'].forEach((col,i)=>{g.fillStyle=col;g.fillRect(i*32,0,32,64)});const t=new THREE.CanvasTexture(c);t.encoding=THREE.sRGBEncoding;return t})();
function thing(m,r,x,z,kind,o){return Object.assign({m,r,x,z,y:r,vx:0,vy:0,vz:0,kind,fr:1.4,bo:.5,held:false},o||{})}
const ball=thing(mesh(new THREE.SphereGeometry(.18,20,14),M('#ffffff',{map:ballT,roughness:.5}),-.9,.18,1.0),.18,-.9,1.0,'ball');

const cupG=new THREE.Group();scene.add(cupG);
const cupPiv=new THREE.Group();cupG.add(cupPiv);
const cupM=M('#7FC4E0',{roughness:.5});
mesh(new THREE.CylinderGeometry(.16,.14,.32,20),cupM,0,.16,0,cupPiv);
mesh(new THREE.TorusGeometry(.08,.025,8,16),cupM,.17,.17,0,cupPiv);
mesh(new THREE.CircleGeometry(.13,20),M('#FFF6E8'),0,.305,0,cupPiv).rotation.x=-Math.PI/2;
const cup=thing(cupG,.18,-1.7,.2,'cup',{fr:5,bo:.3,tip:0,fallen:false,yaw:0});

const CUSH={x:.95,z:-1.85};
const cush=new THREE.Group();cush.position.set(CUSH.x,0,CUSH.z);scene.add(cush);
mesh(A.rbox(1.25,.14,1.0,.07,4),M('#F4D9C6',{roughness:1}),0,.07,0,cush);
mesh(A.rbox(1.0,.06,.78,.03,3),M('#FBEDE1',{roughness:1}),0,.15,0,cush);

const objs=[ball,cup];
let pvx=0,pvz=0,lpx=P.x,lpz=P.z;

let toy=null,chase=null,fetches=0,nap=null,cups=null,focus=null,crouch=0;
const hidden=()=>!!(cups&&cups.hidden);

function free(){
  const S=A.S;
  return !S.out&&!S.sleeping&&!A.onBed&&!A.lift&&!A.dragFood&&!(window.PetFx&&PetFx.busy())&&!cups&&!A.busy();
}
function petSolid(){return !hidden()&&P.y<.3&&!A.lift&&!(window.PetFx&&PetFx.busy())&&!A.S.out}
function approach(x,z,off){
  const dx=x-P.x,dz=z-P.z,d=Math.hypot(dx,dz)||1;
  return {x:clamp(x-dx/d*off,X0+.6,X1-.6),z:clamp(z-dz/d*off,Z0+.4,Z1-.4)};
}
function reward(){
  const r=A.act('play');
  if(r.ok){A.happy=2;A.hearts(3);A.toast(r.msg)}
  else{A.toast(r.msg);if(A.S.energy<15)A.say(pick(T.tired))}
}
function canPlay(){
  const S=A.S;
  if(S.out){A.toast('崽崽出门了，还没回来');return false}
  if(S.sleeping){A.toast('崽崽在睡觉');return false}
  if(S.energy<15){A.say(pick(T.tired));A.toast('崽崽太累了，玩不动');return false}
  return true;
}

// ---------- 杯子倒了的连锁反应 ----------
function tipCup(nx,nz,byPet){
  if(cup.fallen)return;
  cup.fallen=true;cup.yaw=Math.atan2(nx,nz);
  for(let i=0;i<4;i++)A.emit('spark',cup.x+(Math.random()-.5)*.3,.3,cup.z);
  if(byPet){if(Math.random()<.6)A.say(pick(T.oops))}
  else startle();
}
function fixCup(){cup.fallen=false;cup.vx=cup.vz=0}
function startle(){
  if(!free())return;
  A.stopRoutine();A.play('poke',.45);A.say(pick(T.startle));
  const fast=Math.hypot(ball.vx,ball.vz)>1;
  A.routine([{t:'wait',d:.6},{t:'face',tx:cup.x,tz:cup.z,d:.3},{t:'fn',f:()=>{
    if(fast&&!chase&&A.S.energy>=15)chase={t:0,lazy:true};else goFix();
  }}]);
}
function goFix(){
  if(!cup.fallen||!free())return;
  const a=approach(cup.x,cup.z,.85);
  A.routine([{t:'walk',x:a.x,z:a.z,sp:1.8},{t:'face',tx:cup.x,tz:cup.z,d:.25},{t:'anim',n:'wave',d:.8},{t:'fn',f:()=>{fixCup();A.say(pick(T.fix))}}]);
}

function phys(dt){
  objs.forEach(o=>{
    if(o.held)return;
    if(o.kind==='ball'){
      o.vy-=9.8*dt;o.y+=o.vy*dt;
      if(o.y<o.r){o.y=o.r;if(o.vy<-1.2)o.vy=-o.vy*.5;else o.vy=0}
    }
    const ground=o.kind!=='ball'||o.y<o.r+.02;
    if(ground){const f=Math.max(0,1-o.fr*dt);o.vx*=f;o.vz*=f}
    if(o.kind==='cup'&&!o.fallen){const f=Math.max(0,1-8*dt);o.vx*=f;o.vz*=f}
    o.x+=o.vx*dt;o.z+=o.vz*dt;
    if(o.x<X0+o.r){o.x=X0+o.r;o.vx=Math.abs(o.vx)*o.bo}
    if(o.x>X1-o.r){o.x=X1-o.r;o.vx=-Math.abs(o.vx)*o.bo}
    if(o.z<Z0+o.r){o.z=Z0+o.r;o.vz=Math.abs(o.vz)*o.bo}
    if(o.z>Z1-o.r){o.z=Z1-o.r;o.vz=-Math.abs(o.vz)*o.bo}
  });
  // 球撞杯子
  const dx=cup.x-ball.x,dz=cup.z-ball.z,d=Math.hypot(dx,dz),md=ball.r+cup.r;
  if(d<md&&d>1e-4&&ball.y<.5&&!ball.held){
    const nx=dx/d,nz=dz/d,rel=ball.vx*nx+ball.vz*nz;
    if(rel>1.1&&!cup.fallen)tipCup(nx,nz,false);
    ball.x-=nx*(md-d);ball.z-=nz*(md-d);
    if(rel>0){ball.vx-=nx*rel*1.4;ball.vz-=nz*rel*1.4;cup.vx+=nx*rel*.4;cup.vz+=nz*rel*.4}
  }
  // 崽崽撞东西
  if(petSolid()){
    const sp=Math.hypot(pvx,pvz);
    objs.forEach(o=>{
      if(o.held||(o.kind==='ball'&&o.y>.9))return;
      const dx=o.x-P.x,dz=o.z-P.z,d=Math.hypot(dx,dz),md=.6+o.r;
      if(d<md&&d>1e-4){
        const nx=dx/d,nz=dz/d;
        o.x=P.x+nx*md;o.z=P.z+nz*md;
        const push=Math.max(0,pvx*nx+pvz*nz),k=o.kind==='ball'?1.5:.6;
        o.vx+=nx*(push*k+.4);o.vz+=nz*(push*k+.4);
        if(o.kind==='ball'&&push>.5)o.vy=Math.max(o.vy,.8);
        if(o.kind==='cup'&&!o.fallen&&sp>.5)tipCup(nx,nz,true);
      }
    });
  }
  ball.m.position.set(ball.x,ball.y,ball.z);
  if(!ball.held){ball.m.rotation.z-=ball.vx*dt/ball.r;ball.m.rotation.x+=ball.vz*dt/ball.r}
  cupG.position.set(cup.x,0,cup.z);cupG.rotation.y=cup.yaw;
  cup.tip+=((cup.fallen?1:0)-cup.tip)*Math.min(1,dt*(cup.fallen?7:5));
  cupPiv.rotation.x=cup.tip*Math.PI/2;cupPiv.position.y=cup.tip*.15;
}

// ---------- 追球 ----------
function updChase(dt){
  if(!chase)return;
  if(A.lift||A.S.out||A.S.sleeping){chase=null;return}
  if(!free()||ball.held)return;
  chase.t+=dt;
  const d=Math.hypot(ball.x-P.x,ball.z-P.z),sp=Math.hypot(ball.vx,ball.vz);
  if(d<.9&&ball.y<.6&&sp<2.2){
    A.stopRoutine();
    let tx=0,tz=1.6;
    if(chase.lazy){tx=(Math.random()-.5)*4;tz=(Math.random()-.5)*3}
    const dx=tx-ball.x,dz=tz-ball.z,dd=Math.hypot(dx,dz)||1,v=chase.lazy?1.6:2.6;
    ball.vx=dx/dd*v;ball.vz=dz/dd*v;ball.vy=1.4;
    A.play('hop',.7);
    if(!chase.lazy){fetches++;if(Math.random()<.6)A.say(pick(T.fetch));if(fetches%3===0)reward()}
    chase=null;return;
  }
  if(chase.t>12){chase=null;A.say('球球跑远了');return}
  chase.re=(chase.re||0)-dt;
  if(chase.re<=0){
    chase.re=.45;
    const a=approach(ball.x+ball.vx*.3,ball.z+ball.vz*.3,.75);
    A.routine([{t:'walk',x:a.x,z:a.z,sp:2.6}]);
  }
}

// ---------- 泡泡 ----------
const bubM=new THREE.MeshStandardMaterial({color:'#E3F4FF',roughness:.05,metalness:.1,transparent:true,opacity:.42,emissive:'#BFE6FF',emissiveIntensity:.15});
const bubG=new THREE.SphereGeometry(1,16,12);
const bubs=[];let pops=0,hopCd=0,blowAt=0;
function blow(x,y,z,vx,vz){
  if(bubs.length>=14)return;
  const m=new THREE.Mesh(bubG,bubM),r=.08+Math.random()*.07;
  m.scale.setScalar(r);m.position.set(x,y,z);scene.add(m);
  bubs.push({m,vx:vx!=null?vx:(Math.random()-.5)*.3,vy:(Math.random()-.4)*.12,vz:vz!=null?vz:(Math.random()-.5)*.3,t:0,ph:Math.random()*6});
}
function popBub(i,byPet){
  const b=bubs[i],p=b.m.position;scene.remove(b.m);bubs.splice(i,1);
  for(let k=0;k<3;k++)A.emit('spark',p.x,p.y,p.z);
  if(byPet){pops++;if(Math.random()<.5)A.say(pick(T.pop));if(pops%6===0)reward()}
}
function updBubs(dt){
  for(let i=bubs.length-1;i>=0;i--){
    const b=bubs[i],p=b.m.position;b.t+=dt;
    p.x+=(b.vx+Math.sin(b.t*1.3+b.ph)*.12)*dt;p.y+=(b.vy+Math.sin(b.t*2+b.ph)*.05)*dt;p.z+=b.vz*dt;
    b.vx*=.995;b.vz*=.995;
    p.x=clamp(p.x,X0,X1);p.z=clamp(p.z,Z0,Z1);
    if(p.y<.5)b.vy=Math.abs(b.vy)+.02;if(p.y>1.9)b.vy=-Math.abs(b.vy);
    if(b.t>10)popBub(i,false);
  }
  if(toy!=='bubble'||!free()||!bubs.length)return;
  hopCd-=dt;
  let best=0,bd=1e9;
  bubs.forEach((b,i)=>{const d=Math.hypot(b.m.position.x-P.x,b.m.position.z-P.z);if(d<bd){bd=d;best=i}});
  const b=bubs[best];focus=b.m.position;
  if(bd<.6&&b.m.position.y<1.8){
    if(hopCd<=0&&!A.anim){
      hopCd=.9;A.stopRoutine();A.play('hop',.7);
      const ref=b;
      setTimeout(()=>{const j=bubs.indexOf(ref);if(j>=0&&Math.hypot(ref.m.position.x-P.x,ref.m.position.z-P.z)<.8)popBub(j,true)},280);
    }
  }else if(!A.inRoutine()){
    const a=approach(b.m.position.x,b.m.position.z,.15);
    A.routine([{t:'walk',x:a.x,z:a.z,sp:2}]);
  }
}
function blowAtFinger(e){
  blowAt=performance.now();
  const p=floorAt(e.clientX,e.clientY,1.2);
  if(p)blow(clamp(p.x,X0,X1),1.0+Math.random()*.5,clamp(p.z,Z0,Z1),(Math.random()-.5)*.4,(Math.random()-.5)*.4);
}

// ---------- 逗猫棒 ----------
const ts={H:new V3(-.6,1.4,1.4),F:new V3(-.6,.4,1.4),Fp:new V3(-.6,.4,1.4),drag:false,ph:'watch',t:0,cd:0,catches:0,still:0};
let teaserG=null,teaserLine=null,feather=null,stick=null;
function makeTeaser(){
  teaserG=new THREE.Group();scene.add(teaserG);
  stick=mesh(new THREE.CylinderGeometry(.02,.025,.9,8),M('#C99A6E'),0,0,0,teaserG);
  feather=new THREE.Group();teaserG.add(feather);
  [['#F28B82',0],['#F5C84C',.9],['#7FC4E0',-.9]].forEach(([c,r])=>{
    const f=mesh(new THREE.SphereGeometry(.07,10,8),M(c,{roughness:.9}),Math.sin(r)*.06,-.1,0,feather);
    f.scale.set(.6,2.2,.6);f.rotation.z=r*.5;
  });
  mesh(new THREE.SphereGeometry(.045,10,8),M('#FFFFFF'),0,0,0,feather);
  const g=new THREE.BufferGeometry().setFromPoints([new V3(),new V3()]);
  teaserLine=new THREE.Line(g,new THREE.LineBasicMaterial({color:'#8B7F70'}));scene.add(teaserLine);
  ts.H.set(-.6,1.4,1.4);ts.F.set(-.6,.4,1.4);ts.Fp.copy(ts.F);ts.ph='watch';ts.still=0;ts.cd=.8;
}
function dropTeaser(){if(teaserG){scene.remove(teaserG);scene.remove(teaserLine);teaserG=null;teaserLine=null}}
function moveHandle(e){
  const p=floorAt(e.clientX,e.clientY,1.4);
  if(p)ts.H.set(clamp(p.x,X0,X1),1.4,clamp(p.z,Z0,Z1+.6));
}
function landPounce(){
  ts.ph='watch';ts.cd=1.3;ts.still=0;
  if(Math.hypot(ts.F.x-P.x,ts.F.z-P.z)<.75&&ts.F.y<1.2){
    ts.catches++;A.play('happy',.9);A.say(pick(T.caught));A.hearts(1);
    ts.Fp.x=ts.F.x-(Math.random()-.5)*.08;ts.Fp.y=ts.F.y-.06;
    if(ts.catches%3===0)reward();
  }else{A.play('wiggle',.9);if(Math.random()<.6)A.say(pick(T.nocatch))}
}
function updTeaser(dt){
  if(!teaserG)return;
  const fp=ts.F.clone();
  ts.F.x+=(ts.F.x-ts.Fp.x)*.985;
  ts.F.y+=(ts.F.y-ts.Fp.y)*.985-9.8*dt*dt;
  ts.F.z+=(ts.F.z-ts.Fp.z)*.985;
  ts.Fp.copy(fp);
  tv.subVectors(ts.F,ts.H);const len=tv.length();
  if(len>1)ts.F.copy(ts.H).addScaledVector(tv,1/len);
  if(ts.F.y<.06)ts.F.y=.06;
  const fs=ts.F.distanceTo(ts.Fp)/dt;
  feather.position.copy(ts.F);
  stick.position.set(ts.H.x+.13,ts.H.y+.42,ts.H.z);stick.rotation.z=-.3;
  const lp=teaserLine.geometry.attributes.position;
  lp.setXYZ(0,ts.H.x,ts.H.y,ts.H.z);lp.setXYZ(1,ts.F.x,ts.F.y,ts.F.z);lp.needsUpdate=true;

  if(toy!=='teaser'||!free())return;
  focus=ts.F;ts.cd-=dt;
  if(ts.ph==='pounce'&&!A.inRoutine())ts.ph='watch';
  const dx=ts.F.x-P.x,dz=ts.F.z-P.z,d=Math.hypot(dx,dz);
  if(ts.ph==='watch'){
    ts.still=fs<.8&&ts.F.y<1.0&&d<2.4&&d>.3?ts.still+dt:0;
    if(ts.still>.35&&ts.cd<=0&&!A.inRoutine()){
      ts.ph='crouch';ts.t=0;A.stopRoutine();
      A.routine([{t:'face',tx:ts.F.x,tz:ts.F.z,d:.2}]);
      if(Math.random()<.4)A.say(pick(T.crouch));
    }else if(d>2.6&&!A.inRoutine()&&ts.cd<=0){
      const a=approach(ts.F.x,ts.F.z,1.4);
      A.routine([{t:'walk',x:a.x,z:a.z,sp:1.6}]);
    }
  }else if(ts.ph==='crouch'){
    ts.t+=dt;crouch=1;
    if(ts.t>.75){
      ts.ph='pounce';
      const k=Math.min(1,1.9/Math.max(d,.01));
      const jx=clamp(P.x+dx*k,X0+.6,X1-.6),jz=clamp(P.z+dz*k,Z0+.4,Z1-.4);
      A.routine([{t:'jump',x:jx,y:0,z:jz,d:.5},{t:'fn',f:landPounce}]);
    }
  }
}

// ---------- 扣杯子 ----------
const CUPX=[-1.6,0,1.6],CUPZ=.75,CUPC=['#F2B8A0','#A9C6D9','#F5C84C'];
function makeBigCup(c){
  const g=new THREE.Group();
  mesh(new THREE.CylinderGeometry(.6,.8,1.3,32,1,true),M(c,{roughness:.55,side:THREE.DoubleSide}),0,.65,0,g);
  mesh(new THREE.CircleGeometry(.6,32),M(c,{roughness:.55}),0,1.3,0,g).rotation.x=-Math.PI/2;
  mesh(new THREE.TorusGeometry(.8,.04,8,40),M('#FFFFFF',{roughness:.5}),0,.03,0,g).rotation.x=Math.PI/2;
  scene.add(g);return g;
}
function endCups(){if(cups){cups.list.forEach(k=>scene.remove(k.g));cups=null}}
function startCups(){
  if(!canPlay()||!free())return false;
  A.stopRoutine();chase=null;nap=null;
  const hide=Math.floor(Math.random()*3);
  cups={ph:'drop',t:0,list:CUPX.map((x,i)=>({g:makeBigCup(CUPC[i]),x,z:CUPZ,y:4,ty:2.0})),pet:hide,hidden:false,sw:null,n:0};
  cups.list.forEach(k=>k.g.position.set(k.x,k.y,k.z));
  A.say(pick(T.hide));
  const c=cups;
  A.routine([{t:'walk',x:CUPX[hide],z:CUPZ,sp:2.2},{t:'face',yaw:0,d:.3},{t:'anim',n:'wave',d:.8},{t:'fn',f:()=>{if(cups!==c)return;c.ph='cover';c.t=0;c.list[c.pet].ty=0}}]);
  return true;
}
function updCups(dt){
  if(!cups)return;
  const c=cups;c.t+=dt;
  if(A.lift||A.S.out||(c.ph==='drop'&&c.t>7)){endCups();toy=null;updToyTray();return}
  c.list.forEach(k=>{k.y+=(k.ty-k.y)*Math.min(1,dt*(k.ty<k.y?9:6))});
  if(c.ph==='cover'){
    if(c.list[c.pet].y<.7)c.hidden=true;
    if(c.t>.5)c.list.forEach(k=>k.ty=0);
    if(c.t>1.1){c.ph='shuffle';c.t=0;c.n=0;c.sw=null}
  }else if(c.ph==='shuffle'){
    if(!c.sw){
      if(c.n>=6){c.ph='guess';A.toast('崽崽藏在哪个杯子底下？点一下')}
      else{
        const i=Math.floor(Math.random()*3),j=(i+1+Math.floor(Math.random()*2))%3;
        c.sw={a:c.list[i],b:c.list[j],ax:c.list[i].x,bx:c.list[j].x,t:0,d:Math.max(.32,.6-c.n*.05)};c.n++;
      }
    }
    const s=c.sw;
    if(s){
      s.t+=dt;const k=Math.min(1,s.t/s.d),e=k*k*(3-2*k);
      s.a.x=s.ax+(s.bx-s.ax)*e;s.b.x=s.bx+(s.ax-s.bx)*e;
      s.a.z=CUPZ+Math.sin(Math.PI*k)*.55;s.b.z=CUPZ-Math.sin(Math.PI*k)*.55;
      if(k>=1)c.sw=null;
    }
  }else if(c.ph==='reveal'){
    if(c.t>3.2){c.list.forEach(k=>k.ty=5);c.ph='leave';c.t=0}
  }else if(c.ph==='leave'){
    if(c.t>1){endCups();toy=null;updToyTray();return}
  }
  c.list.forEach(k=>k.g.position.set(k.x,k.y,k.z));
  if(c.hidden){const k=c.list[c.pet];P.x=k.x;P.z=k.z}
}
function guessCup(i){
  const c=cups;if(!c||c.ph!=='guess')return;
  c.ph='reveal';c.t=0;c.list[i].ty=1.9;
  if(i===c.pet){
    setTimeout(()=>{if(cups!==c)return;c.hidden=false;A.play('happy',.9);A.say(pick(T.found));A.hearts(4);reward()},300);
  }else{
    A.say(pick(T.miss));
    setTimeout(()=>{if(cups!==c)return;c.list[c.pet].ty=1.9;
      setTimeout(()=>{if(cups!==c)return;c.hidden=false;A.play('wiggle',.9);A.say(pick(T.here));reward()},300)},1100);
  }
}

// ---------- 自己找事干 ----------
function updNap(dt){
  if(!nap)return;
  nap.t+=dt;
  const an=A.anim,pd=A.pd;
  if(nap.t>nap.d||A.lift||(pd&&pd.hit)||toy||(an&&nap.t>.5)||A.S.out||A.S.sleeping){
    const woke=nap.t>1;nap=null;
    if(woke&&!A.S.out&&!A.S.sleeping)A.say(pick(T.wake));
    return;
  }
  A.setEyes('closed');root.scale.y*=.84;root.position.y+=.1;
  if(A.clock>nap.z){nap.z=A.clock+1.7;A.emit('z',P.x+.5,P.y+1.0,P.z+.3)}
}
function batBall(){
  const a=approach(ball.x,ball.z,.8);
  A.routine([{t:'walk',x:a.x,z:a.z,sp:1.4},{t:'face',tx:ball.x,tz:ball.z,d:.25},{t:'anim',n:'wave',d:.6},{t:'fn',f:()=>{
    const dx=ball.x-P.x,dz=ball.z-P.z,d=Math.hypot(dx,dz)||1;
    if(d<1.3){
      ball.vx=dx/d*2;ball.vz=dz/d*2;ball.vy=.8;
      if(Math.random()<.5)A.say(pick(T.bat));
      if(Math.random()<.5&&A.S.energy>=15)setTimeout(()=>{if(free()&&!chase)chase={t:0,lazy:true}},500);
    }
  }}]);
}
function goNap(){
  A.routine([{t:'walk',x:CUSH.x,z:CUSH.z,sp:1.2},{t:'face',yaw:0,d:.4},{t:'anim',n:'stretch',d:1.0},{t:'fn',f:()=>{
    nap={t:0,d:8+Math.random()*10,z:0};if(Math.random()<.5)A.say(pick(T.nap));
  }}]);
}
function mischief(){
  const a=approach(cup.x,cup.z,.85);
  A.routine([{t:'walk',x:a.x,z:a.z,sp:1.2},{t:'face',tx:cup.x,tz:cup.z,d:.3},{t:'anim',n:'look',d:.8},{t:'anim',n:'wave',d:.5},{t:'fn',f:()=>{
    const dx=cup.x-P.x,dz=cup.z-P.z,d=Math.hypot(dx,dz)||1;
    tipCup(dx/d,dz/d,true);cup.vx=dx/d*.6;cup.vz=dz/d*.6;
  }},{t:'wait',d:1.2},{t:'fn',f:()=>{if(Math.random()<.6)goFix()}}]);
}
function idle(){
  if(!free()||toy||Math.random()<.45)return false;
  const h=new Date().getHours(),S=A.S,opts=[];
  if(cup.fallen)opts.push([3,goFix]);
  if(Math.hypot(ball.vx,ball.vz)<.3&&!ball.held)opts.push([2,batBall]);
  opts.push([S.energy<55||(h>=13&&h<15)||h>=22?3:1,goNap]);
  if(!cup.fallen)opts.push([.7,mischief]);
  let r=Math.random()*opts.reduce((a,o)=>a+o[0],0);
  for(const o of opts){r-=o[0];if(r<=0){o[1]();return true}}
  return false;
}

// ---------- 眼睛先转 ----------
const eyeGs=root.children.filter(c=>c.userData&&c.userData.bx!==undefined);
let eox=0,eoy=0,lastYaw=pet.rotation.y;
function updEyes(dt){
  let ox=0,oy=0;
  if(focus&&!hidden()){tv2.copy(focus);root.worldToLocal(tv2);ox=clamp(tv2.x*.05,-.06,.06);oy=clamp((tv2.y-.7)*.05,-.05,.05)}
  let dy=pet.rotation.y-lastYaw;dy=Math.atan2(Math.sin(dy),Math.cos(dy));lastYaw=pet.rotation.y;
  ox+=clamp(dy/dt*.03,-.07,.07);
  eox+=(ox-eox)*Math.min(1,dt*12);eoy+=(oy-eoy)*Math.min(1,dt*12);
  eyeGs.forEach(g=>{g.position.x+=eox;g.position.y+=eoy});
}

// ---------- 玩具箱 ----------
const css=document.createElement('style');
css.textContent=`
#tyTray{position:fixed;left:50%;bottom:84px;transform:translate(-50%,20px);z-index:6;display:flex;gap:2px;border-radius:26px;padding:6px 8px;opacity:0;pointer-events:none;transition:opacity .22s,transform .22s}
#tyTray.on{opacity:1;transform:translate(-50%,0);pointer-events:auto}
#tyTray button{border:none;background:none;width:56px;display:flex;flex-direction:column;align-items:center;font-size:11.5px;color:#3D3929;padding:4px 0;gap:1px;border-radius:18px}
#tyTray button span{font-size:24px;line-height:1.15}
#tyTray button.hot{background:#FBE1D3}
#tyTray button:active{transform:scale(.92)}
body.map #tyTray{display:none}
`;
document.head.appendChild(css);
const tray=document.createElement('div');tray.id='tyTray';tray.className='glass';
tray.innerHTML='<button id="tyBall"><span>🎾</span>扔球</button><button id="tyBub"><span>🫧</span>吹泡泡</button><button id="tyCups"><span>🥤</span>扣杯子</button><button id="tyTeaser"><span>🪶</span>逗猫棒</button><button id="tyClose"><span>✕</span>收起</button>';
document.body.appendChild(tray);
function updToyTray(){
  $('#tyBub').classList.toggle('hot',toy==='bubble');
  $('#tyCups').classList.toggle('hot',toy==='cups');
  $('#tyTeaser').classList.toggle('hot',toy==='teaser');
}
function setToy(t){
  if(toy==='teaser'&&t!=='teaser')dropTeaser();
  if(toy==='cups'&&t!=='cups')endCups();
  toy=t;updToyTray();
}
function closeToys(){setToy(null);tray.classList.remove('on')}
$('#tyBall').onclick=()=>{
  ball.x=.7;ball.z=1.5;ball.y=.6;ball.vx=ball.vz=ball.vy=0;
  A.toast('拖着球往外一甩，它会去捡回来');
};
$('#tyBub').onclick=()=>{
  if(toy==='bubble'){setToy(null);return}
  if(!canPlay())return;
  setToy('bubble');
  for(let i=0;i<6;i++)setTimeout(()=>blow(-2+Math.random()*.3,1.1+Math.random()*.4,1.4,.5+Math.random()*.5,-.2-Math.random()*.3),i*150);
  A.say(pick(T.bubble));A.toast('在空地上划手指，会吹出更多泡泡');
};
$('#tyCups').onclick=()=>{
  if(toy==='cups'){setToy(null);return}
  setToy(null);
  if(startCups()){toy='cups';updToyTray()}
};
$('#tyTeaser').onclick=()=>{
  if(toy==='teaser'){setToy(null);return}
  if(!canPlay())return;
  setToy('teaser');makeTeaser();
  A.say(pick(T.teaser));A.toast('按住空地拖来拖去，晃给它看');
};
$('#tyClose').onclick=closeToys;
document.getElementById('playBtn').onclick=()=>{
  if(A.S.out){A.toast('崽崽出门了，还没回来');return}
  if(tray.classList.contains('on')){closeToys();return}
  if(window.PetFx&&PetFx.busy()){A.toast('先洗完澡');return}
  const ft=document.getElementById('fxTray');
  if(ft&&ft.classList.contains('on'))document.getElementById('fxClose').click();
  tray.classList.add('on');updToyTray();
};
document.getElementById('bathBtn').addEventListener('click',()=>{if(tray.classList.contains('on'))closeToys()});
const oldBack=window.petBack;
window.petBack=function(){
  if(tray.classList.contains('on')&&!document.getElementById('sheet').classList.contains('on')){closeToys();return true}
  return oldBack?oldBack():false;
};
document.getElementById('helpBtn').onclick=()=>A.openSheet('<h3>怎么跟崽崽玩</h3><div class="help">'+[
  '<b>点一下</b> 戳它，戳的地方会凹下去，连着戳五下会晕',
  '<b>连点两下</b> 抱抱',
  '<b>在它身上来回摸</b> 眯眼享受，摸久了瘫成一张饼',
  '<b>按住它往上拖</b> 拎起来，甩出去会飞，摔狠了滚两圈',
  '<b>洗澡</b> 放出澡盆，把它拎进去或点澡盆，搓出泡泡再冲水',
  '<b>陪玩</b> 打开玩具箱：扔球、吹泡泡、扣杯子、逗猫棒',
  '<b>地上的球</b> 随时能拖起来甩出去',
  '<b>不理它</b> 它会自己拨球、推杯子、趴垫子上睡觉',
  '<b>喂食</b> 选好吃的，把零食拖到它身上',
  '<b>出门</b> 去小岛打工、上学、玩，到点自己回来',
  '<b>点名字</b> 给它改名'
].join('<br>')+'</div>');

// ---------- 手指 ----------
let grab=null,blowing=false,down=null;
const fv={vx:0,vz:0,t:0,px:0,pz:0};
function moveGrab(e){
  const p=floorAt(e.clientX,e.clientY,.45);if(!p)return;
  const x=clamp(p.x,X0+.2,X1-.2),z=clamp(p.z,Z0+.2,Z1-.2),now=performance.now(),dt=(now-fv.t)/1000;
  if(dt>0&&dt<.2){fv.vx=fv.vx*.4+(x-fv.px)/dt*.6;fv.vz=fv.vz*.4+(z-fv.pz)/dt*.6}
  fv.t=now;fv.px=x;fv.pz=z;
  ball.x=x;ball.z=z;ball.y=.45;ball.vx=ball.vz=ball.vy=0;
}
cv.addEventListener('pointerdown',e=>{
  if(A.mode!=='home'||A.S.out)return;
  down={x:e.clientX,y:e.clientY,moved:false};
  setRay(e.clientX,e.clientY);
  const list=[body,ball.m];
  if(cups)cups.list.forEach(k=>list.push(k.g));
  const h=ray.intersectObjects(list,true)[0];
  if(h&&under(h.object,body))return;
  if(h&&under(h.object,ball.m)){
    grab=true;ball.held=true;chase=null;
    fv.t=performance.now();fv.px=ball.x;fv.pz=ball.z;fv.vx=fv.vz=0;
    moveGrab(e);return;
  }
  if(h&&cups)return;
  if(toy==='teaser'&&teaserG){ts.drag=true;moveHandle(e)}
  else if(toy==='bubble'){blowing=true;blowAtFinger(e)}
});
cv.addEventListener('pointermove',e=>{
  if(down&&Math.abs(e.clientX-down.x)+Math.abs(e.clientY-down.y)>10)down.moved=true;
  if(grab)moveGrab(e);
  else if(ts.drag&&teaserG)moveHandle(e);
  else if(blowing&&performance.now()-blowAt>110)blowAtFinger(e);
});
function up(e){
  if(grab){
    grab=null;ball.held=false;
    if(performance.now()-fv.t>100){fv.vx=0;fv.vz=0}
    ball.vx=clamp(fv.vx,-8,8);ball.vz=clamp(fv.vz,-8,8);ball.vy=1.5;
    if(Math.hypot(ball.vx,ball.vz)>1.2&&free()){
      if(A.S.energy<15)A.say(pick(T.tired));
      else{chase={t:0};if(Math.random()<.5)A.say(pick(T.chase))}
    }
  }
  ts.drag=false;blowing=false;
  if(e&&down&&!down.moved&&cups&&cups.ph==='guess'){
    setRay(e.clientX,e.clientY);
    const h=ray.intersectObjects(cups.list.map(k=>k.g),true)[0];
    if(h){const i=cups.list.findIndex(k=>under(h.object,k.g));if(i>=0)guessCup(i)}
  }
  down=null;
}
cv.addEventListener('pointerup',up);
cv.addEventListener('pointercancel',()=>up(null));

// ---------- 每一帧 ----------
function frame(dt){
  if(dt<=0)return;
  if(A.S.out){chase=null;nap=null;if(toy)setToy(null);if(cups)endCups()}
  focus=null;crouch=0;
  pvx=clamp((P.x-lpx)/dt,-4,4);pvz=clamp((P.z-lpz)/dt,-4,4);lpx=P.x;lpz=P.z;
  phys(dt);
  if(ball.held||chase||Math.hypot(ball.vx,ball.vz)>1.2)focus=ball.m.position;
  updChase(dt);updBubs(dt);updTeaser(dt);updCups(dt);updNap(dt);
  if(crouch){root.scale.y*=.82;root.position.y-=.04;root.rotation.z+=Math.sin(A.clock*30)*.05}
  if(hidden())root.scale.set(1e-4,1e-4,1e-4);
  updEyes(dt);
}

window.PetToys={frame,idle,busy:()=>!!(nap||cups||chase||toy==='bubble'||toy==='teaser')};
})();
