import kotlin.math.*
class Tactic:SnakeTactic{
var o=IntArray(0);var q=o;var g=0;var w=0;var h=0;var t=true;var hd=0;var bs=0;var ln=0;var gr=0
fun nx(c:Int,k:Int):Int{val x=c%w+Move.DX[k];val y=c/w+Move.DY[k];return if(t)(y+h)%h*w+(x+w)%w else if(x in 0 until w&&y in 0 until h)y*w+x else -1}
fun ds(a:Int,c:Int):Int{val i=abs(a%w-c%w);val j=abs(a/w-c/w);return if(t)min(i,w-i)+min(j,h-j)else i+j}
fun ok(c:Int,d:Int):Boolean{val m=o[c];return m!=g&&(m<=bs||m>bs+ln||d>=ln+bs+1-m+gr)}
fun rm(b:IntArray,c:Int,cp:Int,s:Int):Int{
for(i in 0 until ln)if(o[b[i]]!=g)o[b[i]]=bs+1+i
o[hd]=g
if(!ok(c,1))return 0
o[c]=s;q[0]=c;var n=1;var k=0;var d=1;var e=1
while(k<n){if(k==e){d++;e=n};val a=q[k++];for(m in 0..3){val x=nx(a,m);if(x>=0&&o[x]!=s&&ok(x,d+1)){o[x]=s;q[n++]=x;if(n>cp)return cp}}}
return n}
override fun decide(p:Perception):Int{
w=p.width;h=p.height;t=p.toroidal;o=p.marks;q=p.queue;g=p.newMark()
val b=p.myBody;hd=b[0];val v=p.myScore;val tl=b[b.size-1]
bs=p.newMark();ln=0;val g0=if(p.previousReward==15f)1 else 0
if(v>98){ln=b.size;repeat(ln){p.newMark()}}
if(p.newMark()<bs)return decide(p)
val el=v>298||p.enemyCollision
if(el)for(e in p.enemies){for(c in e.body)o[c]=g;val z=e.body[0];for(k in 0..3){val x=nx(z,k);if(x>=0)o[x]=g}}
val tw=p.turnsWithoutFood;val hu=tw>800;val sv=tw>p.starvationTurns-150
val wl=v in 200..299
var tg=tl;var bk=Int.MAX_VALUE
for(i in p.foods.indices){val f=p.foods[i];val d=ds(hd,f);val r=p.foodRivalDistance(i);val k=if(hu)d else if(el&&r<8)d+800000 else if(r<d)d+8+2*(d-r)else if(r==d)d+2 else d;if(k<bk){bk=k;tg=f}}
val cp=if(sv)1 else if(hu)b.size/2+2 else if(wl)b.size*2+180 else b.size*3/2+120+(if(el)40 else 0)
val req=if(wl)ln else ln/2
val md=p.myDirection
var be=p.validMoves[0];var k0=-1;var k1=0;var k2=Int.MIN_VALUE;var k3=Int.MIN_VALUE;var k4=Int.MIN_VALUE;var k5=2
for(m in p.validMoves){
if(k0>=0&&p.nanosLeft()<=0)break
val c=nx(hd,m);val f=c>=0&&c in p.foods;gr=g0+(if(f)1 else 0)
val r=if(c<0)0 else rm(b,c,cp,p.newMark())
val a0=if(sv)if(r>0)1 else 0 else if(hu)if(r>=req)1 else 0 else r
val a1=if(hu||!f)0 else 1
val bx=min(c%w,w-1-c%w);val by=min(c/w,h-1-c/w);val bw=min(bx,by)*4+min(bx+by,30)
val a2=-ds(c,tg)+(if(wl)min(bw,12)else 0)
val a3=if(wl)bw else if(hu)0 else -ds(c,tl)
val a4=if(wl&&!hu)-ds(c,tl)else 0
val a5=if(m==md)0 else 1
if(a0>k0||a0==k0&&(a1>k1||a1==k1&&(a2>k2||a2==k2&&(a3>k3||a3==k3&&(a4>k4||a4==k4&&a5<k5))))){be=m;k0=a0;k1=a1;k2=a2;k3=a3;k4=a4;k5=a5}}
if(k0<1)for(m in p.validMoves){val c=nx(hd,m);if(c>=0&&p.bodies[c].toInt()==0)return m}
return be}
}
