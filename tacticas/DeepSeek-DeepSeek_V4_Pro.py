import time
MODEL_NAME="AI_Competitor"
V={"UP":(0,-1),"DOWN":(0,1),"LEFT":(-1,0),"RIGHT":(1,0)}
D=((0,-1),(0,1),(-1,0),(1,0))
TB=0.006
I=999
def _f(st,bk,W,H,t,cp,t0):
 se=bytearray(W*H);se[st]=1;q=[st];qi=0
 while qi<len(q)and len(q)<cp:
  if(qi&127)==0 and time.perf_counter()-t0>TB:break
  c=q[qi];qi+=1;x=c%W;y=c//W
  for dx,dy in D:
   nx=x+dx;ny=y+dy
   if 0<=nx<W and 0<=ny<H:ni=ny*W+nx
   elif t:ni=ny%H*W+nx%W
   else:continue
   if not se[ni]and not bk[ni]:
    se[ni]=1;q.append(ni)
    if len(q)>=cp:break
 return len(q)
class SnakeAgent:
 def decide_action(s,p):
  try:a=s._d(p)
  except Exception:a=None
  if a in V:return a
  v=p.get("valid_actions");return v[0]if v else"UP"
 def _d(s,p):
  t0=time.perf_counter()
  W=p.get("width",1000)//20;H=p.get("height",700)//20
  va=[a for a in(p.get("valid_actions")or("UP","DOWN","LEFT","RIGHT"))if a in V]
  if not va:return None
  mh=p.get("my_head")or(0,0);hx=mh[0]//20;hy=mh[1]//20
  if not(0<=hx<W and 0<=hy<H):return va[0]
  hi=hy*W+hx;md=p.get("my_direction");sc=float(p.get("my_score",0));r=p.get("active_rules")or{}
  sl=r.get("self_collision",False);el=r.get("enemy_collision",False)or sc>=298;tr=r.get("toroidal_world",True);co=sl or r.get("self_collision_warning",False)
  b=[y*W+x for x,y in((g[0]//20,g[1]//20)for g in(p.get("my_body")or())if len(g)>=2)if 0<=x<W and 0<=y<H]
  if not b:b=[hi]
  elif b[0]!=hi:b.insert(0,hi)
  bl=len(b);bst=set(b)
  fs=set(y*W+x for x,y in((f[0]//20,f[1]//20)for f in(p.get("foods")or())if len(f)>=2)if 0<=x<W and 0<=y<H)
  bb=bytearray(W*H);ehs=[]
  for e in(p.get("enemies")or()):
   eh=e.get("head")
   if eh:ehs.append(eh[1]//20*W+eh[0]//20)
  if el:
   for e in(p.get("enemies")or()):
    for g in(e.get("body")or()):
     x=g[0]//20;y=g[1]//20
     if 0<=x<W and 0<=y<H:bb[y*W+x]=1
    eh=e.get("head")
    if eh:
     ei=eh[1]//20*W+eh[0]//20
     for dx,dy in D:
      nx=ei%W+dx;ny=ei//W+dy
      if 0<=nx<W and 0<=ny<H:bb[ny*W+nx]=1
  fe={f:min((abs(f%W-e%W)+abs(f//W-e//W)for e in ehs),default=I)for f in fs}
  cd=[];dz=[]
  for a in va:
   d=V[a];x=hx+d[0];y=hy+d[1]
   if 0<=x<W and 0<=y<H:ni=y*W+x
   elif tr:ni=y%H*W+x%W
   else:continue
   if sl:
    ea=ni in fs
    if ea:
     if ni in bst:continue
    elif bl>1 and ni in bst and(ni!=b[-1]or b[-1]==b[-2]):continue
   if fs:
    if tr:bd=min((d:=min(abs(ni%W-f%W),W-abs(ni%W-f%W))+min(abs(ni//W-f//W),H-abs(ni//W-f//W)))+(50 if fe.get(f,I)<d else 0)for f in fs)
    else:bd=min((d:=abs(ni%W-f%W)+abs(ni//W-f//W))+(50 if fe.get(f,I)<d else 0)for f in fs)
   else:bd=I
   if el and bb[ni]:dz.append((bd,a,ni))
   else:cd.append((bd,a,ni))
  if not cd:
   if dz:return max(dz,key=lambda z:min((abs(z[2]%W-e%W)+abs(z[2]//W-e//W)for e in ehs),default=9))[1]
   return va[0]
  cd.sort()
  if not co:return min(cd,key=lambda x:(x[0],x[1]!=md))[1]
  cp=min(2000,max(700,bl*2))
  ok=None;uk=None;us=-1
  for ct,a,ni in cd:
   if time.perf_counter()-t0>TB:
    if ok:return ok[1]
    if uk:return uk[1]
    return cd[0][1]
   ea=ni in fs
   nb=[ni]+(b if ea else b[:-1]);nl=len(nb)
   b2=bytearray(bb)
   for i in nb[1:]:b2[i]=1
   if not ea:b2[nb[-1]]=0
   b2[ni]=0
   sp=_f(ni,b2,W,H,tr,cp,t0)
   if co and sp<2*nl and sp<cp:
    if sp>us:us=sp;uk=(sp,a)
    continue
   eh=min((abs(ni%W-e%W)+abs(ni//W-e//W)for e in ehs),default=I)if el else I
   k=(ct,-sp,-eh,a!=md)
   if ok is None or k<ok[0]:ok=(k,a)
  return ok[1]if ok else(uk[1]if uk else cd[0][1])
def create_agent():return SnakeAgent()
