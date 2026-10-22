MODEL_NAME="AI_Competitor"
M=(0,-1),(0,1),(-1,0),(1,0)
N=dict(zip('UP DOWN LEFT RIGHT'.split(),M))
class A:
 def decide_action(s,p):
  b=p['block_size'];W=p['width'];H=p['height'];B=p['my_body'];h=B[0];v=p['my_score'];w=v<200;E=p['enemies'];F=p['foods'];t=B[-1];l=v>298;md=p['my_direction']
  Z={}
  def z(c,k):Z[c]=max(Z.get(c,0),k)
  if v>98:
   a=len(B)+(p.get('previous_reward',0)>9)
   for i,c in enumerate(B):z(c,a-i)
  if l:
   for e in E:
    o=e['body'];i,j=e['head']
    for k,c in enumerate(o):z(c,len(o)-k+1)
    for u,q in M:z((i+u*b,j+q*b),9e9)
  def n(c,d):
   i=c[0]+d[0]*b;j=c[1]+d[1]*b
   return(i%W,j%H)if w else(i,j)if-1<i<W and-1<j<H else h
  def D(a,c):
   i=abs(a[0]-c[0]);j=abs(a[1]-c[1])
   return min(i,W-i)+min(j,H-j)if w else i+j
  def K(f):d=D(h,f);e=min([D(x['head'],f)for x in E]or[9e9]);return d+(e<d)*(8*b+2*(d-e))+(e==d)*2*b+l*(e<4*b)*9e9
  u=p.get('turns_without_food',0)>600
  T=min(F,key=(lambda f:D(h,f))if u else K)if F else t
  L=len(B)+8
  def f(c):
   if Z.get(c,0)>1:return 0
   S={c:1};Q=[c]
   for c in Q:
    for d in M:
     k=n(c,d)
     if k not in S and Z.get(k,0)<=S[c]+1:
      S[k]=S[c]+1;Q.append(k)
      if len(S)>L:return L
   return len(S)
  def g(a):
   c=n(h,N[a]);r=f(c)
   if r==0:return-9e9,
   if u:return r>0,-D(c,T)
   return r,c in F,-D(c,T),-D(c,t),a!=md
  return max(p['valid_actions'],key=g)
def create_agent():return A()
