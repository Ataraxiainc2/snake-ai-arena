import kotlin.math.*
class Tactic:SnakeTactic{
var o=IntArray(0);var q=o;var g=0;var w=0;var h=0;var t=true;var hd=0;var bs=0;var n=0;var gr=0
fun nx(c:Int,k:Int):Int{val x=c%w+Move.DX[k];val y=c/w+Move.DY[k];return if(t)(y+h)%h*w+(x+w)%w else if(x in 0 until w&&y in 0 until h)y*w+x else -1}
fun ds(a:Int,c:Int):Int{val i=abs(a%w-c%w);val j=abs(a/w-c/w);return if(t)min(i,w-i)+min(j,h-j)else i+j}
fun ok(c:Int,d:Int):Boolean{val m=o[c];return m!=g&&(m<=bs||m>bs+n||d>=n+bs+1-m+gr)}
fun rm(p:Perception,b:IntArray,c:Int,cp:Int):Int{for(i in 0 until n)if(o[b[i]]!=g)o[b[i]]=bs+1+i
o[hd]=g;if(!ok(c,1))return 0
val s=p.newMark();o[c]=s;q[0]=c;var r=1;var k=0;var d=1;var e=1
while(k<r){if(k==e){d++;e=r};val a=q[k++];for(m in 0..3){val x=nx(a,m);if(x>=0&&o[x]!=s&&ok(x,d+1)){o[x]=s;q[r++]=x;if(r>cp)return cp}}}
return r}
override fun decide(p:Perception):Int{
w=p.width;h=p.height;t=p.toroidal;o=p.marks;q=p.queue;g=p.newMark()
val b=p.myBody;hd=b[0];val v=p.myScore;val tl=b[b.size-1]
bs=p.newMark();n=if(v>98)b.size else 0;repeat(n){p.newMark()}
if(p.newMark()<bs)return decide(p)
val el=v>298||p.enemyCollision
if(el)for(e in p.enemies){for(c in e.body)o[c]=g;for(k in 0..3){val x=nx(e.body[0],k);if(x>=0)o[x]=g}}
val tw=p.turnsWithoutFood;val hu=tw>800;val sv=tw>p.starvationTurns-150;val g0=if(p.previousReward==15f)1 else 0
var tg=tl;var bk=Int.MAX_VALUE
for(i in p.foods.indices){val f=p.foods[i];val d=ds(hd,f);val r=p.foodRivalDistance(i)
val k=if(hu)d else if(el&&r<8)d+800000 else if(r<d)d+8+2*(d-r)else if(r==d)d+3 else d;if(k<bk){bk=k;tg=f}}
val cp=if(sv)1 else if(hu)b.size/2+2 else b.size*2+150
var be=p.validMoves[0];var k0=-1;var k1=0;var k2=0;var k3=0;var k4=2
for(m in p.validMoves){if(k0>=0&&p.nanosLeft()<=0)break
val c=nx(hd,m);val f=c>=0&&c in p.foods;gr=g0+(if(f)1 else 0)
val r=if(c<0)0 else rm(p,b,c,cp)
val a0=if(sv)min(r,1)else if(hu)(if(2*r>=n)1 else 0)else r
val a1=if(hu||!f)0 else 1;val a2=-ds(c,tg);val a3=if(hu||v<99)0 else-ds(c,tl);val a4=if(m==p.myDirection)0 else 1
if(a0>k0||a0==k0&&(a1>k1||a1==k1&&(a2>k2||a2==k2&&(a3>k3||a3==k3&&a4<k4)))){be=m;k0=a0;k1=a1;k2=a2;k3=a3;k4=a4}}
if(k0<1)for(m in p.validMoves){val c=nx(hd,m);if(c>=0&&p.bodies[c].toInt()==0)return m}
return be}}
