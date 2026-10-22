import kotlin.math.abs
import kotlin.math.min

class Tactic : SnakeTactic {
 private var o=IntArray(0);private var s=IntArray(0);private var q=IntArray(0);private var g=0;private var sg=0
 private var w=0;private var h=0;private var tr=true;private var hd=0
 private fun ds(a:Int,c:Int):Int{val i=abs(a%w-c%w);val j=abs(a/w-c/w);return if(tr)min(i,w-i)+min(j,h-j)else i+j}
 private fun nx(c:Int,d:Int):Int{val x=c%w+Move.DX[d];val y=c/w+Move.DY[d];return if(tr)(y+h)%h*w+(x+w)%w else if(x in 0 until w&&y in 0 until h)y*w+x else hd}
 private fun room(c:Int,cap:Int,tail:Int,ef:Boolean):Pair<Int,Boolean>{
  if(o[c]==g)return Pair(0,false)
  sg++;s[c]=sg;q[0]=c;var n=1;var k=0;var reach=c==tail&&!ef
  while(k<n&&!reach&&n<=cap){val a=q[k++]
   for(d in 0..3){val b=nx(a,d)
    if(s[b]!=sg){val isTl=b==tail
     if(isTl&&!ef){reach=true;s[b]=sg;q[n++]=b;break}
     if(o[b]!=g&&(!isTl||!ef)){s[b]=sg;q[n++]}}}}
  return Pair(n,reach)}
 override fun decide(p:Perception):Int{
  val vm=p.validMoves;if(vm.size<2)return vm[0]
  w=p.width;h=p.height;tr=p.toroidal;val n=w*h
  if(o.size!=n){o=IntArray(n);s=IntArray(n);q=IntArray(n+1);g=0;sg=0}
  g++;val b=p.myBody;hd=b[0];val tl=b[b.size-1];val v=p.myScore
  val sl=v>98||p.selfCollision||p.selfCollisionWarning;val el=v>298||p.enemyCollision
  if(sl)for(i in 0 until b.size-1)o[b[i]]=g
  if(el)for(e in p.enemies){for(c in e.body)o[c]=g;val eh=e.body[0];val ex=eh%w;val ey=eh/w
   for(d in 0..3){val x=ex+Move.DX[d];val y=ey+Move.DY[d];val ec=if(tr)(y+h)%h*w+(x+w)%w else if(x in 0 until w&&y in 0 until h)y*w+x else-1;if(ec>=0)o[ec]=g}}
  o[hd]=g
  val u=p.turnsWithoutFood>500;var tg=tl;var bk=Int.MAX_VALUE
  for(i in p.foods.indices){val f=p.foods[i];val d=ds(hd,f);val e=p.foodRivalDistance(i);val k=if(u)d else d+(if(el&&(e<=d||e<4))999999 else if(e<d)8+2*(d-e)else if(e==d)2 else 0);if(k<bk){bk=k;tg=f}}
  val cap=minOf(if(p.nanosLeft()<400000L)80 else 500,b.size*2+8);var best=vm[0];var maxSc=-1e18
  for(m in vm){val c=nx(hd,m);if(sl&&o[c]==g)continue;if(el&&o[c]==g)continue;val ef=c in p.foods;if(c==tl&&ef)continue
   val(r,reach)=room(c,cap,tl,ef);val tier=if(reach)2.0 else if(r>cap)1.0 else 0.0
   val sc=tier*1e6+(if(tier>0.0){if(ef)5e4 else 0.0}else r*100.0)-ds(c,tg)*10.0-ds(c,tl)+(if(m==p.myDirection)2.0 else 0.0)
   if(sc>maxSc){maxSc=sc;best=m}}
  return best
 }
}
