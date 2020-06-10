import com.simplemobiletools.calendar.pro.ashish.calendarmodel.BaseCalendarModel
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface Api {


    @GET("calendertype/{year}/{month}")
    fun getCalendarEvents(
        @Path("year") year: String?, @Path("month") month: String?
    ): Call<BaseCalendarModel>


}
