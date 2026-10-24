class Tactic:SnakeTactic{
 override fun decide(p:Perception):Int{
  val w=p.width;val h=p.height;val t=p.toroidal;val mk=p.marks;val q=p.queue;val g=p.newMark()
  val b=p.myBody;val hd=b[0];val v=p.myScore;val tl=b[b.size-1];val bs=p.newMark();var ln=0;var gr=0
  val g0=if(p.previousReward==15f)1 else 0
  fun nx(c:Int,d:Int):Int{val x=c%w+Move.DX[d];val y=c/w+Move.DY[d];return if(t)(y+h)%h*w+(x+w)%w else if(x in 0 until w&&y in 0 until h)y*w+x else hd}
  fun ds(a:Int,c:Int):Int{val i=Math.abs(a%w-c%w);val j=Math.abs(a/w-c/w);return if(t)minOf(i,w-i)+minOf(j,h-j)else i+j}
  fun ok(c:Int,d:Int):Boolean{val m=mk[c];return m!=g&&(m<=bs||m>bs+ln||d>=ln+bs+1-m+gr)}
  fun room(c:Int,cap:Int,sg:Int):Int{for(i in 0 until ln)if(mk[b[i]]!=g)mk[b[i]]=bs+1+i;mk[hd]=g;if(!ok(c,1))return 0;mk[c]=sg;q[0]=c;var n=1;var k=0;var d=1;var e=1
   while(k<n){if(k==e){d++;e=n};val a=q[k++];for(m in 0..3){val z=nx(a,m);if(mk[z]!=sg&&ok(z,d+1)){mk[z]=sg;q[n++]=z;if(n>cap)return cap}}};return n}
  if(v>98){ln=b.size;for(i in b.indices){mk[b[i]]=bs+1+i;p.newMark()}}
  if(v>298)for(e in p.enemies){for(c in e.body)mk[c]=g;if(!t)for(d in 0..3)mk[nx(e.body[0],d)]=g}
  mk[hd]=g
  var tg=tl;var bk=Int.MAX_VALUE
  for(i in p.foods.indices){val f=p.foods[i];val d=ds(hd,f);val e=p.foodRivalDistance(i);val k=d+(if(e<d)8+2*(d-e)else 0)+(if(e==d)2 else 0);if(k<bk){bk=k;tg=f}}
  val cap=b.size*3/2+120;val hungry=p.turnsWithoutFood>800;val starving=p.turnsWithoutFood>p.starvationTurns-150;val md=p.myDirection
  var best=p.validMoves[0];var k0=-1;var k1=0;var k2=0;var k3=0;var k4=2
  for(m in p.validMoves){val c=nx(hd,m);val f=c in p.foods;gr=g0+(if(f)1 else 0);val r=room(c,cap,p.newMark());val a0=if(hungry)(if(starving)(if(r>0)1 else 0)else(if(2*r>=ln)1 else 0))else r;val a1=if(hungry||!f)0 else 1;val a2=-ds(c,tg);val a3=if(hungry)0 else-ds(c,tl);val a4=if(m==md)0 else 1;if(a0>k0||a0==k0&&(a1>k1||a1==k1&&(a2>k2||a2==k2&&(a3>k3||a3==k3&&a4<k4)))){best=m;k0=a0;k1=a1;k2=a2;k3=a3;k4=a4}}
  return best
 }
}
