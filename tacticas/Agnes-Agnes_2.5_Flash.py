MODEL_NAME="Agnes-Agnes_2.5_Flash"
M=(0,-1),(0,1),(-1,0),(1,0)
N=dict(zip('UP DOWN LEFT RIGHT'.split(),M))
class A:
 def decide_action(s,p):
  b=p['block_size'];W=p['width'];H=p['height'];B=p['my_body'];h=B[0];v=p['my_score']
  w=v<200;E=p['enemies'];F=p['foods'];T=B[-1]
  P=O=set(B[:-1])if v>98 else set()
  if v>298:
   for e in E:i,j=e['head'];O=O|set(e['body'][:-1])|{(i+u*b,j+t*b)for u,t in M}
  def n(c,d):
   i=c[0]+d[0]*b;j=c[1]+d[1]*b
   return(i%W,j%H)if w else(i,j)if-1<i<W and-1<j<H else h
  def D(a,c):
   i=abs(a[0]-c[0]);j=abs(a[1]-c[1])
   return min(i,W-i)+min(j,H-j)if w else i+j
  def K(f):
   d=D(h,f);e=min([D(x['head'],f)for x in E]or[9e9])
   return d+(e<d)*(8*b+2*(d-e))+(e==d)*2*b
  def reachable(c,tg):
   if c in O:return 0
   S={c};Q=[c];L=len(B)+3
   while Q:
    x=Q.pop(0)
    for d in M:
     k=n(x,d)
     if k not in S and k not in O:
      S.add(k);Q.append(k)
      if len(S)>=L or k==tg:return L
   return len(S)
  T=min(F,key=K)if F else h
  G=set(p['valid_actions'])
  if len(G)==1:return G.pop()
  best=None
  for a in G:
   c=n(h,N[a]);safe=c not in P;ate=c in F;acc=reachable(c,T)
   sc=(acc,1 if ate else 0,safe,D(c,T)<D(h,T))
   if best is None or sc>best:best=sc;be=a
  if be not in G:
   for a in G:
    c=n(h,N[a])
    if c>=0 and c not in P and c not in O:return a
  return be
def create_agent():return A()
