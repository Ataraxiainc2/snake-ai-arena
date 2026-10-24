import kotlin.math.*

class Tactic:SnakeTactic{
    var o=IntArray(0)
    var q=o
    var g=0
    var w=0
    var h=0
    var t=true
    var hd=0
    var bs=0
    var ln=0
    var gr=0

    fun nx(c:Int,d:Int):Int{
        val x=c%w+Move.DX[d]
        val y=c/w+Move.DY[d]
        return if(t)(y+h)%h*w+(x+w)%w else if(x in 0 until w && y in 0 until h) y*w+x else hd
    }

    fun ds(a:Int,c:Int):Int{
        val i=abs(a%w-c%w)
        val j=abs(a/w-c/w)
        return if(t) min(i,w-i)+min(j,h-j) else i+j
    }

    fun ok(c:Int,d:Int):Boolean{
        val m=o[c]
        return m!=g && (m<=bs || m>bs+ln || d>=ln+bs+1-m+gr)
    }

    fun rm(b:IntArray,c:Int,cp:Int,s:Int):Int{
        for(i in 0 until ln) if(o[b[i]]!=g) o[b[i]]=bs+1+i
        o[hd]=g
        if(!ok(c,1)) return 0
        o[c]=s
        q[0]=c
        var n=1
        var k=0
        var d=1
        var e=1
        while(k<n){
            if(k==e){d++; e=n}
            val a=q[k++]
            for(m in 0..3){
                val x=nx(a,m)
                if(o[x]!=s && ok(x,d+1)){
                    o[x]=s
                    q[n++]=x
                    if(n>cp) return cp
                }
            }
        }
        return n
    }

    override fun decide(p:Perception):Int{
        w=p.width; h=p.height; t=p.toroidal; o=p.marks; q=p.queue; g=p.newMark()
        val b=p.myBody; hd=b[0]
        val v=p.myScore
        val tl=b[b.size-1]
        bs=p.newMark(); ln=0
        val grew = if(p.previousReward==15f) 1 else 0
        if(v>98){
            ln=b.size
            repeat(ln){ p.newMark() }
        }
        if(p.newMark()<bs) return decide(p)
        val el=v>298
        if(el){
            for(e in p.enemies){
                for(c in e.body) o[c]=g
                for(d in 0..3) o[nx(e.body[0], d)]=g
                o[nx(e.body[0], e.direction)]=g
            }
        }
        val tw=p.turnsWithoutFood
        val hu=tw>700
        var tg=tl
        var bestCost=Int.MAX_VALUE
        for(i in p.foods.indices){
            val f=p.foods[i]
            val d=ds(hd,f)
            val rDist=p.foodRivalDistance(i)
            val cost = if(hu) d else d + if(rDist<d) 8+2*(d-rDist) else if(rDist==d) 2 else 0
            if(cost<bestCost){
                bestCost=cost; tg=f
            }
        }
        val rawCp = when{
            tw>1350 -> 1
            hu -> b.size/2+2
            else -> b.size + if(el) 40 else 8
        }
        val cp = if(rawCp > w*h) w*h else rawCp
        var bestReach=-1
        var bestEval=0
        var bestMove=p.validMoves[0]
        val md=p.myDirection
        for(m in p.validMoves){
            if(bestReach>=0 && p.nanosLeft()<=0) break
            val c=nx(hd,m)
            val isFood = c in p.foods
            gr=grew + if(isFood) 1 else 0
            val r=rm(b,c,cp,p.newMark())
            var s = if(isFood) 1 shl 20 else 0
            s -= (ds(c,tg) shl 10)
            if(!hu) s -= ds(c,tl)
            if(r<1) s -= 4000000
            if(m!=md) s -= 1
            if(el){
                var minDist=Int.MAX_VALUE
                for(e in p.enemies){
                    val dh=ds(c, e.body[0])
                    if(dh<minDist) minDist=dh
                }
                if(minDist<8) s -= (8-minDist) shl 15
            }
            if(r>bestReach || (r==bestReach && s>bestEval)){
                bestReach=r; bestEval=s; bestMove=m
            }
        }
        return bestMove
    }
}