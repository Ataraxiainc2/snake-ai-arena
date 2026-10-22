MODEL_NAME="AI_Competitor"
M=(0,-1),(0,1),(-1,0),(1,0)
N=dict(zip('UP DOWN LEFT RIGHT'.split(),M))
class A:
 def decide_action(s,p):
  b=p['block_size'];W=p['width'];H=p['height'];B=p['my_body'];h=B[0];v=p['my_score'];E=p['enemies'];F=p['foods'];tl=B[-1];md=p['my_direction']
  w=v<200;el=v>298;ate=p.get('previous_reward',0)>9;Z={}
  def z(c,k):Z[c]=max(Z.get(c,0),k)
  if v>98:
   a=len(B)+ate
   for i,c in enumerate(B):z(c,a-i)
  if el:
   for e in E:
    o=e['body']
    for k,c in enumerate(o):z(c,len(o)-k+1)
    i,j=e['head']
    for u,q in M:z((i+u*b,j+q*b),9e9)
  def n(c,d):
   i=c[0]+d[0]*b;j=c[1]+d[1]*b
   if w:return i%W,j%H
   if 0<=i<W and 0<=j<H:return i,j
  def D(p,q):
   i=abs(p[0]-q[0]);j=abs(p[1]-q[1]);return min(i,W-i)+min(j,H-j)if w else i+j
  def K(f):
   d=D(h,f);e=min([D(x['head'],f)for x in E]or[9e9]);return d+(e<d)*(8*b+2*(d-e))+(e==d)*2*b+el*(e<4*b)*9e9
  u=p.get('turns_without_food',0)>800
  T=min(F,key=(lambda f:D(h,f))if u else K)if F else tl
  L=len(B)+8
  def f(c,eat):
   if not c or Z.get(c,0)>1:return 0,0
   S={c:1};Q=[c];rt=c==tl
   for c in Q:
    for d in M:
     k=n(c,d)
     if k and k not in S and Z.get(k,0)+eat<=S[c]+1:
      S[k]=S[c]+1;Q.append(k);rt|=k==tl
      if len(S)>L:return 1,L
   return rt,len(S)
  def g(a):
   c=n(h,N[a])
   if not c:return-1,0
   rt,sp=f(c,c in F)
   if u:return(rt or sp>2),-D(c,T)
   return rt,sp,c in F,-D(c,T),-D(c,tl),a==md
  return max(p['valid_actions'],key=g)
def create_agent():return A()
