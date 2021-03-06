package io.exoji2e.sugartop.settings

import android.content.Context
import com.takisoft.fix.support.v7.preference.EditTextPreference
import android.util.AttributeSet

class HiThresholdPreference(val c: Context, attrs: AttributeSet): EditTextPreference(c, attrs) {
    init{
        refresh()
    }
    fun refresh() {
        val value = UserData.get_hi_threshold(c).toString()
        text = value
        summary = "%s %s".format(value, UserData.get_unit(c))
    }
    override fun getPersistedString(defaultReturnValue: String?) : String {
        return UserData.get_hi_threshold(c).toString()
    }
    override fun persistString(value: String) : Boolean {
        val v = value.toFloat()/UserData.get_multiplier(c)
        if(v < 2 || v > 20) return false
        UserData.set_thresholds(c, UserData.get_low_threshold(c), value.toFloat())
        return true
    }
}