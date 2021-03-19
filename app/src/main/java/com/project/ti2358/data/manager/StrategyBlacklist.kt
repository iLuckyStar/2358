package com.project.ti2358.data.manager

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.project.ti2358.TheApplication
import com.project.ti2358.service.*
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinApiExtension
class StrategyBlacklist : KoinComponent {
    private val stockManager: StockManager by inject()
    private val keySavedBlacklistStock: String = "blacklist"

    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()
    var currentSort: Sorting = Sorting.DESCENDING

    private val gson = Gson()

    fun process(): MutableList<Stock> {
        val all = stockManager.getAllStocks()
        stocks = all
        stocks.sortBy { it.changePrice2359DayPercent }
        loadSelectedStocks()
        return stocks
    }

    fun getBlacklistStocks(): MutableList<Stock> {
        process()
        return stocksSelected
    }

    private fun loadSelectedStocks() {
        stocksSelected.clear()

        val jsonStocks = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext).getString(keySavedBlacklistStock, null)
        jsonStocks?.let {
            val itemType = object : TypeToken<List<String>>() {}.type
            val stocksSelectedList: List<String> = gson.fromJson(jsonStocks, itemType)
            stocksSelected = stocks.filter { it.instrument.ticker in stocksSelectedList }.toMutableList()
        }
    }

    private fun saveSelectedStocks() {
        val list = stocksSelected.map { it.instrument.ticker }.toMutableList()

        val preferences = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext)
        val editor: SharedPreferences.Editor = preferences.edit()
        val data = gson.toJson(list)
        editor.putString(keySavedBlacklistStock, data)
        editor.apply()
    }

    fun resort(): MutableList<Stock> {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocks.sortBy {
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            val multiplier = if (it in stocksSelected) 100 else 1
            it.changePrice2359DayPercent * sign - multiplier
        }
        return stocks
    }

    fun setSelected(stock: Stock, value: Boolean) {
        if (value) {
            if (stock !in stocksSelected)
                stocksSelected.add(stock)
        } else {
            stocksSelected.remove(stock)
        }
        stocksSelected.sortBy { it.changePrice2359DayPercent }

        saveSelectedStocks()
    }

    fun isSelected(stock: Stock): Boolean {
        return stock in stocksSelected
    }
}