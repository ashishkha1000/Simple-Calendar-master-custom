package com.simplemobiletools.calendar.pro.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.Gson
import com.simplemobiletools.calendar.pro.App
import com.simplemobiletools.calendar.pro.R
import com.simplemobiletools.calendar.pro.activities.MainActivity
import com.simplemobiletools.calendar.pro.ashish.calendarmodel.BaseCalendarModel
import com.simplemobiletools.calendar.pro.ashish.calendarmodel.Data
import com.simplemobiletools.calendar.pro.extensions.*
import com.simplemobiletools.calendar.pro.helpers.*
import com.simplemobiletools.calendar.pro.helpers.Formatter
import com.simplemobiletools.calendar.pro.interfaces.MonthlyCalendar
import com.simplemobiletools.calendar.pro.interfaces.NavigationListener
import com.simplemobiletools.calendar.pro.models.Attendee
import com.simplemobiletools.calendar.pro.models.DayMonthly
import com.simplemobiletools.calendar.pro.models.Event
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.isVisible
import com.simplemobiletools.commons.extensions.toInt
import com.simplemobiletools.commons.extensions.value
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.MyAutoCompleteTextView
import kotlinx.android.synthetic.main.activity_event.*
import kotlinx.android.synthetic.main.fragment_month.view.*
import kotlinx.android.synthetic.main.top_navigation.view.*
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class MonthFragment : Fragment(), MonthlyCalendar {
    private var monthInt: Int=0
    private var mTextColor = 0
    private var mSundayFirst = false
    private var mShowWeekNumbers = false
    private var mDayCode = ""
    private var mPackageName = ""
    private var mLastHash = 0L
    private var mCalendar: MonthlyCalendarImpl? = null

    var listener: NavigationListener? = null

    lateinit var mRes: Resources
    lateinit var mHolder: RelativeLayout
    lateinit var mConfig: Config

    private lateinit var mEvent: Event
    private lateinit var mEventStartDateTime: DateTime
    private lateinit var mEventEndDateTime: DateTime
    private var mOriginalTimeZone = DateTimeZone.getDefault().id
    private var mEventCalendarId = STORED_LOCALLY_ONLY
    private var mEventTypeId = REGULAR_EVENT_TYPE_ID
    private var mSelectedContacts = ArrayList<Attendee>()
    private var mAttendeeAutoCompleteViews = ArrayList<MyAutoCompleteTextView>()
    private var mAvailableContacts = ArrayList<Attendee>()
    private var mRepeatInterval = 0
    companion object{
        private var listOfMonthFetchFromApi = ArrayList<String>()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_month, container, false)
        mRes = resources
        mPackageName = activity!!.packageName
        mHolder = view.month_calendar_holder
        mDayCode = arguments!!.getString(DAY_CODE)!!
        mConfig = context!!.config
        storeStateVariables()

        setupButtons()
        mCalendar = MonthlyCalendarImpl(this, context!!)
        return view
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onResume() {
        super.onResume()
        Log.d("TAG","check on resume called ak: 1")
        if (mConfig.showWeekNumbers != mShowWeekNumbers) {
            mLastHash = -1L
        }

        mCalendar!!.apply {
            mTargetDate = Formatter.getDateTimeFromCode(mDayCode)
            getDays(false)    // prefill the screen asap, even if without events
        }

        storeStateVariables()
        updateCalendar()
    }

    private fun storeStateVariables() {
        Log.d("TAG","check on resume called ak: 2")
        mConfig.apply {
            mSundayFirst = isSundayFirst
            mShowWeekNumbers = showWeekNumbers
        }
    }

    fun updateCalendar() {
        Log.d("TAG","check on resume called ak: 3")
        mCalendar?.updateMonthlyCalendar(Formatter.getDateTimeFromCode(mDayCode))
    }

    override fun updateMonthlyCalendar(context: Context, month: String, days: ArrayList<DayMonthly>, checkedEvents: Boolean, currTargetDate: DateTime) {
        Log.d("TAG","check on resume called ak: 4")
        val newHash = month.hashCode() + days.hashCode().toLong()
        if ((mLastHash != 0L && !checkedEvents) || mLastHash == newHash) {
            return
        }

        mLastHash = newHash
        Log.d("TAG","check on resume called ak: 5")
        activity?.runOnUiThread {
            mHolder.top_value.apply {
                text = month
                contentDescription = text
                setTextColor(mConfig.textColor)
            }
            Log.d("TAG","check on resume called ak: 6")
            updateDays(days)
        }
        Log.d("TAG","check on resume called ak: 7")
    }

    private fun setupButtons() {
        mTextColor = mConfig.textColor

        mHolder.top_left_arrow.apply {
            applyColorFilter(mTextColor)
            background = null
            setOnClickListener {
                listener?.goLeft()
            }

            val pointerLeft = context!!.getDrawable(R.drawable.ic_chevron_left_vector)
            pointerLeft?.isAutoMirrored = true
            setImageDrawable(pointerLeft)
        }

        mHolder.top_right_arrow.apply {
            applyColorFilter(mTextColor)
            background = null
            setOnClickListener {
                listener?.goRight()
            }

            val pointerRight = context!!.getDrawable(R.drawable.ic_chevron_right_vector)
            pointerRight?.isAutoMirrored = true
            setImageDrawable(pointerRight)
        }

        mHolder.top_value.apply {
            setTextColor(mConfig.textColor)
            setOnClickListener {
                (activity as MainActivity).showGoToDateDialog()
            }
        }
    }

    private fun updateDays(days: ArrayList<DayMonthly>) {
        mHolder.month_view_wrapper.updateDays(days) {

            (activity as MainActivity).openDayFromMonthly(Formatter.getDateTimeFromCode(it.code))
        }



    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)


        callAPI()

        var intentFilter = IntentFilter()
        intentFilter.addAction("events")
        LocalBroadcastManager.getInstance(activity!!.applicationContext)
                .registerReceiver(broadCastReceiver, intentFilter)
    }

    val broadCastReceiver = object : BroadcastReceiver() {
        override fun onReceive(contxt: Context?, intent: Intent?) {
            val action = intent!!.action
            if (action != null && action.equals("events")) {
            }
        }
    }


     fun callAPI() {
//        val baseActivity = context as? BaseActivity<*>

        //get month value
        var date = Formatter.getDateTimeFromCode(mDayCode).toDate()
        var localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        monthInt=localDate.monthValue
         Log.d("TAG","check month int:"+listOfMonthFetchFromApi.contains(localDate.monthValue.toString())+"___"+(localDate.monthValue.toString()))
         if(!listOfMonthFetchFromApi.contains(localDate.monthValue.toString()))
         {
             listOfMonthFetchFromApi.add(localDate.monthValue.toString())
         }
         else{
             return
         }

        RetrofitClient.instance.getCalendarEvents(Formatter.getDateTimeFromCode(mDayCode).year.toString(), localDate.monthValue.toString())
                .enqueue(object : Callback<BaseCalendarModel> {
                    override fun onFailure(call: Call<BaseCalendarModel>, t: Throwable) {
                    }

                    override fun onResponse(
                            call: Call<BaseCalendarModel>,
                            response: Response<BaseCalendarModel>
                    ) {
                        var baseCalendarModel: BaseCalendarModel? = response.body()
                        fromStartToEndDateGetEventInDays(baseCalendarModel!!.data)
                        Handler().postDelayed(Runnable {
                            onResume()
                        },10000)
                    }
                })
    }

    private fun fromStartToEndDateGetEventInDays(data: List<Data>) {
        var temp: DateTime? = null
        for(singleData in data){
            for(i in 0..getTotalDaysCount(singleData.start_date,singleData.end_date)){

                if (i.equals(0.toLong())) {
                    temp = DateTime(
                            convertStringToDateAndReturnWithoutFormatted(singleData.start_date)
                    )
                } else {
                    temp = temp!!.plusDays(1)
                }

                var dateFromTemp = DateFormat.format("dd", temp!!.toDate())

                //set index
                /*var calDate = Calendar.getInstance()
                if(singleData.start_time!=null)
                    calDate.time= convertStringToTimeAndReturn(singleData.start_time)*/


                var cal = Calendar.getInstance()
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND,0)
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.HOUR,4);
                cal.set(Calendar.AM_PM, Calendar.PM);
                cal.set(Calendar.MONTH, (monthInt-1));
                cal.set(Calendar.DAY_OF_MONTH, dateFromTemp.toInt());
                cal.set(Calendar.YEAR, Formatter.getDateTimeFromCode(mDayCode).year);

                var dt = DateTime(cal.time)
                temp=dt
                Log.d("TAG ","event start end time1:"+temp+"__"+singleData.name)

                saveEvent(singleData.name,temp)

            }
        }
    }

    private fun saveEvent(name: String, temp: DateTime) {
        val newTitle = name
        val offset = if (!mConfig.allowChangingTimeZones || mEvent.getTimeZoneString().equals(mOriginalTimeZone, true)) {
            0
        } else {
            val original = if (mOriginalTimeZone.isEmpty()) DateTimeZone.getDefault().id else mOriginalTimeZone
            (DateTimeZone.forID(mEvent.getTimeZoneString()).getOffset(System.currentTimeMillis()) - DateTimeZone.forID(original).getOffset(System.currentTimeMillis())) / 1000L
        }

        mEventStartDateTime=temp
        mEventEndDateTime=temp

        val newStartTS = mEventStartDateTime.withSecondOfMinute(0).withMillisOfSecond(0).seconds() - offset
        val newEndTS = mEventEndDateTime.withSecondOfMinute(0).withMillisOfSecond(0).seconds() - offset

        Log.d("TAG ","event start end time2:"+temp+"__"+mEventStartDateTime+"___"+mEventStartDateTime.millis+"___"+newEndTS+"___"+newTitle)

        Log.d("TAG","check date with name2:"+temp+"__"+newTitle)

      /*  if (newStartTS > newEndTS) {
//            toast(R.string.end_before_start)
            return
        }*/


        val newEventType = if (!mConfig.caldavSync || mConfig.lastUsedCaldavCalendarId == 0 || mEventCalendarId == STORED_LOCALLY_ONLY) {
            mEventTypeId
        } else {
            context!!.calDAVHelper.getCalDAVCalendars("", true).firstOrNull { it.id == mEventCalendarId }?.apply {
                if (!canWrite()) {
                 /*   runOnUiThread {
                        toast(R.string.insufficient_permissions)
                    }*/
                    return
                }
            }

            context!!.eventsHelper.getEventTypeWithCalDAVCalendarId(mEventCalendarId)?.id ?: mConfig.lastUsedLocalEventTypeId
        }

        mEvent = Event(null)
        mEvent.apply {
            startTS = newStartTS
            endTS = newStartTS
            title = newTitle
            description = ""
            reminder1Minutes = 0
            reminder2Minutes = 0
            reminder3Minutes = 0
            reminder1Type = 0
            reminder2Type = 0
            reminder3Type = 0
            repeatInterval = 0
            importId = ""
            timeZone = ""
            flags = 0
            repeatLimit = 0
            repeatRule = 0
            attendees = if (mEventCalendarId == STORED_LOCALLY_ONLY) "" else getAllAttendees(true)
            eventType = newEventType
            lastUpdated = System.currentTimeMillis()
            source = ""
            location = ""
        }

        // recreate the event if it was moved in a different CalDAV calendar
        /*if (mEvent.id != null && oldSource != newSource) {
            eventsHelper.deleteEvent(mEvent.id!!, true)
            mEvent.id = null
        }*/

        storeEvent(false)
    }


    private fun getTotalDaysCount(startDate: String, endDate: String): Long {

        var dateStartTime:Date?=null
        if(startDate!=null)
            dateStartTime=convertStringToDateAndReturnWithoutFormatted(startDate)

        var dateEndTime:Date?=null
        if(endDate!=null)
            dateEndTime=convertStringToDateAndReturnWithoutFormatted(endDate)

        var diff:Long
        if(dateEndTime!=null)
            diff = dateEndTime!!.time - dateStartTime!!.time
        else
            diff = dateStartTime!!.time - dateStartTime!!.time

        var count = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)
        if (count.equals(0.toLong())) {
            return 0.toLong()
        } else {
            return TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)
        }
    }

    fun convertStringToDateAndReturnWithoutFormatted(stringDate: String?): Date {
        //convert string to date
        var sdfParsing = SimpleDateFormat("yyyy-MM-dd")
        var dateObj = sdfParsing.parse(stringDate)

        return dateObj
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(activity!!.applicationContext)
                .unregisterReceiver(broadCastReceiver)
    }
    private fun getAllAttendees(isSavingEvent: Boolean): String {
        var attendees = ArrayList<Attendee>()
        mSelectedContacts.forEach {
            attendees.add(it)
        }

        val customEmails = mAttendeeAutoCompleteViews.filter { it.isVisible() }.map { it.value }.filter { it.isNotEmpty() }.toMutableList() as ArrayList<String>
        customEmails.mapTo(attendees) {
            Attendee(0, "", it, CalendarContract.Attendees.ATTENDEE_STATUS_INVITED, "", false, CalendarContract.Attendees.RELATIONSHIP_NONE)
        }
        attendees = attendees.distinctBy { it.email }.toMutableList() as ArrayList<Attendee>

        if (mEvent.id == null && isSavingEvent && attendees.isNotEmpty()) {
            val currentCalendar = context!!.calDAVHelper.getCalDAVCalendars("", true).firstOrNull { it.id == mEventCalendarId }
            mAvailableContacts.firstOrNull { it.email == currentCalendar?.accountName }?.apply {
                attendees = attendees.filter { it.email != currentCalendar?.accountName }.toMutableList() as ArrayList<Attendee>
                status = CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED
                relationship = CalendarContract.Attendees.RELATIONSHIP_ORGANIZER
                attendees.add(this)
            }
        }

        return Gson().toJson(attendees)
    }

    private fun storeEvent(wasRepeatable: Boolean) {
        if (mEvent.id == null || mEvent.id == null) {
            context!!.eventsHelper.insertEvent(mEvent, true, true) {
                if (DateTime.now().isAfter(mEventStartDateTime.millis)) {
                    if (mEvent.repeatInterval == 0 && mEvent.getReminders().any { it.type == REMINDER_NOTIFICATION }) {
//                        context!!.notifyEvent(mEvent)
                    }
                }

//                finish()
            }
        } else {
            if (mRepeatInterval > 0 && wasRepeatable) {
             /*   runOnUiThread {
                    showEditRepeatingEventDialog()
                }*/
            } else {
                context!!.eventsHelper.updateEvent(mEvent, true, true) {
//                    finish()
                }
            }
        }
    }
    fun convertStringToTimeAndReturn(stringTime: String?): Date {
        //convert string to time
        var sdfParsing = SimpleDateFormat("hh:mm:ss")
        var date= sdfParsing.parse(stringTime)
        return date
        /*//format time
        var sdFormat = SimpleDateFormat("hh:mm aa")
        val formattedTime = sdFormat.format(timeObj)
        return formattedTime*/
    }

}
