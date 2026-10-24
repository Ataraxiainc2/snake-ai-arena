MODEL_NAME="Microsoft-MAI-Code-1.1-Flash"
# Port of kotlin/tactics/Microsoft-MAI-Code-1.1-Flash.kt (the model's own strategy), translated to Python by the arena admin.
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
 w=p.width;h=p.height;tor=p.toroidal;mk=p.marks;F=set(p.foods)
 body=p.myBody;head=body[0];tail=body[-1];moves=p.validMoves
 if len(moves)==1:return moves[0]
 def nxt(c,d):
  x=c%w+DX[d];y=c//w+DY[d]
  if tor:return(y+h)%h*w+(x+w)%w
  return y*w+x if 0<=x<w and 0<=y<h else -1
 def dist(a,b):
  dx=abs(a%w-b%w);dy=abs(a//w-b//w);return min(dx,w-dx)+min(dy,h-dy)if tor else dx+dy
 sd=p.selfCollision or p.selfCollisionWarning;base=0
 if sd:
  base=p.newMark()
  for _ in body:p.newMark()
  for i,c in enumerate(body):mk[c]=base+i+1
 ed=p.myScore>=299 or p.enemyCollision;blocked=p.newMark()
 if ed:
  for e in p.enemies:
   for c in e.body:mk[c]=blocked
   for d in range(4):
    c=nxt(e.body[0],d)
    if c>=0:mk[c]=blocked
 hu=p.turnsWithoutFood>700;target=p.foods[0]if p.foods else tail;tc=BIG
 for i,f in enumerate(p.foods):
  d=dist(head,f);rv=p.foodRivalDistance(i);cost=d if hu else d+(8+2*(d-rv)if rv<d else 2 if rv==d else 0)
  if cost<tc:tc=cost;target=f
 if not sd and not ed and p.myScore<100:
  best=moves[0];bv=None
  for m in moves:
   c=nxt(head,m)
   if c<0:continue
   val=(1000000 if c in F else 0)-dist(c,target)*100+(1 if m==p.myDirection else 0)
   if bv is None or val>bv:bv=val;best=m
  return best
 L=len(body)
 def opn(value,step,growth):
  if base==0 or value<=base or value>base+L:return True
  return step>=L-(value-base-1)+growth
 def room(start,cap,growth):
  if base!=0:
   for i,c in enumerate(body):
    if mk[c]!=blocked:mk[c]=base+i+1
  if mk[start]==blocked or not opn(mk[start],1,growth):return -1
  mark=p.newMark();q=p.queue;q[0]=start;mk[start]=mark;rd=0;size=1;depth=1;end=1
  while rd<size and size<=cap:
   if rd&15==0 and p.nanosLeft()<40000:break
   c=q[rd];rd+=1
   for d in range(4):
    k=nxt(c,d)
    if k>=0 and mk[k]!=mark and mk[k]!=blocked and opn(mk[k],depth+1,growth):
     mk[k]=mark;q[size]=k;size+=1
     if size>cap:break
   if rd==end:depth+=1;end=size
  return size
 rem=p.nanosLeft();cap=min(L+8,32 if rem<300000 else 96 if rem<1000000 else 400)
 ig=1 if p.previousReward>10 else 0;best=moves[0];bv=None
 for m in moves:
  if p.nanosLeft()<40000 and bv is not None:break
  c=nxt(head,m)
  if c<0 or mk[c]==blocked:continue
  food=c in F;r=room(c,cap,ig+(1 if food else 0))
  if r<0:continue
  if hu:val=r*1000000-dist(c,target)*100+(1 if m==p.myDirection else 0)
  else:val=r*1000000+(10000 if food else 0)-dist(c,target)*100-dist(c,tail)+(1 if m==p.myDirection else 0)
  if bv is None or val>bv:bv=val;best=m
 return best
