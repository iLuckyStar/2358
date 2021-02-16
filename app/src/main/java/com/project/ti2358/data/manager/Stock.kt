package com.project.ti2358.data.manager

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.*
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.data.model.dto.MarketInstrument
import com.project.ti2358.data.model.dto.YahooResponse
import com.project.ti2358.data.service.MarketService
import com.project.ti2358.data.service.SettingsManager
import com.project.ti2358.service.Utils
import com.project.ti2358.service.log
import com.project.ti2358.service.toString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs


@KoinApiExtension
data class Stock(
    var marketInstrument: MarketInstrument
) : KoinComponent {
    val strategy1728: Strategy1728 by inject()
    val strategyTazik: StrategyTazik by inject()
    val strategyRocket: StrategyRocket by inject()

    private val marketService: MarketService by inject()
    private val stockManager: StockManager by inject()

    var middlePrice: Double = 0.0
    var dayVolumeCash: Double = 0.0

    var price1000: Double = 0.0             // цена открытия премаркета РФ
    var priceNow: Double = 0.0              // текущая цена
    var priceTazik: Double = 0.0            // цена для утреннего тазика
    var pricePostmarketUS: Double = 0.0     // цена закрытия постмаркета US

    var changeOnStartTimer: Double = 0.0    // сколько % было на старте таймера для 2358

    var yahooPostmarket: YahooResponse? = null
    var candleWeek: Candle? = null                               // недельная свеча
    var candleYesterday: Candle? = null                          // вчерашняя свеча (есть не всегда)
    var candle1000: Candle? = null                               // реалтайм, дневная свеча
    var candle2359: Candle? = null                               // цена закрытия 2359, минутная свеча
    var minute1728Candles: MutableList<Candle> = mutableListOf() // все свечи после 1728

    // разница с ценой открытия премаркета
    var changePriceDayAbsolute: Double = 0.0
    var changePriceDayPercent: Double = 0.0

    // разница с ценой закрытия постмаркет US
    var changePricePostmarketAbsolute: Double = 0.0
    var changePricePostmarketPercent: Double = 0.0

    // разница с ценой закрытия ОС
    var changePrice2359DayAbsolute: Double = 0.0
    var changePrice2359DayPercent: Double = 0.0

    // разница со старта таймера
    var changePrice1728DayAbsolute: Double = 0.0
    var changePrice1728DayPercent: Double = 0.0

    // все минутные свечи с момента запуска приложения
    var minuteCandles: MutableList<Candle> = mutableListOf()

    private val gson = Gson()

    fun processCandle(candle: Candle) {
        when (candle.interval) {
            Interval.DAY -> {
                processDayCandle(candle)
            }
            Interval.MINUTE -> {
                processMinuteCandle(candle)
            }
            Interval.WEEK -> {
                processWeekCandle(candle)
            }
            else -> {

            }
        }
    }

    private fun processDayCandle(candle: Candle) {
        val diffInMilli: Long = Calendar.getInstance().time.time - candle.time.time
        val diffInHours: Long = TimeUnit.MILLISECONDS.toHours(diffInMilli)

        if (diffInHours > 24) return

        if (diffInHours > 20) {
            candleYesterday = candle
        } else {
            candle1000 = candle
        }

        updateChangeToday()
        updateChange2359()
        updateChangePostmarket()

        strategyTazik.processStrategy(this)
        strategyRocket.processStrategy(this)
    }

    private fun processWeekCandle(candle: Candle) {
        candleWeek = candle
        priceNow = candle.closingPrice
        price1000 = priceNow

        stockManager.unsubscribeStock(this, Interval.WEEK)

        updateChange2359()
        updateChangePostmarket()
    }

    private fun processMinuteCandle(candle: Candle) {
        var exists = false
        for ((index, c) in minuteCandles.withIndex()) {
            if (c.time == candle.time) {
                minuteCandles[index] = candle
                exists = true
            }
        }
        if (!exists) {
            minuteCandles.add(candle)
        }

        // проверка на стратегию 1728
        val timeCandle = Calendar.getInstance()
        timeCandle.time = candle.time
        val timeTrackStart = strategy1728.strategyStartTime

        if (timeCandle.time >= timeTrackStart.time) {
            exists = false
            for ((index, c) in minute1728Candles.withIndex()) {
                if (c.time == candle.time) {
                    minute1728Candles[index] = candle
                    exists = true
                }
            }
            if (!exists) {
                minute1728Candles.add(candle)
            }
        }

        updateChange1728()
    }

    fun getVolume1728BeforeStart(): Int {
        return getTodayVolume() - getVolume1728AfterStart()
    }

    fun getVolume1728AfterStart(): Int {
        var volume = 0
        for (candle in minute1728Candles) {
            volume += candle.volume
        }
        return volume
    }

    fun getTodayVolume(): Int {
        return candle1000?.volume ?: 0
    }

    fun getPostmarketVolume(): Int {
        return yahooPostmarket?.getVolumeShares() ?: 0
    }

    fun getPostmarketVolumeCash(): Int {
        return yahooPostmarket?.getVolumeCash() ?: 0
    }

    fun getPriceDouble(): Double {
        if (minuteCandles.isNotEmpty()) {
            return minuteCandles.last().closingPrice
        }

        candle1000?.let {
            return it.closingPrice
        }

        candleWeek?.let {
            return it.closingPrice
        }

        candle2359?.let {
            return it.closingPrice
        }

        yahooPostmarket?.let {
            return it.getLastPrice()
        }

        return 0.0
    }

    fun getPricePostmarketUSDouble(): Double {
        yahooPostmarket?.let {
            return it.getLastPrice()
        }

        return 0.0
    }
    fun getPriceString(): String {
        val price = getPriceDouble()
        return "$price$"
    }

    fun getPrice1000String(): String {
        candle2359?.let {
            return "${it.openingPrice}$"
        }
        return "0$"
    }

    fun getPrice2359String(): String {
        candle2359?.let {
            return "${it.closingPrice}$"
        }
        return "0$"
    }

    fun getPrice1728String(): String {
        if (minute1728Candles.isNotEmpty()) {
            return "${minute1728Candles.first().closingPrice}$"
        }
        return "0$"
    }

    private fun updateChangeToday() {
        candle1000?.let {
            changePriceDayAbsolute = it.closingPrice - it.openingPrice
            changePriceDayPercent = (100 * it.closingPrice) / it.openingPrice - 100

            middlePrice = (it.highestPrice + it.lowestPrice ) / 2.0
            dayVolumeCash = middlePrice * it.volume

            price1000 = it.openingPrice
            priceNow = it.closingPrice
        }
    }

    private fun updateChangePostmarket() {
        yahooPostmarket?.let {
            candle2359?.let { close ->
                changePricePostmarketAbsolute = it.getLastPrice() - close.closingPrice
                changePricePostmarketPercent = (100 * it.getLastPrice()) / close.closingPrice - 100
            }

            candleWeek?.let { week ->
                changePricePostmarketAbsolute = it.getLastPrice() - week.closingPrice
                changePricePostmarketPercent = (100 * it.getLastPrice()) / week.closingPrice - 100
            }

            candle1000?.let { today ->
                changePricePostmarketAbsolute = it.getLastPrice() - today.closingPrice
                changePricePostmarketPercent = (100 * it.getLastPrice()) / today.closingPrice - 100
            }
        }
    }

    private fun updateChange2359() {
        candle2359?.let {
            candleWeek?.let { week ->
                changePrice2359DayAbsolute = week.closingPrice - it.closingPrice
                changePrice2359DayPercent = (100 * week.closingPrice) / it.closingPrice - 100
            }

            candle1000?.let { today ->
                changePrice2359DayAbsolute = today.closingPrice - it.closingPrice
                changePrice2359DayPercent = (100 * today.closingPrice) / it.closingPrice - 100
            }
        }
    }

    private fun updateChange1728() {
        if (minute1728Candles.isNotEmpty()) {
            candleWeek?.let { week ->
                changePrice1728DayAbsolute = week.closingPrice - minute1728Candles.first().openingPrice
                changePrice1728DayPercent = (100 * week.closingPrice) / minute1728Candles.first().openingPrice - 100
            }
        }
    }

    fun reset1728() {
        changePrice1728DayAbsolute = 0.0
        changePrice1728DayPercent = 0.0
        minute1728Candles.clear()
    }

    fun loadClosingPostmarketRUCandle(prevDelay: Long): Long {
        if (candleWeek != null) return prevDelay

        val zone = getCurrentTimezone()
        val dayBack = -5
        val dayFront = 1
        var from = getLastClosingPostmarketRUDate(dayBack) + zone
        var to = getLastClosingPostmarketRUDate(dayFront) + zone

        val key = "price_postmarket_ru_${marketInstrument.figi}_${from}"

        val preferences = PreferenceManager.getDefaultSharedPreferences(SettingsManager.context)
        val jsonClosingCandle = preferences.getString(key, null)
        if (jsonClosingCandle != null) {
            candleWeek = gson.fromJson(jsonClosingCandle, Candle::class.java)
            updateChangePostmarket()
            updateChange2359()
            return prevDelay
        }

        val delay = prevDelay + kotlin.random.Random.Default.nextLong(1000, 1500)
        GlobalScope.launch(Dispatchers.Main) {
            while (candleWeek == null) {
                try {
                    delay(delay)
                    if (candleWeek != null) return@launch

                    val value = preferences.getString(key, null)
                    if (value != null) return@launch

                    log("close ${marketInstrument.ticker}")
                    val candles = marketService.candles(marketInstrument.figi, "day", from, to)

                    if (candles.candles.isNotEmpty()) {
                        candleWeek = candles.candles.last()
                        val data = gson.toJson(candleWeek)

                        val editor: SharedPreferences.Editor = preferences.edit()
                        editor.putString(key, data)
                        editor.apply()

                        updateChange2359()

                        log("closing price postmarket ru ${marketInstrument.ticker}")
                        return@launch
                    } else { // если свечей нет, то сделать шаг назад во времени
                        from = getLastClosingPostmarketRUDate(dayBack) + zone
                        to = getLastClosingPostmarketRUDate(dayFront) + zone
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(delay)
            }
        }
        return delay
    }

    fun loadClosingOSCandle(prevDelay: Long): Long {
        if (candle2359 != null) return prevDelay

        val zone = getCurrentTimezone()
        var from = getLastClosingDate(true) + zone
        var to = getLastClosingDate(false) + zone

        val key = "closing_os_${marketInstrument.figi}_${from}"

        val preferences = PreferenceManager.getDefaultSharedPreferences(SettingsManager.context)
        val jsonClosingCandle = preferences.getString(key, null)
        if (jsonClosingCandle != null) {
            candle2359 = gson.fromJson(jsonClosingCandle, Candle::class.java)
            updateChangePostmarket()
            updateChange2359()
            return prevDelay
        }

        var deltaDay = 0

        val delay = prevDelay + kotlin.random.Random.Default.nextLong(1000, 1500)
        GlobalScope.launch(Dispatchers.Main) {
            while (candle2359 == null) {
                try {
                    delay(delay)
                    if (candle2359 != null) return@launch

                    val value = preferences.getString(key, null)
                    if (value != null) return@launch

                    log("close ${marketInstrument.ticker}")
                    val candles = marketService.candles(marketInstrument.figi, "1min", from, to)

                    if (candles.candles.isNotEmpty()) {
                        candle2359 = candles.candles.first()
                        val data = gson.toJson(candle2359)

                        val editor: SharedPreferences.Editor = preferences.edit()
                        editor.putString(key, data)
                        editor.apply()

                        updateChange2359()

                        log("closing price os ${marketInstrument.ticker}")
                        return@launch
                    } else { // если свечей нет, то сделать шаг назад во времени
                        deltaDay--
                        from = getLastClosingDate(true, deltaDay) + zone
                        to = getLastClosingDate(false, deltaDay) + zone
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(delay)
            }
        }
        return delay
    }

    fun loadClosingPostmarketUSPrice(prevDelay: Long): Long {
        if (yahooPostmarket != null) return prevDelay

        val zone = getCurrentTimezone()
        val from = getLastClosingDate(false) + zone

        val key = "closing_postmarket_us_${marketInstrument.figi}_${from}"

        val preferences = PreferenceManager.getDefaultSharedPreferences(SettingsManager.context)
        val jsonClosing = preferences.getString(key, null)
        if (jsonClosing != null) {
            yahooPostmarket = gson.fromJson(jsonClosing, YahooResponse::class.java)
            updateChangePostmarket()
            updateChange2359()
            return prevDelay
        }

        val delay = prevDelay + kotlin.random.Random.Default.nextLong(1000, 1500)
        GlobalScope.launch(Dispatchers.Main) {
            while (yahooPostmarket == null) {
                try {
                    delay(delay)
                    if (yahooPostmarket != null) return@launch

                    val queue = Volley.newRequestQueue(Utils.context)
                    var ticker = marketInstrument.ticker
                    if (ticker == "SPB@US") ticker = "SPB" // костыль, в yahoo тикер назван по-другому
                    ticker.replace(".", "-")

                    val url = "https://query1.finance.yahoo.com/v10/finance/quoteSummary/${ticker}?modules=price"

                    val stringRequest = StringRequest(Request.Method.GET, url,
                        { response ->
                            val convertedObject: JsonObject = gson.fromJson(response, JsonObject::class.java)

                            try {
                                val summary = convertedObject["quoteSummary"] as JsonObject
                                val result = summary["result"] as JsonArray
                                val prices = result[0] as JsonObject
                                val price = prices["price"] as JsonObject
                                yahooPostmarket = gson.fromJson(price, YahooResponse::class.java)

                                if (yahooPostmarket != null) {
                                    val data = gson.toJson(yahooPostmarket)
                                    val editor: SharedPreferences.Editor = preferences.edit()
                                    editor.putString(key, data)
                                    editor.apply()

                                    updateChangePostmarket()
                                }

                                log("closing price us yahoo ${marketInstrument.ticker} = $yahooPostmarket")
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        { log("error") })

                    queue.add(stringRequest)
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(delay)
            }
        }
        return delay
    }

    private fun getLastClosingDate(before: Boolean, delta: Int = 0): String {
        val differenceHours: Int = Utils.getTimeDiffBetweenMSK()

        var hours = 23
        var minutes = 59
        var seconds = 0

        if (!before) {
            hours = 0
            minutes = 0
            seconds = 0
        }

        val time = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"))
        time.add(Calendar.HOUR_OF_DAY, -differenceHours)

        time.set(Calendar.HOUR_OF_DAY, hours)
        time.set(Calendar.MINUTE, minutes)
        time.set(Calendar.SECOND, seconds)
        time.set(Calendar.MILLISECOND, 0)

        if (delta != 0) {
            time.add(Calendar.DAY_OF_MONTH, delta)
        }

        // если воскресенье, то откатиться к субботе
        if (time.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            time.add(Calendar.DAY_OF_MONTH, -1)
        }

        // если понедельник, то откатиться к субботе
        if (time.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
            time.add(Calendar.DAY_OF_MONTH, -2)
        }

        if (before) {
            time.add(Calendar.DAY_OF_MONTH, -1)
        }

        return time.time.toString("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    }

    private fun getLastClosingPostmarketRUDate(delta: Int = 0): String {
        val time = Calendar.getInstance()
        time.set(Calendar.MILLISECOND, 0)

        if (delta != 0) {
            time.add(Calendar.DAY_OF_MONTH, delta)
        }


        time.set(Calendar.HOUR_OF_DAY, 20)
        time.set(Calendar.MINUTE, 0)
        time.set(Calendar.SECOND, 0)
        time.set(Calendar.MILLISECOND, 0)

        // если воскресенье, то откатиться к субботе
//        if (time.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
//            time.add(Calendar.DAY_OF_MONTH, -1)
//        }
//
//        // если понедельник, то откатиться к субботе
//        if (time.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
//            time.add(Calendar.DAY_OF_MONTH, -2)
//        }

        return time.time.toString("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    }

    private fun getCurrentTimezone(): String {
        val tz = TimeZone.getDefault()
        val cal = GregorianCalendar.getInstance(tz)
        val offsetInMillis = tz.getOffset(cal.timeInMillis)
        var offset = String.format("%02d:%02d", abs(offsetInMillis / 3600000), abs(offsetInMillis / 60000 % 60))
        offset = (if (offsetInMillis >= 0) "+" else "-") + offset
        return offset
    }
}