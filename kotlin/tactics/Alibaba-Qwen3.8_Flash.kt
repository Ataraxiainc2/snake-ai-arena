import kotlin.math.abs
import kotlin.math.min
class Tactic:SnakeTactic{
var o=IntArray(0);var s=o;var d=o;var q=o;var g=0;var sg=0;var w=0;var h=0;var t=true;var tl=0
fun nx(c:Int,k:Int):Int{val x=c%w+Move.DX[k];val y=c/w+Move.DY[k]
return if(t)(y+h)%h*w+(x+w)%w else if(x<0||y<0||x>=w||y>=h)-1 else y*w+x}
fun ds(a:Int,c:Int):Int{val i=abs(a%w-c%w);val j=abs(a/w-c/w)
return if(t)min(i,w-i)+min(j,h-j)else i+j}
fun bl(c:Int,e:Boolean)=c<0||o[c]==g||e&&c==tl
fun rm(st:Int,e:Boolean,cp:Int):Int{if(bl(st,e))return 0
var f=0
if(st==tl)f=1
sg++;s[st]=sg;q[0]=st;var n=1;var i=0
while(i<n&&n<=cp){val c=q[i++]
for(k in 0..3){val b=nx(c,k)
if(b==tl)f=1
if(!bl(b,e)&&s[b]!=sg){s[b]=sg;q[n++]=b}}}
return n+f*cp/4}
override fun decide(p:Perception):Int{
val vm=p.validMoves;w=p.width;h=p.height;t=p.toroidal
if(o.size!=w*h){o=IntArray(w*h);s=IntArray(w*h);d=IntArray(w*h);q=IntArray(min(w*h,3000));g=0;sg=0}
g++
val b=p.myBody;val n=b.size;val hd=b[0];tl=b[n-1];val v=p.myScore
val sl=v>98||p.selfCollision||p.selfCollisionWarning;val el=v>298||p.enemyCollision
if(sl){for(c in b)o[c]=g;if(p.previousReward!=15f)o[tl]=0}
if(el)for(e in p.enemies){for(c in e.body)o[c]=g;val z=e.body[0]
for(k in 0..3){val c=nx(z,k);if(c>=0)o[c]=g}}
val cp=min(q.size-8,if(p.nanosLeft()>3000000)4000 else if(p.nanosLeft()>800000)1200 else 200)
var tg=tl;var bk=Int.MAX_VALUE
for(i in p.foods.indices){val f=p.foods[i]
if(bl(f,false)||f==hd||f==tl)continue
val dd=ds(hd,f);val e=p.foodRivalDistance(i)
val k=dd+(if(el&&e<8)9000000 else if(e<dd)9000 else 0)
if(k<bk){bk=k;tg=f}}
sg++;val ps=sg;var sp=0;var i=0
if(!bl(tg,false)){s[tg]=sg;d[tg]=0;q[sp++]=tg}
while(i<sp&&i<cp){val c=q[i++]
for(k in 0..3){val x=nx(c,k)
if(!bl(x,false)&&s[x]!=sg){s[x]=sg;d[x]=d[c]+1;if(sp<q.size)q[sp++]=x}}}
val ne=n+8;val hu=p.turnsWithoutFood>500
var be=vm[0];var mx=-1e18
for(m in vm){val c=nx(hd,m);val e=if(c>=0&&c in p.foods)1 else 0
val Y=min(ne+e,cp);val r=rm(c,e>0,Y)
val pd=if(c>=0&&s[c]==ps)d[c]else 10000+ds(c,tg)
val rk=if(hu)if(r>0)if(e>0)2 else 1 else 0 else if(r>Y+4)ne+4 else min(r,ne)
val sc=rk*1e6-pd*50.0+e*900.0+(if(m==p.myDirection)1.0 else 0.0)
if(sc>mx){mx=sc;be=m}}
return be}
}
