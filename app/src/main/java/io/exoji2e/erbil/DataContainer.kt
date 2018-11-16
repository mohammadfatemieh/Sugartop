package io.exoji2e.erbil

import android.content.Context
import android.util.Log
import io.exoji2e.erbil.database.*

class DataContainer {
    private val recent = mutableListOf<GlucoseEntry>()
    private val history = mutableListOf<GlucoseEntry>()
    val noH = 32
    val TAG = "DataContainer"
    private var mDb : ErbilDataBase? = null
    private var done : Boolean = false
    private var lastId : Int = -1
    private val lock = java.lang.Object()
    private var lastTimeStamp : Int = 0
    constructor(context : Context) {
        mDb = ErbilDataBase.getInstance(context)
        val glucoseData =
                mDb?.glucoseEntryDao()?.getAll()
        synchronized(lock){
            if (glucoseData == null || glucoseData.isEmpty()) {
                Log.d(TAG, "No data in db")
            } else {
                for(g: GlucoseEntry in glucoseData) {
                    if(g.history) history.add(g)
                    else recent.add(g)
                    lastId = Math.max(lastId, g.id)
                }
                history.sortBy { entry -> entry.utcTimeStamp }
                recent.sortBy { entry -> entry.utcTimeStamp }
                Log.d(TAG, "history contained %d items".format(history.size))
                Log.d(TAG, "recent contained %d items".format(recent.size))
            }
            done = true
            lock.notifyAll()
        }
    }
    private fun waitForDone() {
        synchronized(lock) {
            while(!done) {
                lock.wait()
            }
        }
    }
    fun append(raw_data: ByteArray, readingTime: Long, sensorId : Long) {

        val timestamp = RawParser.timestamp(raw_data)
        if(timestamp == 0) {
            mDb?.sensorContactDao()?.insert(SensorContact(0, readingTime, sensorId, 0, 0))
            return
        }
        // Timestamp is 2 mod 15 every time a new reading to history is done.
        val minutesSinceLast = (timestamp + 12)%15
        val start = readingTime - Time.MINUTE*(15*(noH - 1) + minutesSinceLast)
        val now_history = RawParser.history(raw_data)
        val now_recent = RawParser.recent(raw_data)

        val recent_prepared = prepare(now_recent, sensorId, recent, 1*Time.MINUTE, readingTime - 16*Time.MINUTE, timestamp < Time.DURATION_MINUTES)
        val history_prepared = prepare(now_history, sensorId, history, 15*Time.MINUTE, start, minutesSinceLast != 14 && timestamp < Time.DURATION_MINUTES)
        val added = extend(recent_prepared, recent) + extend(history_prepared, history)
        mDb?.sensorContactDao()?.insert(SensorContact(0, sensorId, readingTime, timestamp, added))
        lastTimeStamp = timestamp
        Log.d(TAG, String.format("recent_size %d", recent.size))
        Log.d(TAG, String.format("histroy_size %d", history.size))
    }

    fun insert(v: List<GlucoseEntry>) {
        waitForDone()
        synchronized(lock){
            if(history.size + recent.size != 0) return
            for(g in v) {
                if(g.history) history.add(g)
                else recent.add(g)
                mDb?.glucoseEntryDao()?.insert(g)
                lastId = Math.max(lastId, g.id)
            }
        }
        Log.d(TAG, String.format("inserted %d vales into database", v.size))
    }

    private fun extend(v: List<GlucoseReading>, into: MutableList<GlucoseEntry>) : Int {
        synchronized(lock) {
            val toExtend = v.filter { g: GlucoseReading -> g.status != 0 && g.value > 10 }
                    .mapIndexed { i: Int, g: GlucoseReading -> GlucoseEntry(g, lastId + 1 + i) }
            lastId += toExtend.size
            into.addAll(toExtend)
            for (r: GlucoseEntry in toExtend) {
                mDb?.glucoseEntryDao()?.insert(r)
            }
            Log.d(TAG, "Inserted into db!")
            return toExtend.size
        }
    }

    // Inspects last entry from the same sensor and filters out all that are already logged.
    private fun prepare(chunks : List<SensorChunk>,
                        sensorId : Long,
                        into: List<GlucoseEntry>,
                        dt : Long,
                        start : Long,
                        certain : Boolean) : List<GlucoseReading> {
        val lastRecent = last(sensorId, into)
        if(lastRecent != null) {
            var match = -1
            for (i in 0 until chunks.size) {
                if (lastRecent.eq(chunks[i])) {
                    match = i
                }
            }
            if(match > -1) {
                val range = IntRange(match + 1, chunks.size - 1)
                if(!certain)
                    return chunks.slice(range)
                            .mapIndexed { i: Int, chunk: SensorChunk ->
                                GlucoseReading(chunk,
                                        lastRecent.utcTimeStamp + (i + 1) * dt, sensorId)
                        }
                else {
                    return chunks.mapIndexed { i: Int, chunk: SensorChunk ->
                        GlucoseReading(chunk,
                                start + i * dt, sensorId)
                    }.slice(range)
                }
            }
        }
        return chunks.mapIndexed { i: Int, chunk: SensorChunk ->
            GlucoseReading(chunk, start + i * dt, sensorId)
        }
    }
    fun nice(g : GlucoseEntry) : Boolean = g.status == 200 && (g.value < 5000 && g.value > 10)
    fun get(after: Long, before : Long) : List<GlucoseEntry> {
        return get(after, before, true)
    }
    fun get(after: Long, before : Long, nice : Boolean) : List<GlucoseEntry> {
        waitForDone()
        synchronized(lock) {
            val v = (history.filter{r -> r.utcTimeStamp < before && r.utcTimeStamp > after} +
                    recent.filter{r -> r.utcTimeStamp < before && r.utcTimeStamp > after})
                    .sortedWith(compareBy({ it.utcTimeStamp }, { it.history }))
            if(!nice) return v
            else return v.filter{g -> nice(g)}

        }
    }

    private fun last() : Pair<GlucoseEntry, Int>? {
        var i = recent.size - 1
        while (i >= 0){
            val last = recent[i]
            i -= 1
            if (nice(last)) return Pair(last, i)
        }
        return null
    }
    fun guess() : Pair<GlucoseReading, GlucoseReading>? {
        waitForDone()
        synchronized(lock){
            val ret = last()
            if(ret == null) return null
            var i = ret.second
            val last = ret.first
            val last_as_reading = GlucoseReading(last.value, last.utcTimeStamp, last.sensorId, last.status, false, 0)
            var out = Pair(last_as_reading, last_as_reading)
            while (i >= 0) {
                val entry = recent[i]
                if(entry.utcTimeStamp + Time.MINUTE*5 < last.utcTimeStamp) return out
                if(entry.sensorId == last.sensorId && nice(entry)) {
                    val guess = last.value * 2 - entry.value
                    val time = last.utcTimeStamp * 2 - entry.utcTimeStamp
                    out = Pair(last_as_reading, GlucoseReading(guess,
                            time,
                            last.sensorId, last.status, false, 0))
                }
                i -= 1
            }
            return out
        }
    }
    fun lastTimeStamp() : Int {
        waitForDone()
        synchronized(lock){return lastTimeStamp}
    }
    // For merging readings (does not guarantee readings have a success status-code, etc).
    private fun last(sensorId: Long, v: List<GlucoseEntry>) : GlucoseEntry? {
        waitForDone()
        synchronized(lock) {
            val sz = v.size - 1
            val now = Time.now()
            for(i in 0 until v.size) {
                if(v[sz - i].sensorId == sensorId) return v[sz - i]
                if(now - Time.HOUR*24 > v[sz-i].utcTimeStamp) break
            }
            return null
        }
    }
    fun size() : Int {
        waitForDone()
        synchronized(lock) {
            return history.size + recent.size
        }
    }

    fun insertIntoDb(manual : ManualGlucoseEntry) {
        waitForDone()
        synchronized(lock) {
            mDb!!.manualEntryDao().insert(manual)
            Log.d(TAG, "inserted manual entry into db.")
            val v: List<ManualGlucoseEntry> = mDb!!.manualEntryDao().getAll()
            Log.d(TAG, String.format("table size: %d", v.size))
        }
    }
    companion object {
        private var INSTANCE : DataContainer? = null
        fun getInstance(context : Context) : DataContainer {
            if (INSTANCE == null) {
                synchronized(DataContainer::class) {
                    if(INSTANCE == null)
                        INSTANCE = DataContainer(context)
                }
            }
            return this.INSTANCE!!
        }
    }

}