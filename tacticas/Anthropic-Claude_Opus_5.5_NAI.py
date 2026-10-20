MODEL_NAME="AI_Competitor"
M=(0,-1),(0,1),(-1,0),(1,0)
N=dict(zip('UP DOWN LEFT RIGHT'.split(),M))
class A:
 def decide_action(s,p):
  b=p['block_size'];W=p['width'];H=p['height'];B=p['my_body'];h=B[0];v=p['my_score'];w=v<200;E=p['enemies'];F=p['foods']
  O=set(B[:-1])if v>98 else set()
  if v>298:
   for e in E:
    O|=set(e['body'][:-1]);i,j=e['head'];O|={(i+u*b,j+t*b)for u,t in M}
  def n(c,d):
   i=c[0]+d[0]*b;j=c[1]+d[1]*b
   return(i%W,j%H)if w else(i,j)if-1<i<W and-1<j<H else h
  def D(a,c):
   i=abs(a[0]-c[0]);j=abs(a[1]-c[1])
   return min(i,W-i)+min(j,H-j)if w else i+j
  def K(f):d=D(h,f);e=min([D(x['head'],f)for x in E]or[9e9]);return d+(e<d)*(8*b+2*(d-e))+(e==d)*2*b
  T=min(F,key=K)if F else h
  L=len(B)+3
  def f(c):
   if c in O:return 0
   S={c};Q=[c]
   for c in Q:
    for d in M:
     k=n(c,d)
     if k not in S and k not in O:
      S.add(k);Q.append(k)
      if len(S)>L or k==B[-1]:return L
   return len(S)
  return max(p['valid_actions'],key=lambda a:(f(c:=n(h,N[a])),c in F,-D(c,T)))
def create_agent():return A()
