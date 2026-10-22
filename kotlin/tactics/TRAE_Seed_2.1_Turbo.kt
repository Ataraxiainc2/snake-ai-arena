import kotlin.math.abs
import kotlin.math.min

class Tactic : SnakeTactic {
 private var d=ShortArray(0);private var w=0;private var h=0;private var t=true;private var hd=0;private var tl=0
 private fun nx(c:Int,k:Int):Int{val x=c%w+Move.DX[k];val y=c/w+Move.DY[k]
  return if(t)(y+h)%h*w+(x+w)%w else if(x in 0 until w&&y in 0 until h)y*w+x else-1}
 private fun ds(a:Int,c:Int):Int{val i=abs(a%w-c%w);val j=abs(a/w-c/w)
  return if(t)min(i,w-i)+min(j,h-j)else i+j}
 private fun rm(p:Perception,st:Int,bl:Int,cp:Int,og:Int,rg:Int):Int{
  val m=p.marks;if(st<0||m[st]==og||(bl>0&&st==tl))return 0
  if(st==tl)return cp;m[st]=rg;p.queue[0]=st;var n=1;var i=0;val q=p.queue
  while(i<n&&n<=cp){val c=q[i++]
   for(k in 0..3){val b=nx(c,k)
    if(b>=0&&m[b]!=rg&&m[b]!=og&&!(bl>0&&b==tl)){if(b==tl)return cp;m[b]=rg;q[n++]=b}}}
  return n}
 override fun decide(p:Perception):Int{
  val vm=p.validMoves;w=p.width;h=p.height;t=p.toroidal
  if(d.size!=w*h)d=ShortArray(w*h)
  val m=p.marks;val q=p.queue;val og=p.newMark()
  val b=p.myBody;val n=b.size;hd=b[0];tl=b[n-1];val v=p.myScore
  val sl=v>98||p.selfCollision||p.selfCollisionWarning;val el=v>298||p.enemyCollision
  val ja=p.previousReward>10f
  if(sl){val u=if(ja)n else n-1;for(i in 0 until u)m[b[i]]=og}
  if(el)for(e in p.enemies){for(c in e.body)m[c]=og;val e0=e.body[0]
   for(k in 0..3){val c=nx(e0,k);if(c>=0)m[c]=og}}
  val hu=p.turnsWithoutFood>800;var tg=tl;var bk=Int.MAX_VALUE
  for(i in p.foods.indices){val f=p.foods[i];val dd=ds(hd,f);val e=p.foodRivalDistance(i)
   val k=if(hu)dd else dd+(if(e<dd)8+2*(dd-e)else if(e==dd)2 else 0)
   if(k<bk){bk=k;tg=f}}
  val fg=p.newMark();var sp=0;var i=0
  if(tg>=0&&m[tg]!=og){m[tg]=fg;d[tg]=0;q[sp++]=tg}
  val cp=min(w*h,if(p.nanosLeft()>3000000)4000 else if(p.nanosLeft()>800000)1200 else 200)
  while(i<sp&&i<cp&&m[hd]!=fg){val c=q[i++]
   for(k in 0..3){val x=nx(c,k)
    if(x>=0&&m[x]!=fg&&m[x]!=og){m[x]=fg;d[x]=(d[c]+1).toShort();if(sp<q.size)q[sp++]=x}}}
  val ne=n+8;val rg=p.newMark()
  var be=vm[0];var k0=-1;var k1=0;var k2=0;var k3=0
  for(mv in vm){val c=nx(hd,mv);val ef=if(c>=0&&c in p.foods)1 else 0
   val bl=if(ja||ef>0)1 else 0
   val r=rm(p,c,bl,min(ne+ef,q.size-8),og,rg)
   val a0=if(hu)if(r>0)1 else 0 else min(r,ne)
   val a1=if(hu)0 else ef
   val a2=if(c>=0&&m[c]==fg)-d[c].toInt()else-10000-ds(c,tg)
   val a3=if(hu)0 else-ds(c,tl)
   if(a0>k0||a0==k0&&(a1>k1||a1==k1&&(a2>k2||a2==k2&&a3>k3))){be=mv;k0=a0;k1=a1;k2=a2;k3=a3}}
  return be}
}
