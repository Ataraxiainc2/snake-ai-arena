class Tactic:SnakeTactic{
var w=1;var h=1;var t=false
fun nx(c:Int,d:Int):Int{val x=c%w+Move.DX[d];val y=c/w+Move.DY[d];return if(t)(y+h)%h*w+(x+w)%w else if(x in 0 until w&&y in 0 until h)y*w+x else -1}
fun ds(a:Int,b:Int):Int{val x=kotlin.math.abs(a%w-b%w);val y=kotlin.math.abs(a/w-b/w);return if(t)minOf(x,w-x)+minOf(y,h-y)else x+y}
fun food(p:Perception,c:Int):Boolean{for(f in p.foods)if(f==c)return true;return false}
fun open(v:Int,d:Int,g:Int,n:Int,r:Int):Boolean{if(g==0||v<=g||v>g+n)return true;return d>=n-(v-g-1)+r}
fun room(p:Perception,s:Int,cap:Int,g:Int,block:Int,r:Int):Int{
 val mk=p.marks;val b=p.myBody
 if(g>0)for(i in b.indices)if(mk[b[i]]!=block)mk[b[i]]=g+i+1
 if(s<0||mk[s]==block||!open(mk[s],1,g,b.size,r))return -1
 val z=p.newMark();val q=p.queue;mk[s]=z;q[0]=s;var n=1;var i=0;var d=1;var end=1
 while(i<n&&n<=cap){if((i and 15)==0&&p.nanosLeft()<=0)break
  val c=q[i++]
  for(k in 0..3){val a=nx(c,k);if(a>=0&&mk[a]!=z&&mk[a]!=block&&open(mk[a],d+1,g,b.size,r)){mk[a]=z;q[n++]=a;if(n>cap)break}}
  if(i==end){d++;end=n}
 }
 return n
}
override fun decide(p:Perception):Int{
 w=p.width;h=p.height;t=p.toroidal
 val b=p.myBody;val hd=b[0];val tl=b[b.size-1];val ms=p.marks
 val self=p.myScore>=99||p.selfCollision||p.selfCollisionWarning
 val g=if(self)p.newMark()else 0
 if(g>0){for(i in b.indices)p.newMark();for(i in b.indices)ms[b[i]]=g+i+1}
 val danger=p.myScore>=299||p.enemyCollision;val block=p.newMark()
 if(danger)for(e in p.enemies){for(c in e.body)ms[c]=block;val a=e.body[0]
  for(d in 0..3){val c=nx(a,d);if(c>=0)ms[c]=block}
 }
 var target=if(p.foods.isEmpty())tl else p.foods[0];var bestCost=Int.MAX_VALUE
 val urgent=p.turnsWithoutFood>=p.starvationTurns-150
 for(i in p.foods.indices){val f=p.foods[i];val d=ds(hd,f);val rival=p.foodRivalDistance(i)
  val cost=d+if(urgent)0 else if(rival<d)8+2*(d-rival)else if(rival==d)2 else 0
  if(cost<bestCost){bestCost=cost;target=f}
 }
 val moves=p.validMoves;var best=moves[0];var bestVal=Long.MIN_VALUE
 val cap=minOf(b.size+8,if(p.nanosLeft()<300000L)32 else if(p.nanosLeft()<1000000L)96 else 400)
 val grow=if(p.previousReward>10f)1 else 0
 for(m in moves){
  if(bestVal!=Long.MIN_VALUE&&p.nanosLeft()<=0)break
  val c=nx(hd,m);if(c<0||ms[c]==block)continue
  val f=food(p,c);val r=room(p,c,cap,g,block,grow+(if(f)1 else 0));if(r<0)continue
  val enough=r>=if(urgent)1 else maxOf(2,b.size/2)
  val v=(if(enough)1000000000000L else 0L)+(if(f)100000000L else 0L)+r*100L-ds(c,target)*1000L-ds(c,tl)*2+(if(m==p.myDirection)1 else 0)
  if(v>bestVal){bestVal=v;best=m}
 }
 return best
}
}
