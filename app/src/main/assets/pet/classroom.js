// 崽崽：上学时偷看教室。纯观看：不改数值、不调 act()、不记日记。
// 进教室时把房间物件藏起来、显示教室组；退出时原样还原。身体和眼睛动画全部复用 index.html 的 anim 和 motion.js 的弹簧。
(function(){
const A=window.PetApp;if(!A||!A.scene||!A.resetPet||!A.floorT||!A.eyes)return;
const THREE=A.THREE,scene=A.scene,cam=A.cam,pet=A.pet,root=A.root,P=A.P;
const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
const pick=a=>a[Math.floor(Math.random()*a.length)];
const $=s=>document.querySelector(s);

// ---------- 作息（手机本地时间，纯展示） ----------
const COURSES=['语文','数学','美术','体育','音乐','科学','编程','天文'];
const NTH=['一','二','三','四','五','六','七','八'];
function phaseAt(d){
  const dow=d.getDay(),t=d.getHours()*60+d.getMinutes();
  const cls=(i,hr)=>{const c=COURSES[(dow*7+hr)%8];return {kind:c==='体育'?'pe':'class',course:c,label:'第'+NTH[i]+'节 · '+c+'课'}};
  const brk={kind:'break',label:'课间'};
  if(t>=420&&t<480)return {kind:'read',label:'早读'};
  if(t>=480&&t<720){const k=Math.floor((t-480)/60);return (t-480)%60<45?cls(k,8+k):brk}
  if(t>=720&&t<760)return {kind:'lunch',label:'午饭'};
  if(t>=760&&t<810)return {kind:'nap',label:'午休'};
  if(t>=810&&t<1050){const k=Math.floor((t-810)/60);return (t-810)%60<45?cls(4+k,13+k):brk}
  return {kind:'study',label:'自习'};
}

// ---------- 布局 ----------
const SEAT={x:0,y:.16,z:-.05};
// 崽崽在家是 1.3 宽，课桌只有 0.82，进教室整只缩到 PS；坐在课桌后面的凳子上，面朝镜头。课桌面在 .38，眼睛要比它高
const PS=.45,SIT={x:SEAT.x,y:.31,z:SEAT.z-.42};
const DESKS=[{x:-1.35,z:-1.2},{x:1.35,z:-1.2},{x:-1.4,z:-2.45},{x:1.4,z:-2.45},{x:0,z:-2.45}];
const FRIEND_DESKS=[0,1,2];

let group=null,on=false,hidden=[],seated=true,node=0,st=null,ph=null,phKey='';
let board=null,boardKey='',lunch=null,book=null,fSprites=[],fKey='';
// 左右滑动镜头：camX 是目标，cam.position.x 每帧追过去
const CAM_PAN=1.3;let camX=0,drag=null;
let lookCur=0,lookTgt=0,lookAt=0,blinkAt=0,camUntil=0,uiAt=0,phAt=0,lastDoze=false,eatAt=0;

// ---------- 界面：顶部小玻璃条 + 上学卡片里的按钮 ----------
const css=document.createElement('style');
css.textContent=`
body.cls .home{display:none!important}
#clsBar{position:fixed;z-index:5;left:12px;right:12px;top:12px;height:46px;border-radius:23px;display:none;align-items:center;gap:6px;padding:0 6px}
body.cls #clsBar{display:flex}
#clsBack{flex:none;border:none;background:#F6E6D6;border-radius:18px;padding:8px 14px;font-size:14px;font-weight:600;color:#7A5A44}
#clsBack:active{transform:scale(.94)}
#clsNow{flex:1;min-width:0;text-align:center;font-size:14px;font-weight:600;color:#3D3929;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
#clsLeft{flex:none;font-size:12px;color:#8B7F70;padding-right:10px;white-space:nowrap}
#peek{margin-top:14px;border:none;background:#D97757;color:#fff;border-radius:16px;padding:9px 20px;font-size:14px;font-weight:600;display:none}
#peek:active{transform:scale(.94)}
`;
document.head.appendChild(css);
const bar=document.createElement('div');bar.id='clsBar';bar.className='glass';
bar.innerHTML='<button id="clsBack">‹ 回家</button><div id="clsNow"></div><div id="clsLeft"></div>';
document.body.appendChild(bar);
const peek=document.createElement('button');peek.id='peek';peek.textContent='👀 偷偷看一眼';
const outEl=$('#out');if(outEl)outEl.appendChild(peek);
$('#clsBack').onclick=()=>exit();
if(outEl)outEl.addEventListener('click',()=>enter());

// ---------- 黑板 ----------
const boardC=document.createElement('canvas');boardC.width=512;boardC.height=320;
const boardT=new THREE.CanvasTexture(boardC);boardT.encoding=THREE.sRGBEncoding;
function doodle(g,k){
  g.strokeStyle='rgba(255,252,247,.85)';g.fillStyle='rgba(255,252,247,.85)';g.lineWidth=5;g.lineCap='round';g.lineJoin='round';
  const n=k%3;
  if(n===0){for(let i=0;i<5;i++){g.beginPath();const a=i*Math.PI*2/5-Math.PI/2;g.moveTo(Math.cos(a)*34,Math.sin(a)*34);const b=a+Math.PI*2/5/2;g.lineTo(Math.cos(b)*14,Math.sin(b)*14);const c=a+Math.PI*2/5;g.lineTo(Math.cos(c)*34,Math.sin(c)*34);g.stroke()}}
  else if(n===1){
    g.beginPath();g.ellipse(0,-4,34,16,0,0,Math.PI*2);g.stroke();
    g.beginPath();g.moveTo(-34,-4);g.quadraticCurveTo(-56,6,-44,22);g.quadraticCurveTo(-30,6,-34,-4);g.stroke();
    g.beginPath();g.arc(8,-6,4,0,Math.PI*2);g.fill();
  }else{
    g.font='bold 46px sans-serif';g.textAlign='center';g.textBaseline='middle';g.fillText('1 2 3',0,0);
  }
}
function drawBoard(p){
  const key=p.course||p.kind;if(key===boardKey)return;boardKey=key;
  const g=boardC.getContext('2d');
  g.fillStyle='#2F5D50';g.fillRect(0,0,512,320);
  g.fillStyle='rgba(255,252,247,.9)';g.font='bold 64px sans-serif';g.textAlign='center';g.textBaseline='middle';
  g.fillText(p.kind==='class'||p.kind==='pe'?p.course+'课':p.label,256,118);
  g.save();g.translate(400,240);doodle(g,(p.course?COURSES.indexOf(p.course):0)+key.length);g.restore();
  boardT.needsUpdate=true;
}

// ---------- 教室（第一次进时才建，之后复用） ----------
function build(){
  group=new THREE.Group();
  const wood=A.M('#ffffff',{map:A.floorT,roughness:.75,color:'#C9A07A'});
  const fl=new THREE.Mesh(new THREE.PlaneGeometry(30,30),wood);fl.rotation.x=-Math.PI/2;fl.receiveShadow=true;group.add(fl);
  const wall=new THREE.Mesh(new THREE.PlaneGeometry(30,16),A.M('#FBF6EE',{roughness:.95}));wall.position.set(0,8,-4.2);wall.receiveShadow=true;group.add(wall);
  group.add(new THREE.Mesh(new THREE.BoxGeometry(30,.18,.06),A.M('#E7D7C4')));
  group.children[group.children.length-1].position.set(0,.09,-4.16);

  const frame=A.mesh(new THREE.BoxGeometry(2.7,1.72,.06),A.M('#6B4A32'),0,1.05,-4.14,group);
  board=A.mesh(new THREE.PlaneGeometry(2.5,1.5),new THREE.MeshBasicMaterial({map:boardT}),0,1.05,-4.1,group);board.castShadow=false;

  // 窗户：透点光
  const win=new THREE.Group();win.position.set(-2.6,1.7,-4.16);group.add(win);
  A.mesh(new THREE.PlaneGeometry(1.5,1.15),new THREE.MeshBasicMaterial({color:'#C9E6F6'}),0,0,.01,win);
  [[-.37,0],[.37,0],[0,-.28],[0,.28]].forEach(([x,y])=>A.mesh(new THREE.BoxGeometry(x?0.03:1.5,y?0.03:1.15,.02),A.M('#F4E7D6'),x,y,.02,win));

  function deskAt(x,z){
    const g=new THREE.Group();g.position.set(x,0,z);group.add(g);
    A.mesh(A.rbox(.82,.045,.5,.02,2),A.M('#E8C9A0'),0,.38,0,g);
    [[-.34,-.2],[.34,-.2],[-.34,.2],[.34,.2]].forEach(([lx,lz])=>A.mesh(new THREE.CylinderGeometry(.025,.025,.38,8),A.M('#C9A27C'),lx,.19,lz,g));
    const s=new THREE.Group();s.position.set(0,0,-.42);g.add(s);
    A.mesh(A.rbox(.34,.04,.34,.018,2),A.M('#D97757'),0,.3,0,s);
    A.mesh(new THREE.CylinderGeometry(.03,.03,.3,8),A.M('#C9A27C'),0,.15,0,s);
    addObs(x,z,.82,.5);addObs(x,z-.42,.34,.34);
    return g;
  }
  deskAt(SEAT.x,SEAT.z);
  DESKS.forEach(d=>deskAt(d.x,d.z));
  buildGrid();

  lunch=new THREE.Group();lunch.position.set(SEAT.x,.405,SEAT.z-.02);group.add(lunch);
  A.mesh(A.rbox(.2,.055,.15,.02,2),A.M('#F2EFE9'),0,0,0,lunch);
  A.mesh(A.rbox(.17,.02,.12,.008,2),A.M('#F0C36A'),0,.03,0,lunch);
  lunch.visible=false;

  book=new THREE.Group();book.position.set(SEAT.x,.415,SEAT.z-.06);group.add(book);
  A.mesh(new THREE.BoxGeometry(.16,.012,.2),A.M('#7FC4E0'),-.04,0,0,book).rotation.y=.05;
  A.mesh(new THREE.BoxGeometry(.16,.012,.2),A.M('#F08FA0'),.04,0,0,book).rotation.y=-.05;
  book.visible=false;

  group.visible=false;scene.add(group);
}

function friendKey(){return ((A.S.friendList)||[]).filter(f=>f.at==='school').map(f=>f.id).sort().join(',')}
function syncFriends(){
  const k=friendKey();if(k===fKey)return;fKey=k;
  fSprites.forEach(s=>group.remove(s));fSprites=[];
  const fl=((A.S.friendList)||[]).filter(f=>f.at==='school');
  fl.forEach((f,i)=>{
    const d=DESKS[FRIEND_DESKS[i]];if(!d)return;
    const c=document.createElement('canvas');c.width=c.height=128;const g=c.getContext('2d');
    g.font='96px sans-serif';g.textAlign='center';g.textBaseline='middle';g.fillText(f.emoji,64,70);
    const t=new THREE.CanvasTexture(c);t.encoding=THREE.sRGBEncoding;
    const sp=new THREE.Sprite(new THREE.SpriteMaterial({map:t,transparent:true,depthWrite:false}));
    sp.scale.set(.42,.42,.42);sp.position.set(d.x,.8,d.z-.05);sp.userData.base=.8;group.add(sp);fSprites.push(sp);
  });
}

// ---------- 进出 ----------
function hideRoom(){
  hidden=[];
  scene.children.slice().forEach(o=>{
    if(o===pet||o===group||o.isLight)return;
    hidden.push([o,o.visible]);o.visible=false;
  });
}
function showRoom(){hidden.forEach(([o,v])=>{o.visible=v});hidden=[]}

function enter(){
  const o=A.S.out;if(!o||o.zone!=='schoolday'||on)return;
  if(!group)build();
  on=true;document.body.classList.add('cls');
  hideRoom();group.visible=true;
  A.resetPet();window._clsPose=null;seated=true;node=0;st=null;ph=null;phKey='';boardKey='';lastDoze=false;eatAt=0;
  pet.position.set(SIT.x,SIT.y,SIT.z);pet.visible=true;pet.scale.setScalar(PS);
  camX=0;drag=null;cam.position.set(0,1.5,2.5);cam.lookAt(0,.55,-.4);
  syncFriends();applyPhase(true);tickUi();
}
function exit(){
  if(!on)return;on=false;document.body.classList.remove('cls');
  if(group)group.visible=false;showRoom();
  st=null;seated=true;if(lunch)lunch.visible=false;if(book)book.visible=false;
  A.resetPet();window._clsPose=null;pet.scale.setScalar(1);
  A.refresh();
  A.layout();
}
function tickUi(){
  const o=A.S.out,left=o?Math.max(0,o.end-Date.now()):0;
  const m=Math.floor(left/60000),h=Math.floor(m/60);
  $('#clsNow').textContent=ph?ph.label:'';
  $('#clsLeft').textContent=(h?h+' 小时 ':'')+ (m%60)+' 分钟后放学';
  if(peek)peek.style.display=(o&&o.zone==='schoolday'&&!on)?'inline-block':'none';
}
function applyPhase(force){
  const p=phaseAt(new Date());
  const key=p.kind+'|'+(p.course||'');
  if(!force&&key===phKey){ph=p;return}
  phKey=key;ph=p;
  drawBoard(p);
  lunch.visible=p.kind==='lunch';
  book.visible=p.kind==='read'||p.kind==='study'||p.kind==='class';
  st=null;lastDoze=false;eatAt=0;
  if(p.kind==='break'||p.kind==='pe'){seated=false;pet.position.set(SEAT.x,0,SEAT.z+.75)}
  else{seated=true;pet.position.set(SIT.x,SIT.y,SIT.z)}
  pet.rotation.y=0;A.baseYaw=0;A.lookT=0;
  tickUi();
}

// ---------- 状态机 ----------
function sayLine(arr){A.say(pick(arr),2600)}
function nextDelay(){return 8+Math.random()*7}
function start(s){s.t0=A.clock;st=s}
function pickState(){
  const k=ph.kind,r=Math.random();
  if(k==='break'||k==='pe'){
    if(r<.45)return {n:'wander',d:nextDelay()};
    if(r<.75&&fSprites.length)return {n:'visit',d:nextDelay()};
    return {n:'hop',d:2.2};
  }
  if(k==='lunch')return r<.7?{n:'eat',d:5}:{n:'look',d:3};
  if(k==='nap')return {n:'doze',d:nextDelay()};
  if(r<.16)return {n:'raise',d:3.2};
  if(r<.3)return {n:'doze',d:nextDelay()};
  if(r<.62)return {n:'write',d:nextDelay()};
  return {n:'listen',d:nextDelay()};
}
const RAISE=['我知道！','叫我叫我！','这个我会！','老师我来！'];
const VISIT_SAY=['壳壳你看','泡泡，下课找你玩','阿蓝～'];

function stepState(dt){
  if(!st){start(pickState());return}
  const t=A.clock,k=st.d>0?clamp((t-st.t0)/st.d,0,1):1;
  switch(st.n){
    case 'listen':
      seated=true;
      if(t>lookAt){lookAt=t+1.6+Math.random()*2.2;lookTgt=(Math.random()-.5)*1.1}
      A.lookT+=(lookTgt-A.lookT)*Math.min(1,dt*3);
      if(t>blinkAt){blinkAt=t+3+Math.random()*4;A.setEyes('closed');setTimeout(()=>A.setEyes('normal'),130)}
      break;
    case 'raise':
      seated=true;A.lookT+=(0-A.lookT)*Math.min(1,dt*4);
      if(!st.said){st.said=1;sayLine(RAISE)}
      break;
    case 'write':
      seated=true;A.lookT+=(0-A.lookT)*Math.min(1,dt*4);
      break;
    case 'doze':
      seated=true;A.lookT=0;
      if(!st.phase){st.phase='down';st.nod=0}
      if(st.phase==='down'){st.nod=Math.min(1,st.nod+dt*.4);if(st.nod>=1){st.phase='up';A.setEyes('normal');if(Math.random()<.6)A.emit('z',P.x+.2,P.y+.48,P.z+.14)}}
      else if(st.phase==='up'){st.nod=Math.max(0,st.nod-dt*2.6);if(st.nod<=0){st.phase='hold';st.hold=t+.7}}
      else if(t>st.hold)st.phase='down';
      break;
    case 'eat':
      seated=true;
      if(t-eatAt>.55){eatAt=t;A.play('eat',.5)}
      break;
    case 'look':
      seated=true;A.lookT+=(Math.sin(t*1.4)*.6-A.lookT)*Math.min(1,dt*3);
      break;
    case 'hop':
      seated=false;
      if(!st.played){st.played=1;A.play('happy',1.6)}
      break;
    case 'wander':case 'visit':
      seated=false;walkPath(dt);break;
  }
  if(k>=1)st=null
}

// ---------- 走路避开桌子凳子 ----------
// 每张课桌、凳子记一个矩形，按崽崽半径 PR 往外放大；课间在 0.1 一格的网格上 A* 找路，再把能直线走的点拉直
const PR=.28;
const GX0=-2.3,GZ0=-3.6,GS=.1,GW=47,GH=56;
let OBS=[],grid=null;
function addObs(cx,cz,w,d){OBS.push({x0:cx-w/2-PR,x1:cx+w/2+PR,z0:cz-d/2-PR,z1:cz+d/2+PR})}
function inObs(x,z,m=0){for(const o of OBS)if(x>o.x0-m&&x<o.x1+m&&z>o.z0-m&&z<o.z1+m)return o;return null}
function blocked(x,z,m=0){return x<GX0||x>GX0+(GW-1)*GS||z<GZ0||z>GZ0+(GH-1)*GS||!!inObs(x,z,m)}
// 格子四角任何一处碰到障碍就整格不能走，相邻两格之间走直线也不会擦到桌角
function buildGrid(){
  grid=new Uint8Array(GW*GH);const e=GS*.6;
  for(let j=0;j<GH;j++)for(let i=0;i<GW;i++){
    const x=GX0+i*GS,z=GZ0+j*GS;
    grid[j*GW+i]=blocked(x,z)||blocked(x-e,z-e)||blocked(x+e,z-e)||blocked(x-e,z+e)||blocked(x+e,z+e)?1:0;
  }
}
function cellOf(x,z){return [clamp(Math.round((x-GX0)/GS),0,GW-1),clamp(Math.round((z-GZ0)/GS),0,GH-1)]}
function freeCell(i,j){
  for(let r=0;r<12;r++)for(let dj=-r;dj<=r;dj++)for(let di=-r;di<=r;di++){
    if(Math.max(Math.abs(di),Math.abs(dj))!==r)continue;
    const ni=i+di,nj=j+dj;if(ni>=0&&nj>=0&&ni<GW&&nj<GH&&!grid[nj*GW+ni])return nj*GW+ni;
  }
  return j*GW+i;
}
const cellX=k=>GX0+(k%GW)*GS,cellZ=k=>GZ0+Math.floor(k/GW)*GS;
function clearLine(ax,az,bx,bz){
  const n=Math.max(1,Math.ceil(Math.hypot(bx-ax,bz-az)/.02));
  for(let k=1;k<=n;k++){const t=k/n;if(blocked(ax+(bx-ax)*t,az+(bz-az)*t,.03))return false}
  return true;
}
function findPath(ax,az,bx,bz){
  const s=freeCell(...cellOf(ax,az)),t=freeCell(...cellOf(bx,bz));
  const N=GW*GH,g=new Float32Array(N).fill(1e9),from=new Int32Array(N).fill(-1),done=new Uint8Array(N);
  const ti=t%GW,tj=Math.floor(t/GW),h=k=>Math.hypot(k%GW-ti,Math.floor(k/GW)-tj);
  g[s]=0;const open=[s];
  while(open.length){
    let bi=0;for(let q=1;q<open.length;q++)if(g[open[q]]+h(open[q])<g[open[bi]]+h(open[bi]))bi=q;
    const c=open[bi];open[bi]=open[open.length-1];open.pop();
    if(done[c])continue;done[c]=1;if(c===t)break;
    const ci=c%GW,cj=(c-ci)/GW;
    for(let dj=-1;dj<=1;dj++)for(let di=-1;di<=1;di++){
      if(!di&&!dj)continue;
      const ni=ci+di,nj=cj+dj;if(ni<0||nj<0||ni>=GW||nj>=GH)continue;
      const n=nj*GW+ni;if(grid[n]||done[n])continue;
      if(di&&dj&&(grid[cj*GW+ni]||grid[nj*GW+ci]))continue; // 斜着走不能擦角
      const ng=g[c]+(di&&dj?1.414:1);if(ng<g[n]){g[n]=ng;from[n]=c;open.push(n)}
    }
  }
  if(!done[t])return [];
  const pts=[];for(let k=t;k!==-1&&k!==s;k=from[k])pts.push({x:cellX(k),z:cellZ(k)});
  pts.reverse();
  if(!blocked(bx,bz,.08))pts.push({x:bx,z:bz});
  const out=[];let cx=ax,cz=az,i=0;
  while(i<pts.length){
    let j=pts.length-1;while(j>i&&!clearLine(cx,cz,pts[j].x,pts[j].z))j--;
    out.push(pts[j]);cx=pts[j].x;cz=pts[j].z;i=j+1;
  }
  return out;
}
// 课间随便走：挑一个空地、在镜头里、离现在至少半米的点
const _v=new THREE.Vector3();
function randomGoal(){
  for(let n=0;n<40;n++){
    const x=-1.6+Math.random()*3.2,z=-1.1+Math.random()*2.8;
    if(blocked(x,z,.08)||Math.hypot(x-P.x,z-P.z)<.5)continue;
    _v.set(x,.2,z).project(cam);
    if(Math.abs(_v.x)<.75&&_v.y>-.85&&_v.y<.5)return {x,z};
  }
  return {x:P.x,z:P.z};
}
// 兜底：万一在障碍里（比如刚从座位上下来），推到最近的那条边外面
function pushOut(){
  const o=inObs(P.x,P.z);if(!o)return;
  const dl=P.x-o.x0,dr=o.x1-P.x,db=P.z-o.z0,df=o.z1-P.z,m=Math.min(dl,dr,db,df);
  if(m===dl)P.x=o.x0-.001;else if(m===dr)P.x=o.x1+.001;else if(m===db)P.z=o.z0-.001;else P.z=o.z1+.001;
}

// 教室里专用的小路径：每段先找好绕开桌子的路，走完停一会儿
function walkPath(dt){
  if(st.n==='visit'&&st.arrived){A.walking=false;A.lookT=0;if(!st.said){st.said=1;sayLine(VISIT_SAY)}return}
  // 走到一个点停一会儿，转回来脸朝镜头，不然课间一直看见背影
  if(st.wait&&A.clock<st.wait){A.walking=false;A.baseYaw+=(0-A.baseYaw)*Math.min(1,dt*5);return}
  if(!st.path){
    st.pi=0;
    if(st.n==='visit'&&fSprites.length){
      const sp=fSprites[st.fi!=null?st.fi:(st.fi=Math.floor(Math.random()*fSprites.length))].position;
      st.path=findPath(P.x,P.z,sp.x,sp.z+.55);
    }else{
      // 有的空地被桌子围死了走不进去，换一个点
      for(let k=0;k<6;k++){const g=randomGoal();st.path=findPath(P.x,P.z,g.x,g.z);if(st.path.length)break}
    }
  }
  const q=st.path[st.pi];
  if(!q){
    st.path=null;A.walking=false;
    if(st.n==='visit')st.arrived=true;else st.wait=A.clock+1.2+Math.random()*1.6;
    return;
  }
  const dx=q.x-P.x,dz=q.z-P.z,dist=Math.hypot(dx,dz);
  if(dist<.03){st.pi++;return}
  const step=Math.min(dist,.5*dt);
  P.x+=dx/dist*step;P.z+=dz/dist*step;
  A.baseYaw=Math.atan2(dx,dz);A.walking=true;
}
function clampSeat(){
  if(seated){P.x+=(SIT.x-P.x)*1;P.z+=(SIT.z-P.z)*1;P.y=SIT.y;A.baseYaw+=(0-A.baseYaw)*.2}
  else{P.y+=(0-P.y)*1;pushOut()}
}

// ---------- 每一帧 ----------
function frame(dt){
  if(!on){if(A.clock>uiAt){uiAt=A.clock+1;tickUi()}return}
  if(A.mode!=='home'||!A.S.out||A.S.out.zone!=='schoolday'){exit();return}
  if(Date.now()>=A.S.out.end){exit();return}
  if(A.clock>phAt){phAt=A.clock+5;applyPhase(false)}
  if(A.clock>uiAt){uiAt=A.clock+1;tickUi()}
  syncFriends();
  stepState(dt);
  clampSeat();
  // 举手 / 写字 / 打瞌睡：交给 motion.js 的弹簧和 updatePetCls，不另写身体动画
  const dozing=!!(st&&st.n==='doze'&&st.phase!=='up'&&st.phase!=='hold');
  window._clsPose={
    raise:st&&st.n==='raise'?2.1+Math.sin(A.clock*7)*.25:0,
    write:st&&st.n==='write'?1:0,
    doze:st&&st.n==='doze'?st.nod||0:0,
    dozing:dozing
  };
  if(st&&st.n==='raise')A.setEyes('happy');
  else if(st&&st.n==='write')A.setEyes('dot');
  else if(dozing)A.setEyes('closed');
  lastDoze=dozing;
  // 午饭：桌上的饭盒跟着吃的动作轻轻抖
  if(lunch&&lunch.visible&&st&&st.n==='eat')lunch.position.y=.405+Math.abs(Math.sin(A.clock*9))*.012;
  // 同学轻轻上下晃
  fSprites.forEach((s,i)=>{s.position.y=s.userData.base+Math.sin(A.clock*1.6+i*1.7)*.035});
  // 黑板前的光，跟着课表不需要动
  cam.position.x+=(camX-cam.position.x)*Math.min(1,dt*10);
  const cx=cam.position.x;
  if(A.clock<camUntil){cam.position.z+=(2.1-cam.position.z)*Math.min(1,dt*3);cam.lookAt(cx,.6,-.4)}
  else{cam.position.z+=(2.5-cam.position.z)*Math.min(1,dt*2);cam.lookAt(cx,.55,-.4)}
}

// ---------- 点崽崽：只转头看一眼、说一句，不加数值 ----------
const IN_CLASS=['嘘，在上课呢','妈妈怎么来了','等下课再说嘛','老师在看我呢'];
const IN_BREAK=['妈妈！','下课啦，妈妈来看我','嘘……其实在玩','妈妈也来上课吗'];
function tap(){
  if(!on)return;
  camUntil=A.clock+1.6;
  pet.rotation.y=0;A.baseYaw=0;A.lookT=0;
  const k=ph?ph.kind:'';
  const loose=k==='break'||k==='pe'||k==='lunch';
  A.setEyes(loose?'happy':'squint');
  A.say(pick(loose?IN_BREAK:IN_CLASS),2400);
  setTimeout(()=>{if(on)A.setEyes('normal')},900);
}
const cv=$('#c');
// 按下记位置；左右拖就挪镜头，没怎么动就松手才算点了崽崽
cv.addEventListener('pointerdown',e=>{
  if(!on)return;
  try{cv.setPointerCapture(e.pointerId)}catch(_){}
  drag={x:e.clientX,y:e.clientY,lx:e.clientX,cx:camX,moved:false};
},true);
cv.addEventListener('pointermove',e=>{
  if(!on||!drag)return;
  if(!drag.moved&&Math.abs(e.clientX-drag.x)+Math.abs(e.clientY-drag.y)>10)drag.moved=true;
  if(drag.moved)camX=clamp(drag.cx-(e.clientX-drag.x)/innerWidth*2.6,-CAM_PAN,CAM_PAN);
},true);
function dragEnd(e){
  if(!on||!drag)return;
  const d=drag;drag=null;
  if(d.moved)return;
  const ndc=new THREE.Vector2(e.clientX/innerWidth*2-1,-(e.clientY/innerHeight)*2+1);
  const ray=new THREE.Raycaster();ray.setFromCamera(ndc,cam);
  if(ray.intersectObject(A.body,false).length)tap();
}
cv.addEventListener('pointerup',dragEnd,true);
cv.addEventListener('pointercancel',()=>{drag=null},true);

// 放学、切走、重新渲染都要能退回
const origRefresh=A.refresh;
A.refresh=function(){const was=on;origRefresh();if(was&&(!A.S.out||A.S.out.zone!=='schoolday'))exit()};
const origSetMode=A.setMode;
A.setMode=function(m){if(on&&m!=='home')exit();origSetMode(m)};
document.addEventListener('visibilitychange',()=>{if(!document.hidden&&on&&(!A.S.out||A.S.out.zone!=='schoolday'||Date.now()>=A.S.out.end))exit()});

window.PetClassroom={frame,get on(){return on},enter,exit,get _obs(){return OBS}};
})();
