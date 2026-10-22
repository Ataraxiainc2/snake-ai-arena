MODEL_NAME="AI_Competitor"
M=(0,-1),(0,1),(-1,0),(1,0)
N=dict(zip('UP DOWN LEFT RIGHT'.split(),M))
class A:
 def decide_action(s,p):
  b=p['block_size'];W=p['width'];H=p['height'];B=p['my_body'];h=B[0];v=p['my_score'];E=p['enemies'];F=p['foods'];t=B[-1];L=len(B)+6;w=v<199
  O=set(B[:-1])if v>98 else set()
  if v>298:
   for e in E:
    O|=set(e['body'][:-1]);i,j=e['head'];O|={(i+u*b,j+q*b)for u,q in M}
  def n(c,d):
   i=c[0]+d[0]*b;j=c[1]+d[1]*b
   return(i%W,j%H)if w else(i,j)if-1<i<W and-1<j<H else h
  def D(a,c):
   i=abs(a[0]-c[0]);j=abs(a[1]-c[1])
   return min(i,W-i)+min(j,H-j)if w else i+j
  def K(f):d=D(h,f);e=min([D(x['head'],f)for x in E]or[9e9]);return d+(e<d)*(8*b+2*(d-e))+(e==d)*2*b+(v>298)*(e<4*b)*9e9
  def f(c,Y):
   if c in Y or c==h:return 0
   S={c};Q=[c]
   for c in Q:
    for d in M:
     k=n(c,d)
     if k not in S and k not in Y:
      S.add(k);Q.append(k)
      if len(S)>L or k==t:return L
   return len(S)
  def R(q):
   G={q:0};Q=[q];i=0
   while i<len(Q)and i<200 and h not in G:
    c=Q[i];i+=1
    for d in M:
     k=n(c,d)
     if k not in G and k not in O:G[k]=G[c]+1;Q.append(k)
   return G
  T=t;G={}
  for q in sorted(F,key=K)[:2]:
   T=q;G=R(q)
   if h in G:break
  def z(c):return min([D(c,x['head'])for x in E]or[0])
  u=p.get('turns_without_food',0)>800
  def g(a):
   c=n(h,N[a]);s=L if v<99 else f(c,O|{t}if c in F else O)
   if u:return s>0,-G.get(c,9e9),-D(c,T)
   return s,-G.get(c,9e9),c in F,-D(c,T),-(v>298)*z(c),-D(c,t),a==p['my_direction']
  return max(p['valid_actions'],key=g)
def create_agent():return A()
