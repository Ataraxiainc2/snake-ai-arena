import kotlin.math.abs;import kotlin.math.min
class Tactic:SnakeTactic{
 override fun decide(p:Perception):Int{
  val w=p.width;val h=p.height;val t=p.toroidal;val mk=p.marks;val q=p.queue;val g=p.newMark()
  val b=p.myBody;val hd=b[0];val v=p.myScore;val tl=b[b.size-1]
  val g0=if(p.previousReward==15f)1 else 0
  fun nx(c:Int,d:Int):Int{val x=c%w+Move.DX[d];val y=c/w+Move.DY[d];return if(t)(y+h)%h*w+(x+w)%w else if(x in 0 until w&&y in 0 until h)y*w+x else hd}
  fun ds(a:Int,c:Int):Int{val i=abs(a%w-c%w);val j=abs(a/w-c/w);return if(t)min(i,w-i)+min(j,h-j)else i+j}
  // ── Fast path: below 100 nothing kills, body is tiny → pure greedy to nearest food ──────────
  if(v<100){
   var tg=tl;var bd=Int.MAX_VALUE
   for(f in p.foods){val d=ds(hd,f);if(d<bd){bd=d;tg=f}}
   val md=p.myDirection;var best=p.validMoves[0];var bk=Int.MAX_VALUE;var bm=2
   for(m in p.validMoves){val c=nx(hd,m);val d=ds(c,tg);val mo=if(m==md)0 else 1
    if(d<bk||d==bk&&mo<bm){best=m;bk=d;bm=mo}}
   return best}
  // ── From 100+: time-aware flood + rival-aware food targeting ────────────────────────────────
  val bs=p.newMark();var ln=0;var gr=0
  if(p.selfCollision||p.selfCollisionWarning){ln=b.size;for(i in b.indices){mk[b[i]]=bs+1+i;p.newMark()}}
  if(p.newMark()<bs)return decide(p)
  fun ok(c:Int,d:Int):Boolean{val m=mk[c];return m!=g&&(m<=bs||m>bs+ln||d>=ln+bs+1-m+gr)}
  fun room(c:Int,cap:Int,sg:Int):Int{
   for(i in 0 until ln)if(mk[b[i]]!=g)mk[b[i]]=bs+1+i;mk[hd]=g
   if(!ok(c,1))return 0;mk[c]=sg;q[0]=c;var n=1;var k=0;var d=1;var e=1
   while(k<n){if(k==e){d++;e=n};val a=q[k++]
    for(m in 0..3){val z=nx(a,m);if(mk[z]!=sg&&ok(z,d+1)){mk[z]=sg;q[n++]=z;if(n>cap)return cap}}};return n}
  val el=v>298||p.enemyCollision
  if(el)for(e in p.enemies){for(c in e.body)mk[c]=g;for(d in 0..3)mk[nx(e.body[0],d)]=g}
  mk[hd]=g
  val tw=p.turnsWithoutFood;val hu=tw>800;val sv=tw>p.starvationTurns-150
  var tg=tl;var bk=Int.MAX_VALUE
  for(i in p.foods.indices){val f=p.foods[i];val d=ds(hd,f);val e=p.foodRivalDistance(i)
   val k=if(hu)d else d+(if(el&&e<8)800000 else if(e<d)8+2*(d-e)else if(e==d)2 else 0)
   if(k<bk){bk=k;tg=f}}
  val cap=b.size*3/2+120;val md=p.myDirection
  var best=p.validMoves[0];var k0=-1;var k1=0;var k2=0;var k3=0;var k4=2
  for(m in p.validMoves){if(k0>=0&&p.nanosLeft()<=0)break
   val c=nx(hd,m);val f=c in p.foods;gr=g0+(if(f)1 else 0);val r=room(c,cap,p.newMark())
   val a0=if(hu)if(sv)if(r>0)1 else 0 else if(2*r>=ln)1 else 0 else r
   val a1=if(hu||!f)0 else 1;val a2=-ds(c,tg);val a3=if(hu)0 else-ds(c,tl);val a4=if(m==md)0 else 1
   if(a0>k0||a0==k0&&(a1>k1||a1==k1&&(a2>k2||a2==k2&&(a3>k3||a3==k3&&a4<k4)))){best=m;k0=a0;k1=a1;k2=a2;k3=a3;k4=a4}}
  return best}
}
