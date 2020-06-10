package com.simplemobiletools.calendar.pro

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.multidex.MultiDexApplication
import com.simplemobiletools.calendar.pro.fragments.MonthFragment
import com.simplemobiletools.commons.extensions.checkUseEnglish
import org.joda.time.DateTime

class App : Application() {
    var dateTimeFromCode: DateTime?=null
    override fun onCreate() {
        super.onCreate()
        sInstance = this
        checkUseEnglish()
    }

    companion object {
        private lateinit var sInstance: App
        fun getInstance(): App {
            return sInstance
        }
    }

    fun setDateTime(dateTimeFromCode: DateTime) {
        this.dateTimeFromCode=dateTimeFromCode
   /*     var intent = Intent()
        intent.action = "events"
        LocalBroadcastManager.getInstance(getInstance())
                .sendBroadcast(intent)*/
    }
}
