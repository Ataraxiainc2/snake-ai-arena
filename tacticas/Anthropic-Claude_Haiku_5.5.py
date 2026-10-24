MODEL_NAME="Anthropic-Claude_Haiku_5.5"
# Port of kotlin/tactics/Anthropic-Claude_Haiku_5.5.kt (the model's own strategy), translated to Python by the arena admin.
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
 w=p.width;h=p.height;t=p.toroidal and p.myScore<200;mk=p.marks;B=p.bodies;F=set(p.foods)
 b=p.myBody;hd=b[0];tl=b[-1];v=p.myScore;el=v>298 or p.enemyCollision
 def nx(c,k):
  x=c%w+DX[k];y=c//w+DY[k];return(y+h)%h*w+(x+w)%w if t else y*w+x if 0<=x<w and 0<=y<h else hd
 def ds(a,c):
  i=abs(a%w-c%w);j=abs(a//w-c//w);return min(i,w-i)+min(j,h-j)if t else i+j
 bs=p.newMark();ln=len(b)if v>98 else 0
 for _ in range(ln):p.newMark()
 g=p.newMark();mk[hd]=g
 def blk(c,k):
  m=mk[c];return m==g or bs<m<=bs+ln and m-bs>k or el and B.get(c,0)>0
 def stamp():
  for i in range(ln):
   if mk[b[i]]!=g:mk[b[i]]=bs+ln-i
 def flood(s,cap,ea):
  q=p.queue;x0=p.newMark();mk[s]=x0;q[0]=s;n=1;i=0;d=1;e=1
  while i<n:
   if i==e:d+=1;e=n
   c=q[i];i+=1
   for k in range(4):
    x=nx(c,k)
    if mk[x]!=x0 and not blk(x,d+1-ea):
     mk[x]=x0;q[n]=x;n+=1
     if n>=cap:return n
  return n
 if el:
  for e in p.enemies:
   for k in range(4):mk[nx(e.body[0],k)]=g
 stamp();tg=tl;bk=BIG
 for i,f in enumerate(p.foods):
  d=ds(hd,f);r=p.foodRivalDistance(i);k=d+(800000 if el and r<8 else 8+2*(d-r)if r<d else 0)
  if k<bk:bk=k;tg=f
 hu=p.turnsWithoutFood>800;st=p.turnsWithoutFood>p.starvationTurns-150;cap=len(b)*3//2+120+(40 if el else 0)
 r0=-1;s0=0;bm=p.validMoves[0]
 for m in p.validMoves:
  if r0>=0 and p.nanosLeft()<=0:break
  c=nx(hd,m);stamp()
  if blk(c,0 if p.previousReward==15 else 1):continue
  f=c in F;x=flood(c,cap,1 if f else 0)
  r=x if not hu else min(x,1)if st else(1 if 2*x>=len(b)else 0)
  s=(1<<20 if f else 0)-(ds(c,tg)<<10)-ds(c,tl)
  if r>r0 or r==r0 and s>s0:r0=r;s0=s;bm=m
 if r0<0:
  for m in p.validMoves:
   if B.get(nx(hd,m),0)<1:return m
 return bm
