import kotlin.math.*
class Tactic : SnakeTactic {
 private var o=IntArray(0);private var q=o;private var g=0;private var w=0;private var h=0;private var t=true;private var hd=0;private var bs=0;private var ln=0;private var gr=0
 private fun nx(c:Int,d:Int):Int{val x=c%w+Move.DX[d];val y=c/w+Move.DY[d];return if(t)(y+h)%h*w+(x+w)%w else if(x in 0 until w&&y in 0 until h)y*w+x else hd}
 private fun ds(a:Int,c:Int):Int{val i=abs(a%w-c%w);val j=abs(a/w-c/w);return if(t)min(i,w-i)+min(j,h-j)else i+j}
 private fun ok(c:Int,d:Int):Boolean{val m=o[c];return m!=g&&(m<=bs||m>bs+ln||d>=ln+bs+1-m+gr)}
 private fun rm(b:IntArray,c:Int,cp:Int,s:Int):Int{
  for(i in 0 until ln)if(o[b[i]]!=g)o[b[i]]=bs+1+i
  o[hd]=g;if(!ok(c,1))return 0
  o[c]=s;q[0]=c;var n=1;var k=0;var d=1;var e=1
  while(k<n){if(k==e){d++;e=n};val a=q[k++];for(m in 0..3){val x=nx(a,m);if(o[x]!=s&&ok(x,d+1)){o[x]=s;q[n++]=x;if(n>cp)return cp}}}
  return n
 }
 override fun decide(p:Perception):Int{
  w=p.width;h=p.height;t=p.toroidal;o=p.marks;q=p.queue;g=p.newMark()
  val b=p.myBody;hd=b[0];val v=p.myScore;val tl=b[b.size-1];bs=p.newMark();ln=0;val g0=if(p.previousReward==15f)1 else 0
  if(v>98){ln=b.size;repeat(ln){p.newMark()}}
  if(p.newMark()<bs)return decide(p)
  val el=v>298
  if(el)for(e in p.enemies){for(c in e.body)o[c]=g;for(d in 0..3)o[nx(e.body[0],d)]=g}
  val tw=p.turnsWithoutFood;val hu=tw>700;var tg=tl;var bk=1 shl 30
  for(i in p.foods.indices){val f=p.foods[i];val d=ds(hd,f);val e=p.foodRivalDistance(i);val k=if(hu)d else d+(if(el&&e<8)800000 else if(e<d)8+2*(d-e)else if(e==d)2 else 0);if(k<bk){bk=k;tg=f}}
  val cp=if(tw>1350)1 else if(hu)b.size/2+2 else b.size+(if(el)40 else 8);var r0=-1;var s0=-1e18;var bm=p.validMoves[0]
  for(m in p.validMoves){
   if(r0>=0&&p.nanosLeft()<=0)break
   val c=nx(hd,m);val f=c in p.foods;gr=g0+(if(f)1 else 0);val r=rm(b,c,cp,p.newMark())
   val s=(if(f)1 shl 20 else 0)-(ds(c,tg)shl 10)-(if(hu)0 else ds(c,tl))+(if(m==p.myDirection)2 else 0)-(if(r<1)p.bodies[c]*4000000 else 0)
   if(r>r0||(r==r0&&s.toDouble()>s0)){r0=r;s0=s.toDouble();bm=m}
  }
  return bm
 }
}
