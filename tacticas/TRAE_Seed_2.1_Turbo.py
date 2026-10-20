from time import perf_counter as T
MODEL_NAME="AI_Competitor"
V={"UP":0,"DOWN":1,"LEFT":2,"RIGHT":3};B=10**9;C={}
def _g(W,H,t):
 if(k:=(W,H,bool(t)))not in C:C[k]=[((y-1)%H*W+x,(y+1)%H*W+x,y*W+(x-1)%W,y*W+(x+1)%W)if t else(i-W if y else-1,i+W if y<H-1 else-1,i-1 if x else-1,i+1 if x<W-1 else-1)for y in range(H)for x in range(W)for i in(y*W+x,)]
 return C[k]
def _m(a,b,W,H,t):u,v=abs(a%W-b%W),abs(a//W-b//W);return(min(u,W-u)+min(v,H-v))if t else u+v
def _f(st,t0,zo,ze,ed,nb,dl,K,wp=0,sh=0):
 N=len(nb);ds=[-1]*N;pr=[-1]*N if wp else 0;ds[st]=t0;q=[st];qi=0;ar=1
 while qi<len(q)and ar<K:
  c=q[qi];qi+=1;nd=ds[c]+1
  for n in nb[c]:
   if n>=0 and ds[n]<0 and zo[n]+sh<=nd and(not ze or ze[n]<=nd)and(not ed or nd<ed[n]):
    ds[n]=nd;q.append(n)
    if wp:pr[n]=c
    ar+=1
    if ar>=K:break
  if(qi&31)==0 and T()>dl:break
 return ds,pr,ar
def _vo(f,ni,ds,pr,b,L,ze,nb,hd,K):
 if ds[f]<=0:return 1
 pt=[];x=f
 while x!=ni and x>=0 and len(pt)<=len(nb):pt.append(x);x=pr[x]
 if x!=ni:return 1
 pt.append(ni);lv=L+1;vb=(pt+b)[:lv];vz=[0]*len(nb)
 for i,c in enumerate(vb[1:],1):vz[c]=max(vz[c],lv-i)
 vz[vb[0]]=0;_,_,ar=_f(vb[0],0,vz,ze,0,nb,hd,K)
 return ar>=K
def _d(p):
 t0=T();hd=t0+.008;sd=t0+.005
 G=p.get;bs=G("block_size")or 20
 W,H=G("width",1000)//bs,G("height",700)//bs;N=W*H
 va=[a for a in(G("valid_actions")or V)if a in V]
 if len(va)<=1:return va[0]if va else"UP"
 P=lambda g:int(g[1])//bs%H*W+int(g[0])//bs%W
 hi=P(G("my_head")or(0,0));sc=float(G("my_score")or 0);md=G("my_direction")
 R=(G("active_rules")or{}).get
 wl=bool(R("wall_collision",sc>=200));sl=bool(R("self_collision"))
 el=bool(R("enemy_collision",sc>=300))or sc>=298
 tr=bool(R("toroidal_world",not wl))and not wl
 al=sl or bool(R("self_collision_warning"))or sc>=98
 pw=wl or not tr or sc>=198.5
 b=[P(g)for g in G("my_body")or()if g]
 if not b or b[0]!=hi:b=[hi]+b
 L=len(b);K=max(600,L*2)
 fs={P(f)for f in G("foods")or()if f}
 hf=float(G("turns_without_food")or 0)>(100 if sc<100 else 250)
 ens=[(P(e["head"]),[P(g)for g in e.get("body")or[e.get("head")]if g],float(e.get("score")or 0)<200)for e in G("enemies")or()]
 sf=lambda d,de:d+((12+2*(d-de)if el else 6+(d-de))if de<d else(4 if el else 2)if de==d else 0)
 for ut in dict.fromkeys((not pw,tr)):
  nb=_g(W,H,ut)
  fe={f:min((_m(h,f,W,H,ut and w)for h,_,w in ens),default=B)for f in fs}
  if not(al or el or wl):
   return min(va,key=lambda a:(min((sf(_m(nb[hi][V[a]],f,W,H,1)+1,fe[f])for f in fs),default=B),a!=md))
  zo=[0]*N;ze=[0]*N if(el and ens)else 0;ed=0
  if al:
   for i,c in enumerate(b):zo[c]=max(zo[c],L-i)
  if ze:
   ed=[B]*N;q=[]
   for eh,eb,ew in ens:
    le=len(eb)
    for j,c in enumerate(eb):ze[c]=max(ze[c],le-j+1)
    ed[eh]=0;q.append(eh)
   for qi,c in enumerate(q):
    nd=ed[c]+1
    if nd>8 or((qi&31)==0 and T()>sd):break
    for n in _g(W,H,ew or ut)[c]:
     if n>=0 and ed[n]==B and zo[n]<=nd and ze[n]<=nd:ed[n]=nd;q.append(n)
  cs=[];opn=[]
  for a in va:
   ni=nb[hi][V[a]]
   if ni<0:cs.append((2,a));continue
   sh=ni in fs;fo=zo[ni]+(sh if zo[ni]else 0);fx=ze[ni]if ze else 0
   cs.append((max(2 if fo>2 else 1 if fo>1 and sl else 0,2 if fx>2 else 1 if fx>1 and el else 0),a))
   if fo<=1 and fx<=1:opn.append((a,ni,sh))
  ev=[]
  for idx,(a,ni,sh)in enumerate(opn):
   if T()>sd and ev:break
   dl=min(hd,T()+max(.0004,(sd-T())/(len(opn)-idx)))if len(opn)>idx+1 else hd
   ds,pr,ar=_f(ni,1,zo,ze,ed,nb,dl,K,1,sh)
   tf=sorted((sf(d,fe[f]),f)for f in fs if 0<(d:=ds[f]if ds[f]>0 else _m(ni,f,W,H,ut)+1)<B)[:3]
   lv=3 if ar>=K else(1 if ar>=L+1 else 0)
   ev.append((lv,ar,tf,a,ni,ds,pr))
  for tg in(3,2,1,0):
   for c in sorted((c for c in ev if c[0]==tg and c[2]),key=lambda c:(c[2][0][0],-c[1],c[3]!=md)):
    for _,f in c[2][:2]:
     if T()<=hd and _vo(f,c[4],c[5],c[6],b,L,ze,nb,hd,K):return c[3]
  if ev:
   b0=max(ev,key=lambda c:(c[0],c[1],-(c[2][0][0]if hf and c[2]else B),c[3]==md))
   if b0[0]>=1 or ut==tr:return b0[3]
 if cs:return min(cs,key=lambda c:(c[0],c[1]!=md))[1]
 return va[0]
class A:
 def decide_action(s,p):
  try:a=_d(p)
  except:a=0
  v=p.get("valid_actions")if isinstance(p,dict)else 0
  return a if a in(v or V)else(v[0]if v else"UP")
create_agent=A
