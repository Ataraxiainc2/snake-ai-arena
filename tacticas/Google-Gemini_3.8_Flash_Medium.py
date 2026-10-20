from time import perf_counter as T
MODEL_NAME="Google-Gemini_3.8_Flash_Medium"
V={"UP":0,"DOWN":1,"LEFT":2,"RIGHT":3};B=10**9;C={}
def _g(W,H,t):
 if(k:=(W,H,bool(t)))not in C:C[k]=[((y-1)%H*W+x,(y+1)%H*W+x,y*W+(x-1)%W,y*W+(x+1)%W)if t else(i-W if y else-1,i+W if y<H-1 else-1,i-1 if x else-1,i+1 if x<W-1 else-1)for y in range(H)for x in range(W)for i in(y*W+x,)]
 return C[k]
def _m(a,b,W,H,t):u,v=abs(a%W-b%W),abs(a//W-b//W);return(min(u,W-u)+min(v,H-v))if t else u+v
def _f(st,t0,zo,ze,ed,nb,dl,wp,se,K,sh=0):
 N=len(nb);ds=[-1]*N;pr=[-1]*N if wp else 0;ds[st]=t0;q=[st];qi=es=0;ar=1
 while qi<len(q)and ar<K:
  c=q[qi];qi+=1;nd=ds[c]+1
  for n in nb[c]:
   if n>=0 and ds[n]<0 and zo[n]+sh<=nd and(not ze or ze[n]<=nd)and(not ed or nd<ed[n]):
    ds[n]=nd;q.append(n)
    if wp:pr[n]=c
    if zo[n]:es=1
    else:ar+=1
    if ar>=K:break
  if(se and es)or((qi&31)==0 and T()>dl):break
 return ds,pr,ar,es,ds[q[-1]]
def _d(p):
 t0=T();hd,sd=t0+.0065,t0+.0038;G=p.get;bs=G("block_size")or 20
 W,H=G("width",1000)//bs,G("height",700)//bs;N=W*H;va=[a for a in(G("valid_actions")or V)if a in V]
 if len(va)<=1:return va[0]if va else"UP"
 P=lambda g:int(g[1])//bs%H*W+int(g[0])//bs%W;hi,sc,md=P(G("my_head")or(0,0)),float(G("my_score")or 0),G("my_direction");R=(G("active_rules")or{}).get
 wl=bool(R("wall_collision",sc>=200));sl=bool(R("self_collision"));el=bool(R("enemy_collision",sc>=300))or sc>=298;tr=bool(R("toroidal_world",not wl))and not wl
 al=sl or bool(R("self_collision_warning"))or sc>=98;pw=wl or not tr or sc>=198.5
 b=[P(g)for g in G("my_body")or()if g]
 if not b or b[0]!=hi:b=[hi]+b
 L=len(b);K=max(700,L*2);fs={P(f)for f in G("foods")or()if f}
 hf=float(G("turns_without_food")or 0)>(120 if sc<100 else 300)
 ens=[(eb[0],eb,float(e.get("score")or 0)<200,V.get(e.get("direction"),-1))for e in G("enemies")or()if(eb:=[P(g)for g in e.get("body")or[e.get("head")]if g])]
 sf=lambda d,de:d+((16+3*(d-de)if el else 8+2*(d-de))if de<d else(6 if el else 2)if de==d else 0)
 def vo(f,ni,ds,pr,vf):
  if ds[f]<=0:return 1
  pt,x=[],f
  while x!=ni and x>=0 and len(pt)<=N:pt.append(x);x=pr[x]
  if x!=ni:return 1
  pt.append(ni);k,lv=len(pt),L+1;vb=(pt+b)[:lv];vz=[0]*N
  for i,c in enumerate(vb[1:],1):vz[c]=max(vz[c],lv-i)
  vz[vb[0]]=0;_,_,ar,ve,_=_f(vb[0],0,vz,[x-k for x in ze]if ze else 0,[x-k for x in vf]if vf else 0,nb,hd,0,1,K)
  return ve or ar>=K
 for ut in dict.fromkeys((not pw,tr)):
  nb=_g(W,H,ut);fe={f:min((_m(h,f,W,H,ut and w)for h,_,w,_ in ens),default=B)for f in fs}
  if not(al or el or pw):
   if(W,H,0)not in C and T()-t0<.002:_g(W,H,0)
   return min(va,key=lambda a:(min((sf(d,fe[f])-(.3 if d==1 else 0)for f in fs for d in(_m(nb[hi][V[a]],f,W,H,1)+1,)),default=B),a!=md))
  zo=[0]*N;ze=[0]*N if(el and ens)else 0;rk,ed=bytearray(N),0
  if al:
   for i,c in enumerate(b):zo[c]=max(zo[c],L-i)
  if ze:
   aw,ed,q=0,[B]*N,[]
   for eh,eb,ew,dr in ens:
    le=len(eb);aw|=ew;en=_g(W,H,ew)[eh];me=any(n in fs for n in en if n>=0)
    for j,c in enumerate(eb):ze[c]=max(ze[c],le-j+1+me)
    for i,n in enumerate(en):
     if n>=0:rk[n]=min(9,rk[n]+(2 if i==dr else 1))
    ed[eh]=0;q.append(eh)
   for qi,c in enumerate(q):
    if(nd:=ed[c]+1)>10 or((qi&31)==0 and T()>sd):break
    for n in _g(W,H,aw or ut)[c]:
     if n>=0 and ed[n]==B and zo[n]<=nd and ze[n]<=nd:ed[n]=nd;q.append(n)
  cs,opn=[],[]
  for a in va:
   if(ni:=nb[hi][V[a]])<0:cs.append((2 if wl else 0,a));continue
   sh=ni in fs;fo=zo[ni]+(sh if zo[ni]else 0);fx=ze[ni]if ze else 0
   cs.append((max(2 if fo>2 else 1 if fo>1 and sl else 0,2 if fx>2 else 1 if fx>1 and el else 0),a))
   if fo<=1 and fx<=1:opn.append((rk[ni]if el else 0,a,ni,sh))
  opn.sort();ev=[]
  F=lambda ni,ds,ar,mx,lm:sorted((sf(d,fe[f]),f)for f in fs if not(el and rk[f])and 0<(d:=ds[f]if ds[f]>0 else(max(m,mx)+4 if ar>=K and(m:=_m(ni,f,W,H,ut)+1)>=mx-4 else 0))<(ed[f]if lm else B))
  for idx,(r,a,ni,sh)in enumerate(opn):
   if(nw:=T())>sd and ev:break
   dl=nw+max(.0004,(sd-nw)/(len(opn)-idx));ds,pr,ar,es,mx=_f(ni,1,zo,ze,ed,nb,dl,1,0,K,sh);arc,tf=ar,F(ni,ds,ar,mx,0)[:3]
   if es or ar>=K:lv=3
   else:
    lv=1 if ar>=L+1 else 0
    if ze:
     dsb,prb,arb,esb,mxb=_f(ni,1,zo,ze,0,nb,min(hd,max(dl,T()+.0004)),1,0,K,sh);arc=arb
     if esb or arb>=K:lv=2
     elif arb>=L+1:lv=1
     if not tf:tf=F(ni,dsb,arb,mxb,1)[:2];ds,pr=dsb,prb
   ev.append(((lv+4 if lv else(4 if arc>=8 else 1))if not r else(3 if lv>1 else 2 if lv else 0),-r,ar,arc,es,tf,a,ni,ds,pr))
  for tg,ix,vf in((7,2,ed),(7,2,0),(6,3,0),(5,3,0)):
   for c in sorted((c for c in ev if c[0]==tg and c[5]),key=lambda c:(c[5][0][0],-min(c[ix],4*L),c[6]!=md)):
    for _,f in c[5][:3 if tg>6 else 2]:
     if T()<=hd and vo(f,c[7],c[8],c[9],vf):return c[6]
  if ev:
   b0=max(ev,key=lambda c:(c[0],c[1],min(c[3],2*L+10),c[2],c[4],-(c[5][0][0]if hf and c[5]else _m(c[7],b[-1],W,H,ut)),c[6]==md))
   if b0[0]>=5 or ut==tr:return b0[6]
 if cs:return min(cs,key=lambda c:(c[0],c[1]!=md))[1]
 return va[0]
class SnakeAgent:
 def decide_action(s,p):
  try:a=_d(p)
  except Exception:a=0
  v=p.get("valid_actions")if isinstance(p,dict)else 0
  return a if a in(v or V)else(v[0]if v else"UP")
create_agent=SnakeAgent
