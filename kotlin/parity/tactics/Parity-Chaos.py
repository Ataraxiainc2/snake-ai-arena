# Parity test only (kotlin/parity): Moonshot_AI-Kimi_K3_High plus deterministic trouble, so the replay also covers
# exceptions and invalid moves below 100 points (random moves from the game's RNG) and, from 200 on, dives into walls.
# Its Kotlin twin is arena.reference.ParityChaos.
M=(0,-1),(0,1),(-1,0),(1,0)
N=dict(zip('UP DOWN LEFT RIGHT'.split(),M))
OPP={'UP':'DOWN','DOWN':'UP','LEFT':'RIGHT','RIGHT':'LEFT'}
class A:
 def __init__(s):s.dive=None
 def decide_action(s,p):
  b=p['block_size'];W=p['width'];H=p['height'];B=p['my_body'];h=B[0];v=p['my_score']
  k=(h[0]//b*7+h[1]//b*13+int(v)*3+len(B))%29
  if v<200:s.dive=None
  if s.dive is None and v>=200 and k in(5,6):
   s.dive=min((h[0],'LEFT'),(W-b-h[0],'RIGHT'),(h[1],'UP'),(H-b-h[1],'DOWN'))[1]
  if s.dive in p['valid_actions']:return s.dive
  if v<100 and k==0:raise ValueError('chaos')
  if v<100 and k==1:return 'NOPE'
  if v<100 and k==2:return OPP[p['my_direction']]
  w=v<200;E=p['enemies'];F=p['foods'];tl=B[-1]
  O=set(B if p.get('previous_reward')else B[:-1])if v>98 else set()
  if v>298:
   for e in E:
    O|=set(e['body'][:-1]);i,j=e['head'];O|={(i+u*b,j+q*b)for u,q in M}
  def n(c,d):
   i=c[0]+d[0]*b;j=c[1]+d[1]*b
   return(i%W,j%H)if w else(i,j)if-1<i<W and-1<j<H else h
  def D(a,c):
   i=abs(a[0]-c[0]);j=abs(a[1]-c[1])
   return min(i,W-i)+min(j,H-j)if w else i+j
  def K(f):d=D(h,f);e=min([D(x['head'],f)for x in E]or[9e9]);return d+(e<d)*(8*b+2*(d-e))+(e==d)*2*b
  T=min(F,key=K)if F else tl
  L=len(B)+8
  def f(c):
   if c in O:return 0
   S={c};Q=[c]
   for c in Q:
    for d in M:
     k=n(c,d)
     if k not in S and k not in O:
      S.add(k);Q.append(k)
      if len(S)>L or k==tl:return L
   return len(S)
  u=p.get('turns_without_food',0)>800
  def g(a):
   c=n(h,N[a])
   if u:return f(c)>0,-D(c,T)
   return f(c),c in F,-D(c,T),-D(c,tl)
  return max(p['valid_actions'],key=g)
def create_agent():return A()
