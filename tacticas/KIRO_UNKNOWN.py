from time import perf_counter as T
MODEL_NAME="KIRO_UNKNOWN"
M=(0,-1),(0,1),(-1,0),(1,0)
N=dict(zip('UP DOWN LEFT RIGHT'.split(),M))
class A:
 def decide_action(s,p):
  t0=T();b=p['block_size'];W=p['width'];H=p['height']
  B=p['my_body'];h=B[0];v=p['my_score'];E=p['enemies']
  F=p['foods'];tl=B[-1];md=p['my_direction'];L=len(B)
  w=v<200;el=v>=298;ate=p.get('previous_reward',0)>9
  # time-aware obstacle map: Z[c]=turns until free
  Z={}
  def zz(c,k):Z[c]=max(Z.get(c,0),k)
  if v>=98:
   a=L+(1 if ate else 0)
   for i,c in enumerate(B):zz(c,a-i)
  if el:
   for e in E:
    o=e['body']
    for k,c in enumerate(o):zz(c,len(o)-k+1)
    i,j=e['head']
    for u,q in M:zz((i+u*b,j+q*b),99999)
  def nxt(c,d):
   i=c[0]+d[0]*b;j=c[1]+d[1]*b
   return(i%W,j%H)if w else((i,j)if 0<=i<W and 0<=j<H else None)
  def dist(a,c):
   i=abs(a[0]-c[0]);j=abs(a[1]-c[1])
   return(min(i,W-i)+min(j,H-j))if w else i+j
  def food_cost(f):
   d=dist(h,f);e=min([dist(x['head'],f)for x in E]or[99999])
   return d+(e<d)*(8*b+2*(d-e))+(e==d)*2*b+(el and e<4*b)*99999
  hz=p.get('turns_without_food',0)>700
  tgt=(min(F,key=lambda f:dist(h,f))if hz else min(F,key=food_cost))if F else tl
  Cap=L+12
  def flood(c,step):
   # step=1 if eating (tail stays), 0 otherwise
   if not c or Z.get(c,0)>1:return 0
   S={c:1};Q=[c]
   for c in Q:
    for d in M:
     k=nxt(c,d)
     if k and k not in S:
      need=Z.get(k,0)
      # tail cell: free after L turns if not eating, unavailable if eating
      if k==tl and not step:need=0
      if need<=S[c]+1:
       S[k]=S[c]+1;Q.append(k)
       if len(S)>=Cap:return Cap
       if T()-t0>0.011:return len(S)
   return len(S)
  def score(a):
   c=nxt(h,N[a])
   if not c:return(-9,)
   eat=c in F
   sp=flood(c,1 if eat else 0)
   if sp==0:return(-9,)
   ok=sp>=Cap//2
   if hz:return(sp>2,-dist(c,tgt))
   return(ok,eat,-dist(c,tgt),sp,-dist(c,tl),a==md)
  return max(p['valid_actions'],key=score)
create_agent=A
