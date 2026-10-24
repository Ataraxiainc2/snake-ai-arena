import kotlin.math.*
class Tactic:SnakeTactic{
 var w=0;var h=0;var t=true;var hd=0;var g=0;var bs=0;var ln=0;var gr=0
 fun nx(c:Int,d:Int):Int{val x=c%w+Move.DX[d];val y=c/w+Move.DY[d];return if(t)(y+h)%h*w+(x+w)%w else if(x in 0 until w&&y in 0 until h)y*w+x else hd}
 fun ds(a:Int,c:Int):Int{val x=abs(a%w-c%w);val y=abs(a/w-c/w);return if(t)min(x,w-x)+min(y,h-y)else x+y}
 fun ok(c:Int,d:Int,o:IntArray):Boolean{val m=o[c];return m!=g&&(m<=bs||m>bs+ln||d>=ln+bs+1-m+gr)}
 fun rm(p:Perception,b:IntArray,c:Int,cp:Int,s:Int):Int{
  val o=p.marks
  for(i in 0 until ln)if(o[b[i]]!=g)o[b[i]]=bs+1+i
  o[hd]=g
  if(!ok(c,1,o))return 0
  o[c]=s;val q=p.queue;q[0]=c;var n=1;var k=0;var d=1;var e=1
  while(k<n){
   if(k==e){d++;e=n}
   val a=q[k++]
   if((k and 127)==0&&p.nanosLeft()<=0)return n
   for(m in 0..3){val z=nx(a,m);if(o[z]!=s&&ok(z,d+1,o)){o[z]=s;q[n++]=z;if(n>cp)return cp}}
  }
  return n
 }
 override fun decide(p:Perception):Int{
  w=p.width;h=p.height;t=p.toroidal;g=p.newMark()
  val b=p.myBody;hd=b[0];val v=p.myScore;val tl=b[b.size-1]
  bs=p.newMark();ln=if(v>98)b.size else 0
  repeat(ln){p.newMark()}
  val el=v>298||p.enemyCollision
  val o=p.marks
  if(el)for(x in p.enemies){for(c in x.body)o[c]=g;for(d in 0..3)o[nx(x.body[0],d)]=g}
  o[hd]=g
  var tg=tl;var bk=Int.MAX_VALUE
  for(i in p.foods.indices){
   val f=p.foods[i];val d=ds(hd,f);val r=p.foodRivalDistance(i)
   val z=if(el&&r<8)d+800000 else d+(if(r<d)8+2*(d-r)else if(r==d)2 else 0)
   if(z<bk){bk=z;tg=f}
  }
  val tw=p.turnsWithoutFood;val hu=tw>800;val sv=tw>p.starvationTurns-150
  val cp=if(sv)1 else if(hu)b.size/2+2 else b.size*2+180+(if(el)60 else 0)
  val wl=v>198;val md=p.myDirection
  var be=p.validMoves[0];var k0=-1;var k1=0;var k2=0;var k3=0;var k4=0;var k5=2
  for(m in p.validMoves){
   if(k0>=0&&p.nanosLeft()<=0)break
   val c=nx(hd,m);val f=c in p.foods;gr=(if(p.previousReward==15f)1 else 0)+(if(f)1 else 0)
   val r=rm(p,b,c,cp,p.newMark())
   val x=c%w;val y=c/w;val bw=min(min(x,w-1-x),min(y,h-1-y))*4+min(min(x,w-1-x)+min(y,h-1-y),30)
   val a0=if(sv)if(r>0)1 else 0 else if(hu)if(r>=b.size/2)1 else 0 else r
   val a1=if(hu||!f)0 else 1
   val a2=if(wl)-ds(c,tg)*3+min(bw,12)else-ds(c,tg)
   val a3=if(wl)bw else if(hu)0 else-ds(c,tl)
   val a4=if(wl&& !hu)-ds(c,tl)else 0
   val a5=if(m==md)0 else 1
   if(a0>k0||a0==k0&&(a1>k1||a1==k1&&(a2>k2||a2==k2&&(a3>k3||a3==k3&&(a4>k4||a4==k4&&a5<k5))))){
    be=m;k0=a0;k1=a1;k2=a2;k3=a3;k4=a4;k5=a5
   }
  }
  return be
 }
}
