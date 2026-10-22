M=(0,-1),(0,1),(-1,0),(1,0)
N=dict(zip('UP DOWN LEFT RIGHT'.split(),M))
class A:
 def decide_action(s,p):
  b=p['block_size'];W=p['width'];H=p['height'];B=p['my_body'];h=B[0];v=p['my_score'];w=v<200;E=p['enemies'];F=p['foods'];t=B[-1]
  O=set(B)if v>98 else set()
  if v>298:
   for e in E:i,j=e['head'];O|=set(e['body'])|{(i+u*b,j+q*b)for u,q in M}
  def n(c,d):
   i=c[0]+d[0]*b;j=c[1]+d[1]*b
   return(i%W,j%H)if w else(i,j)if-1<i<W and-1<j<H else h
  def D(a,c):
   i=abs(a[0]-c[0]);j=abs(a[1]-c[1])
   return min(i,W-i)+min(j,H-j)if w else i+j
  def K(f):d=D(h,f);e=min([D(x['head'],f)for x in E]or[9e9]);return d+(e<d)*(8*b+2*(d-e))+(e==d)*2*b
  T=min(F,key=K)if F else t
  L=len(B)+8
  def f(c):
   if c in O:return 0
   S={c};Q=[c]
   for c in Q:
    for d in M:
     k=n(c,d)
     if k not in S and k not in O:
      S.add(k);Q.append(k)
      if len(S)>L:return L
   return len(S)
  u=p.get('turns_without_food',0)>800
  def g(a):
   c=n(h,N[a]);r=f(c)
   if u:return r>0,-D(c,T)
   return r,c in F,-D(c,T),-D(c,t)
  return max(p['valid_actions'],key=g)
def create_agent():return A()
