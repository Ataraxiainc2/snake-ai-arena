MODEL_NAME="AI_Competitor"
M=((0,-1),(0,1),(-1,0),(1,0));N=dict(zip("UP DOWN LEFT RIGHT".split(),M))
class A:
 def decide_action(s,p):
  b=p["block_size"];W=p["width"];H=p["height"];B=p["my_body"];h=B[0];v=p["my_score"];w=v<200;E=p["enemies"];F=p["foods"];tl=B[-1];md=p["my_direction"]
  O=set(B if p.get("previous_reward")else B[:-1])if v>98 else set()
  if v>298:
   for e in E:
    O|=set(e["body"][:-1]);i,j=e["head"];O|={(i+x*b,j+y*b)for x,y in M}
  def n(c,d):
   i=c[0]+d[0]*b;j=c[1]+d[1]*b
   return(i%W,j%H)if w else(i,j)if 0<=i<W and 0<=j<H else h
  def D(a,c):
   i=abs(a[0]-c[0]);j=abs(a[1]-c[1])
   return min(i,W-i)+min(j,H-j)if w else i+j
  def K(f):
   d=D(h,f);e=min([D(x["head"],f)for x in E]or[10**9])
   return d+(e<d)*(8*b+2*(d-e))+(e==d)*2*b
  u=p.get("turns_without_food",0)>800;T=min(F,key=(lambda f:D(h,f))if u else K)if F else tl;L=len(B)+8
  def f(c):
   if c in O:return 0
   S={c};Q=[c]
   for x in Q:
    for d in M:
     k=n(x,d)
     if k not in S and k not in O:
      S.add(k);Q.append(k)
      if len(S)>L or k==tl:return L
   return len(S)
  def g(a):
   c=n(h,N[a]);z=f(c)
   return(z>0,-D(c,T),z,a==md)if u else(z,c in F,-D(c,T),-D(c,tl),a==md)
  return max(p["valid_actions"],key=g)
def create_agent():return A()
