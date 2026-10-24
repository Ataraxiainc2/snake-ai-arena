import kotlin.math.*
class Tactic:SnakeTactic{
 override fun decide(p:Perception):Int{
  val w=p.width;val h=p.height;val t=p.toroidal;val o=p.marks;val q=p.queue;val g=p.newMark()
  val b=p.myBody;val hd=b[0];val v=p.myScore;val tl=b[b.size-1];val bs=p.newMark();var ln=0;var gr=0
  val g0=if(p.previousReward==15f)1 else 0
  fun nx(c:Int,d:Int):Int{val x=c%w+Move.DX[d];val y=c/w+Move.DY[d];return if(t)(y+h)%h*w+(x+w)%w else if(x in 0 until w&&y in 0 until h)y*w+x else hd}
  fun ds(a:Int,c:Int):Int{val i=abs(a%w-c%w);val j=abs(a/w-c/w);return if(t)min(i,w-i)+min(j,h-j)else i+j}
  fun ok(c:Int,d:Int):Boolean{val m=o[c];return m!=g&&(m<=bs||m>bs+ln||d>=ln+bs+1-m+gr)}
  fun rm(c:Int,cp:Int,s:Int):Int{
   for(i in 0 until ln)if(o[b[i]]!=g)o[b[i]]=bs+1+i
   o[hd]=g;if(!ok(c,1))return 0;o[c]=s;q[0]=c;var n=1;var k=0;var d=1;var e=1
   while(k<n){if(k==e){d++;e=n};val a=q[k++];for(m in 0..3){val z=nx(a,m);if(o[z]!=s&&ok(z,d+1)){o[z]=s;q[n++]=z;if(n>cp)return cp}}}
   return n
  }
  if(v>98){ln=b.size;for(i in b.indices){o[b[i]]=bs+1+i;p.newMark()}}
  if(p.newMark()<bs)return decide(p)
  val el=v>298||p.enemyCollision
  if(el)for(e in p.enemies){for(c in e.body)o[c]=g;for(d in 0..3)o[nx(e.body[0],d)]=g}
  o[hd]=g
  var tg=tl;var bk=Int.MAX_VALUE
  for(i in p.foods.indices){
   val f=p.foods[i];val d=ds(hd,f);val e=p.foodRivalDistance(i)
   val k=d+(if(e<d)8+2*(d-e)else 0)+(if(e==d)2 else 0);if(k<bk){bk=k;tg=f}
  }
  val cap=b.size*3/2+120;val tw=p.turnsWithoutFood;val hu=tw>800;val sv=tw>p.starvationTurns-150;val md=p.myDirection
  var best=p.validMoves[0];var k0=-1;var k1=0;var k2=0;var k3=0;var k4=2
  for(m in p.validMoves){
   if(k0>=0&&p.nanosLeft()<=0)break
   val c=nx(hd,m);val f=c in p.foods;gr=g0+(if(f)1 else 0);val r=rm(c,cap,p.newMark())
   val a0=if(hu)(if(sv)(if(r>0)1 else 0)else(if(2*r>=ln)1 else 0))else r
   val a1=if(hu||!f)0 else 1;val a2=-ds(c,tg);val a3=if(hu)0 else-ds(c,tl);val a4=if(m==md)0 else 1
   if(a0>k0||a0==k0&&(a1>k1||a1==k1&&(a2>k2||a2==k2&&(a3>k3||a3==k3&&a4<k4)))){best=m;k0=a0;k1=a1;k2=a2;k3=a3;k4=a4}
  }
  return best
 }
}
