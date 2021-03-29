package com.project.ti2358.ui.strategy1000Sell

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.manager.PurchaseStock
import com.project.ti2358.data.manager.Strategy1000Sell
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class Strategy1000SellFinishFragment : Fragment() {

    private val strategy1000Sell: Strategy1000Sell by inject()
    var adapterList: Item1000RecyclerViewAdapter = Item1000RecyclerViewAdapter(emptyList())
    var infoTextView: TextView? = null
    var positions: MutableList<PurchaseStock> = mutableListOf()
    var buttonStart700: Button? = null
    var buttonStart1000: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_1000_sell_finish, container, false)
        val list = view.findViewById<RecyclerView>(R.id.list)

        list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))

        if (list is RecyclerView) {
            with(list) {
                layoutManager = LinearLayoutManager(context)
                adapter = adapterList
            }
        }

        ///////////////////////
        buttonStart1000 = view.findViewById(R.id.buttonStart1000)
        buttonStart1000?.setOnClickListener {
            if (Utils.isServiceRunning(requireContext(), Strategy1000SellService::class.java)) {
                requireContext().stopService(Intent(context, Strategy1000SellService::class.java))
            } else {
                if (strategy1000Sell.getTotalPurchasePieces() > 0) {
                    Utils.startService(requireContext(), Strategy1000SellService::class.java)
                }
            }

            this.findNavController().navigateUp()
            updateServiceButtonText1000()
        }
        updateServiceButtonText1000()
        //////////////////////
        buttonStart700 = view.findViewById(R.id.buttonStart700)
        buttonStart700?.setOnClickListener {
            if (Utils.isServiceRunning(requireContext(), Strategy700SellService::class.java)) {
                requireContext().stopService(Intent(context, Strategy700SellService::class.java))
            } else {
                if (strategy1000Sell.getTotalPurchasePieces() > 0) {
                    Utils.startService(requireContext(), Strategy700SellService::class.java)
                }
            }

            this.findNavController().navigateUp()
            updateServiceButtonText700()
        }
        updateServiceButtonText700()

        positions = strategy1000Sell.processSellPosition()
        adapterList.setData(positions)

        infoTextView = view.findViewById(R.id.info_text)
        updateInfoText()

        return view
    }

    private fun updateServiceButtonText1000() {
        if (Utils.isServiceRunning(requireContext(), Strategy1000SellService::class.java)) {
            buttonStart1000?.text = getString(R.string.stop_sell_1000)
        } else {
            buttonStart1000?.text = getString(R.string.start_sell_1000)
        }
    }

    private fun updateServiceButtonText700() {
        if (Utils.isServiceRunning(requireContext(), Strategy700SellService::class.java)) {
            buttonStart700?.text = getString(R.string.stop_sell_700)
        } else {
            buttonStart700?.text = getString(R.string.start_sell_700)
        }
    }

    fun updateInfoText() {
        val time = "07:00:00.100ms или 10:00:00.100ms"
        val prepareText: String =
            TheApplication.application.applicationContext.getString(R.string.prepare_start_1000_sell_text)
        infoTextView?.text = String.format(
            prepareText,
            time,
            positions.size,
            strategy1000Sell.getTotalSellString()
        )
    }

    inner class Item1000RecyclerViewAdapter(
        private var values: List<PurchaseStock>
    ) : RecyclerView.Adapter<Item1000RecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<PurchaseStock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_1000_sell_finish_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.purchase = item

            val avg = item.position.getAveragePrice()
            holder.tickerView.text = "${item.position.ticker} x ${item.position.lots}"

            val profit = item.position.getProfitAmount()
            var totalCash = item.position.balance * avg
            val percent = item.position.getProfitPercent()
            holder.currentProfitView.text = percent.toPercent()

            totalCash += profit
            holder.currentPriceView.text = "${avg.toMoney(item.stock)} ➡ ${totalCash.toMoney(item.stock)}"

            holder.totalPriceProfitView.text = profit.toMoney(item.stock)
            holder.priceProfitView.text = (profit / item.position.lots).toMoney(item.stock)

            holder.currentPriceView.setTextColor(Utils.getColorForValue(percent))
            holder.currentProfitView.setTextColor(Utils.getColorForValue(percent))
            holder.priceProfitView.setTextColor(Utils.getColorForValue(percent))
            holder.totalPriceProfitView.setTextColor(Utils.getColorForValue(percent))

            refreshFuturePercent(holder)

            holder.buttonPlus.setOnClickListener {
                item.percentProfitSellFrom += 0.05
                refreshFuturePercent(holder)
                updateInfoText()
            }

            holder.buttonMinus.setOnClickListener {
                item.percentProfitSellFrom += -0.05
                refreshFuturePercent(holder)
                updateInfoText()
            }

            holder.itemView.setBackgroundColor(Utils.getColorForIndex(position))
        }

        fun refreshFuturePercent(holder: ViewHolder) {
            val item = holder.purchase
            val futurePercent = item.percentProfitSellFrom
            holder.futureProfitView.text = futurePercent.toPercent()

            val avg = item.position.getAveragePrice()
            val futureProfitPrice = item.getProfitPriceForSell() - avg
            holder.futureProfitPriceView.text = futureProfitPrice.toMoney(item.stock)
            holder.totalFutureProfitPriceView.text = (futureProfitPrice * item.position.balance).toMoney(item.stock)

            val sellPrice = item.getProfitPriceForSell()
            val totalSellPrice = item.getProfitPriceForSell() * item.position.balance
            holder.totalPriceView.text = "${sellPrice.toMoney(item.stock)} ➡ ${totalSellPrice.toMoney(item.stock)}"

            holder.totalPriceView.setTextColor(Utils.getColorForValue(futureProfitPrice))
            holder.futureProfitView.setTextColor(Utils.getColorForValue(futureProfitPrice))
            holder.futureProfitPriceView.setTextColor(Utils.getColorForValue(futureProfitPrice))
            holder.totalFutureProfitPriceView.setTextColor(Utils.getColorForValue(futureProfitPrice))
        }

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            lateinit var purchase: PurchaseStock

            val tickerView: TextView = view.findViewById(R.id.tickerView)
            val currentPriceView: TextView = view.findViewById(R.id.priceView)
            val totalPriceView: TextView = view.findViewById(R.id.stock_total_price)

            val priceProfitView: TextView = view.findViewById(R.id.stock_item_price_profit)
            val totalPriceProfitView: TextView = view.findViewById(R.id.stock_total_price_profit)

            val currentProfitView: TextView = view.findViewById(R.id.stock_current_change)

            val futureProfitPriceView: TextView = view.findViewById(R.id.stock_profit_price_change)
            val totalFutureProfitPriceView: TextView = view.findViewById(R.id.stock_profit_total_price_change)

            val futureProfitView: TextView = view.findViewById(R.id.stock_profit_percent)

            val buttonPlus: Button = view.findViewById(R.id.buttonPlus)
            val buttonMinus: Button = view.findViewById(R.id.buttonMinus)
        }
    }
}