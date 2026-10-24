import kotlin.math.*
class Tactic:SnakeTactic{
var w=0;var h=0;var t=true;var bs=0;var n=0;var gr=0;var g=0;var o=IntArray(0)
fun nx(c:Int,k:Int):Int{val x=c%w+Move.DX[k];val y=c/w+Move.DY[k];return if(t)(y+h)%h*w+(x+w)%w else if(x in 0 until w&&y in 0 until h)y*w+x else -1}
fun ds(a:Int,c:Int):Int{val i=abs(a%w-c%w);val j=abs(a/w-c/w);return if(t)min(i,w-i)+min(j,h-j)else i+j}
fun ok(c:Int,d:Int):Boolean{val m=o[c];return if(m>bs&&m<=bs+n)d>=n-m+bs+1+gr else m!=g}
fun rm(p:Perception,b:IntArray,c:Int,cp:Int):Int{for(i in n-1 downTo 0)o[b[i]]=bs+1+i
if(!ok(c,1))return 0
val s=p.newMark();val q=p.queue;o[c]=s;q[0]=c;var k=0;var e=1;var d=1;var r=1
while(k<r){if(k%64==0&&p.nanosLeft()<0)return r;if(k==e){d++;e=r};val a=q[k++];for(m in 0..3){val x=nx(a,m);if(x>=0&&o[x]!=s&&ok(x,d+1)){o[x]=s;q[r++]=x;if(r>=cp)return cp}}}
return r}
override fun decide(p:Perception):Int{
w=p.width;h=p.height;t=p.toroidal;o=p.marks
val b=p.myBody;val hd=b[0];val v=p.myScore;val tl=b[b.size-1];val el=v>298
g=p.newMark();bs=p.newMark();n=if(v>98)b.size else 0;repeat(n){p.newMark()}
if(p.newMark()<g)return decide(p)
if(el)for(e in p.enemies)for(c in e.body){o[c]=g;if(c==e.body[0])for(m in 0..3){val x=nx(c,m);if(x>=0)o[x]=g}}
val g0=if(p.previousReward==15f)1 else 0
var tg=tl;var bk=Int.MAX_VALUE
for(i in p.foods.indices){val f=p.foods[i];val d=ds(hd,f);val e=p.foodRivalDistance(i)
val k=d+if(el&&(e<=d||e<5))9999 else if(e>d)0 else 4+3*(d-e);if(k<bk){bk=k;tg=f}}
val hu=p.turnsWithoutFood>900;val cp=b.size*3/2+120
var be=p.validMoves[0];var k0=-1;var k1=0;var k2=0;var k3=0;var k4=2
for(m in p.validMoves){if(k0>=0&&p.nanosLeft()<0)break
val c=nx(hd,m);val f=c>=0&&c in p.foods;gr=g0+(if(f)1 else 0)
val r=if(c<0)0 else rm(p,b,c,cp)
val a0=if(hu)(if(r>b.size/2+1)2 else min(r,1))else r;val a1=if(f)1 else 0;val a2=-ds(c,tg);val a3=if(hu||v<99)0 else-ds(c,tl);val a4=if(m==p.myDirection)0 else 1
if(a0>k0||a0==k0&&(a1>k1||a1==k1&&(a2>k2||a2==k2&&(a3>k3||a3==k3&&a4<k4)))){be=m;k0=a0;k1=a1;k2=a2;k3=a3;k4=a4}}
if(k0<1)for(m in p.validMoves){val c=nx(hd,m);if(c>=0&&p.bodies[c]<1)return m}
return be}}
