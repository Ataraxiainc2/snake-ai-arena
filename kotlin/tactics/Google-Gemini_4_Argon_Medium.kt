import kotlin.math.*

class Tactic:SnakeTactic{
var w=0;var h=0;var t=true
fun nx(c:Int,d:Int):Int{val x=c%w+Move.DX[d];val y=c/w+Move.DY[d]
return if(t)(y+h)%h*w+(x+w)%w else if(x in 0 until w&&y in 0 until h)y*w+x else -1}
fun ds(a:Int,c:Int):Int{val i=abs(a%w-c%w);val j=abs(a/w-c/w)
return if(t)min(i,w-i)+min(j,h-j)else i+j}
fun rm(c:Int,cp:Int,m:IntArray,q:IntArray,s:Int,g:Int,tl:Int):Int{if(c<0||m[c]==g)return 0
m[c]=s;q[0]=c;var n=1;var k=0;while(k<n){val a=q[k++];for(d in 0..3){val b=nx(a,d)
if(b>=0&&m[b]!=s&&m[b]!=g){if(b==tl||n+1>=cp)return cp;m[b]=s;q[n++]=b}}}
return n}
override fun decide(p:Perception):Int{
w=p.width;h=p.height;val v=p.myScore;t=p.toroidal&&v<199
val m=p.marks;val q=p.queue;val g=p.newMark();val b=p.myBody
val hd=b[0];val tl=b[b.size-1];val el=v>298||p.enemyCollision
if(v>98||p.selfCollision||p.selfCollisionWarning){val l=b.size-(if(p.previousReward>9f)0 else 1)
for(i in 0 until l)m[b[i]]=g}
if(el)for(e in p.enemies){val eb=e.body;for(i in 0 until eb.size-1)m[eb[i]]=g
if(eb.isNotEmpty())for(d in 0..3){val z=nx(eb[0],d);if(z>=0)m[z]=g}}
m[hd]=g;val hu=p.turnsWithoutFood>750;var tg=tl;var bk=999999
for(i in p.foods.indices){val f=p.foods[i];val d=ds(hd,f);val e=p.foodRivalDistance(i)
val k=if(hu)d else d+(if(e<d)8+2*(d-e)else if(e==d)2 else 0)+(if(el&&e<4)99999 else 0)
if(k<bk){bk=k;tg=f}}
val cp=b.size+(if(el)40 else 12);val md=p.myDirection
var be=p.validMoves[0];var k0=-1;var k1=0;var k2=-9999;var k3=-9999;var k4=2
for(x in p.validMoves){if(k0>=0&&p.nanosLeft()<=0)break
val c=nx(hd,x);if(c<0)continue;val f=c in p.foods
val r=rm(c,cp,m,q,p.newMark(),g,if(f)-1 else tl)
val a0=if(hu)(if(r>0)1 else 0)else r;val a1=if(!hu&&f)1 else 0
val a2=-ds(c,tg);val a3=if(hu)0 else-ds(c,tl);val a4=if(x==md)0 else 1
if(a0>k0||a0==k0&&(a1>k1||a1==k1&&(a2>k2||a2==k2&&(a3>k3||a3==k3&&a4<k4)))){
be=x;k0=a0;k1=a1;k2=a2;k3=a3;k4=a4}}
return be}
}