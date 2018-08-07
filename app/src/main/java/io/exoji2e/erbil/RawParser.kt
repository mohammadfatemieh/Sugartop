package io.exoji2e.erbil

class RawParser {
    companion object {
        fun bin2int(a: Byte, b: Byte) : Int = (byte2uns(a) shl 8) or byte2uns(b)
        fun bin2int(a: Byte, b: Byte, c: Byte) : Int = (byte2uns(a) shl 16) or (byte2uns(b) shl 8) or byte2uns(c)
        fun byte2uns(a: Byte) : Int = (a.toInt() + 256)%256
        fun bin2long(b: ByteArray) : Long {
            var r = 0L
            for (i in 0..7) {
                r = r shl 8
                r = r or byte2uns(b[i]).toLong()
            }
            return r
        }

        // TODO: should use user-function instead.
        fun sensor2mmol(v: Int) : Double = v*0.0062492 - 1.89978

        private fun deflatten(bytes : ByteArray) : List<SensorData> {
            return bytes
                    .toList()
                    .windowed(6, 6,false, {list -> SensorData(list.toByteArray())})
        }
        private fun chunk(bytes: ByteArray, history: Boolean) : List<SensorChunk> {
            return bytes
                    .toList()
                    .windowed(6, 6,false, {list -> SensorChunk(list.toByteArray(), history)})
        }

        // each sample contains 6 bytes. History consists of 32 samples in a cyclical buffer
        // spanning the last 8 hours (4 samples per hour). index 27 of data tells us where in the
        // buffer, the next value will be written. The buffer starts at index 124.
        fun history(data: ByteArray): List<SensorData> {
            val (iH, startH) = Pair(data[27], 124)
            val flat_history = data.sliceArray(IntRange(startH + iH*6, startH + 32*6 - 1)).
                    plus(data.sliceArray(IntRange(startH, startH + iH*6 - 1)))
            return deflatten(flat_history)
        }
        fun historySensorChunk(data: ByteArray): List<SensorChunk> {
            val (iH, startH) = Pair(data[27], 124)
            val flat_history = data.sliceArray(IntRange(startH + iH*6, startH + 32*6 - 1)).
                    plus(data.sliceArray(IntRange(startH, startH + iH*6 - 1)))
            return chunk(flat_history, true)
        }

        // Similar to history, but only stores 16 values, from the last 16 minutes.
        fun recent(data: ByteArray): List<SensorData> {
            val (iR, startR) = Pair(data[26], 28)
            val flat_recent = data.sliceArray(IntRange(startR + iR*6, startR + 16*6 - 1)).
                    plus(data.sliceArray(IntRange(startR, startR + iR*6 - 1)))
            return deflatten(flat_recent)
        }

        fun recentSensorChunk(data: ByteArray): List<SensorChunk> {
            val (iR, startR) = Pair(data[26], 28)
            val flat_recent = data.sliceArray(IntRange(startR + iR*6, startR + 16*6 - 1)).
                    plus(data.sliceArray(IntRange(startR, startR + iR*6 - 1)))
            return chunk(flat_recent, false)
        }

        fun last(data: ByteArray) : Int {
            val recent = recent(data)
            return recent[15].value
        }
        fun guess(data: ByteArray) : Int {
            val recent = recent(data)
            val now = recent[15].value
            val prev = recent[10].value
            return now*2 - prev
        }
        fun timestamp(data: ByteArray) : Int {
            return bin2int(data[317], data[316])
        }
    }
}