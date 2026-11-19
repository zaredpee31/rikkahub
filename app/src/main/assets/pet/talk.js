// 崽崽自己说话：刚发生的事（摸、喂、放学、考试、朋友……）和妈妈对它说的话，打包成事实发给服务器的小模型。
// 只显示气泡，不改任何数值、不写日记。服务器那边全挂了就不冒泡，不塞写死的句子。
(function(){
const A=window.PetApp,B=window.PetBridge;if(!A||!B||typeof B.say!=='function')return;
const $=s=>document.querySelector(s);
let seq=0,want=null,lastAt=-99,recent=[],prevOut=A.S?A.S.out:null,talkPending=false;
// 同一件事隔多久才再说（秒），免得一直摸一直说
const COOL={pet:25,hug:12,bath:20,play:20,feed:8,cook:8};const lastEv={};
const ROOMS=['在家厨房','在家客厅','在家小书房'];
const noNum=s=>String(s||'').replace(/[+\-]?\d+(\.\d+)?/g,'').replace(/\s+/g,' ').trim().slice(0,30);
function facts(ev,detail,text){
  const S=A.S,d=new Date(),day=new Date(d);day.setHours(0,0,0,0);
  const who=w=>w==='mom'?'妈妈':w==='claude'?'爸爸':'我';
  const log=(S.log||[]).filter(x=>x.t>=day.getTime()).map(x=>who(x.who)+'：'+noNum(x.text)).slice(-12);
  return {name:S.name,grade:S.grade,
    time:'周'+'日一二三四五六'[d.getDay()]+' '+d.getHours()+':'+String(d.getMinutes()).padStart(2,'0'),
    where:S.out?('在外面：'+(S.out.place||S.out.name||'')):(ROOMS[A.room]||'在家'),
    state:{hunger:S.hunger,energy:S.energy,mood:S.mood,clean:S.clean,sick:!!S.sick,sleeping:!!S.sleeping},
    friends:(S.friendList||[]).map(f=>f.name),log,event:ev,detail:noNum(detail),text:text||'',recent:recent.slice(-6)};
}
function ask(ev,detail,text){
  const t=A.clock;
  if(!text){if(COOL[ev]&&t-(lastEv[ev]||-99)<COOL[ev])return;if(t-lastAt<4)return}
  lastEv[ev]=t;lastAt=t;
  const id=String(++seq);want=id;
  try{B.say(id,JSON.stringify(facts(ev,detail,text)))}catch(e){}
}
window.petSaid=function(id,text){
  if(id!==want)return;
  const wasTalk=talkPending;talkPending=false;
  if(!text){if(wasTalk)A.toast('崽崽没听清，再说一次吧');return}
  recent.push(text);if(recent.length>8)recent.shift();
  A.say(text,Math.max(3000,text.length*280));
};

const MAP={pet:'pet',hug:'hug',bath:'bath',play:'play',sleep:'sleep',wake:'wake',pickup:'pickup',homework:'homework',feed:'feed',cook:'cook'};
window.petHook=function(cmd,r){
  if(!r||!r.ok)return;
  const c=String(cmd).split(' ')[0],ev=MAP[c];if(!ev)return;
  ask(ev,c==='pet'||c==='hug'?'':r.msg);
};
// 放学、考试、朋友的通知页面会弹一次 toast，弹的时候顺手让崽崽说一句；考试和朋友优先
window.petNotes=function(n){
  if(n.exam)ask('exam',n.exam);else if(n.friend)ask('friend',n.friend);
};
// 出门回来：上学回来算放学到家，别的算玩回来
setInterval(()=>{
  const o=A.S&&A.S.out;
  const was=prevOut;prevOut=o||null;
  if(was&&!o)setTimeout(()=>ask(was.zone==='schoolday'?'home':'back',was.place||was.name||''),1200);
  talkBtn.style.display=(A.mode==='home'&&!o)?'':'none';
},1000);

// 跟崽崽说话：右边一列加个按钮，弹一个小输入框
const right=$('#right');
const talkBtn=document.createElement('button');talkBtn.className='act';talkBtn.id='talkBtn';
talkBtn.innerHTML='<span>💬</span>说话';
if(right)right.appendChild(talkBtn);
talkBtn.onclick=()=>{
  if(A.S.out){A.toast('崽崽出门了，还没回来');return}
  A.openSheet('<h3>跟崽崽说</h3><input class="nm" id="talkIn" maxlength="60" placeholder="想跟它说什么"><button class="btn" id="talkOk">说</button>');
  const inp=$('#talkIn');setTimeout(()=>inp&&inp.focus(),100);
  const go=()=>{const v=(inp.value||'').trim();if(!v)return;A.closeSheet();talkPending=true;A.say('……',9000);ask('talk','',v)};
  $('#talkOk').onclick=go;inp.onkeydown=e=>{if(e.key==='Enter')go()};
};
})();
