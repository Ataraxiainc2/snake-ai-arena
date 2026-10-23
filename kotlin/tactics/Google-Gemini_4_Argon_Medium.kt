import kotlin.math.*
class Tactic:SnakeTactic{
var g=0;var w=0;var h=0;var t=true;var hd=0;var tl=0;var el=false
fun nx(c:Int,k:Int):Int{val x=c%w+Move.DX[k];val y=c/w+Move.DY[k];return if(t)(y+h)%h*w+(x+w)%w else if(x in 0 until w&&y in 0 until h)y*w+x else hd}
fun ds(a:Int,c:Int):Int{val i=abs(a%w-c%w);val j=abs(a/w-c/w);return if(t)min(i,w-i)+min(j,h-j)else i+j}
fun bl(p:Perception,c:Int,e:Boolean)=c==tl&&e||p.marks[c]==g||el&&p.bodies[c]>0
fun rm(p:Perception,st:Int,e:Boolean,cp:Int):Int{val mk=p.marks;if(bl(p,st,e))return 0
val mv=p.newMark();mk[st]=mv;val q=p.queue;q[0]=st;var n=1;var i=0
while(i<n){val c=q[i++];for(k in 0..3){val b=nx(c,k);if(mk[b]!=mv&&!bl(p,b,e)){if(b==tl||n+1>=cp)return cp;mk[b]=mv;q[n++]=b}}}
return n}
override fun decide(p:Perception):Int{
w=p.width;h=p.height;val v=p.myScore;t=p.toroidal&&v<199;val mk=p.marks;g=p.newMark()
val b=p.myBody;hd=b[0];tl=b[b.size-1];el=v>298||p.enemyCollision
if(v>98){for(i in 0 until b.size-(if(p.previousReward==15f)0 else 1))mk[b[i]]=g}
mk[hd]=g
if(el)for(e in p.enemies){val z=e.body[0];for(k in 0..3)mk[nx(z,k)]=g}
var tg=tl;var bk=999999
for(i in p.foods.indices){val f=p.foods[i];val d=ds(hd,f);val r=p.foodRivalDistance(i);val k=d+(if(el&&r<6)99999 else if(r<d)8+2*(d-r)else if(r==d)2 else 0)
if(k<bk){bk=k;tg=f}}
val Y=min(3000,b.size+10+(if(el)40 else 0));val hu=p.turnsWithoutFood>800;val md=p.myDirection
var be=p.validMoves[0];var k0=-1;var k1=0;var k2=-99999;var k3=-99999;var k4=2
for(m in p.validMoves){val c=nx(hd,m);val f=c in p.foods;val r=rm(p,c,f,Y)
val a0=if(hu)min(r,2)else r;val a1=if(!hu&&f)1 else 0;val a2=-ds(c,tg);val a3=if(hu)0 else-ds(c,tl);val a4=if(m==md)0 else 1
if(a0>k0||a0==k0&&(a1>k1||a1==k1&&(a2>k2||a2==k2&&(a3>k3||a3==k3&&a4<k4)))){be=m;k0=a0;k1=a1;k2=a2;k3=a3;k4=a4}}
return be}
}