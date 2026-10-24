MODEL_NAME="Cursor_Grok_4.6_Medium"
# Port of kotlin/tactics/Cursor_Grok_4.6_Medium.kt (the model's own strategy), translated to Python by the arena admin.
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
 w=p.width;h=p.height;t=p.toroidal;mk=p.marks;g=p.newMark();N=p.nb;B=p.bodies;F=set(p.foods)
 b=p.myBody;hd=b[0];tl=b[-1];v=p.myScore;el=v>298 or p.enemyCollision
 def nx(c,k):
  x=N[4*c+k];return x if x>=0 else hd
 def ds(a,c):
  i=abs(a%w-c%w);j=abs(a//w-c//w);return min(i,w-i)+min(j,h-j)if t else i+j
 def bl(c,e):return c==tl and e or mk[c]==g or el and B.get(c,0)>0
 def rm(st,e,cp):
  if not e and st==tl:return cp
  if bl(st,e):return 0
  mv=p.newMark();mk[st]=mv;q=p.queue;q[0]=st;n=1;i=0
  while i<n:
   c=q[i];i+=1
   for k in range(4):
    x=nx(c,k)
    if mk[x]!=mv and not bl(x,e):
     if not e and x==tl:return cp
     mk[x]=mv;q[n]=x;n+=1
     if n>cp:return cp
  return n
 if v>98:
  for i in range(len(b)if p.previousReward==15 else len(b)-1):mk[b[i]]=g
 mk[hd]=g
 if el:
  for e in p.enemies:
   for k in range(4):mk[nx(e.body[0],k)]=g
 tg=tl;bk=BIG
 for i,f in enumerate(p.foods):
  d=ds(hd,f);r=p.foodRivalDistance(i);k=d+(800000 if el and r<8 else 8+2*(d-r)if r<d else 2 if r==d else 0)
  if k<bk:bk=k;tg=f
 cp=min(len(b)+(48 if el else 8),3000);hu=p.turnsWithoutFood>800;sv=p.turnsWithoutFood>1350;md=p.myDirection
 be=p.validMoves[0];K=None
 for m in p.validMoves:
  c=nx(hd,m);f=c in F;r=rm(c,f,2 if sv else cp)
  a=((1 if r>0 else 0)if hu else r,0 if hu or not f else 1,-ds(c,tg),0 if hu else-ds(c,tl),-(m!=md))
  if K is None or a>K:K=a;be=m
 return be
