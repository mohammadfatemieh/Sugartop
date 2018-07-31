package io.exoji2e.erbil

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import com.github.mikephil.charting.data.Entry
import java.util.*

class ResultActivity : AppCompatActivity() {
    private var dc : DataContainer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        dc = DataContainer.getInstance(this)
        showLayout()
    }
    private fun fmt(str: String, readVal: Int) =
            String.format("%s %d %.2f", str, readVal, RawParser.sensor2mmol(readVal))

    private fun showLayout() {
        val raw_data = dc!!.dump()
        val predict = RawParser.guess(raw_data)
        val last = RawParser.last(raw_data)
        val timeStamp = RawParser.timestamp(raw_data)
        TimeLeftTV.text = String.format("Time left %s %d", Time.timeLeft(timeStamp), timeStamp)
        DebugTV.text = String.format("%s\n%s\n%s", fmt(resources.
                getString(R.string.history), RawParser.history(raw_data)[31].value),
                fmt(resources.
                getString(R.string.recent), last),
                fmt(resources.
                getString(R.string.guess), predict))

        val readings = dc!!.get8h()
        val avg = Compute.avg(readings)
        val percentInside = Compute.inGoal(4.0, 8.0, readings)*100
        val values = ArrayList<Entry>()
        val now = Time.now()
        val first : Long = if (readings.isEmpty()) now else readings[0].utcTimeStamp
        values.addAll(readings.map{r -> Entry((r.utcTimeStamp - first).toFloat(), r.tommol().toFloat()) })
        values.add(Entry((now - first).toFloat(), RawParser.sensor2mmol(last).toFloat()))
        values.add(Entry((now + 5*Time.MINUTE - first).toFloat(), RawParser.sensor2mmol(predict).toFloat()))
        Push2Plot.setPlot(values, graph, first)
        ingData.text = String.format("%.1f %s", percentInside, "%")
        avgData.text = String.format("%.1f", avg)
        recentData.text = String.format("%.1f", RawParser.sensor2mmol(predict))
    }
}
