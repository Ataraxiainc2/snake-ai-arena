from time import perf_counter as T
MODEL_NAME="AI_Competitor"
V={"UP":0,"DOWN":1,"LEFT":2,"RIGHT":3};INF=10**9;C={}
def _g(W,H,t):
 k=(W,H,bool(t))
 if k not in C:C[k]=[((y-1)%H*W+x,(y+1)%H*W+x,y*W+(x-1)%W,y*W+(x+1)%W)if t else(i-W if y else-1,i+W if y<H-1 else-1,i-1 if x else-1,i+1 if x<W-1 else-1)for y in range(H)for x in range(W)for i in(y*W+x,)]
 return C[k]
def _m(a,b,W,H,t):
 u,v=abs(a%W-b%W),abs(a//W-b//W);return(min(u,W-u)+min(v,H-v))if t else u+v
def _f(st,t0,zo,ze,ed,nb,dl,K,wp=0,sh=0):
 N=len(nb);ds=[-1]*N;pr=[-1]*N if wp else 0;ds[st]=t0;q=[st];i=ar=es=0;ar=1
 while i<len(q)and ar<K:
  c=q[i];i+=1;nd=ds[c]+1
  for n in nb[c]:
   if n>=0 and ds[n]<0 and zo[n]+sh<=nd and(not ze or ze[n]<=nd)and(not ed or nd<ed[n]):
    ds[n]=nd;q.append(n)
    if wp:pr[n]=c
    if zo[n]:es=1
    else:ar+=1
    if ar>=K:break
  if(i&63)==0 and T()>dl:break
 return ds,pr,ar,es
def _ok(f,ni,ds,pr,b,L,ze,nb,hd,K):
 if ds[f]<=0:return 1
 pt=[];x=f
 while x!=ni and x>=0 and len(pt)<=len(nb):pt.append(x);x=pr[x]
 if x!=ni:return 1
 pt.append(ni);lv=L+1;vb=(pt+b)[:lv];vz=[0]*len(nb)
 for i,c in enumerate(vb[1:],1):vz[c]=max(vz[c],lv-i)
 vz[vb[0]]=0;_,_,ar,es=_f(vb[0],0,vz,ze,0,nb,hd,K)
 return es or ar>=K
def _d(p):
 t0=T();hd,sd=t0+.008,t0+.005;G=p.get;bs=G("block_size")or 20
 W,H=G("width",1000)//bs,G("height",700)//bs;N=W*H
 va=[a for a in(G("valid_actions")or V)if a in V]
 if len(va)<=1:return va[0]if va else"UP"
 P=lambda g:int(g[1])//bs%H*W+int(g[0])//bs%W
 hi=P(G("my_head")or(0,0));sc=float(G("my_score")or 0);md=G("my_direction")
 R=(G("active_rules")or{}).get
 wl=bool(R("wall_collision",sc>=200));sl=bool(R("self_collision"))
 el=bool(R("enemy_collision",sc>=300))or sc>=296
 tr=bool(R("toroidal_world",not wl))and not wl
 al=sl or bool(R("self_collision_warning"))or sc>=98
 pw=wl or not tr or sc>=199
 b=[P(g)for g in G("my_body")or()if g]
 if not b or b[0]!=hi:b=[hi]+b
 L=len(b);K=max(520,L*2+16)
 fs={P(f)for f in G("foods")or()if f}
 hz=float(G("turns_without_food")or 0);hf=hz>(90 if sc<100 else 350)
 ens=[];bad=set()
 for e in G("enemies")or():
  eb=[P(g)for g in e.get("body")or[e.get("head")]if g]
  if eb:
   ens.append((eb[0],eb,float(e.get("score")or 0)<200,V.get(e.get("direction"),-1)))
   if sc>=290:bad.update(eb if el else eb[:-1])
 def sf(d,de):return d+((14+3*(d-de)if el else 7+(d-de))if de<d else(5 if el else 2)if de==d else 0)
 for ut in dict.fromkeys((not pw,tr)):
  nb=_g(W,H,ut)
  fe={f:min((_m(h,f,W,H,ut and w)for h,_,w,_ in ens),default=INF)for f in fs}
  if not(al or el or pw):
   return min(va,key=lambda a:(min((sf(_m(nb[hi][V[a]],f,W,H,1)+1,fe[f])for f in fs),default=INF),a!=md))if fs else(md if md in va else va[0])
  zo=[0]*N;ze=[0]*N if(el and ens)else 0;rk=bytearray(N);ed=0
  if al:
   for i,c in enumerate(b):zo[c]=max(zo[c],L-i)
  if ze:
   aw=0;ed=[INF]*N;q=[]
   for eh,eb,ew,dr in ens:
    le=len(eb);aw|=ew;en=_g(W,H,ew)[eh];me=any(n in fs for n in en if n>=0)
    for j,c in enumerate(eb):ze[c]=max(ze[c],le-j+1+me)
    for i,n in enumerate(en):
     if n>=0:rk[n]=min(9,rk[n]+(2 if i==dr else 1))
    ed[eh]=0;q.append(eh)
   for qi,c in enumerate(q):
    nd=ed[c]+1
    if nd>8 or((qi&31)==0 and T()>sd):break
    for n in _g(W,H,aw or ut)[c]:
     if n>=0 and ed[n]==INF and zo[n]<=nd and ze[n]<=nd:ed[n]=nd;q.append(n)
  cs=[];opn=[]
  for a in va:
   ni=nb[hi][V[a]]
   if ni<0:cs.append((2,a));continue
   sh=ni in fs;fo=zo[ni]+(sh if zo[ni]else 0);fx=ze[ni]if ze else 0
   if ni in bad:fx=max(fx,3)
   dg=max(2 if fo>2 else 1 if fo>1 and sl else 0,2 if fx>2 else 1 if fx>1 and el else 0)
   cs.append((dg,a))
   if fo<=1 and fx<=1:opn.append((rk[ni]if el else 0,a,ni,sh))
  opn.sort();ev=[]
  for idx,(r,a,ni,sh)in enumerate(opn):
   if T()>sd and ev:break
   ds,pr,ar,es=_f(ni,1,zo,ze,ed,nb,min(hd,T()+.0008),K,1,sh)
   fd=[]
   for f in fs:
    if(el and rk[f])or(sc>=290 and f in bad):continue
    d=ds[f]
    if d>0:fd.append((sf(d,fe[f]),f))
   fd.sort();lv=2 if es or ar>=K else(1 if ar>=L+4 else 0)
   ev.append((lv,ar,-r,fd,a,ni,ds,pr))
  for c in sorted((x for x in ev if x[0]and x[3]),key=lambda c:(c[3][0][0],-c[1],c[4]!=md)):
   for _,f in c[3][:2]:
    if T()<=hd and _ok(f,c[5],c[6],c[7],b,L,ze,nb,hd,K):return c[4]
  if ev:
   b0=max(ev,key=lambda c:(c[0],min(c[1],2*L+12),c[2],-(c[3][0][0]if c[3]else(_m(c[5],b[-1],W,H,ut)if hf else INF)),c[4]==md))
   if b0[0] or ut==tr:return b0[4]
 if cs:return min(cs,key=lambda c:(c[0],c[1]!=md))[1]
 return va[0]
class A:
 def decide_action(s,p):
  try:a=_d(p)
  except Exception:a=0
  v=p.get("valid_actions")if isinstance(p,dict)else 0
  return a if a in(v or V)else(v[0]if v else"UP")
create_agent=A
