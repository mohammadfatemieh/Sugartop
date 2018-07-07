package io.exoji2e.erbil

class RawParser {
    companion object {
        fun bin2int(a: Byte, b: Byte) : Int = byte2uns(a)*256 + byte2uns(b)

        fun byte2uns(a: Byte) : Int = (a.toInt() + 256)%256

        fun sensor2mmol(v: Int) : Double = v/200.0

        private fun deflatten(bytes : ByteArray) : Array<ByteArray> {
            return Array(bytes.size/6, init = {i -> bytes.sliceArray(IntRange(i*6, (i+1)*6 - 1))})
        }

        // each sample contains 6 bytes. History consists of 32 samples in a cyclical buffer
        // spanning the last 8 hours (4 samples per hour). index 27 of data tells us where in the
        // buffer, the next value will be written. The buffer starts at index 124.
        fun history(data: ByteArray): Array<ByteArray> {
            val (iH, startH) = Pair(data[27], 124)
            val flat_history = data.sliceArray(IntRange(startH + iH*6, startH + 32*6 - 1)).
                    plus(data.sliceArray(IntRange(startH, startH + iH*6 - 1)))
            return deflatten(flat_history)
        }

        // Similar to history, but only stores 16 values, from the last 16 minutes.
        fun recent(data: ByteArray): Array<ByteArray> {
            val (iR, startR) = Pair(data[26], 28)
            val flat_recent = data.sliceArray(IntRange(startR + iR*6, startR + 16*6 - 1)).
                    plus(data.sliceArray(IntRange(startR, startR + iR*6 - 1)))
            return deflatten(flat_recent)
        }

        fun last(data: ByteArray) : Int {
            val recent = recent(data)
            return bin2int(recent[15][1], recent[15][0])
        }
    }
}