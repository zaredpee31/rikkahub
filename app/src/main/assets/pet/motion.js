// 崽崽全身联动：每个部位挂一根弹簧。身子先动，胳膊、腿、眼睛慢半拍跟上，冲过头再弹回来
(function(){
const A=window.PetApp;if(!A||!A.body||!A.legs||!A.arms||!A.eyeG)return;
const THREE=A.THREE,P=A.P,body=A.body,root=A.root,pet=A.pet,scene=A.scene,cam=A.cam;
const legs=A.legs,arms=A.arms,eyeG=A.eyeG;
const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
const wrap=a=>Math.atan2(Math.sin(a),Math.cos(a));

// 正着走，不横着走了
if(window.PetFx)window.PetFx.crab=a=>a;

function S(k,c,x){return {x:x||0,v:0,k,c}}
function stepS(s,t,h){s.v+=(s.k*(t-s.x)-s.c*s.v)*h;s.x+=s.v*h}
const sp={px:S(200,14),py:S(360,20),pz:S(200,14),rx:S(260,14),rz:S(380,16),sy:S(300,11,1)};
const yawS=S(170,13),yxS=S(150,12);
const arm=[0,1].map(()=>({r:S(160,7),s:S(160,7)}));
const eye={x:S(300,16),y:S(300,16),s:S(250,9,1)};
const SIDE=[-1,-1,1,1];
const leg=legs.map(()=>({lift:0,sw:0,spl:0,ext:1}));
const legC=legs.map(()=>({lift:0,sw:0,spl:0,e:1}));

let init=false,lastAnim=null,lastName='',prevName='',scramble=0;
let lpx=0,lpz=0,lpy=0,lvx=0,lvy=0,lvz=0,spd=0,phase=0,walkAmt=0,lyaw=0,lyv=0;
let shift=0,shiftAt=0,glY=0,glP=0,glAt=0,hadFood=false,wasHidden=false,ball=null,lbx=0,lbz=0,bsp=0;
let hit={x:0,y:0,t:-9};

const tv=new THREE.Vector3(),ray=new THREE.Raycaster(),ndc=new THREE.Vector2();
document.getElementById('c').addEventListener('pointerdown',e=>{
  if(A.mode!=='home')return;
  ndc.set(e.clientX/innerWidth*2-1,-(e.clientY/innerHeight)*2+1);ray.setFromCamera(ndc,cam);
  const h=ray.intersectObject(body,false)[0];
  if(h){const lp=body.worldToLocal(h.point.clone());hit={x:lp.x,y:lp.y,t:A.clock}}
  else hit.t=-9;
});
function scr(o){o.getWorldPosition(tv);tv.project(cam);return {x:(tv.x+1)/2*innerWidth,y:(1-tv.y)/2*innerHeight}}
function findBall(){
  if(ball&&ball.parent)return ball;
  ball=scene.children.find(o=>o.isMesh&&o.geometry&&o.geometry.type==='SphereGeometry'&&o.geometry.parameters&&Math.abs(o.geometry.parameters.radius-.18)<1e-3&&o.material&&o.material.map)||null;
  return ball;
}

// 动作刚开始的那一下：给弹簧一脚
function onStart(n){
  if(n==='poke'){
    const fresh=A.clock-hit.t<.8;
    const side=fresh?clamp(hit.x/.5,-1,1):0,top=fresh&&hit.y>.18;
    if(Math.abs(side)<.35){sp.rx.v-=7;sp.rz.v+=side*5}
    else{sp.rz.v+=side*8;sp.px.v-=side*2.6}
    sp.sy.v-=top?5:2.5;
    arm.forEach((a,i)=>{a.r.v+=8+(i===(side>0?0:1)?5:0)});
    scramble=.6;eye.s.v-=4;
  }else if(n==='land'){sp.sy.v-=4;arm.forEach(a=>a.r.v+=7)}
  else if(n==='hug'){sp.sy.v+=4}
  else if(n==='dizzy'){arm.forEach(a=>a.r.v+=6)}
  else if(n==='eat'){sp.rx.v+=5;sp.sy.v-=3}
  else if(n==='happy'||n==='hop'){arm.forEach(a=>a.r.v-=3)}
}

function frame(dt){
  if(dt<=0||!pet.visible||A.mode!=='home')return;
  const t=A.clock,an=A.anim,lift=A.lift,St=A.S,step=A.step,df=A.dragFood;
  // 前面几层这一帧算出来的样子
  const U={px:root.position.x,py:root.position.y,pz:root.position.z,rx:root.rotation.x,rz:root.rotation.z,sx:root.scale.x,sy:root.scale.y,sz:root.scale.z,yaw:pet.rotation.y};
  const UR=[-arms[0].rotation.z,arms[1].rotation.z];
  // 躲在杯子底下
  if(U.sy<.01){wasHidden=true;return}
  if(!init||wasHidden){
    sp.px.x=U.px;sp.py.x=U.py;sp.pz.x=U.pz;sp.rx.x=U.rx;sp.rz.x=U.rz;
    sp.sy.x=wasHidden?U.sy*.4:U.sy;sp.sy.v=wasHidden?5:0;
    if(!init){yawS.x=U.yaw;lyaw=U.yaw;lpx=P.x;lpz=P.z;lpy=P.y+U.py}
    init=true;wasHidden=false;
  }

  // 速度变化，用来给弹簧惯性
  let vx=(P.x-lpx)/dt,vz=(P.z-lpz)/dt,vy=(P.y+U.py-lpy)/dt;
  if(Math.hypot(vx,vz)>14){vx=lvx;vz=lvz}
  if(Math.abs(vy)>20)vy=lvy;
  const sn=Math.sin(U.yaw),cs=Math.cos(U.yaw);
  const dvx=vx-lvx,dvz=vz-lvz,dvy=vy-lvy,pvy=lvy;
  const dF=dvx*sn+dvz*cs,dS=dvx*cs-dvz*sn;
  lpx=P.x;lpz=P.z;lpy=P.y+U.py;lvx=vx;lvz=vz;lvy=vy;
  spd+=(Math.hypot(vx,vz)-spd)*Math.min(1,dt*10);
  let yv=wrap(U.yaw-lyaw)/dt;if(Math.abs(yv)>30)yv=lyv;
  const dY=yv-lyv;lyaw=U.yaw;lyv=yv;

  const n=an?an.n:'',k=an?clamp((t-an.t0)/an.d,0,1):0;
  if(an&&an!==lastAnim){prevName=lastName;lastName=n;lastAnim=an;onStart(n)}
  if(!an)lastAnim=null;

  let mx=0,my=0,mp=0,mr=0,ms=1,myaw=0,tip=1,couple=true,eyX=0,eyY=0,eyS=1;
  const ar=[0,0],asw=[0,0],aAbs=[null,null],dU={py:0,rz:0};
  leg.forEach(l=>{l.lift=0;l.sw=0;l.spl=0;l.ext=1});
  const fxBusy=!!(window.PetFx&&PetFx.busy());
  const jumping=!!(step&&step.t==='jump');
  const asleep=!!St.sleeping&&A.onBed&&!A.inRoutine();

  // ---------- 每个动作全身怎么动 ----------
  switch(n){
    case 'poke':
      dU.py=.18*Math.sin(Math.PI*k);dU.rz=.15*Math.sin(Math.PI*k);couple=false;break;
    case 'eat':{
      const c=Math.max(0,Math.sin(k*Math.PI*12));
      mp=.28+.14*c;ms=.9-.06*c;asw[0]=asw[1]=1.0;ar[0]=ar[1]=.25+.25*c;eyY=-.04;
      leg.forEach(l=>l.ext=.92);couple=false;break}
    case 'happy':case 'hop':{
      const kk=n==='happy'?(k*2)%1:k;
      if(kk<.2){ar[0]=ar[1]=-.5;asw[0]=asw[1]=-.3;leg.forEach(l=>l.ext=.85)}
      else if(kk<.75){ar[0]=ar[1]=1.3;asw[0]=asw[1]=.2;ms=1.08;leg.forEach(l=>{l.lift=.04;l.sw=-.35;l.ext=.85})}
      else{ar[0]=ar[1]=.8;leg.forEach(l=>l.spl=.25)}
      if(n==='happy'){
        mr=Math.sin(k*Math.PI*4)*.1;
        // 吃完往后一坐，拍拍肚子
        if(prevName==='eat'){mp=-.22;ms=.92;ar[0]=ar[1]=.1+Math.max(0,Math.sin(t*14))*.3;asw[0]=asw[1]=.7}
      }
      couple=false;break}
    case 'hug':
      if(k<.25){ms=1.15;tip=1.15;ar[0]=ar[1]=.7;asw[0]=asw[1]=-.3}
      else if(k<.8){ms=.86;mp=.22;tip=1.1;ar[0]=ar[1]=-.3;asw[0]=asw[1]=1.1}
      else{mr=Math.sin((k-.8)*Math.PI*10)*.6*(1-k);mp=.08}
      couple=false;break;
    case 'land':
      ar[0]=ar[1]=.6;leg.forEach(l=>l.spl=.35*(1-k));couple=false;break;
    case 'dizzy':{
      const w=1-k;
      ar[0]=.2+Math.sin(t*6)*.5*w;ar[1]=.2+Math.sin(t*6+2)*.5*w;ms=.92;
      eyX=Math.cos(t*9)*.03*w;eyY=Math.sin(t*9)*.03*w;
      leg.forEach((l,i)=>l.spl=Math.sin(t*7+i)*.25*w);couple=false;break}
    case 'wave':{
      const b=findBall();
      if(b&&Math.hypot(b.position.x-P.x,b.position.z-P.z)<1.6){
        // 拨球：右手往前一扫，身子跟着扭，左手往后甩
        const s=Math.sin(Math.PI*k);
        aAbs[1]=.2;asw[1]=1.4*s;asw[0]=-.7*s;ar[0]=.2;mr=-.12*s;myaw=-.35*s;mp=.15;
        leg[3].lift=.08*s;leg[3].sw=.4*s;
      }else{mr=.1;my=.02*Math.sin(t*15);ar[0]=-.2;leg[2].ext=leg[3].ext=1.12}
      couple=false;break}
    case 'look':mp=-.15;break;
    case 'tiptoe':mp=-.22;tip=1.25;eyY=.05;break;
    case 'type':mp=.18;asw[0]=asw[1]=.7;my=.01*Math.sin(t*18);couple=false;break;
    case 'stretch':{const s=Math.sin(Math.PI*k);mp=-.25*s;tip=1+.3*s;ms=1+.08*s;couple=false;break}
    case 'wiggle':{
      const w=Math.sin(k*Math.PI*6);
      my=Math.abs(w)*.05;ar[0]=Math.max(0,w)*.6;ar[1]=Math.max(0,-w)*.6;
      leg.forEach((l,i)=>l.lift=Math.max(0,i<2?w:-w)*.05);break}
    case 'spin':ar[0]=ar[1]=1.0;ms=.92;leg.forEach(l=>l.spl=.3);couple=false;break;
    case 'roll':
      leg.forEach((l,i)=>{l.sw=Math.sin(t*16+i*1.7)*.6;l.lift=.03});
      ar[0]=Math.sin(t*9)*.6+.4;ar[1]=Math.sin(t*9+2)*.6+.4;couple=false;break;
    case 'rub':mp=.1;ms=.96;ar[0]=-.15;break;
    case 'sleepWiggle':mr=Math.sin(k*Math.PI*4)*.05;break;
    case 'bath':ar[0]=Math.sin(t*12)*.5+.5;ar[1]=Math.sin(t*12+1.5)*.5+.5;break;
  }

  // ---------- 拎着、掉下来、跳、睡 ----------
  if(lift&&!lift.fall){
    // 被拽长，腿在半空乱蹬，胳膊垂着像钟摆
    ms=1.18;mp=-.08;couple=false;ar[0]=ar[1]=-.45;
    leg.forEach((l,i)=>{l.sw=Math.sin(t*18+i*1.9)*.7;l.lift=Math.max(0,Math.sin(t*18+i*1.9))*.06;l.ext=.92});
    arm.forEach((a,i)=>{a.r.v+=clamp(dS,-3,3)*(i?-4:4);a.s.v-=clamp(dF,-3,3)*4});
  }else if(lift&&lift.fall){
    ms=1+clamp(Math.abs(vy)*.05,0,.25);couple=false;
    ar[0]=1.2+Math.sin(t*16)*.3;ar[1]=1.2+Math.sin(t*16+1.3)*.3;
    leg.forEach(l=>{l.spl=.35;l.ext=1.1});
  }else if(jumping){
    couple=false;
    if(vy>0){ms=1.12;ar[0]=ar[1]=1.1;asw[0]=asw[1]=.4;leg.forEach(l=>{l.lift=.05;l.sw=-.4;l.ext=.85})}
    else{ar[0]=ar[1]=.7;asw[0]=asw[1]=.2;leg.forEach(l=>{l.ext=1.1;l.spl=.15})}
  }else if(asleep){
    mp=.04;ms=.9+Math.sin(t*1.2)*.035;tip=.8;ar[0]=ar[1]=.05;couple=false;
  }

  // ---------- 走路：腿交替迈，胳膊反着摆，身子一颠一摇 ----------
  if(A.walking){dU.py+=Math.abs(Math.sin(t*11))*.06;dU.rz+=Math.sin(t*11)*.05}
  const moving=!lift&&!jumping&&!fxBusy&&(A.walking||spd>.35);
  walkAmt+=((moving?clamp(spd/1.1,.5,1.6):0)-walkAmt)*Math.min(1,dt*8);
  if(walkAmt>.02){
    phase+=dt*(7+spd*4);
    const w=walkAmt,s=Math.sin(phase),run=spd>1.8;
    leg.forEach((l,i)=>{const ph=phase+(i%2?Math.PI:0);l.lift+=Math.max(0,Math.sin(ph))*.08*w;l.sw+=Math.cos(ph)*.5*w});
    my+=Math.abs(s)*.05*w;mr+=s*.06*w;mp+=.12*w+(run?.12:0);
    if(run)ms*=.94;
    asw[0]+=s*.6*w-(run?.4:0);asw[1]+=-s*.6*w-(run?.4:0);
    ar[0]+=.15*w;ar[1]+=.15*w;
    couple=false;
  }

  // ---------- 看吃的：举高就仰头看，胳膊往下，脚踮起来；凑近了伸手够 ----------
  if(df&&!hadFood){eye.s.v+=6;sp.sy.v+=3;arm.forEach(a=>a.r.v+=5)}
  hadFood=!!df;
  if(df&&!n&&!lift){
    const ps=scr(body);
    const up=clamp((ps.y-df.y)/(innerHeight*.3),-.6,1),side=clamp((df.x-ps.x)/(innerWidth*.4),-1,1);
    mp+=-.42*up;myaw+=side*.45;eyX+=side*.06;eyY+=up*.06;eyS=1.15;
    if(A.reaching){
      ar[0]=ar[1]=.9+Math.sin(t*9)*.2;asw[0]=asw[1]=.9;my+=Math.abs(Math.sin(t*8))*.04;tip=1.12;couple=false;
    }
  }

  // ---------- 盯着球：球在前面低处就低头往前趴 ----------
  const b=findBall();
  if(b){
    const bx=b.position.x,bz=b.position.z;
    bsp+=(Math.hypot(bx-lbx,bz-lbz)/dt-bsp)*Math.min(1,dt*8);lbx=bx;lbz=bz;
    if(!df&&!lift&&!asleep&&(!n||n==='wave')){
      const dx=bx-P.x,dz=bz-P.z,d=Math.hypot(dx,dz),f=dx*sn+dz*cs,sd=dx*cs-dz*sn;
      if(d<3&&(bsp>.3||spd>1||d<1.3)){
        const a=clamp(1.3/Math.max(d,.3),0,1);
        if(f>0)mp+=.22*a;
        eyY-=.05*a;eyX+=clamp(sd/Math.max(d,.3),-1,1)*.06;
        myaw+=clamp(sd/Math.max(d,.5),-1,1)*.25*(1-walkAmt*.5);
      }
    }
  }

  // ---------- 平时站着：换重心、到处看，眼睛先转身子后跟 ----------
  const idle=!n&&!lift&&!df&&!jumping&&walkAmt<.1&&!asleep&&!fxBusy&&U.sy>.9&&!(A.pd&&A.pd.hit);
  if(idle){
    if(t>shiftAt){shiftAt=t+2.5+Math.random()*3;shift=[-1,0,1][Math.floor(Math.random()*3)]}
    if(t>glAt){glAt=t+2.5+Math.random()*4;if(Math.random()<.6){glY=(Math.random()-.5)*1.0;glP=(Math.random()-.6)*.3}else{glY=0;glP=0}}
    mr-=.07*shift;
    leg.forEach((l,i)=>{if(SIDE[i]===shift)l.ext*=.9;else if(shift)l.ext*=1.03});
    if(shift)ar[shift>0?1:0]-=.15;
    myaw+=glY*.5;eyX+=glY*.08;mp+=glP;
  }

  // 往上看：胳膊往下往后，脚踮起来；往下看：胳膊往前，腿弯一点
  if(couple){
    const u=Math.max(0,-mp),d=Math.max(0,mp);
    ar[0]-=u*.9;ar[1]-=u*.9;asw[0]-=u*.5;asw[1]-=u*.5;tip+=u*.6;eyY+=u*.12;
    asw[0]+=d*.6;asw[1]+=d*.6;leg.forEach(l=>l.ext-=d*.12);eyY-=d*.06;
  }

  // ---------- 惯性：起步往后仰、急停往前冲、往上窜胳膊往下甩 ----------
  if(!fxBusy){
    const iF=clamp(dF,-3,3),iY=clamp(dvy,-4,4);
    sp.rx.v-=iF*2.5;
    arm.forEach(a=>{a.s.v-=iF*5;a.r.v-=iY*1.5;a.r.v+=Math.min(Math.abs(dY),8)*.4});
    if(pvy<-1.5&&dvy>1.5)sp.sy.v-=Math.min(dvy,7)*.8;
    sp.rz.v+=clamp(dY,-8,8)*.12;
  }
  if(scramble>0){
    scramble-=dt;const w=Math.max(0,scramble/.6);
    leg.forEach((l,i)=>{l.lift+=Math.max(0,Math.sin(t*32+i*1.6))*.06*w;l.sw+=Math.sin(t*32+i)*.45*w});
  }

  // ---------- 弹簧追过去 ----------
  const T={px:U.px+mx,py:U.py-dU.py+my+.36*(tip-1),pz:U.pz,rx:U.rx+mp,rz:U.rz-dU.rz+mr,sy:U.sy*ms};
  const nsub=Math.min(8,Math.ceil(dt*120)),h=dt/nsub;
  eyX+=clamp(wrap(U.yaw-yawS.x)*.12,-.06,.06)+clamp(myaw-yxS.x,-.5,.5)*.12;
  for(let i=0;i<nsub;i++){
    if(!fxBusy){
      for(const key in T){const s=sp[key];stepS(s,key==='rz'||key==='rx'?s.x+wrap(T[key]-s.x):T[key],h)}
      stepS(yawS,yawS.x+wrap(U.yaw-yawS.x),h);
    }
    stepS(yxS,fxBusy?0:myaw,h);
    for(let j=0;j<2;j++){stepS(arm[j].r,(aAbs[j]!=null?aAbs[j]:UR[j])+ar[j],h);stepS(arm[j].s,asw[j],h)}
    stepS(eye.x,eyX,h);stepS(eye.y,eyY,h);stepS(eye.s,eyS,h);
  }
  if(fxBusy){
    for(const key in T){sp[key].x=T[key];sp[key].v=0}
    yawS.x=U.yaw;yawS.v=0;
  }

  // ---------- 写回去 ----------
  root.position.set(sp.px.x,sp.py.x,sp.pz.x);
  root.rotation.x=sp.rx.x;root.rotation.z=sp.rz.x;
  const sy=Math.max(.05,sp.sy.x),ratio=clamp(sy/Math.max(U.sy,.05),.5,1.8);
  root.scale.set(U.sx/Math.sqrt(ratio),sy,U.sz/Math.sqrt(ratio));
  pet.rotation.y=yawS.x+yxS.x;

  arms[0].rotation.z=-clamp(arm[0].r.x,-1.2,2.2);arms[1].rotation.z=clamp(arm[1].r.x,-1.2,2.2);
  arms[0].rotation.y=clamp(arm[0].s.x,-1.4,1.6);arms[1].rotation.y=-clamp(arm[1].s.x,-1.4,1.6);

  const f=Math.min(1,dt*16);
  legs.forEach((m,i)=>{
    const l=leg[i],c=legC[i];
    c.lift+=(l.lift-c.lift)*f;c.sw+=(l.sw-c.sw)*f;c.spl+=(l.spl-c.spl)*f;c.e+=(clamp(tip*l.ext,.6,1.5)-c.e)*f;
    m.scale.y=c.e;m.position.y=.36-.18*c.e+c.lift;m.rotation.x=c.sw;m.rotation.z=SIDE[i]*c.spl;
  });

  const ex=clamp(eye.x.x,-.1,.1),ey=clamp(eye.y.x,-.08,.1),es=clamp(eye.s.x,.6,1.5);
  eyeG.forEach(g=>{g.position.x+=ex;g.position.y+=ey;g.scale.setScalar(es)});
}

window.PetMotion={frame};
})();
