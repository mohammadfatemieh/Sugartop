package io.exoji2e.erbil

import io.exoji2e.erbil.database.GlucoseEntry

class Compute {
    companion object {
        fun avg(data : List<GlucoseEntry>, sd: SensorData) : Double {
            if(data.isEmpty()) return 0.0
            if(data.size == 1) return data[0].tommol(sd)
            var sum = 0.0
            for (i in 1..data.size - 1) {
                val tdiff = data[i].utcTimeStamp - data[i-1].utcTimeStamp
                sum += (data[i-1].tommol(sd)+ data[i].tommol(sd))/2.0*tdiff
            }
            return sum/(data.last().utcTimeStamp - data[0].utcTimeStamp)
        }
        fun inGoal(lo: Double, hi : Double, data: List<GlucoseEntry>, sd: SensorData) : String {
            val v = _inGoal(lo, hi, data, sd)*100
            val res = String.format("%.1f", v)
            val ret =  if(res.equals("100.0")) "100" else res
            return String.format("%s %s", ret, "%")
        }
        fun occurenciesBelow(lo: Double, bound: Double, data : List<GlucoseEntry>, sd: SensorData) : Int {
            var occs = 0
            var last = false
            for(g in data){
                val below = sd.sensor2mmol(g.value, g.sensorId) < lo
                val conf = sd.sensor2mmol(g.value, g.sensorId) < bound
                if(below && !last) occs += 1
                last = (last && conf) || below // We have get above bound to reset.
            }
            return occs
        }
        private fun _inGoal(lo: Double, hi : Double, data: List<GlucoseEntry>, sd: SensorData) : Double {
            if(data.isEmpty()) return 0.0
            if(data.size == 1) {
                val v = data[0].tommol(sd)
                if(v < lo || hi < v) return 0.0
                return 1.0
            }
            var sum = 0.0
            for (i in 1..data.size - 1) {
                val tdiff = data[i].utcTimeStamp - data[i-1].utcTimeStamp
                val (v0, v1) = Pair(data[i-1].tommol(sd), data[i].tommol(sd))
                val min = Math.min(v0, v1)
                val max = Math.max(v0, v1)
                if (max <= lo) continue
                if (min >= hi) continue
                var (ts, tf) = Pair(0.0, tdiff.toDouble())
                if (min < lo) {
                    ts = (lo - min)*tdiff / (max - min)
                }
                if (max > hi) {
                    tf = tdiff*(1 - (max - hi)/(max - min))
                }
                sum += tf - ts
            }
            return sum/(data.last().utcTimeStamp - data[0].utcTimeStamp)
        }
    }
}