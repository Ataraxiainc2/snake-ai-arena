class Tactic:SnakeTactic{
var g=0;var w=0;var h=0;var t=true;var hd=0;var tl=0;var el=false
fun nx(c:Int,k:Int):Int{val x=c%w+Move.DX[k];val y=c/w+Move.DY[k];return if(t)(y+h)%h*w+(x+w)%w else if(x in 0 until w&&y in 0 until h)y*w+x else hd}
fun ds(a:Int,c:Int):Int{val i=kotlin.math.abs(a%w-c%w);val j=kotlin.math.abs(a/w-c/w);return if(t)kotlin.math.min(i,w-i)+kotlin.math.min(j,h-j)else i+j}
fun bl(p:Perception,c:Int,e:Boolean)=c==tl&&e||p.marks[c]==g||el&&p.bodies[c]>0
fun rm(p:Perception,st:Int,e:Boolean,cp:Int):Int{val mk=p.marks;if(bl(p,st,e))return 0
val mv=p.newMark();mk[st]=mv;val q=p.queue;q[0]=st;var n=1;var i=0
while(i<n){val c=q[i++];if(i%96==0&&p.nanosLeft()<80)return n;for(k in 0..3){val b=nx(c,k);if(mk[b]!=mv&&!bl(p,b,e)){mk[b]=mv;q[n++]=b;if(n>cp)return cp}}}
return n}
override fun decide(p:Perception):Int{
w=p.width;h=p.height;t=p.toroidal;val mk=p.marks;g=p.newMark()
val b=p.myBody;hd=b[0];tl=b[b.size-1];val v=p.myScore;el=v>298||p.enemyCollision
if(v>98){val z=if(p.previousReward==15f)b.size else b.size-1;for(i in 0 until z)mk[b[i]]=g}
mk[hd]=g
if(el)for(e in p.enemies){val z=e.body[0];for(k in 0..3)mk[nx(z,k)]=g}
var tg=tl;var bk=Int.MAX_VALUE
for(i in p.foods.indices){val f=p.foods[i];val d=ds(hd,f);val r=p.foodRivalDistance(i);val k=d+(if(el&&r<8)800000 else if(r<d)8+2*(d-r)else 0)
if(k<bk){bk=k;tg=f}}
val Y=b.size+108+(if(el)200 else 0);val hu=p.turnsWithoutFood>800;val md=p.myDirection
var be=p.validMoves[0];var k0=-1;var k1=0;var k2=0;var k3=0;var k4=2
for(m in p.validMoves){val c=nx(hd,m);val r=rm(p,c,c in p.foods,Y)
val a0=if(hu)if(r>Y/2)1 else 0 else r;val a1=if(c in p.foods)1 else 0;val a2=-ds(c,tg);val a3=if(hu)0 else-ds(c,tl);val a4=if(m==md)0 else 1
if(a0>k0||a0==k0&&(a1>k1||a1==k1&&(a2>k2||a2==k2&&(a3>k3||a3==k3&&a4<k4)))){be=m;k0=a0;k1=a1;k2=a2;k3=a3;k4=a4}}
return be}
}
