MODEL_NAME="Google-Gemini_3.8_Flash_Medium"
M=(0,-1),(0,1),(-1,0),(1,0)
N=dict(zip('UP DOWN LEFT RIGHT'.split(),M))
class A:
 def decide_action(s,p):
  va=p['valid_actions']
  if len(va)<2:return va[0]
  b=p['block_size'];W=p['width'];H=p['height'];B=p['my_body'];h=B[0];v=p['my_score'];w=v<200;E=p['enemies'];F=p['foods'];tl=B[-1];l=v>298;md=p['my_direction']
  O=set(B if p.get('previous_reward')else B[:-1])if v>98 else set()
  def n(c,d):i=c[0]+d[0]*b;j=c[1]+d[1]*b;return(i%W,j%H)if w else((i,j)if-1<i<W and-1<j<H else None)
  if l:
   for e in E:O|=set(e['body'])|{n(e['head'],d)for d in M}
   O.discard(None)
  def D(a,c):i=abs(a[0]-c[0]);j=abs(a[1]-c[1]);return min(i,W-i)+min(j,H-j)if w else i+j
  def K(f):d=D(h,f);e=min([D(x['head'],f)for x in E]or[9e9]);return d+(e<d)*(8*b+2*(d-e))+(e==d)*2*b+l*(e<4*b)*9e9
  u=p.get('turns_without_food',0)>600
  T=min(F,key=(lambda f:D(h,f))if u else K)if F else tl
  C=min(500,len(B)*2+10)
  def f(c):
   if not c or c in O:return-1,0
   S={c};Q=[c];t=c==tl and c not in F
   for x in Q:
    if t or len(S)>C:break
    for d in M:
     k=n(x,d)
     if k and k not in S and(k not in O or(k==tl and c not in F)):
      if k==tl:t=1
      S.add(k);Q.append(k)
      if len(S)>C or t:break
   return(2 if t else 1 if len(S)>C else 0),len(S)
  def g(a):
   c=n(h,N[a]);r,m=f(c)
   if r<0:return-9e9,
   if u:return r>0,-D(c,T),m,a==md
   return r,c in F if r else m,-D(c,T),-D(c,tl),a==md
  return max(va,key=g)
def create_agent():return A()
