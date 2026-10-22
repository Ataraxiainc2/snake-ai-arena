class Tactic : SnakeTactic {
 private var w=0;private var h=0;private var t=true;private var hd=0
 private fun nx(c:Int,d:Int):Int{val x=c%w+Move.DX[d];val y=c/w+Move.DY[d];return if(t)(y+h)%h*w+(x+w)%w else if(x in 0 until w&&y in 0 until h)y*w+x else hd}
 private fun ds(a:Int,c:Int):Int{val i=kotlin.math.abs(a%w-c%w);val j=kotlin.math.abs(a/w-c/w);return if(t)kotlin.math.min(i,w-i)+kotlin.math.min(j,h-j)else i+j}
 private fun room(c:Int,cap:Int,mk:IntArray,q:IntArray,sm:Int,bm:Int,tl:Int,ef:Boolean):Int{
  if(mk[c]==bm||ef&&c==tl)return 0
  if(!ef&&c==tl)return 99999
  mk[c]=sm;q[0]=c;var n=1;var k=0
  while(k<n){val a=q[k++];for(d in 0..3){val e=nx(a,d);val m=mk[e]
   if(m!=sm&&m!=bm&&!(ef&&e==tl)){if(!ef&&e==tl)return 99999;mk[e]=sm;q[n++]=e;if(n>cap)return cap}}}
  return n
 }
 override fun decide(p:Perception):Int{
  w=p.width;h=p.height;t=p.toroidal;val mk=p.marks;val q=p.queue;val bm=p.newMark();val b=p.myBody;hd=b[0];val v=p.myScore;val tl=b[b.size-1]
  if(v>98){val z=if(p.previousReward==15f)b.size else b.size-1;for(i in 0 until z)mk[b[i]]=bm}
  if(v>298)for(e in p.enemies){for(c in e.body)mk[c]=bm;if(!t)for(d in 0..3)mk[nx(e.body[0],d)]=bm}
  mk[hd]=bm;var tg=tl;var bk=Int.MAX_VALUE
  for(i in p.foods.indices){
   val f=p.foods[i];val d=ds(hd,f);val e=p.foodRivalDistance(i)
   val k=d+(if(v>298&&(e<=d||e<4))99999 else if(e<d)8+2*(d-e) else if(e==d)2 else 0)
   if(k<bk){bk=k;tg=f}
  }
  val cap=b.size+(if(v>298)40 else 8);val hungry=p.turnsWithoutFood>800;val md=p.myDirection;var best=p.validMoves[0];var k0=-1;var k1=0;var k2=0;var k3=0;var k4=2
  for(m in p.validMoves){
   val c=nx(hd,m);val ef=c in p.foods;val r=room(c,cap,mk,q,p.newMark(),bm,tl,ef)
   val a0=if(hungry)if(r>0)1 else 0 else r;val a1=if(hungry)0 else if(ef)1 else 0;val a2=-ds(c,tg);val a3=if(hungry)0 else-ds(c,tl);val a4=if(m==md)0 else 1
   if(a0>k0||a0==k0&&(a1>k1||a1==k1&&(a2>k2||a2==k2&&(a3>k3||a3==k3&&a4<k4)))){best=m;k0=a0;k1=a1;k2=a2;k3=a3;k4=a4}
  }
  return best
 }
}
