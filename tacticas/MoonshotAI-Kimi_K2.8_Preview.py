import time
MODEL_NAME="Moonshot AI-Kimi K2.8 Preview"
V={"UP":(0,-1),"DOWN":(0,1),"LEFT":(-1,0),"RIGHT":(1,0)}
D=((0,-1),(0,1),(-1,0),(1,0))
def _f(st,bk,W,H,t,cp,t0):
 se=bytearray(W*H);se[st]=1;q=[st];i=0
 while i<len(q)and len(q)<cp:
  if(i&255)==0 and time.perf_counter()-t0>.008:break
  c=q[i];i+=1;x=c%W;y=c//W
  for dx,dy in D:
   nx=x+dx;ny=y+dy
   if 0<=nx<W and 0<=ny<H:ni=ny*W+nx
   elif t:ni=ny%H*W+nx%W
   else:continue
   if not se[ni]and not bk[ni]:se[ni]=1;q.append(ni)
 return se,len(q)
class A:
 def decide_action(s,p):
  try:return s._d(p)
  except Exception:
   v=p.get("valid_actions")
   return v[0]if v else"UP"
 def _d(s,p):
  t0=time.perf_counter()
  W=p["width"]//20;H=p["height"]//20
  va=[a for a in p["valid_actions"]if a in V]or["UP"]
  h=p["my_head"];hx=h[0]//20;hy=h[1]//20
  if not(0<=hx<W and 0<=hy<H):return va[0]
  sc=p["my_score"];r=p.get("active_rules")or{}
  sl=r.get("self_collision")or sc>=100
  wl=r.get("wall_collision")or sc>=200
  el=r.get("enemy_collision")or sc>=298
  tr=bool(r.get("toroidal_world",True))and not wl
  b=[q[1]//20*W+q[0]//20 for q in p["my_body"]if len(q)>=2]or[hy*W+hx]
  bl=len(b);bst=set(b)
  fs={q[1]//20*W+q[0]//20 for q in p["foods"]}
  fx=[(f%W,f//W)for f in fs]
  def dm(i):
   x=i%W;y=i//W
   return min(min(abs(x-u),W-abs(x-u))+min(abs(y-v),H-abs(y-v))for u,v in fx)if fx else 999
  bb=bytearray(W*H)
  if el:
   for e in p.get("enemies")or():
    for q in list(e.get("body")or())+([e["head"]]if e.get("head")else[]):
     if len(q)>=2:
      x=q[0]//20;y=q[1]//20
      if 0<=x<W and 0<=y<H:bb[y*W+x]=1
    eh=e.get("head")
    if eh and len(eh)>=2:
     ei=eh[1]//20*W+eh[0]//20;x=ei%W;y=ei//W
     for dx,dy in D:
      nx=x+dx;ny=y+dy
      if 0<=nx<W and 0<=ny<H:bb[ny*W+nx]=1
  cd=[];dz=[]
  for a in va:
   dx,dy=V[a];nx=hx+dx;ny=hy+dy
   if 0<=nx<W and 0<=ny<H:ni=ny*W+nx
   elif tr:ni=ny%H*W+nx%W
   else:continue
   if sl:
    ea=ni in fs
    if ea:
     if ni in bst:continue
    elif bl>1 and ni in bst and(ni!=b[-1]or b[-1]==b[-2]):continue
   if el and bb[ni]:dz.append((dm(ni),a));continue
   cd.append((dm(ni),a,ni))
  if not cd:
   if dz:return max(dz)[1]
   return va[0]
  if not sl:return min(cd)[1]
  cp=min(2000,max(600,bl+50))
  ok=None;uk=None;us=-1
  for d,a,ni in sorted(cd):
   if time.perf_counter()-t0>.009:return(ok or uk or(d,a))[1]
   ea=ni in fs
   nb=[ni]+b if ea else[ni]+b[:-1]
   bk=bytearray(bb)
   for i in nb[1:]:bk[i]=1
   if not ea:bk[nb[-1]]=0
   bk[ni]=0
   se,z=_f(ni,bk,W,H,tr,cp,t0)
   if z<len(nb)and z<cp:
    if z>us:us=z;uk=(d,a)
    continue
   k=(1 if ea or se[nb[-1]]else 2,d,-z)
   if ok is None or k<ok[0]:ok=(k,a)
  return ok[1]if ok else uk[1]if uk else cd[0][1]
def create_agent():return A()
