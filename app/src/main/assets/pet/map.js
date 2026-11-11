(function(){
const A=window.PetApp;if(!A)return;
const THREE=A.THREE,R=A.R,rbox=A.rbox;
const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));

// ---------- 界面 ----------
const css=document.createElement('style');
css.textContent=`
#mapui{display:none}
body.map #mapui{display:block}
#mBack{position:fixed;top:14px;left:14px;z-index:5;font-size:30px;padding-bottom:4px}
#mTitle{position:fixed;top:18px;left:50%;transform:translateX(-50%);z-index:5;border-radius:18px;padding:8px 22px;font-size:16px;font-weight:700}
#mShell{position:fixed;top:20px;right:14px;z-index:5;border-radius:16px;padding:6px 12px;font-size:14px;font-weight:600}
#mOut{position:fixed;top:76px;left:50%;transform:translateX(-50%);z-index:5;border-radius:16px;padding:8px 16px;font-size:13px;display:none;white-space:nowrap}
#mLabels{position:fixed;left:0;top:0;width:0;height:0;z-index:4}
.mlab{position:absolute;transform:translate(-50%,-100%);background:rgba(255,252,247,.93);border-radius:16px;padding:6px 14px;font-size:14px;font-weight:600;white-space:nowrap;box-shadow:0 4px 12px rgba(60,80,60,.2);text-align:center;color:#3D3929}
.mlab small{display:block;font-size:10.5px;font-weight:400;color:#8B7F70}
.mlab .here{position:absolute;top:-10px;right:-8px;background:#D97757;color:#fff;font-size:10px;border-radius:8px;padding:1px 6px}
#mHint{position:fixed;bottom:22px;left:50%;transform:translateX(-50%);z-index:5;font-size:12px;color:#fff;background:rgba(40,70,90,.45);border-radius:14px;padding:6px 14px;white-space:nowrap}
.acard{background:#F8EEE3;border-radius:18px;padding:12px 14px;margin-bottom:10px}
.acard.lock{opacity:.55}
.acard .h{display:flex;align-items:center;gap:8px}
.acard .h b{font-size:15px}
.acard .h span{font-size:11.5px;color:#8B7F70}
.acard .h button{margin-left:auto;border:none;border-radius:14px;padding:6px 18px;font-size:13.5px;font-weight:600;background:#D97757;color:#fff}
.acard .h button:disabled{background:#D9CFC4}
.acard .ds{font-size:12.5px;color:#7A6E5E;margin-top:4px}
.acard .cs{font-size:11.5px;color:#8B7F70;margin-top:6px;line-height:1.6}
.acard .cs em{font-style:normal;color:#5E8A4E}
.acard .lk{font-size:11.5px;color:#B65C3C;margin-top:4px}
`;
document.head.appendChild(css);
const ui=document.createElement('div');ui.id='mapui';
ui.innerHTML='<button id="mBack" class="rbtn glass">‹</button><div id="mTitle" class="glass">出门</div><div id="mShell" class="glass"></div><div id="mOut" class="glass"></div><div id="mLabels"></div><div id="mHint">左右拖转小岛，上下拖拉远拉近，点地名看看</div>';
document.body.appendChild(ui);
document.getElementById('mBack').onclick=()=>A.setMode('home');

// ---------- 场景 ----------
const scene=new THREE.Scene();
const BG=new THREE.Color('#BFE3F2'),BGN=new THREE.Color('#2C3A5E');
scene.background=BG.clone();scene.fog=new THREE.Fog(BG.clone(),50,95);
const cam=new THREE.PerspectiveCamera(36,1,.1,200);
const hemi=new THREE.HemisphereLight(0xffffff,0x9cc58a,.85);scene.add(hemi);
const sun=new THREE.DirectionalLight(0xfff2dd,1.05);
sun.position.set(8,16,10);sun.castShadow=true;sun.shadow.mapSize.set(1024,1024);
const sc=sun.shadow.camera;sc.left=-10;sc.right=10;sc.top=10;sc.bottom=-10;sc.near=1;sc.far=50;sc.updateProjectionMatrix();
sun.shadow.bias=-.0008;scene.add(sun);
const M=(c,o)=>new THREE.MeshStandardMaterial(Object.assign({color:c,roughness:.85},o||{}));
function mesh(g,m,x,y,z,p){const o=new THREE.Mesh(g,m);o.position.set(x||0,y||0,z||0);o.castShadow=true;o.receiveShadow=true;p.add(o);return o}
let seed=7;
function rnd(){seed=seed+0x6D2B79F5|0;let t=Math.imul(seed^seed>>>15,1|seed);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}

const seaM=M('#7CC4DE',{roughness:.35});
const sea=new THREE.Mesh(new THREE.CircleGeometry(80,48),seaM);sea.rotation.x=-Math.PI/2;sea.receiveShadow=true;scene.add(sea);

const isl=new THREE.Group();scene.add(isl);
const GY=.6;
const foamM=new THREE.MeshBasicMaterial({color:'#FFFFFF',transparent:true,opacity:.6});
const foam=new THREE.Mesh(new THREE.RingGeometry(1,1.06,72),foamM);foam.rotation.x=-Math.PI/2;foam.position.y=.03;foam.scale.set(7.7,6.35,1);isl.add(foam);
const sand=mesh(new THREE.CylinderGeometry(1,1.06,.5,72),M('#F2DDB0'),0,.1,0,isl);sand.scale.set(7.3,1,6);
const grass=mesh(new THREE.CylinderGeometry(1,1,.25,72),M('#9CCB7A'),0,.475,0,isl);grass.scale.set(6.4,1,5.1);

const winM=M('#CFE6F2',{emissive:'#FFD27A',emissiveIntensity:0});
const doorM=M('#8A5A3C');
function zoneGroup(x,y,z,zone){const g=new THREE.Group();g.position.set(x,y,z);g.userData.zone=zone;isl.add(g);return g}
function house(x,z,w,h,d,wall,roof,zone){
  const g=zoneGroup(x,GY,z,zone);
  mesh(rbox(w,h,d,.06,3),M(wall),0,h/2,0,g);
  const rf=new THREE.Group();rf.position.y=h;rf.scale.set(w*.78,1,d*.78);g.add(rf);
  const c=mesh(new THREE.ConeGeometry(1,h*.75,4),M(roof),0,h*.375,0,rf);c.rotation.y=Math.PI/4;
  mesh(new THREE.BoxGeometry(w*.24,h*.42,.04),doorM,0,h*.21,d/2+.01,g);
  [-1,1].forEach(s=>mesh(new THREE.BoxGeometry(w*.18,w*.18,.04),winM,s*w*.3,h*.62,d/2+.01,g));
  return g;
}

// 职业小镇
house(-3.5,-.5,1.0,.8,.9,'#FFF3E0','#E07A5F','work');
house(-2.2,-2.1,1.1,1.0,.9,'#F4E9D8','#6C8EBF','work');
house(-2.0,-.5,.9,.75,.85,'#FFFFFF','#F2B33D','work');
const ws=house(-3.6,-2.0,1.0,.9,.9,'#F7F1EA','#D97757','work');
mesh(new THREE.BoxGeometry(.14,.4,.14),M('#B9A89A'),.3,1.25,-.15,ws);
const dock=zoneGroup(-7.5,.26,1.2,'work');
mesh(new THREE.BoxGeometry(1.9,.08,.62),M('#B98B5E'),0,0,0,dock);
[[-.85,-.27],[-.85,.27],[.85,-.27],[.85,.27]].forEach(p=>mesh(new THREE.CylinderGeometry(.05,.05,.6,8),M('#9A714A'),p[0],-.25,p[1],dock));
mesh(new THREE.BoxGeometry(.24,.22,.24),M('#C9A36E'),-.5,.15,.1,dock);
mesh(new THREE.BoxGeometry(.2,.18,.2),M('#B88E58'),-.25,.13,-.12,dock);

// 海螺学校
const sch=zoneGroup(2.7,GY,-1.9,'school');
const schW=M('#FBEFE2');
mesh(rbox(2.2,1.1,1.1,.08,3),schW,0,.55,0,sch);
mesh(new THREE.CylinderGeometry(.75,.75,2.35,3,1,false,Math.PI/2),M('#E07A5F'),0,1.475,0,sch).rotation.z=Math.PI/2;
mesh(rbox(.6,1.9,.6,.05,3),schW,0,.95,.3,sch);
mesh(new THREE.ConeGeometry(.5,.7,4),M('#6C8EBF'),0,2.25,.3,sch).rotation.y=Math.PI/4;
mesh(new THREE.CircleGeometry(.17,24),M('#FFFFFF'),0,1.5,.61,sch);
mesh(new THREE.BoxGeometry(.02,.12,.01),M('#3D3929'),0,1.54,.62,sch);
mesh(new THREE.BoxGeometry(.09,.02,.01),M('#3D3929'),.035,1.5,.62,sch);
mesh(new THREE.BoxGeometry(.2,.36,.04),doorM,0,.18,.61,sch);
[-.8,-.45,.45,.8].forEach(x=>mesh(new THREE.BoxGeometry(.2,.2,.04),winM,x,.62,.56,sch));
mesh(new THREE.CylinderGeometry(.02,.02,1.6,6),M('#E8E2D8'),1.35,.8,.75,sch);
mesh(new THREE.BoxGeometry(.38,.22,.02),M('#F28B82'),1.55,1.45,.75,sch);

// 游乐园
const fair=zoneGroup(-.5,GY,2.5,'play');
const legM=M('#F4F1EA');
[-1,1].forEach(s=>[-1,1].forEach(f=>{const l=mesh(new THREE.CylinderGeometry(.035,.045,1.6,8),legM,s*.32,.72,f*.12,fair);l.rotation.z=-s*.38}));
const wheel=new THREE.Group();wheel.position.set(0,1.45,0);fair.add(wheel);
mesh(new THREE.TorusGeometry(.85,.035,8,40),M('#F2B33D'),0,0,0,wheel);
for(let i=0;i<4;i++){const b=mesh(new THREE.BoxGeometry(1.7,.03,.03),legM,0,0,0,wheel);b.rotation.z=i*Math.PI/4}
const cabins=[],cabCol=['#F28B82','#7FC4E0','#9CCB7A','#F5C84C'];
for(let i=0;i<8;i++){const a=i/8*Math.PI*2;const c=mesh(rbox(.2,.18,.18,.05,2),M(cabCol[i%4]),Math.cos(a)*.85,Math.sin(a)*.85,.05,wheel);cabins.push(c)}

// 风车公园
const park=zoneGroup(2.8,GY,2.1,'play');
mesh(new THREE.CylinderGeometry(.25,.4,1.4,8),M('#F6EEDF'),0,.7,0,park);
mesh(new THREE.ConeGeometry(.34,.42,8),M('#D97757'),0,1.6,0,park);
const blades=new THREE.Group();blades.position.set(0,1.3,.34);park.add(blades);
for(let i=0;i<4;i++){const b=mesh(new THREE.BoxGeometry(.12,.95,.02),M('#FFFFFF'),0,.48,0,new THREE.Group());const p=new THREE.Group();p.rotation.z=i*Math.PI/2;p.add(b.parent);blades.add(p)}
mesh(new THREE.SphereGeometry(.06,10,8),M('#B9926C'),0,0,.02,blades);

// 月牙海滩
const beach=zoneGroup(5.2,.35,3.4,'play');
mesh(new THREE.CylinderGeometry(.025,.025,.9,6),M('#FFFFFF'),0,.45,0,beach);
mesh(new THREE.ConeGeometry(.6,.25,12),M('#F28B82'),0,.95,0,beach);
mesh(new THREE.BoxGeometry(.4,.02,.7),M('#7FC4E0'),.45,.01,.1,beach);

// 灯塔
const lh=zoneGroup(-.6,GY,-4.2,'play');
[['#FFFFFF',.32,.38,0],['#E8505B',.28,.32,.4],['#FFFFFF',.24,.28,.8],['#E8505B',.2,.24,1.2]].forEach(a=>mesh(new THREE.CylinderGeometry(a[1],a[2],.4,16),M(a[0]),0,a[3]+.2,0,lh));
const lampM=M('#FFF1B8',{emissive:'#FFE27A',emissiveIntensity:.3});
mesh(new THREE.CylinderGeometry(.17,.17,.22,12),lampM,0,1.71,0,lh);
mesh(new THREE.ConeGeometry(.24,.28,12),M('#E8505B'),0,1.96,0,lh);

// 小广场和小路
const pathM=M('#EBD3A8',{roughness:.95});
const PATHS=[[0,0,-2.7,-1.1],[0,0,2.7,-1.0],[0,0,-.5,1.8],[0,0,2.6,1.6],[0,0,-.6,-3.7],[-3.4,-.2,-6.5,1.2]];
PATHS.forEach(([x1,z1,x2,z2])=>{const dx=x2-x1,dz=z2-z1,L=Math.hypot(dx,dz);const p=new THREE.Mesh(new THREE.BoxGeometry(.38,.02,L),pathM);p.position.set((x1+x2)/2,GY+.011,(z1+z2)/2);p.rotation.y=Math.atan2(dx,dz);p.receiveShadow=true;isl.add(p)});
mesh(new THREE.CylinderGeometry(.9,.9,.03,32),pathM,0,GY+.015,0,isl);
mesh(new THREE.CylinderGeometry(.35,.4,.18,20),M('#D9D2C5'),0,GY+.09,0,isl);
mesh(new THREE.CylinderGeometry(.3,.3,.02,20),M('#8FD3EA',{roughness:.2}),0,GY+.18,0,isl);
mesh(new THREE.CylinderGeometry(.05,.07,.3,10),M('#D9D2C5'),0,GY+.3,0,isl);

// 树
function segD(x,z,[x1,z1,x2,z2]){const dx=x2-x1,dz=z2-z1,t=clamp(((x-x1)*dx+(z-z1)*dz)/(dx*dx+dz*dz),0,1);return Math.hypot(x-x1-t*dx,z-z1-t*dz)}
const KEEP=[[-2.8,-1.2,1.9],[2.7,-1.9,1.75],[-.5,2.5,1.3],[2.8,2.1,.9],[-.6,-4.2,.7],[0,0,1.2]];
const trunkG=new THREE.CylinderGeometry(.06,.08,.3,6),c1=new THREE.ConeGeometry(.38,.6,8),c2=new THREE.ConeGeometry(.28,.45,8);
const trunkM=M('#9A714A'),greens=['#7FB069','#6FA35B','#8CC07A'].map(c=>M(c));
let n=0;
for(let i=0;i<200&&n<46;i++){
  const x=(rnd()*2-1)*6.2,z=(rnd()*2-1)*4.9;
  if((x/5.9)**2+(z/4.7)**2>1)continue;
  if(KEEP.some(k=>Math.hypot(x-k[0],z-k[1])<k[2]))continue;
  if(PATHS.some(p=>segD(x,z,p)<.45))continue;
  const t=new THREE.Group();t.position.set(x,GY,z);t.scale.setScalar(.75+rnd()*.5);isl.add(t);
  const gm=greens[n%3];
  mesh(trunkG,trunkM,0,.15,0,t);mesh(c1,gm,0,.55,0,t);mesh(c2,gm,0,.85,0,t);
  n++;
}

// 云
const cloudM=new THREE.MeshStandardMaterial({color:'#FFFFFF',roughness:1,transparent:true,opacity:.9});
const clouds=[];
for(let i=0;i<5;i++){
  const c=new THREE.Group();
  [[0,0,0,1],[.9,-.1,.1,.75],[-.85,-.15,0,.7]].forEach(p=>{const s=new THREE.Mesh(new THREE.SphereGeometry(.8*p[3],12,10),cloudM);s.position.set(p[0],p[1],p[2]);c.add(s)});
  c.position.set(-18+i*8+rnd()*3,2.5+rnd(),i%2?-11-rnd()*3:9+rnd()*2);c.userData.v=.3+rnd()*.3;scene.add(c);clouds.push(c);
}

// 小崽崽
const mini=new THREE.Group();isl.add(mini);
const mfur=M('#D97757',{roughness:.8}),meye=M('#1C1712',{roughness:.3});
mesh(rbox(.5,.32,.36,.1,6),mfur,0,.2,0,mini);
[-.17,-.06,.06,.17].forEach(x=>mesh(new THREE.BoxGeometry(.06,.1,.16),mfur,x,.04,0,mini));
[-1,1].forEach(s=>{mesh(new THREE.BoxGeometry(.13,.08,.14),mfur,s*.3,.14,0,mini);mesh(new THREE.BoxGeometry(.045,.1,.02),meye,s*.1,.26,.185,mini)});
mini.scale.setScalar(1.15);
const SPOT={cafe:[-3.5,.25],library:[-2.2,-1.35],helpdesk:[-2.0,.25],code:[-3.6,-1.25],dock:[-7.1,1.2,.3],beach:[4.75,3.9,.35],park:[2.8,2.75],fair:[-.5,3.2],stargaze:[-.6,-3.6]};
A.CAT.acts.forEach(a=>{if(a.zone==='school')SPOT[a.id]=[2.7,-.95]});

// ---------- 地名 ----------
const ZONES=[
  {id:'work',name:'职业小镇',sub:'打工赚贝壳',at:[-2.8,2.0,-1.2]},
  {id:'school',name:'海螺学校',sub:'上课长本事',at:[2.7,3.2,-1.6]},
  {id:'play',name:'出去玩',sub:'海滩 公园 游乐园 灯塔',at:[-.5,3.2,2.5]}
];
const labBox=document.getElementById('mLabels');
const labs=ZONES.map(z=>{
  const d=document.createElement('div');d.className='mlab';
  d.innerHTML=z.name+'<small>'+z.sub+'</small><span class="here" style="display:none">崽崽在这</span>';
  d.onclick=()=>openZone(z.id);labBox.appendChild(d);return d;
});
const v=new THREE.Vector3();
function placeLabels(){
  isl.updateMatrixWorld();const o=A.S.out;
  ZONES.forEach((z,i)=>{
    v.set(z.at[0],z.at[1],z.at[2]).applyMatrix4(isl.matrixWorld).project(cam);
    const d=labs[i];
    d.style.left=((v.x+1)/2*innerWidth)+'px';d.style.top=((1-v.y)/2*innerHeight)+'px';
    d.style.display=v.z<1?'block':'none';
    d.querySelector('.here').style.display=o&&o.zone===z.id?'block':'none';
  });
}

// ---------- 能去干什么 ----------
function lockOf(a,S){
  if(S.lv<a.lv)return a.lv+' 级解锁';
  if(S.int<a.needInt)return '智力要 '+a.needInt;
  if(S.eq<a.needEq)return '情商要 '+a.needEq;
  if(S.str<a.needStr)return '体能要 '+a.needStr;
  if(a.night&&!S.night)return '晚上七点以后才能去';
  return '';
}
function openZone(z){
  const S=A.S,out=S.out,esc=A.esc;
  const T={work:['职业小镇','打工能赚贝壳，属性越高赚得越多'],school:['海螺学校','上课要交学费，能长智力、情商和体能'],play:['出去玩','玩一玩心情会变好，海滩还能捡贝壳']}[z];
  let h='<h3>'+T[0]+'</h3><div class="sub">'+T[1]+'<br>现在 🐚 '+S.shells+' · 体力 '+Math.round(S.energy)+' · 饱食 '+Math.round(S.hunger)+'</div>';
  if(out)h+='<div class="sub" style="color:#B65C3C">崽崽正在'+esc(out.place)+'，回来以后才能再出门</div>';
  else if(S.sleeping)h+='<div class="sub" style="color:#B65C3C">崽崽在睡觉，先叫它起床</div>';
  h+=A.CAT.acts.filter(a=>a.zone===z).map(a=>{
    const lk=lockOf(a,S);
    const title=a.zone==='school'?a.name:a.place;
    const sub=a.zone==='school'?a.minutes+' 分钟':a.name+' · '+a.minutes+' 分钟';
    const cost=['体力 -'+a.energy,'饱食 -'+a.hunger,'清洁 -'+a.clean];if(a.cost)cost.unshift('🐚 -'+a.cost);
    const gain=[];
    if(a.id==='beach')gain.push('🐚 5~20');else if(a.shells)gain.push('🐚 '+a.shells+' 起');
    if(a.int)gain.push('智力+'+a.int);if(a.eq)gain.push('情商+'+a.eq);if(a.str)gain.push('体能+'+a.str);if(a.mood)gain.push('心情+'+a.mood);
    gain.push('经验+'+a.exp);
    return '<div class="acard'+(lk?' lock':'')+'"><div class="h"><b>'+esc(title)+'</b><span>'+esc(sub)+'</span><button data-id="'+a.id+'"'+(lk||out?' disabled':'')+'>去</button></div>'+
      '<div class="ds">'+esc(a.desc)+'</div><div class="cs">花费 '+cost.join('，')+'<br>得到 <em>'+gain.join('，')+'</em></div>'+(lk?'<div class="lk">🔒 '+lk+'</div>':'')+'</div>';
  }).join('');
  A.openSheet(h);
  document.querySelectorAll('#sheetBody button[data-id]').forEach(b=>b.onclick=()=>{
    const r=A.act('go '+b.dataset.id);A.toast(r.msg);
    if(r.ok){A.closeSheet();prevOut=true;slow()}
  });
}

// ---------- 手指 ----------
const cv=document.getElementById('c');
const ray=new THREE.Raycaster(),ndc=new THREE.Vector2();
let on=false,md=null,vel=0,zoom=1;
cv.addEventListener('pointerdown',e=>{if(!on)return;md={x:e.clientX,y:e.clientY,lx:e.clientX,ly:e.clientY,moved:false};vel=0});
cv.addEventListener('pointermove',e=>{
  if(!on||!md)return;
  const dx=e.clientX-md.lx,dy=e.clientY-md.ly;
  if(Math.abs(e.clientX-md.x)+Math.abs(e.clientY-md.y)>10)md.moved=true;
  if(md.moved){isl.rotation.y+=dx*.008;vel=dx*.008;zoom=clamp(zoom+dy*.002,.55,1.2);layout()}
  md.lx=e.clientX;md.ly=e.clientY;
});
function mUp(e){
  if(!on||!md)return;
  if(!md.moved){
    ndc.set(e.clientX/innerWidth*2-1,-(e.clientY/innerHeight)*2+1);ray.setFromCamera(ndc,cam);
    const hit=ray.intersectObjects(isl.children,true)[0];
    let o=hit&&hit.object;while(o&&o!==isl&&!o.userData.zone)o=o.parent;
    if(o&&o.userData.zone)openZone(o.userData.zone);
  }
  md=null;
}
cv.addEventListener('pointerup',mUp);cv.addEventListener('pointercancel',()=>{md=null});

// ---------- 相机 ----------
const TGT=new THREE.Vector3(0,.5,.6),EL=58*Math.PI/180;
function layout(){
  cam.aspect=innerWidth/innerHeight;
  const base=clamp(7.4/(Math.tan(cam.fov*Math.PI/360)*cam.aspect),18,46),d=base*zoom;
  cam.position.set(0,TGT.y+d*Math.sin(EL),TGT.z+d*Math.cos(EL));cam.lookAt(TGT);cam.updateProjectionMatrix();
}

// ---------- 每一帧 ----------
let t=0,acc=0,prevOut=false,lastRef=0;
function slow(){
  const S=A.S,night=!!S.night;
  scene.background.copy(night?BGN:BG);scene.fog.color.copy(scene.background);
  hemi.intensity=night?.45:.85;sun.intensity=night?.35:1.05;
  winM.emissiveIntensity=night?1:0;lampM.emissiveIntensity=night?2:.3;
  seaM.color.set(night?'#3E5F85':'#7CC4DE');
  document.getElementById('mShell').textContent='🐚 '+S.shells;
  const o=S.out,el=document.getElementById('mOut');
  if(o){
    const left=Math.ceil((o.end-Date.now())/60000);
    el.style.display='block';
    el.textContent='崽崽在'+o.place+(o.zone==='school'?'上'+o.name:o.name)+(left>0?'，'+left+' 分钟后回来':'，快到家了');
    if(Date.now()>=o.end&&Date.now()-lastRef>5000){lastRef=Date.now();A.refresh()}
  }else el.style.display='none';
  if(prevOut&&!S.out)A.toast('崽崽回家啦，回去看看它');
  prevOut=!!S.out;
}
function frame(dt){
  t+=dt;
  if(!md){isl.rotation.y+=vel;vel*=.92}
  wheel.rotation.z+=dt*.25;cabins.forEach(c=>c.rotation.z=-wheel.rotation.z);
  blades.rotation.z+=dt*1.2;
  clouds.forEach(c=>{c.position.x+=dt*c.userData.v;if(c.position.x>22)c.position.x=-22});
  foamM.opacity=.45+Math.sin(t*1.5)*.15;
  const o=A.S.out,sp=o?SPOT[o.id]:null;
  const x=sp?sp[0]:.75,z=sp?sp[1]:.6,y=sp&&sp[2]!=null?sp[2]:GY;
  mini.position.set(x,y+Math.abs(Math.sin(t*(o?5:2.5)))*.08,z);
  mini.rotation.y=o?Math.sin(t*1.3)*.4:Math.sin(t*.7)*.6;
  acc+=dt;if(acc>.5){acc=0;slow()}
  placeLabels();
  R.render(scene,cam);
}

window.PetMap={
  enter(){on=true;md=null;vel=0;layout();prevOut=!!A.S.out;slow()},
  leave(){on=false;md=null},
  back(){return false},
  frame,
  resize(){layout()}
};
})();
