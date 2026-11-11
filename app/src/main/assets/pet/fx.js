// 崽崽的软身子、拎起来甩、澡盆洗澡。挂在 index.html 上，通过 PetApp 拿到崽崽
(function(){
const A=window.PetApp;if(!A||!A.body)return;
const THREE=A.THREE,P=A.P,body=A.body,root=A.root,pet=A.pet,scene=A.scene,cam=A.cam,M=A.M,mesh=A.mesh;
const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
const pick=a=>a[Math.floor(Math.random()*a.length)];
const $=s=>document.querySelector(s);
const cv=document.getElementById('c');
const ray=new THREE.Raycaster(),ndc=new THREE.Vector2(),tv=new THREE.Vector3();
function cast(x,y,objs,deep){ndc.set(x/innerWidth*2-1,-(y/innerHeight)*2+1);ray.setFromCamera(ndc,cam);return ray.intersectObjects(objs,deep)[0]||null}

// ---------- 软身子：戳哪儿哪儿凹下去再弹回来 ----------
const pos=body.geometry.attributes.position,nor=body.geometry.attributes.normal;
const base=Float32Array.from(pos.array),bn=Float32Array.from(nor.array);
const dents=[];let dirty=false;
function dent(lp,v,r){if(dents.length>=5)dents.shift();dents.push({x:lp.x,y:lp.y,z:lp.z,a:0,v:v,r:r||.34})}
function updDents(dt){
  if(!dents.length&&!dirty)return;
  for(let i=dents.length-1;i>=0;i--){const d=dents[i];d.v+=(-260*d.a-11*d.v)*dt;d.a+=d.v*dt;if(Math.abs(d.a)<.002&&Math.abs(d.v)<.02)dents.splice(i,1)}
  const arr=pos.array,n=pos.count;
  for(let i=0;i<n;i++){
    const j=i*3,x=base[j],y=base[j+1],z=base[j+2];let s=0;
    for(let k=0;k<dents.length;k++){const d=dents[k],dx=x-d.x,dy=y-d.y,dz=z-d.z,q=dx*dx+dy*dy+dz*dz;if(q<d.r*d.r){let f=1-Math.sqrt(q)/d.r;f=f*f*(3-2*f);s+=d.a*f}}
    arr[j]=x-bn[j]*s;arr[j+1]=y-bn[j+1]*s;arr[j+2]=z-bn[j+2]*s;
  }
  pos.needsUpdate=true;dirty=dents.length>0;
}

// 整只的弹性：落地压扁、急停晃两下
const jig={s:0,sv:0,w:0,wv:0};
let lastY=0,lastVy=0,lastX=0,lastVx=0,wasWalk=false;

// ---------- 水珠 ----------
const dropG=new THREE.SphereGeometry(.03,6,5),dropM=new THREE.MeshStandardMaterial({color:'#8FCFEA',roughness:.1,transparent:true,opacity:.85});
const drops=[];
function drop(x,y,z,vx,vy,vz){if(drops.length>160)return;const m=new THREE.Mesh(dropG,dropM);m.position.set(x,y,z);scene.add(m);drops.push({m,vx,vy,vz,life:0})}
function updDrops(dt){
  for(let i=drops.length-1;i>=0;i--){
    const d=drops[i],p=d.m.position;d.life+=dt;d.vy-=9*dt;p.x+=d.vx*dt;p.y+=d.vy*dt;p.z+=d.vz*dt;
    if(p.y<0||d.life>2||(tub&&d.vy<0&&p.y<.42&&Math.hypot(p.x-TUB.x,p.z-TUB.z)<.78)){scene.remove(d.m);drops.splice(i,1)}
  }
}

// ---------- 澡盆和小鸭子 ----------
const TUB={x:1.3,z:1.0};
let tub=null,duck=null,bath=null,shake=null,roll=null,rubAt=-9;
function makeTub(){
  const g=new THREE.Group();g.position.set(TUB.x,0,TUB.z);
  const wall=new THREE.Mesh(new THREE.CylinderGeometry(.82,.7,.46,40,1,true),new THREE.MeshStandardMaterial({color:'#FFFFFF',roughness:.35,side:THREE.DoubleSide}));
  wall.position.y=.29;wall.castShadow=true;wall.receiveShadow=true;g.add(wall);
  mesh(new THREE.TorusGeometry(.82,.05,10,48),M('#F2B8A0',{roughness:.5}),0,.52,0,g).rotation.x=Math.PI/2;
  mesh(new THREE.CylinderGeometry(.7,.7,.04,40),M('#FFFFFF',{roughness:.35}),0,.06,0,g);
  const water=new THREE.Mesh(new THREE.CircleGeometry(.79,40),new THREE.MeshStandardMaterial({color:'#A8DDF2',roughness:.15,transparent:true,opacity:.75}));
  water.rotation.x=-Math.PI/2;water.position.y=.4;g.add(water);
  [[.5,.5],[-.5,.5],[.5,-.5],[-.5,-.5]].forEach(([x,z])=>mesh(new THREE.SphereGeometry(.07,10,8),M('#E9A27F'),x,.06,z,g));
  scene.add(g);return g;
}
function makeDuck(){
  const g=new THREE.Group(),y=M('#F7CF4A',{roughness:.4});
  mesh(new THREE.SphereGeometry(.12,16,12),y,0,0,0,g).scale.set(1.2,.8,1);
  mesh(new THREE.SphereGeometry(.075,14,10),y,.07,.12,0,g);
  mesh(new THREE.ConeGeometry(.03,.07,8),M('#F08A3C'),.15,.11,0,g).rotation.z=-Math.PI/2;
  [-1,1].forEach(s=>mesh(new THREE.SphereGeometry(.013,6,6),M('#1C1712'),.11,.15,s*.04,g));
  g.position.set(.42,.44,.3);g.rotation.y=-.6;g.userData.v=0;g.userData.h=0;tub.add(g);return g;
}
const LB={
  in:['水温刚好','咕嘟咕嘟','哇，好多水','扑通！'],
  foam:['痒痒的','泡泡好多','背上也要搓','嘿嘿，香香的','左边还没搓'],
  ready:['泡泡够多啦，可以冲水了'],
  rinse:['哗啦啦','闭眼闭眼'],
  clean:['我已经很干净啦'],
  need:'先在它身上来回搓出泡泡',
  duck:['小鸭子！','嘎嘎','它在看我']
};
const foamM=new THREE.MeshStandardMaterial({color:'#FFFFFF',roughness:.25});
const foamG=new THREE.SphereGeometry(1,10,8);
function addFoam(h){
  if(!bath||bath.out||bath.foam.length>=45)return;
  const lp=body.worldToLocal(h.point.clone()),n=h.face?h.face.normal:new THREE.Vector3(0,1,0);
  const f=new THREE.Mesh(foamG,foamM),s=.05+Math.random()*.05;
  f.scale.setScalar(.001);f.userData.s=s;f.position.copy(lp).addScaledVector(n,s*.4);body.add(f);bath.foam.push(f);
  if(Math.random()<.4)A.emit('bubble',h.point.x,h.point.y,h.point.z+.1);
  const c=bath.foam.length;
  if(c===12)A.say(pick(LB.ready));else if(c===1||c%9===0)A.say(pick(LB.foam));
  updTray();
}
function clearFoam(){if(bath)bath.foam.forEach(f=>body.remove(f))}
function enterTub(){
  if(A.S.clean>=90){bath={foam:[],out:false};A.say(pick(LB.clean));hopOut(false);return}
  bath={foam:[],out:false,rinse:0};
  P.set(TUB.x,.02,TUB.z);
  for(let i=0;i<6;i++)A.emit('bubble',TUB.x+(Math.random()-.5)*1.2,.5,TUB.z+(Math.random()-.5)*.8);
  A.say(pick(LB.in));
  A.routine([{t:'face',yaw:0,d:.3}]);
  updTray();
}
function hopOut(credit){
  if(!bath)return;bath.out=true;
  A.routine([{t:'jump',x:.45,y:0,z:.35,d:.6},{t:'fn',f:()=>{
    clearFoam();bath=null;updTray();
    if(credit)shake={t:0};
    else A.routine([{t:'walk',x:0,z:0},{t:'face',yaw:0}]);
  }}]);
}
function finishBath(){
  const r=A.act('bath');
  if(r.ok){
    A.happy=2.5;A.play('happy',.9);A.say(pick(A.L.bath));A.hearts(3);
    for(let i=0;i<6;i++)A.emit('spark',P.x+(Math.random()-.5)*1.4,.6+Math.random()*.8,P.z+.3);
  }else A.toast(r.msg);
  A.routine([{t:'wait',d:1},{t:'walk',x:0,z:0},{t:'face',yaw:0}]);
}
function splash(n){
  for(let i=0;i<n;i++){const a=Math.random()*Math.PI*2,r=.5+Math.random()*.3;
    drop(TUB.x+Math.cos(a)*r,.45,TUB.z+Math.sin(a)*r,Math.cos(a)*(.6+Math.random()),2+Math.random()*2.2,Math.sin(a)*(.6+Math.random()))}
}
function rinse(){
  if(!bath||bath.out){A.toast('先把崽崽放进澡盆');return}
  if(bath.foam.length<12){A.toast(LB.need);return}
  if(bath.rinse)return;
  bath.rinse=.001;A.say(pick(LB.rinse));updTray();
}
function callToTub(){
  const S=A.S;
  if(bath||shake||A.lift||A.onBed||S.sleeping||A.busy()||A.dragFood)return;
  if(S.clean>=90){A.say(pick(LB.clean));A.play('wiggle',.9);return}
  A.stopRoutine();
  A.routine([{t:'walk',x:.5,z:.45,sp:2},{t:'jump',x:TUB.x,y:.02,z:TUB.z,d:.6},{t:'fn',f:enterTub}]);
}
function quack(){
  duck.userData.v=2.2;duck.getWorldPosition(tv);A.emit('spark',tv.x,tv.y+.2,tv.z);
  if(bath&&!bath.out)A.say(pick(LB.duck));
}

// ---------- 托盘 ----------
const css=document.createElement('style');
css.textContent=`
#fxTray{position:fixed;left:50%;bottom:84px;transform:translate(-50%,20px);z-index:6;display:flex;gap:2px;border-radius:26px;padding:6px 8px;opacity:0;pointer-events:none;transition:opacity .22s,transform .22s}
#fxTray.on{opacity:1;transform:translate(-50%,0);pointer-events:auto}
#fxTray button{border:none;background:none;width:58px;display:flex;flex-direction:column;align-items:center;font-size:11.5px;color:#3D3929;padding:4px 0;gap:1px;border-radius:18px}
#fxTray button span{font-size:24px;line-height:1.15}
#fxTray button i{font-style:normal}
#fxTray button.hot{background:#FBE1D3}
#fxTray button:active{transform:scale(.92)}
body.map #fxTray{display:none}
`;
document.head.appendChild(css);
const tray=document.createElement('div');tray.id='fxTray';tray.className='glass';
tray.innerHTML='<button id="fxTub"><span>🛁</span><i>放澡盆</i></button><button id="fxDuck"><span>🦆</span><i>小鸭子</i></button><button id="fxRinse"><span>🚿</span><i>冲水</i></button><button id="fxClose"><span>✕</span><i>收起</i></button>';
document.body.appendChild(tray);
function updTray(){
  $('#fxTub i').textContent=tub?'收澡盆':'放澡盆';
  $('#fxDuck').style.opacity=tub?1:.4;
  $('#fxDuck i').textContent=duck?'收鸭子':'小鸭子';
  const inTub=!!(bath&&!bath.out);
  $('#fxRinse').style.opacity=inTub?1:.4;
  $('#fxRinse').classList.toggle('hot',inTub&&bath.foam.length>=12&&!bath.rinse);
}
function toggleTub(){
  if(tub){
    if(bath||shake){A.toast('崽崽还在洗呢');return}
    scene.remove(tub);tub=null;duck=null;
  }else{tub=makeTub();A.toast('把崽崽拎进澡盆，或者点一下澡盆叫它过来')}
  updTray();
}
function toggleDuck(){
  if(!tub){A.toast('先放澡盆');return}
  if(duck){tub.remove(duck);duck=null}else duck=makeDuck();
  updTray();
}
function closeTray(){
  tray.classList.remove('on');
  if(tub&&!bath&&!shake){scene.remove(tub);tub=null;duck=null}
  updTray();
}
$('#fxTub').onclick=toggleTub;
$('#fxDuck').onclick=toggleDuck;
$('#fxRinse').onclick=rinse;
$('#fxClose').onclick=closeTray;
document.getElementById('bathBtn').onclick=()=>{
  if(A.S.out){A.toast('崽崽出门了，还没回来');return}
  if(tray.classList.contains('on')){closeTray();return}
  tray.classList.add('on');if(!tub)toggleTub();updTray();
};
const oldBack=window.petBack;
window.petBack=function(){
  if(tray.classList.contains('on')&&!document.getElementById('sheet').classList.contains('on')){closeTray();return true}
  return oldBack?oldBack():false;
};

// ---------- 手指 ----------
const fl={vx:0,vy:0,t:0,lx:0,ly:0};
let down=null;
cv.addEventListener('pointerdown',e=>{
  if(A.mode!=='home'||A.S.out)return;
  fl.t=e.timeStamp;fl.lx=e.clientX;fl.ly=e.clientY;fl.vx=fl.vy=0;
  down={x:e.clientX,y:e.clientY,t:performance.now(),moved:false,lx:e.clientX,ly:e.clientY};
  const h=cast(e.clientX,e.clientY,[body],false);
  if(h&&!A.lift){dent(body.worldToLocal(h.point.clone()),3.2,.34);jig.sv-=1.2}
});
cv.addEventListener('pointermove',e=>{
  const dt=(e.timeStamp-fl.t)/1000;
  if(dt>0&&dt<.2){fl.vx=fl.vx*.5+(e.clientX-fl.lx)/dt*.5;fl.vy=fl.vy*.5+(e.clientY-fl.ly)/dt*.5}
  fl.t=e.timeStamp;fl.lx=e.clientX;fl.ly=e.clientY;
  if(!down)return;
  if(Math.abs(e.clientX-down.x)+Math.abs(e.clientY-down.y)>10)down.moved=true;
  if(bath&&!bath.out&&!bath.rinse&&down.moved&&Math.hypot(e.clientX-down.lx,e.clientY-down.ly)>16){
    const h=cast(e.clientX,e.clientY,[body],false);
    if(h){down.lx=e.clientX;down.ly=e.clientY;rubAt=A.clock;addFoam(h);dent(body.worldToLocal(h.point.clone()),.8,.25)}
  }
});
cv.addEventListener('pointerup',e=>{
  if(!down)return;const d=down;down=null;
  if(d.moved||performance.now()-d.t>350||A.lift)return;
  if(cast(e.clientX,e.clientY,[body],false))return;
  if(duck&&cast(e.clientX,e.clientY,[duck],true)){quack();return}
  if(tub&&cast(e.clientX,e.clientY,[tub],true))callToTub();
});
cv.addEventListener('pointercancel',()=>{down=null});

// 松手：按手指最后的速度甩出去
function release(l){
  if(performance.now()-fl.t>90){fl.vx=0;fl.vy=0}
  let vx=clamp(fl.vx/innerWidth*4.4,-7,7),vy=clamp(-fl.vy/innerHeight*7,-3,7);
  if(Math.abs(vx)<.8)vx=0;if(vy<1)vy=0;
  l.vx=vx;l.vy=vy;fl.vx=fl.vy=0;
}
// 落地：掉进澡盆 / 弹一下 / 摔得太狠滚两圈晕掉
function land(l){
  const vy=-(l.vy||0),vx=l.vx||0;
  if(l.first==null)l.first=Math.max(vy,Math.abs(vx)*1.5);
  if(tub&&!bath&&Math.hypot(P.x-TUB.x,P.z-TUB.z)<.62){splash(Math.round(14+vy*4));enterTub();return 'tub'}
  if(vy>4.2&&(l.b||0)<2){l.b=(l.b||0)+1;l.vy=vy*.36;l.vx=vx*.6;return 'bounce'}
  if(l.first>6.8||Math.abs(vx)>3){
    const dir=Math.sign(vx)||(Math.random()<.5?-1:1);
    roll={t:0,vx:vx*.7||dir*2,dir};
    for(let i=0;i<8;i++)A.emit('spark',P.x+(Math.random()-.5)*1.2,.2,P.z+.3);
    return 'hard';
  }
  return '';
}
// 横着走：身子侧过来，尽量还是朝着你
function crab(a){
  const n=x=>Math.atan2(Math.sin(x),Math.cos(x));
  const p=n(a+Math.PI/2),m=n(a-Math.PI/2);
  return Math.abs(p)<Math.abs(m)?p:m;
}

// ---------- 每一帧 ----------
function frame(dt){
  if(dt<=0)return;
  const S=A.S,lift=A.lift;
  if(S.out&&(bath||shake||roll)){clearFoam();bath=null;shake=null;roll=null;updTray()}
  updDents(dt);

  const vy=(P.y-lastY)/dt,vx=(P.x-lastX)/dt;
  if(lastVy<-2&&vy>-.5)jig.sv-=Math.min(7,-lastVy)*.75;
  const w=A.walking;
  if(wasWalk&&!w)jig.wv+=(Math.abs(lastVx)>.2?-Math.sign(lastVx):(Math.random()<.5?-1:1))*1.8;
  wasWalk=w;lastY=P.y;lastVy=vy;lastX=P.x;lastVx=vx;

  jig.sv+=(-190*jig.s-8*jig.sv)*dt;jig.s=clamp(jig.s+jig.sv*dt,-.45,.45);
  jig.wv+=(-140*jig.w-6*jig.wv)*dt;jig.w=clamp(jig.w+jig.wv*dt,-.5,.5);

  let st=jig.s;
  if(lift){
    if(!lift.fall){st+=clamp((lift.th-P.y)*.5,-.15,.35);root.rotation.z+=clamp((P.x-lift.tx)*.6,-.5,.5)}
    else st+=clamp(Math.abs(lift.vy)*.025,0,.2);
    if(tub&&!bath){const near=Math.abs(P.x-TUB.x)<.75;P.z+=((near?TUB.z:0)-P.z)*Math.min(1,dt*6)}
  }
  root.scale.y*=1+st;
  const kk=1/Math.sqrt(Math.max(.3,1+st));root.scale.x*=kk;root.scale.z*=kk;
  root.rotation.z+=jig.w;

  if(roll){
    roll.t+=dt;const k=Math.min(1,roll.t/.9),e=1-Math.pow(1-k,3),ang=-roll.dir*e*Math.PI*4,h=.65;
    root.rotation.z+=ang;root.position.x+=h*Math.sin(ang);root.position.y+=h-h*Math.cos(ang);
    P.x=clamp(P.x+roll.vx*(1-k)*dt,-2.3,2.3);
    if(k>=1)roll=null;
  }

  if(bath&&!bath.out){
    P.set(TUB.x,.02,TUB.z);root.position.y-=.24;
    if(bath.rinse){
      bath.rinse+=dt;A.setEyes('closed');
      for(let i=0;i<3;i++)drop(P.x+(Math.random()-.5)*1.1,2.4,P.z+(Math.random()-.5)*.8,0,-1.5,0);
      if(bath.rinse>1.6)hopOut(true);
    }else if(A.clock-rubAt<.4)A.setEyes('happy');
  }
  if(bath)bath.foam.forEach(f=>{
    const g=f.userData.s*(bath.rinse?Math.max(0,1-bath.rinse/1.3):1);
    f.scale.setScalar(Math.max(.0001,f.scale.x+(g-f.scale.x)*Math.min(1,dt*10)));
  });

  if(shake){
    shake.t+=dt;const k=shake.t/1.1;
    if(k>=1){shake=null;finishBath()}
    else{
      pet.rotation.y+=Math.sin(shake.t*42)*.5*(1-k);A.setEyes('closed');
      for(let i=0;i<3;i++){const a=Math.random()*Math.PI*2;drop(P.x+Math.cos(a)*.6,.5+Math.random()*.5,P.z+Math.sin(a)*.5,Math.cos(a)*2.5,1+Math.random()*1.5,Math.sin(a)*2.5)}
    }
  }

  updDrops(dt);
  if(duck){
    const u=duck.userData;u.h+=u.v*dt;u.v-=9*dt;if(u.h<0){u.h=0;u.v=0}
    duck.position.y=.44+Math.sin(A.clock*2.2)*.015+u.h;duck.rotation.z=Math.sin(A.clock*1.7)*.08;
  }
}

window.PetFx={frame,release,land,crab,busy:()=>!!(bath||shake||roll)};
updTray();
})();
