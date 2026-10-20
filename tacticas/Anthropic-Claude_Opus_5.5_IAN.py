import time;MODEL_NAME='Anthropic-Claude_Opus_5.5';a4=('UP','DOWN','LEFT','RIGHT');aH={'UP':0,'DOWN':1,'LEFT':2,'RIGHT':3};U=8;ap=30;aB=0.0025;aC=0.004;aE=10**9;a8={(3,0):7,(2,0):6,(3,1):5,(2,1):4,(1,0):3,(1,1):2,(0,0):1,(0,1):0}
def af(d,de):
 if de<d:
  return d+8+2*(d-de)
 if de==d:
  return d+2
 return d
class aI:
 def __init__(self):
  self.h=-1;self.m=-1;self.w=None;self.B=None;self.T=None;self.A=0;self.H=None;self.ao=1.0
 def a9(self,h,m):
  self.h=h;self.m=m;self.A=0;self.w=[];self.B=[];self.T=[aE]*(h*m)
 def W(self,aV):
  h=self.h;m=self.m;w=self.w;B=self.B;y0=self.A;y1=min(m,y0+aV)
  for y in range(y0,y1):
   for x in range(h):
    w.append(((y-1)%m*h+x,(y+1)%m*h+x,y*h+(x-1)%h,y*h+(x+1)%h));B.append(((y-1)*h+x if y>0 else-1,(y+1)*h+x if y<m-1 else-1,y*h+x-1 if x>0 else-1,y*h+x+1 if x<h-1 else-1))
  self.A=y1;return y1==m
 def aJ(self,c,ai):
  h=self.h;m=self.m;x=c%h;y=c//h
  if ai==0:
   y=(y-1)%m
  elif ai==1:
   y=(y+1)%m
  elif ai==2:
   x=(x-1)%h
  else:
   x=(x+1)%h
  return y*h+x
 def aO(self,a,b,av):
  h=self.h;dx=abs(a%h-b%h);dy=abs(a//h-b//h)
  if av:
   if self.h-dx<dx:
    dx=self.h-dx
   if self.m-dy<dy:
    dy=self.m-dy
  return dx+dy
 @staticmethod
 def aw(a0,t0,nb,z,aP,D,bm,l,aa,X,al):
  ab=[-1]*l;S=[-1]*l if X else None;ab[a0]=t0;q=[a0];i=0;ac=1;ax=False;aW=time.perf_counter
  while i<len(q):
   c=q[i];i+=1;nd=ab[c]+1
   for n in nb[c]:
    if n>=0 and ab[n]<0 and(z[n]<=nd)and(nd+bm<D[n]):
     ab[n]=nd
     if X:
      S[n]=c
     if aP[n]:
      ax=True
     elif z[n]==0:
      ac+=1
     q.append(n)
   if al and ax:
    break
   if i&127==0 and aW()>aa:
    break
  return(ab,S,ac,ax)
 def decide_action(self,I):
  K=time.perf_counter();Q=tuple(I.get('valid_actions')or())
  if not Q:
   d=I.get('my_direction');Q=(d,)if d in aH else a4
  try:
   aF=self.a5(I,Q,K)
  except Exception:
   aF=None
  if aF not in Q:
   d=I.get('my_direction');aF=d if d in Q else Q[0]
  return aF
 def a5(self,p,Q,K):
  bs=int(p.get('block_size')or 20);h=max(1,int(p['width'])//bs);m=max(1,int(p['height'])//bs)
  if h!=self.h or m!=self.m:
   self.a9(h,m)
  l=h*m;R=float(p.get('my_score')or 0.0)
  if p.get('died_last_step')or self.H is None:
   self.H=R
  bc=R-self.H
  if 0.5<bc<=100.0 and(p.get('previous_reward')or 0.0)>=14.0:
   self.ao=bc
  self.H=R;g=self.ao;ag=p.get('active_rules')or{};C=bool(ag.get('wall_collision',R>=200));Y=bool(ag.get('self_collision',False));M=bool(ag.get('enemy_collision',R>=300));aY=bool(ag.get('toroidal_world',not C));ah=Y or bool(ag.get('self_collision_warning'))or R+2.0*g>=100;u=C or not aY or R+1.5*g>=200;Z=M;am=not M and R+1.5*g>=300
  def an(pt):
   return int(pt[1])//bs%m*h+int(pt[0])//bs%h
  ad=an(p['my_head']);E=[an(s)for s in p.get('my_body')or()]
  if not E or E[0]!=ad:
   E=[ad]+E
  J=[an(f)for f in p.get('foods')or()];r=[]
  for e in p.get('enemies')or():
   eb=e.get('body')or();eh=e.get('head')
   if eh is None and eb:
    eh=eb[0]
   if eh is None:
    continue
   aG=[an(s)for s in eb]or[an(eh)]
   try:
    N=float(e.get('score')or 0.0)<200
   except(TypeError,ValueError):
    N=True
   r.append((an(eh),aG,N))
  V=p.get('my_direction');o={'head':ad,'body':E,'foods':J,'enemies':r,'z':p.get('turns_without_food',0),'i':V,'e':Q,'u':l,'m':ah,'n':Z,'o':am,'p':Y,'q':M,'r':C,'s':K}
  if not(ah or Z or u):
   if self.A<m:
    self.W(U)
   return self.a6(o)
  if self.A<m:
   self.W(m)
  aF,bo=self.a1(o,u)
  if not bo and u and(not C):
   bp,bg=self.a1(o,False)
   if bg:
    return bp
  return aF
 def O(self,J,r,av):
  fe={}
  for f in J:
   v=aE
   for eh,_,_ in r:
    d=self.aO(eh,f,av)
    if d<v:
     v=d
   fe[f]=v
  return fe
 def a6(self,o):
  ad=o['head'];J=o['foods'];fe=self.O(J,o['enemies'],True);aQ=None;ay=None
  for a in o['e']:
   nc=self.aJ(ad,aH[a]);aZ=aE
   for f in J:
    k=af(self.aO(nc,f,True)+1,fe[f])
    if k<aZ:
     aZ=k
   key=(aZ,0 if a==o['i']else 1)
   if ay is None or key<ay:
    ay=key;aQ=a
  return aQ
 def a1(self,o,u):
  K=o['s'];F=K+aC;G=K+aB;l=o['u'];ad=o['head'];E=o['body'];L=len(E);J=o['foods'];r=o['enemies'];V=o['i'];nb=self.B if u else self.w;av=not u;P=[0]*l;aP=[0]*l
  if o['m']:
   for i in range(L):
    c=E[i];fa=L-i+1
    if fa>P[c]:
     P[c]=fa
    aP[c]=1
  t=None;aL=None;z=P
  if o['n']and r:
   t=[0]*l;aL=[0]*l;ae=False
   for eh,aG,N in r:
    le=len(aG)
    for j in range(le):
     c=aG[j];fa=le-j+1
     if fa>t[c]:
      t[c]=fa
    ae=ae or N
    for n in(self.w if N else self.B)[eh]:
     if n>=0:
      aL[n]=1
   z=[a if a>b else b for a,b in zip(P,t)];bx=self.w if ae else nb;D=[aE]*l;q=[]
   for eh,_,_ in r:
    D[eh]=0;q.append(eh)
   i=0;aW=time.perf_counter
   while i<len(q):
    c=q[i];i+=1;nd=D[c]+1
    if nd>ap:
     break
    if i&127==0 and aW()>G:
     break
    for n in bx[c]:
     if n>=0 and D[n]==aE and(z[n]<=nd):
      D[n]=nd;q.append(n)
  else:
   D=self.T
  fe=self.O(J,r,av);aA=set()
  if o['o']and o['z']<900:
   for eh,aG,N in r:
    aA.update(aG[:-1])
    for n in(self.w if N else self.B)[eh]:
     if n>=0:
      aA.add(n)
   for _ in'aaa':aA|={n for c in list(aA)for n in nb[c]if n>=0}
  ar=[]
  for a in o['e']:
   nc=nb[ad][aH[a]];c={'a':a,'f':nc,'j':False,'g':0,'k':0,'h':0,'d':0,'l':False,'c':0,'b':None}
   if nc<0:
    c['g']=2 if o['r']else 0
   else:
    at=0;fo=P[nc]
    if fo>1 and o['p']:
     at=2 if fo>2 else 1
    if t is not None and o['q']:
     fx=t[nc]
     if fx>1:
      at=max(at,2 if fx>2 else 1)
    c['g']=at;c['j']=z[nc]<=1;c['k']=1 if aL is not None and aL[nc]else 0
   ar.append(c)
  aR=[c for c in ar if c['j']];ev=[]
  for bA,c in enumerate(aR):
   bd=time.perf_counter()
   if bd>G and ev:
    break
   bq=len(aR)-bA;dl=bd+max(0.0005,(G-bd)/bq);ab,S,ac,a2=self.aw(c['f'],1,nb,z,aP,D,0,l,dl,True,False);c['h']=ac;c['l']=a2;c['t']=S;c['d']=ac
   if a2:
    au=3
   else:
    au=1 if ac>=L+1 else 0
    if D is not self.T:
     _,_,aS,bh=self.aw(c['f'],1,nb,z,aP,self.T,0,l,min(F,max(dl,time.perf_counter()+0.0005)),False,False);c['d']=aS
     if bh:
      au=2
     elif aS>=L+1:
      au=1
   c['c']=a8[au,c['k']];v=None
   for f in J:
    if f in aA:
     continue
    df=ab[f]
    if df>0:
     k=af(df,fe[f])
     if v is None or k<v[0]:
      v=(k,f)
   c['b']=v;ev.append(c)
  aT=[c for c in ev if c['c']==7 and c['b']is not None];aT.sort(key=lambda c:(c['b'][0],-min(c['h'],4*L),0 if c['a']==V else 1))
  for c in aT:
   if time.perf_counter()>F:
    break
   if self.aD(c,E,nb,t,D,l,F):
    return(c['a'],True)
  if ev and(max((c['c']for c in ev))<=3 and not o['q']or o['z']>1000):
   bD=max((c['c']for c in ev));aU=[c for c in ev if c['c']==bD and c['b']]
   if aU:
    v=min(aU,key=lambda c:(c['b'][0],-c['d']));return(v['a'],False)
  if ev:
   br=2*L+10;v=max(ev,key=lambda c:(c['c'],min(c['d'],br),c['h'],c['l'],-(c['b'][0]if c['b']is not None else aE),1 if c['a']==V else 0));return(v['a'],v['c']>=6)
  if ar:
   v=min(ar,key=lambda c:(c['g'],0 if c['a']==V else 1));return(v['a'],False)
  return(o['e'][0],False)
 def aD(self,c,E,nb,t,D,l,aa):
  f=c['b'][1];S=c['t'];nc=c['f'];aN=[];x=f;a3=0
  while x!=nc and x>=0 and(a3<=l):
   aN.append(x);x=S[x];a3+=1
  if x!=nc:
   return True
  aN.append(nc);k=len(aN);lv=len(E)+1;vb=(aN+E)[:lv]
  if t is not None:
   ak=[fa-k if fa>k else 0 for fa in t]
  else:
   ak=[0]*l
  be=[0]*l
  for i in range(1,len(vb)):
   cc=vb[i];fa=lv-i+1
   if fa>ak[cc]:
    ak[cc]=fa
   be[cc]=1
  ak[vb[0]]=0;_,_,ac,a2=self.aw(vb[0],0,nb,ak,be,D,k,l,aa,False,True);return a2
def create_agent():
 return aI()
