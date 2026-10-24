MODEL_NAME="Google-Gemini_Flash_Latest"
# Port of kotlin/tactics/Google-Gemini_Flash_Latest.kt (the model's own strategy), translated to Python by the arena admin.
import time
DX=(0,0,-1,1);DY=(-1,1,0,0);AC=('UP','DOWN','LEFT','RIGHT');BIG=1<<62
class E:
 def __init__(s,i,b,v,d):s.id=i;s.body=b;s.score=v;s.direction=d
class P:
 def __init__(s,p,a):
  s.t0=time.perf_counter();k=p['block_size'];w=s.width=p['width']//k;h=s.height=p['height']//k
  C=lambda z:z[1]//k*w+z[0]//k
  s.myBody=[C(z)for z in p['my_body']];s.myScore=int(p['my_score']);s.foods=[C(z)for z in p['foods']]
  s.enemies=[E(e['id'],[C(z)for z in e['body']],int(e['score']),AC.index(e['direction']))for e in p['enemies']]
  r=p['active_rules'];s.toroidal=r['toroidal_world'];s.selfCollision=r['self_collision']
  s.selfCollisionWarning=r['self_collision_warning'];s.enemyCollision=r['enemy_collision']
  s.validMoves=[AC.index(x)for x in p['valid_actions']];s.myDirection=AC.index(p['my_direction'])
  s.turnsWithoutFood=p['turns_without_food'];s.starvationTurns=p['starvation_turns'];s.previousReward=p['previous_reward']
  if len(a.mk)!=w*h:a.mk=[0]*(w*h);a.q=[0]*(w*h+1);a.nb={}
  s.marks=a.mk;s.queue=a.q;s.a=a;B={}
  for c in s.myBody:B[c]=B.get(c,0)+1
  for e in s.enemies:
   for c in e.body:B[c]=B.get(c,0)+1
  s.bodies=B;s.rv=None;t=s.toroidal
  if t not in a.nb:
   N=[]
   for c in range(w*h):
    for d in range(4):
     x=c%w+DX[d];y=c//w+DY[d]
     N.append((y+h)%h*w+(x+w)%w if t else y*w+x if 0<=x<w and 0<=y<h else -1)
   a.nb[t]=N
  s.nb=a.nb[t]
 def newMark(s):s.a.n+=1;return s.a.n
 def nanosLeft(s):return int((.008-time.perf_counter()+s.t0)*1e9)
 def foodRivalDistance(s,i):
  if s.rv is None:
   w,h,t=s.width,s.height,s.toroidal;H=[(e.body[0]%w,e.body[0]//w)for e in s.enemies if e.body];s.rv=[]
   for f in s.foods:
    x,y=f%w,f//w;m=2147483647
    for X,Y in H:
     u=abs(x-X);v=abs(y-Y);d=min(u,w-u)+min(v,h-v)if t else u+v
     if d<m:m=d
    s.rv.append(m)
  return s.rv[i]
class A:
 def __init__(s):s.mk=[];s.q=[];s.n=0;s.nb={}
 def decide_action(s,p):
  q=P(p,s);m=D(q)
  return AC[m]if m in q.validMoves else p['valid_actions'][0]
def create_agent():return A()
def D(p):
 w=p.width;h=p.height;t=p.toroidal and p.myScore<200;m_=p.marks;q=p.queue;g=p.newMark();F=set(p.foods)
 b=p.myBody;hd=b[0];tl=b[-1];v=p.myScore;ate=p.previousReward==15;bs=p.newMark();ln=0
 def nx(c,d):
  x=c%w+DX[d];y=c//w+DY[d];return(y+h)%h*w+(x+w)%w if t else y*w+x if 0<=x<w and 0<=y<h else -1
 def ds(a,c):
  i=abs(a%w-c%w);j=abs(a//w-c//w);return min(i,w-i)+min(j,h-j)if t else i+j
 def ok(c,d,gr):return c>=0 and m_[c]!=g and(m_[c]<=bs or m_[c]>bs+ln or d>=ln+bs+1-m_[c]+gr)
 def room(c,cap,sg,gr):
  for i in range(ln):
   if m_[b[i]]!=g:m_[b[i]]=bs+1+i
  m_[hd]=g
  if not ok(c,1,gr):return 0
  m_[c]=sg;q[0]=c;n=1;k=0;d=1;e=1
  while k<n:
   if k==e:d+=1;e=n
   a=q[k];k+=1
   for i in range(4):
    x=nx(a,i)
    if ok(x,d+1,gr)and m_[x]!=sg:
     m_[x]=sg;q[n]=x;n+=1
     if n>cap:return cap
  return n
 if v>98 or p.selfCollision or p.selfCollisionWarning:
  ln=len(b)
  for i in range(ln):m_[b[i]]=bs+1+i;p.newMark()
 if v>298 or p.enemyCollision:
  for e in p.enemies:
   for c in e.body:m_[c]=g
   if e.score>=298:
    for i in range(4):
     x=nx(e.body[0],i)
     if x>=0:m_[x]=g
 m_[hd]=g;tg=tl;bk=BIG
 for i,f in enumerate(p.foods):
  d=ds(hd,f);r=p.foodRivalDistance(i);k=d+(8+2*(d-r)if r<d else 2 if r==d else 0)
  if k<bk:bk=k;tg=f
 cap=len(b)+(40 if v>298 else 12);ht=p.turnsWithoutFood;pa=ht>p.starvationTurns-150;hu=ht>750;md=p.myDirection
 be=p.validMoves[0];K=None
 for mv in p.validMoves:
  if K is not None and p.nanosLeft()<=0:break
  c=nx(hd,mv)
  if c<0:continue
  f=c in F;r=room(c,cap,p.newMark(),(1 if ate else 0)+(1 if f else 0))
  a=((1 if r>=(1 if pa else len(b)//2)else 0)if hu else r,0 if hu or not f else 1,-ds(c,tg),0 if hu else-ds(c,tl),-(mv!=md))
  if K is None or a>K:K=a;be=mv
 return be
