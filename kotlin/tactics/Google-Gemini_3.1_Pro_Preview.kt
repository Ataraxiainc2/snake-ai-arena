import kotlin.math.*

class Tactic:SnakeTactic{
var o=IntArray(0);var q=o;var g=0;var w=0;var h=0;var t=true;var hd=0;var bs=0;var ln=0;var gr=0
fun nx(c:Int,d:Int):Int{val x=c%w+Move.DX[d];val y=c/w+Move.DY[d]
return if(t)(y+h)%h*w+(x+w)%w else if(x in 0 until w&&y in 0 until h)y*w+x else -1}
fun ds(a:Int,c:Int):Int{if(a<0||c<0)return 99999
val i=abs(a%w-c%w);val j=abs(a/w-c/w)
return if(t)min(i,w-i)+min(j,h-j)else i+j}
fun ok(c:Int,d:Int):Boolean{if(c<0)return false;val m=o[c]
return m!=g&&(m<=bs||m>bs+ln||d>=ln+bs+1-m+gr)}
fun rm(b:IntArray,c:Int,cp:Int,s:Int):Int{
for(i in 0 until ln)if(o[b[i]]!=g)o[b[i]]=bs+1+i
o[hd]=g;if(!ok(c,1))return 0
o[c]=s;q[0]=c;var n=1;var k=0;var d=1;var e=1
while(k<n){if(k==e){d++;e=n}
val a=q[k++];for(m in 0..3){val x=nx(a,m)
if(x>=0&&o[x]!=s&&ok(x,d+1)){o[x]=s;q[n++]=x;if(n>=cp)return cp}}}
return n}
override fun decide(p:Perception):Int{
if(p.validMoves.size==1)return p.validMoves[0]
w=p.width;h=p.height;t=p.toroidal;o=p.marks;q=p.queue;g=p.newMark()
val b=p.myBody;hd=b[0];val v=p.myScore;val tl=b[b.size-1]
bs=p.newMark();ln=0;val g0=if(p.previousReward==15f)1 else 0
if(v>98||p.selfCollision||p.selfCollisionWarning){ln=b.size
for(i in b.indices){o[b[i]]=bs+1+i;p.newMark()}}
if(p.newMark()<bs)return decide(p)
val el=v>298||p.enemyCollision
if(el)for(e in p.enemies){val eb=e.body;if(eb.isEmpty())continue
for(c in eb)o[c]=g;for(d in 0..3){val nb=nx(eb[0],d);if(nb>=0&&nb!=hd)o[nb]=g}}
o[hd]=g;val tw=p.turnsWithoutFood;val hu=tw>700;val sv=tw>1350
var tg=tl;var bk=Int.MAX_VALUE
for(i in p.foods.indices){val f=p.foods[i];val d=ds(hd,f);val e=p.foodRivalDistance(i)
val k=d+(if(el&&e<8)800000 else if(e<d)8+2*(d-e)else if(e==d)2 else 0)
if(k<bk){bk=k;tg=f}}
val cp=b.size*3/2+(if(el)150 else 120)
var be=p.validMoves[0];var k0=-1;var k1=0;var k2=-999999;var k3=-999999;var k4=2
for(m in p.validMoves){if(k0>=0&&p.nanosLeft()<=0)break
val c=nx(hd,m);if(c<0)continue
val f=c in p.foods;gr=g0+(if(f)1 else 0);val r=rm(b,c,if(sv)2 else cp,p.newMark())
val a0=if(hu)(if(sv)(if(r>0)1 else 0)else(if(2*r>=b.size)1 else 0))else r
val a1=if(hu||!f)0 else 1;val a2=-ds(c,tg);val a3=if(hu)0 else-ds(c,tl);val a4=if(m==p.myDirection)0 else 1
if(a0>k0||a0==k0&&(a1>k1||a1==k1&&(a2>k2||a2==k2&&(a3>k3||a3==k3&&a4<k4)))){
be=m;k0=a0;k1=a1;k2=a2;k3=a3;k4=a4}}
return be}
}