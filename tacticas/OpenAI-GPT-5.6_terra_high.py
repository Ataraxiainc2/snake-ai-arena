from time import perf_counter as P
MODEL_NAME="AI_Competitor"
D={"UP":(0,-1),"DOWN":(0,1),"LEFT":(-1,0),"RIGHT":(1,0)}
class SnakeAgent:
 def decide_action(s,p):
  v=p["valid_actions"]
  z=p["block_size"];w=p["width"]//z;h=p["height"]//z;n=w*h
  def I(q):return q[1]//z*w+q[0]//z
  b=[I(q) for q in p["my_body"]];H=b[0];F={I(q)for q in p["foods"]};r=p["active_rules"];S=p["my_score"];T=r["toroidal_world"]and not r["wall_collision"];C=S>=100;E=r["enemy_collision"];G=[I(x["head"])for x in p["enemies"]]
  def N(q,a):
   x=q%w;y=q//w;X=x+D[a][0];Y=y+D[a][1]
   return (Y%h*w+X%w)if T else(Y*w+X if 0<=X<w and 0<=Y<h else-1)
  def d(q,f):
   x=abs(q%w-f%w);y=abs(q//w-f//w)
   return min(x,w-x)+min(y,h-y)if T else x+y
  L=[]
  for a in v:
   q=N(H,a)
   if q>=0:L.append((min((d(q,f)+8*(min((d(g,f)for g in G),default=w+h)<d(q,f))for f in F),default=w+h),a,q,q in F))
  if not C:
   return min(L,key=lambda x:(x[0],x[1]!=p["my_direction"]))[1]
  O=set(b[1:-1]);X=set()
  if E or S>=299:
   for e in p["enemies"]:
    X.update(I(q)for q in e["body"][:-1])
  Q=[];R=[]
  for d0,a,q,e in L:
   Z=E or(S>=299 and e)
   if q not in O and(not e or q!=b[-1])and(not Z or q not in X):
    R.append((d0,a,q,e,Z))
    if not Z or min((d(q,g)for g in G),default=2)>1:Q.append((d0,a,q,e,Z))
  if not Q:Q=R or[(d0,a,q,e,E or(S>=299 and e))for d0,a,q,e in L]
  t=P();B=min(n,900);ans=None;key=None;bad=(-1,Q[0][1])
  for d0,a,q,e,Z in Q:
   if P()-t>.009:break
   o=bytearray(n)
   for x in O:o[x]=1
   o[H]=1
   if e:o[b[-1]]=1
   if Z:
    for x in X:o[x]=1
   u=[q];o[q]=1;i=0
   while i<len(u)and len(u)<B:
    x=u[i];i+=1;xx=x%w;y=x//w
    for j in (x-w if y else x+(h-1)*w if T else-1,x+w if y<h-1 else x-(h-1)*w if T else-1,x-1 if xx else x+w-1 if T else-1,x+1 if xx<w-1 else x-w+1 if T else-1):
     if j>=0 and not o[j]:o[j]=1;u.append(j)
   if len(u)<2*len(b):
    if len(u)>bad[0]:bad=(len(u),a)
    continue
   k=(d0,-len(u),-min((d(q,g)for g in G),default=w+h),a!=p["my_direction"])
   if key is None or k<key:key=k;ans=a
  return ans or bad[1]
def create_agent():return SnakeAgent()
