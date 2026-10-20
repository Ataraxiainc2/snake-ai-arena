from time import perf_counter as T
MODEL_NAME="KIRO_UNKNOWN"
V={"UP":0,"DOWN":1,"LEFT":2,"RIGHT":3};_C={}
def _nb(W,H,t):
 k=(W,H,t)
 if k not in _C:_C[k]=[((y-1)%H*W+x,(y+1)%H*W+x,y*W+(x-1)%W,y*W+(x+1)%W)if t else(i-W if y else-1,i+W if y<H-1 else-1,i-1 if x else-1,i+1 if x<W-1 else-1)for y in range(H)for x in range(W)for i in(y*W+x,)]
 return _C[k]
def _mn(a,b,W,H,t):u=abs(a%W-b%W);v=abs(a//W-b//W);return(min(u,W-u)+min(v,H-v))if t else u+v
def _bfs(st,t0,zo,ze,nb,dl,K):
 N=len(nb);ds=[-1]*N;ds[st]=t0;q=[st];qi=0;ar=1
 while qi<len(q)and ar<K:
  c=q[qi];qi+=1;nd=ds[c]+1
  for n in nb[c]:
   if n>=0 and ds[n]<0 and zo[n]<=nd and(not ze or ze[n]<=nd):
    ds[n]=nd;q.append(n);ar+=1
  if ar>=K:break
  if(qi&63)==0 and T()>dl:break
 return ds,ar
def _decide(p):
 t0=T();dl=t0+.008;G=p.get;bs=G("block_size")or 20
 W=G("width",1000)//bs;H=G("height",700)//bs;N=W*H
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
 hz=float(G("turns_without_food")or 0);hungry=hz>900
 ens=[(eb[0],eb,float(e.get("score")or 0)<200,V.get(e.get("direction"),-1))for e in G("enemies")or()if(eb:=[P(g)for g in e.get("body")or[e.get("head")]if g])]
 for ut in dict.fromkeys((not pw,tr)):
  nb=_nb(W,H,ut)
  B=10**9
  fe={f:min((_mn(h,f,W,H,ut and w)for h,_,w,_ in ens),default=B)for f in fs}
  if not(al or el or pw):
   return min(va,key=lambda a:(min((d+((8+2*(d-fe[f]))if fe[f]<d else 2 if fe[f]==d else 0)for f in fs for d in(_mn(nb[hi][V[a]],f,W,H,1)+1,)),default=B),a!=md))
  zo=[0]*N;ze=[0]*N if(el and ens)else 0;rk=bytearray(N)
  if al:
   for i,c in enumerate(b):zo[c]=max(zo[c],L-i)
  if ze:
   ed=[B]*N;q2=[]
   for eh,eb,ew,dr in ens:
    le=len(eb)
    for j,c in enumerate(eb):ze[c]=max(ze[c],le-j+1)
    for i,n in enumerate(_nb(W,H,ew)[eh]):
     if n>=0:rk[n]=min(9,rk[n]+(2 if i==dr else 1))
    ed[eh]=0;q2.append(eh)
   qi2=0
   while qi2<len(q2):
    c=q2[qi2];qi2+=1;nd=ed[c]+1
    if nd>10:break
    for n in _nb(W,H,ut)[c]:
     if n>=0 and ed[n]==B and zo[n]<=nd and ze[n]<=nd:ed[n]=nd;q2.append(n)
  cs=[];opn=[]
  for a in va:
   ni=nb[hi][V[a]]
   if ni<0:cs.append((2 if wl else 0,a));continue
   sh=ni in fs;fo=zo[ni]+(sh if zo[ni]else 0);fx=ze[ni]if ze else 0
   danger=max(2 if fo>2 else 1 if fo>1 and sl else 0,2 if fx>2 else 1 if fx>1 and el else 0)
   cs.append((danger,a))
   if fo<=1 and fx<=1:opn.append((rk[ni]if el else 0,a,ni,sh))
  opn.sort();best=None;bkey=None
  for r,a,ni,sh in opn:
   if T()>dl and best:break
   zo2=list(zo);zo2[ni]=max(zo2[ni],sh)
   ds,ar=_bfs(ni,1,zo2,ze if ze else 0,nb,dl,K)
   f_reach=[(ds[f],f)for f in fs if ds[f]>0 and not(el and rk[f])]
   f_reach.sort()
   near=f_reach[0][0]if f_reach else B
   fd=fe.get(f_reach[0][1],B)if f_reach else B
   score_near=near+((8+2*(near-fd))if fd<near else 2 if fd==near else 0)
   safe=1 if ar>=L+1 else 0
   space=min(ar,2*L+10)
   key=(-safe,-space,score_near if not hungry else near,r,a!=md)
   if bkey is None or key<bkey:bkey=key;best=a
  if best:
   if ut==tr:return best
   if best in[x[1]for x in cs]and cs[[x[1]for x in cs].index(best)][0]==0:return best
 if cs:return min(cs,key=lambda c:(c[0],c[1]!=md))[1]
 return va[0]
class SnakeAgent:
 def decide_action(s,p):
  try:a=_decide(p)
  except Exception:a=None
  v=p.get("valid_actions")if isinstance(p,dict)else None
  return a if a in(v or V)else(v[0]if v else"UP")
create_agent=SnakeAgent
