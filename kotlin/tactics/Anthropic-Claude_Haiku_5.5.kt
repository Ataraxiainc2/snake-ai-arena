import kotlin.math.*
class Tactic:SnakeTactic{
var w=0;var h=0;var t=true;var hd=0;var g=0;var bs=0;var ln=0;var el=false
fun nx(c:Int,k:Int):Int{val x=c%w+Move.DX[k];val y=c/w+Move.DY[k];return if(t)(y+h)%h*w+(x+w)%w else if(x in 0 until w&&y in 0 until h)y*w+x else hd}
fun ds(a:Int,c:Int):Int{val i=abs(a%w-c%w);val j=abs(a/w-c/w);return if(t)min(i,w-i)+min(j,h-j)else i+j}
fun rv(p:Perception,c:Int):Int{var m=40;for(e in p.enemies)m=min(m,ds(e.body[0],c));return m}
fun blk(p:Perception,c:Int,k:Int):Boolean{val m=p.marks[c];return m==g||m>bs&&m<=bs+ln&&m-bs>k||el&&p.bodies[c]>0}
fun stamp(p:Perception){val b=p.myBody;for(i in 0 until ln)if(p.marks[b[i]]!=g)p.marks[b[i]]=bs+ln-i}
fun flood(p:Perception,s:Int,cap:Int,ea:Int):Int{val mk=p.marks;val q=p.queue;val v=p.newMark();mk[s]=v;q[0]=s
var n=1;var i=0;var d=1;var e=1
while(i<n){if(i==e){d++;e=n};val c=q[i++]
for(k in 0..3){val x=nx(c,k);if(mk[x]!=v&&!blk(p,x,d+1-ea)){mk[x]=v;q[n++]=x;if(n>=cap)return n}}}
return n}
override fun decide(p:Perception):Int{
w=p.width;h=p.height;t=p.toroidal&&p.myScore<200
val b=p.myBody;hd=b[0];val tl=b[b.size-1];val v=p.myScore;el=v>298||p.enemyCollision
bs=p.newMark();ln=if(v>98)b.size else 0
repeat(ln){p.newMark()}
if(p.newMark()<bs)return decide(p)
g=p.newMark();p.marks[hd]=g
if(el)for(e in p.enemies)for(k in 0..3)p.marks[nx(e.body[0],k)]=g
stamp(p)
var tg=tl;var bk=Int.MAX_VALUE
for(i in p.foods.indices){val f=p.foods[i];val d=ds(hd,f);val r=p.foodRivalDistance(i)
val k=d+(if(el&&r<max(d,8))800000 else if(r<d)8+2*(d-r)else 0);if(k<bk){bk=k;tg=f}}
val hu=p.turnsWithoutFood>800;val st=p.turnsWithoutFood>p.starvationTurns-150
val cap=b.size*3/2+120+(if(el)40 else 0)
var r0=-1;var s0=0;var bm=p.validMoves[0]
for(m in p.validMoves){if(r0>=0&&p.nanosLeft()<=0)break
val c=nx(hd,m);stamp(p)
if(blk(p,c,if(p.previousReward==15f)0 else 1))continue
val f=c in p.foods;val x=flood(p,c,cap,if(f)1 else 0)
val r=if(!hu)x else if(st)min(x,1)else if(2*x>=b.size)1 else 0
val s=(if(el)rv(p,c)shl 21 else 0)+(if(f)1 shl 20 else 0)-(ds(c,tg)shl 10)-ds(c,tl)
if(r>r0||r==r0&&s>s0){r0=r;s0=s;bm=m}}
if(r0<0){for(m in p.validMoves)if(nx(hd,m)!=hd&&p.bodies[nx(hd,m)]<1)return m
for(m in p.validMoves)if(nx(hd,m)!=hd)return m}
return bm}}
