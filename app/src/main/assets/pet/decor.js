// 家里摆满：三块房间的小家具、玩具堆、墙上和天花板挂的东西。只是摆设，不参与玩法，崽崽走的路（z>-2.4 那片和餐桌、书桌前）都空着。
// 全挂在一个 group 上，教室 hideRoom 会跟着整屋一起藏。
(function(){
const A=window.PetApp;if(!A||!A.scene||!A.rbox)return;
const THREE=A.THREE,scene=A.scene,rbox=A.rbox;
const G=new THREE.Group();G.name='decor';scene.add(G);
const MC={};
function mat(c,o){const k=c+'|'+(o?JSON.stringify(o):'');if(!MC[k])MC[k]=new THREE.MeshStandardMaterial(Object.assign({color:c,roughness:.8},o||{}));return MC[k]}
function glow(c,i){return new THREE.MeshStandardMaterial({color:c,emissive:c,emissiveIntensity:i||.9,roughness:.4})}
function tex(w,h,draw){const c=document.createElement('canvas');c.width=w;c.height=h;draw(c.getContext('2d'),w,h);const t=new THREE.CanvasTexture(c);t.encoding=THREE.sRGBEncoding;return t}
function tmat(t){return new THREE.MeshStandardMaterial({map:t,roughness:.9})}
function add(g,m,x,y,z,p,sh){const o=new THREE.Mesh(g,typeof m==='string'?mat(m):m);o.position.set(x||0,y||0,z||0);o.castShadow=sh!==false;o.receiveShadow=true;(p||G).add(o);return o}
function grp(x,y,z,p){const g=new THREE.Group();g.position.set(x||0,y||0,z||0);(p||G).add(g);return g}
const SPH=new THREE.SphereGeometry(1,16,12);
function ball(r,c,x,y,z,p,sx,sy,sz,sh){const o=add(SPH,c,x,y,z,p,sh);o.scale.set(r*(sx||1),r*(sy||1),r*(sz||1));return o}
const cyl=(a,b,h,s)=>new THREE.CylinderGeometry(a,b,h,s||18);
const box=(w,h,d,r)=>rbox(w,h,d,r==null?Math.min(w,h,d)*.18:r,3);
const sway=[],spin=[],clocks=[],tw=[];

// 从天花板（画面外）吊下来，返回绳子下端的挂点
function hang(x,top,len,z,amp,p){
  const piv=grp(x,top,z,p);add(cyl(.006,.006,len,4),'#8B7F70',0,-len/2,0,piv,false);
  sway.push({o:piv,a:amp||.04,f:.7+Math.random()*.6,ph:Math.random()*6});return grp(0,-len,0,piv)}
function rope(pts,c,r){const cv=new THREE.CatmullRomCurve3(pts.map(p=>new THREE.Vector3(p[0],p[1],p[2])));return add(new THREE.TubeGeometry(cv,40,r||.008,4),c||'#8B7F70',0,0,0,G,false)}
const sagY=(y,s,t)=>y-s*(1-(2*t-1)**2);
function sag(x0,x1,y,z,s){const a=[];for(let i=0;i<=16;i++){const t=i/16;a.push([x0+(x1-x0)*t,sagY(y,s,t),z])}return a}
// 三角彩旗
function bunting(x0,x1,y,z,s,cols,r){
  rope(sag(x0,x1,y,z,s));const n=Math.round((x1-x0)/(r*2.2)),tri=new THREE.CircleGeometry(r,3);
  for(let i=1;i<n;i++){const t=i/n,f=grp(x0+(x1-x0)*t,sagY(y,s,t),z+.01);
    const o=add(tri,mat(cols[i%cols.length],{side:THREE.DoubleSide}),0,-r*.5,0,f,false);o.rotation.z=-Math.PI/2;
    sway.push({o:f,a:.07,f:1+Math.random(),ph:i})}}
// 小灯串，会一闪一闪
function lights(x0,x1,y,z,s,n){
  rope(sag(x0,x1,y,z,s),'#6B5A4B',.006);const ms=['#FFD27A','#FFB3A7','#BFE3FF','#C8F0B8'].map(c=>glow(c,.9));tw.push(...ms);
  for(let i=1;i<n;i++){const t=i/n;ball(.045,ms[i%4],x0+(x1-x0)*t,sagY(y,s,t)-.05,z+.03,G,1,1.3,1,false)}}
function starGeo(r,d){const s=new THREE.Shape();for(let i=0;i<10;i++){const a=Math.PI/2+i*Math.PI/5,rr=i%2?r*.45:r,x=Math.cos(a)*rr,y=Math.sin(a)*rr;i?s.lineTo(x,y):s.moveTo(x,y)}
  const g=new THREE.ExtrudeGeometry(s,{depth:d||.04,bevelEnabled:true,bevelThickness:.012,bevelSize:.012,bevelSegments:2});g.center();return g}
function plant(x,y,z,p,sc,potC){const g=grp(x,y,z,p);g.scale.setScalar(sc||1);const pc=potC||'#D97757';
  add(cyl(.17,.13,.3),pc,0,.15,0,g);add(cyl(.185,.185,.05),pc,0,.3,0,g);ball(.15,'#7A5A3E',0,.31,0,g,1,.15,1);
  for(let i=0;i<7;i++){const r=grp(0,.3,0,g);r.rotation.y=i/7*Math.PI*2;const l=ball(.1,i%2?'#8DB37A':'#7BA05B',.07,.22+(i%3)*.05,0,r,.35,2.4,.8);l.rotation.z=-.45-(i%2)*.25}
  ball(.1,'#8DB37A',0,.6,0,g,.8,2.6,.35);return g}
function frame(x,y,z,w,h,t,fc,p){const g=grp(x,y,z,p);add(box(w+.08,h+.08,.04,.015),fc||'#C99A6E',0,0,0,g,false);add(new THREE.PlaneGeometry(w,h),tmat(t),0,0,.025,g,false);return g}
function bear(x,y,z,ry,sc,c){const g=grp(x,y,z);g.rotation.y=ry||0;g.scale.setScalar(sc||1);const b=c||'#B98A5E',l='#EBD6BF',e='#3A2E26';
  ball(.2,b,0,.2,0,g,1,1.1,.9);ball(.1,l,0,.2,.15,g,1,1.1,.4);ball(.16,b,0,.5,0,g);
  [-1,1].forEach(s=>{ball(.055,b,s*.11,.64,0,g);ball(.03,l,s*.11,.64,.035,g,1,1,.5);ball(.06,b,s*.2,.28,.05,g,1,1.4,1);ball(.07,b,s*.1,.07,.13,g,1,.8,1.3);ball(.016,e,s*.055,.53,.145,g)});
  ball(.065,l,0,.47,.13,g,1,.8,.7);ball(.022,e,0,.49,.19,g);return g}
function bunny(x,y,z,sc){const g=grp(x,y,z);g.scale.setScalar(sc||1);const w='#FFFDF8',pk='#F2B8A0';
  ball(.11,w,0,.1,0,g,1,1,.9);ball(.085,w,0,.25,.01,g);[-1,1].forEach(s=>{const e=ball(.03,w,s*.04,.38,0,g,1,3,.8);e.rotation.z=-s*.15;ball(.016,pk,s*.04,.38,.02,g,1,3,.5).rotation.z=-s*.15;ball(.012,'#3A2E26',s*.032,.27,.075,g);ball(.018,pk,s*.05,.235,.07,g,1,.7,.4)});return g}
const crab=(c,x,y,r)=>{c.fillStyle='#D97757';c.beginPath();c.ellipse(x,y,r,r*.72,0,0,7);c.fill();[-1,1].forEach(s=>{c.beginPath();c.arc(x+s*r*1.05,y-r*.35,r*.3,0,7);c.fill();c.fillStyle='#3A2E26';c.beginPath();c.arc(x+s*r*.32,y-r*.1,r*.11,0,7);c.fill();c.fillStyle='#D97757'})};
const font=(c,px,col)=>{c.font='bold '+px+'px sans-serif';c.fillStyle=col;c.textAlign='center';c.textBaseline='middle'};
function clock(x,y,z,p){const g=grp(x,y,z,p);
  const t=tex(128,128,(c)=>{c.fillStyle='#FFFDF8';c.fillRect(0,0,128,128);c.fillStyle='#D97757';for(let i=0;i<12;i++){const a=i/12*Math.PI*2;c.beginPath();c.arc(64+Math.sin(a)*50,64-Math.cos(a)*50,i%3?3:6,0,7);c.fill()}});
  add(cyl(.3,.3,.06,32),'#F2B8A0',0,0,0,g,false).rotation.x=Math.PI/2;
  add(new THREE.CircleGeometry(.26,32),tmat(t),0,0,.035,g,false);
  const hr=grp(0,0,.045,g),mn=grp(0,0,.05,g);add(box(.026,.14,.01,.004),'#6B5A4B',0,.06,0,hr,false);add(box(.018,.2,.01,.004),'#6B5A4B',0,.09,0,mn,false);
  ball(.018,'#D97757',0,0,.055,g,1,1,1,false);clocks.push({hr,mn})}
function block(s,c,ch,x,y,z,ry,p){const t=tex(64,64,(g)=>{g.fillStyle=c;g.fillRect(0,0,64,64);g.strokeStyle='rgba(255,255,255,.7)';g.lineWidth=4;g.strokeRect(5,5,54,54);font(g,36,'#FFFDF8');g.fillText(ch,32,35)});
  const o=add(new THREE.BoxGeometry(s,s,s),tmat(t),x,y,z,p);o.rotation.y=ry||0;return o}
function books(x,y,z,n,p,ry){const g=grp(x,y,z,p);g.rotation.y=ry||0;const cs=['#D97757','#A9C6D9','#F0C36A','#8DB37A','#F2B8A0'];let h=0;
  for(let i=0;i<n;i++){const th=.05+(i%2)*.015;const o=add(box(.34-(i%3)*.03,th,.24,.01),cs[i%5],(i%2?.02:-.02),h+th/2,0,g);o.rotation.y=(i%3-1)*.12;h+=th}return g}

// 墙上的画
const famT=tex(160,120,(c)=>{c.fillStyle='#FBEDE1';c.fillRect(0,0,160,120);c.fillStyle='#D97757';c.beginPath();c.arc(72,24,8,0,7);c.arc(88,24,8,0,7);c.fill();c.beginPath();c.moveTo(64,28);c.lineTo(80,44);c.lineTo(96,28);c.fill();
  crab(c,42,70,22);crab(c,118,70,22);crab(c,80,82,13);font(c,16,'#8A5A35');c.fillText('我们家',80,108)});
const fruitT=tex(120,150,(c)=>{c.fillStyle='#FFF8EC';c.fillRect(0,0,120,150);c.fillStyle='#E0524A';c.beginPath();c.arc(36,52,20,0,7);c.fill();c.fillStyle='#7BA05B';c.beginPath();c.ellipse(42,28,8,4,-.5,0,7);c.fill();
  c.fillStyle='#F5D04A';c.beginPath();c.ellipse(84,50,20,15,.3,0,7);c.fill();c.fillStyle='#A8C66C';c.beginPath();c.arc(60,92,16,0,7);c.arc(60,76,11,0,7);c.fill();font(c,17,'#8A5A35');c.fillText('好好吃饭',60,130)});
const awardT=tex(160,116,(c)=>{c.fillStyle='#FFF4C9';c.fillRect(0,0,160,116);c.strokeStyle='#D9534F';c.lineWidth=8;c.strokeRect(6,6,148,104);font(c,30,'#D9534F');c.fillText('奖 状',80,40);font(c,13,'#8A5A35');c.fillText('崽崽小朋友',80,70);c.fillText('每天都很乖',80,90)});
const drawT=tex(128,160,(c)=>{c.fillStyle='#FFFFFF';c.fillRect(0,0,128,160);c.strokeStyle='#F5B83D';c.lineWidth=4;c.beginPath();c.arc(104,26,13,0,7);c.stroke();
  for(let i=0;i<8;i++){const a=i/8*Math.PI*2;c.beginPath();c.moveTo(104+Math.cos(a)*18,26+Math.sin(a)*18);c.lineTo(104+Math.cos(a)*25,26+Math.sin(a)*25);c.stroke()}
  crab(c,34,92,17);crab(c,92,92,17);crab(c,63,112,10);c.fillStyle='#7BA05B';c.fillRect(0,136,128,24);font(c,13,'#3A2E26');c.fillText('爸爸   妈妈',63,62)});
const tableT=tex(140,110,(c)=>{c.fillStyle='#FFFDF8';c.fillRect(0,0,140,110);font(c,14,'#D97757');c.fillText('课程表',70,14);c.strokeStyle='#C9B9A6';c.lineWidth=1;
  for(let i=0;i<=5;i++){c.beginPath();c.moveTo(8+i*25,26);c.lineTo(8+i*25,104);c.stroke()}for(let j=0;j<=4;j++){c.beginPath();c.moveTo(8,26+j*19.5);c.lineTo(133,26+j*19.5);c.stroke()}
  const cs=['#F2B8A0','#A9C6D9','#8DB37A','#F0C36A'];for(let i=0;i<5;i++)for(let j=0;j<4;j++){c.fillStyle=cs[(i+j)%4];c.fillRect(11+i*25,29+j*19.5,19,13)}});
const globeT=tex(128,64,(c)=>{c.fillStyle='#8EC5EE';c.fillRect(0,0,128,64);c.fillStyle='#8DB37A';[[20,22,14,10],[52,40,10,14],[80,20,18,9],[104,44,12,8],[66,54,20,5]].forEach(([x,y,a,b])=>{c.beginPath();c.ellipse(x,y,a,b,.4,0,7);c.fill()})});
const matT=tex(128,64,(c)=>{for(let i=0;i<8;i++){c.fillStyle=i%2?'#FFFDF8':'#F2B8A0';c.fillRect(i*16,0,16,64)}c.strokeStyle='#E9A27F';c.lineWidth=4;c.strokeRect(2,2,124,60)});

// ================= 厨房（x=-5） =================
const K=grp(-5,0,0);
// 灶台上方的搁板和罐子
add(box(1.4,.05,.28,.015),'#C99A6E',-.2,2.05,-3.84,K);
[-.75,.35].forEach(x=>add(box(.04,.18,.2,.01),'#B9926C',x,1.94,-3.88,K,false));
[['#F4EFE6','#D97757',-.7],['#FBE7C6','#8DB37A',-.45],['#F4EFE6','#F0C36A',-.2]].forEach(([c,l,x],i)=>{const h=.22-i*.03;add(cyl(.09,.09,h),mat(c,{roughness:.3}),x,2.075+h/2,-3.82,K);add(cyl(.095,.095,.04),l,x,2.095+h,-3.82,K)});
plant(.25,2.075,-3.82,K,.45,'#F2B8A0');
// 挂锅铲的横杆
add(cyl(.015,.015,1.0,8),'#B9926C',-.2,1.5,-3.9,K,false).rotation.z=Math.PI/2;
add(box(.03,.3,.02,.01),'#C99A6E',-.55,1.33,-3.88,K,false);add(box(.12,.14,.015,.02),'#E9A27F',-.55,1.12,-3.88,K,false);
add(box(.025,.32,.02,.01),'#D7D9DE',-.25,1.32,-3.88,K,false);ball(.07,'#D7D9DE',-.25,1.13,-3.86,K,1,.6,1,false);
add(cyl(.06,.055,.11,16),'#A9C6D9',.1,1.38,-3.86,K,false);add(new THREE.TorusGeometry(.035,.01,6,12),mat('#A9C6D9'),.17,1.38,-3.86,K,false);
// 墙上的辣椒蒜头串
rope([[.85,2.6,-3.9],[.85,1.75,-3.9]],'#C9A27C',.007);ball(.06,'#FFFDF8',.85,2.52,-3.86,K,1,.9,1,false);
for(let i=0;i<6;i++){const o=add(new THREE.ConeGeometry(.035,.15,8),'#E0524A',.85+(i%2?.05:-.05),2.38-i*.11,-3.86,K,false);o.rotation.z=Math.PI+(i%2?-.4:.4)}
clock(1.4,2.75,-3.95,K);
frame(-1.6,2.65,-3.95,.42,.52,fruitT,'#F2B8A0',K);
// 冰箱顶上的饼干罐、冰箱贴
add(cyl(.14,.15,.24),mat('#F4EFE6',{roughness:.3}),-1.75,1.68,-3.5,K);add(cyl(.15,.15,.04),'#D97757',-1.75,1.82,-3.5,K);ball(.035,'#D97757',-1.75,1.86,-3.5,K);
ball(.04,'#8DB37A',-1.55,1.18,-3.13,K,1,1,.4,false);ball(.04,'#F0C36A',-1.95,.92,-3.13,K,1,1,.4,false);
// 灶台前的地垫、角落的大盆栽、门口的菜篮
const fm=add(new THREE.PlaneGeometry(1.3,.6),tmat(matT),-.2,.012,-2.8,K,false);fm.rotation.x=-Math.PI/2;
plant(1.75,0,-3.45,K,1.25);
const bk=grp(-1.6,0,2.5,K);add(cyl(.26,.2,.2,20),'#C99A6E',0,.1,0,bk);add(new THREE.TorusGeometry(.26,.02,6,24),mat('#B9926C'),0,.2,0,bk).rotation.x=Math.PI/2;
ball(.09,'#E0524A',-.08,.24,.05,bk);ball(.085,'#E0524A',.1,.23,-.04,bk);add(new THREE.ConeGeometry(.04,.26,8),'#E9A27F',.05,.3,.1,bk).rotation.z=1.1;ball(.08,'#8DB37A',-.06,.27,-.1,bk,1,1.3,1);
// 吊灯
const k1=hang(1,7,3.75,-.9,.03,K);add(new THREE.CylinderGeometry(.06,.3,.26,24,1,true),mat('#F2B8A0',{side:THREE.DoubleSide}),0,-.13,0,k1,false);ball(.07,glow('#FFE7B8',1),0,-.2,0,k1,1,1,1,false);
bunting(-7.5,-2.7,4.55,-3.85,.3,['#F2B8A0','#FFFDF8','#8DB37A'],.13);

// ================= 客厅（x=0） =================
bunting(-2.6,2.6,4.6,-3.85,.38,['#D97757','#F0C36A','#A9C6D9','#8DB37A','#F2B8A0'],.15);
lights(-2.7,2.7,3.85,-3.9,.3,22);
// 床头上方的搁板：小兔子、小熊、相框
add(box(1.3,.05,.26,.015),'#C99A6E',-2,3.0,-3.86,G);[-2.5,-1.5].forEach(x=>add(box(.04,.16,.2,.01),'#B9926C',x,2.9,-3.9,G,false));
bunny(-2.4,3.03,-3.82,.9);bear(-1.95,3.03,-3.82,.15,.38,'#E9C7A0');
const sf=frame(-1.55,3.17,-3.83,.16,.2,famT,'#F2B8A0');sf.rotation.x=-.1;
frame(.2,2.7,-3.95,.5,.38,famT);
// 墙角玩具箱和堆着的玩具
const tb=grp(2.2,0,-3.25);add(box(.75,.42,.5,.04),'#F0C36A',0,.21,0,tb);add(box(.68,.02,.43,.01),'#C9A24F',0,.415,0,tb,false);ball(.07,'#D97757',0,.22,.255,tb,1,.9,.2,false);
ball(.11,'#E0524A',-.18,.47,.02,tb);block(.16,'#A9C6D9','★',.14,.48,-.04,.5,tb).rotation.z=.4;ball(.04,'#FFFDF8',.02,.5,.12,tb,1,2.4,.8);
[[0,0,'#D97757','A'],[1,0,'#8DB37A','B'],[2,0,'#A9C6D9','C'],[.5,1,'#F0C36A','♥'],[1.5,1,'#F2B8A0','1'],[1,2,'#D97757','★']].forEach(([i,j,c,ch])=>block(.18,c,ch,1.25+i*.19,.09+j*.18,-2.95,(i*7%3-1)*.12));
bear(1.0,0,-3.45,.25,1);
plant(.2,0,-3.55,G,1.3);
const fl=grp(-.4,0,-3.6);add(cyl(.18,.2,.04),'#C99A6E',0,.02,0,fl);add(cyl(.02,.02,1.7,8),'#C99A6E',0,.87,0,fl);
add(new THREE.CylinderGeometry(.18,.3,.34,24,1,true),mat('#FFF4DC',{side:THREE.DoubleSide,emissive:'#FFE2B0',emissiveIntensity:.35}),0,1.8,0,fl,false);ball(.06,glow('#FFE7B8',1),0,1.72,0,fl,1,1,1,false);
// 天花板吊下来的云和热气球
const c1=hang(-1.0,7,2.8,-1.8,.05);[[-.14,0,.1],[0,.05,.13],[.15,0,.1]].forEach(([x,y,r])=>ball(r,'#FFFFFF',x,y-.12,0,c1,1,.85,.7,false));ball(.025,'#3A2E26',-.04,-.1,.09,c1,1,1,1,false);ball(.025,'#3A2E26',.05,-.1,.09,c1,1,1,1,false);
const c2=hang(1.1,7,3.1,-1.4,.05);ball(.22,'#F2B8A0',0,-.24,0,c2,1,1.15,1,false);add(new THREE.TorusGeometry(.2,.02,6,24),mat('#F0C36A'),0,-.24,0,c2,false).rotation.x=Math.PI/2;
add(box(.11,.08,.11,.02),'#C99A6E',0,-.6,0,c2,false);[-1,1].forEach(s=>add(cyl(.004,.004,.2,4),'#8B7F70',s*.05,-.48,0,c2,false));
// 前面地上散着的积木和小黄鸭
block(.16,'#8DB37A','D',-1.9,.08,2.55,.4);block(.16,'#F2B8A0','♥',-1.62,.08,2.8,.9);
const dk=grp(1.85,0,2.7);dk.rotation.y=-.5;ball(.14,'#F5D04A',0,.1,0,dk,1,.75,.85);ball(.085,'#F5D04A',.08,.24,0,dk);add(new THREE.ConeGeometry(.03,.07,8),'#E9A27F',.17,.23,0,dk).rotation.z=-Math.PI/2;
[-1,1].forEach(s=>ball(.014,'#3A2E26',.13,.27,s*.05,dk));

// ================= 小书房（x=5） =================
const S=grp(5,0,0);
bunting(2.6,7.5,4.65,-3.85,.35,['#A9C6D9','#FFFDF8','#F0C36A'],.13);
// 书桌上方的软木板
const cb=grp(.2,2.2,-3.96,S);add(box(1.06,.76,.04,.02),'#C99A6E',0,0,0,cb,false);add(box(.98,.68,.03,.01),'#D2A679',0,0,.01,cb,false);
[[drawT,.3,.38,-.26,.02,.05],[tableT,.36,.28,.22,.13,-.04]].forEach(([t,w,h,x,y,r])=>{add(new THREE.PlaneGeometry(w,h),tmat(t),x,y,.03,cb,false).rotation.z=r;ball(.016,'#E0524A',x,y+h/2-.03,.04,cb,1,1,1,false)});
[['#F5D04A',.12,-.2],['#F2B8A0',.28,-.22],['#C8F0B8',.42,-.18]].forEach(([c,x,y],i)=>{add(new THREE.PlaneGeometry(.12,.12),mat(c),x,y,.03,cb,false).rotation.z=(i-1)*.15});
frame(-1.7,2.5,-3.96,.6,.44,awardT,'#F0C36A',S);
// 书柜顶上的地球仪和书
add(cyl(.07,.09,.03),'#C99A6E',-1.45,1.515,-3.7,S);add(cyl(.01,.01,.12,6),'#C99A6E',-1.45,1.58,-3.7,S);
const gb=add(new THREE.SphereGeometry(.14,24,16),tmat(globeT),-1.45,1.76,-3.7,S);gb.rotation.z=.4;spin.push({o:gb,v:.25});
books(-1.95,1.5,-3.68,3,S,.2);
// 书桌上的台灯和笔筒里的笔
const dl=grp(.72,.96,-3.4,S);add(cyl(.08,.09,.03),'#F2B8A0',0,.015,0,dl);add(cyl(.012,.012,.42,8),'#F2B8A0',.06,.22,0,dl).rotation.z=-.3;
const hd=grp(.14,.42,0,dl);hd.rotation.z=-.9;add(new THREE.CylinderGeometry(.03,.1,.12,16,1,true),mat('#F2B8A0',{side:THREE.DoubleSide}),0,-.04,0,hd,false);ball(.03,glow('#FFE7B8',1),0,-.07,0,hd,1,1,1,false);
['#E0524A','#5B8FD9','#7BA05B'].forEach((c,i)=>{const o=add(cyl(.009,.009,.22,6),c,.7+(i-1)*.022,1.13,-3.1+(i%2)*.02,S,false);o.rotation.z=(i-1)*.15});
// 懒人沙发、地上的书、墙上的星星贴纸
ball(.5,'#F2B8A0',1.9,.28,-2.7,S,1.05,.55,.9);ball(.3,'#EFC7AE',1.9,.47,-2.65,S,1,.3,1);
const sp=add(starGeo(.15,.07),mat('#F5D04A'),1.95,.64,-2.5,S);sp.rotation.x=-.6;
books(1.15,0,-3.45,4,S,-.3);
[[-.75,3.2],[-.45,3.5],[.8,3.3],[1.15,3.0],[1.5,3.45],[-1.1,3.55]].forEach(([x,y],i)=>add(starGeo(.06+(i%2)*.02,.01),mat(i%2?'#F0C36A':'#FBEDE1'),x,y,-3.97,S,false));
// 天花板吊着的星星
[[-1.7,-2.9,4.3],[-.9,-3.2,3.8],[0,-2.7,4.1],[.9,-3.3,3.7],[1.8,-2.9,4.4]].forEach(([x,z,y],i)=>{const e=hang(x,7,7-y,z,.03,S);const st=add(starGeo(.12,.04),mat('#F0C36A',{emissive:'#F0C36A',emissiveIntensity:.25}),0,-.12,0,e,false);spin.push({o:st,v:(i%2?1:-1)*(.3+Math.random()*.3)})});
// 前面地上的蜡笔
['#E0524A','#5B8FD9','#F5D04A','#7BA05B'].forEach((c,i)=>{const o=add(cyl(.02,.02,.14,8),c,-1.7+i*.09,.02,2.6+(i%2)*.08,S);o.rotation.set(0,i*.7,Math.PI/2)});

// ---------- 动起来：挂件轻轻晃、星星转、灯串闪、钟走真实时间 ----------
let last=-9;
function loop(ms){const t=ms/1000;
  for(const s of sway)s.o.rotation.z=Math.sin(t*s.f+s.ph)*s.a;
  for(const s of spin)s.o.rotation.y=t*s.v;
  tw.forEach((m,i)=>{m.emissiveIntensity=.55+.45*Math.max(0,Math.sin(t*1.3+i*1.7))});
  if(t-last>1){last=t;const d=new Date(),h=d.getHours()%12+d.getMinutes()/60,m=d.getMinutes()+d.getSeconds()/60;clocks.forEach(c=>{c.hr.rotation.z=-h/12*Math.PI*2;c.mn.rotation.z=-m/60*Math.PI*2})}
  requestAnimationFrame(loop)}
requestAnimationFrame(loop);
window.PetDecor={group:G};
})();
