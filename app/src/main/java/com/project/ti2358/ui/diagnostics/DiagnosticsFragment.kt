package com.project.ti2358.ui.diagnostics

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.content.FileProvider.getUriForFile
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.project.ti2358.BuildConfig
import com.project.ti2358.R
import com.project.ti2358.TheApplication.Companion.application
import com.project.ti2358.data.alor.service.StreamingAlorService
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.data.manager.TinkoffPortfolioManager
import com.project.ti2358.data.pantini.service.StreamingPantiniService
import com.project.ti2358.data.tinkoff.service.StreamingTinkoffService
import com.project.ti2358.databinding.FragmentDiagnosticsBinding
import com.project.ti2358.service.Utils
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.io.*


@KoinApiExtension

class DiagnosticsFragment : Fragment(R.layout.fragment_diagnostics) {


    val tinkoffPortfolioManager: TinkoffPortfolioManager by inject()
    val stockManager: StockManager by inject()
    val streamingTinkoffService: StreamingTinkoffService by inject()
    val streamingAlorService: StreamingAlorService by inject()
    val streamingPantiniService: StreamingPantiniService by inject()

    private var fragmentDiagnosticsBinding: FragmentDiagnosticsBinding? = null
    private val SAVE_REQUEST_CODE = 666
    private val PICKFILE_RESULT_CODE = 777

    override fun onDestroy() {
        fragmentDiagnosticsBinding = null
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {

        if (resultCode == RESULT_OK) if (resultData != null) {
            var currentUri: Uri? = null
            if (requestCode == SAVE_REQUEST_CODE) {
                resultData.let {
                    //Заглушка, не всегда прилетает ответ, если обрабатывается не средствами системы
                }
            } else if (requestCode == PICKFILE_RESULT_CODE) {
                resultData.let {
                    currentUri = it.data
                    try {
                        loadSharedPreferencesFromFile(currentUri!!)
                    } catch (e: IOException) {
                        Utils.showToastAlert("Что-то пошло не так")
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pathToFile = getPathToFile()
        val binding = FragmentDiagnosticsBinding.bind(view)
        fragmentDiagnosticsBinding = binding

        binding.updateButton.setOnClickListener {
            updateData()
        }
        binding.saveButton.setOnClickListener {
            saveSharedPreferencesToFile(pathToFile)
            saveFile(it)
        }
        binding.loadButton.setOnClickListener {
            openFile(it)
        }
        updateData()
    }

    fun updateData() {
        val tinkoffREST = if (tinkoffPortfolioManager.accounts.isNotEmpty()) "ОК" else "НЕ ОК 😱"
        val tinkoffConnectedStatus = if (streamingTinkoffService.connectedStatus) "ОК" else "НЕ ОК 😱"
        val tinkoffMessagesStatus = if (streamingTinkoffService.messagesStatus) "ОК" else "НЕ ОК 😱"
        val alorConnectedStatus = if (streamingAlorService.connectedStatus) "ОК" else "НЕ ОК 😱"
        val alorMessagesStatus = if (streamingAlorService.messagesStatus) "ОК" else "НЕ ОК 😱"
        val daagerClosePricesStatus = if (stockManager.stockClosePrices.isNotEmpty()) "ОК" else "НЕ ОК 😱"
        val daagerReportsStatus = if (stockManager.stockReports.isNotEmpty()) "ОК" else "НЕ ОК 😱"
        val daagerIndicesStatus = if (stockManager.indices.isNotEmpty()) "ОК" else "НЕ ОК 😱"
        val daagerShortsStatus = if (stockManager.stockShorts.isNotEmpty()) "ОК" else "НЕ ОК 😱"

        val daager1728 = if (stockManager.stockPrice1728?.isNotEmpty() == true) "ОК" else "НЕ ОК 😱"
        var daager1728Step1 = "НЕ ОК 😱"
        var daager1728Step2 = "НЕ ОК 😱"
        var daager1728Step3 = "НЕ ОК 😱"

        val pantiniConnectedStatus = if (streamingPantiniService.connectedStatus) "ОК" else "НЕ ОК 😱"
        val pantiniAuthStatus = if (streamingPantiniService.authStatus) "ОК" else "НЕ ОК 😱"

        stockManager.stockPrice1728?.let {
            if (it["M"] != null) {
                if (it["M"]?.from700to1200 != null) daager1728Step1 = "OK"
                if (it["M"]?.from700to1600 != null) daager1728Step2 = "OK"
                if (it["M"]?.from1630to1635 != null) daager1728Step3 = "OK"
            }
        }

        fragmentDiagnosticsBinding?.textInfoView?.text =
            "Tinkoff REST: $tinkoffREST\n" +
                    "Tinkoff OpenAPI коннект: $tinkoffConnectedStatus\n" +
                    "Tinkoff OpenAPI котировки: $tinkoffMessagesStatus\n\n" +

                    "ALOR OpenAPI коннект: $alorConnectedStatus\n" +
                    "ALOR OpenAPI котировки: $alorMessagesStatus\n\n" +

                    "daager OpenAPI цены закрытия: $daagerClosePricesStatus\n" +
                    "daager OpenAPI отчёты и дивы: $daagerReportsStatus\n" +
                    "daager OpenAPI индексы: $daagerIndicesStatus\n" +
                    "daager OpenAPI шорты: $daagerShortsStatus\n" +
                    "daager OpenAPI 1728: $daager1728\n" +
                    "daager OpenAPI 1728 Шаг 1: $daager1728Step1\n" +
                    "daager OpenAPI 1728 Шаг 2: $daager1728Step2\n" +
                    "daager OpenAPI 1728 Шаг 3: $daager1728Step3\n\n" +

                    "pantini коннект: $pantiniConnectedStatus\n" +
                    "pantini авторизация: $pantiniAuthStatus\n\n"
    }

    private fun saveFile(view: View) {
        val file = File(requireContext().getExternalFilesDir(null), "oostap2358.xml")
        val outputUri = getUriForFile(requireContext(), BuildConfig.APPLICATION_ID + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_TEXT, "Настройки программы oost.app")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.putExtra(Intent.EXTRA_CHOOSER_TARGETS, "org.telegram.messenger")
        }
        intent.putExtra(Intent.EXTRA_STREAM, outputUri)
        intent.type = "text/plain"
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivityForResult(intent, SAVE_REQUEST_CODE)
    }

    private fun openFile(view: View) {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.putExtra("return-data", true)
        intent.type = "text/xml"
        startActivityForResult(intent, PICKFILE_RESULT_CODE)
    }

    private fun saveSharedPreferencesToFile(dst: File): Boolean {
        var res = false
        var output: ObjectOutputStream? = null
        try {
            output = ObjectOutputStream(FileOutputStream(dst))
            val pref = getDefaultSharedPreferences(application.applicationContext)
            output.writeObject(pref.all)
            res = true
        } catch (e: FileNotFoundException) {
            Utils.showToastAlert("Отсутствуют права на запись")
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                if (output != null) {
                    output.flush()
                    output.close()
                }
            } catch (ex: IOException) {
                Utils.showToastAlert("Ошибка")
                ex.printStackTrace()
            }
        }
        return res
    }

    private fun loadSharedPreferencesFromFile(uri: Uri): Boolean {
        val inputStream = activity?.contentResolver?.openInputStream(uri)
        var res = false
        var input: ObjectInputStream? = null
        try {
            input = ObjectInputStream(inputStream)
            val prefEdit: SharedPreferences.Editor = getDefaultSharedPreferences(application.applicationContext).edit()
            prefEdit.clear()
            val entries = input.readObject() as Map<String, *>
            for ((key, value) in entries) {
                when (val v = value!!) {
                    is Boolean -> prefEdit.putBoolean(key, v)
                    is Float -> prefEdit.putFloat(
                        key,
                        v.toFloat()
                    )
                    is Int -> prefEdit.putInt(key, v.toInt())
                    is Long -> prefEdit.putLong(
                        key,
                        v.toLong()
                    )
                    is String -> prefEdit.putString(
                        key,
                        v
                    )
                }
            }
            prefEdit.apply()
            res = true
            Utils.showToastAlert("Настройки применены\nНеобходим перезапуск!")
        } catch (e: FileNotFoundException) {
            Utils.showToastAlert("Отсутствует файл")
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        } finally {
            try {
                input?.close()
            } catch (ex: IOException) {
                Utils.showToastAlert("Ошибка")
                ex.printStackTrace()
            }
        }
        return res
    }

    private fun getPathToFile(): File {
        /*Сохраняется в папку приложения по пути: (/storage/emulated/0/Android/data/com.project.ti2358/files/), потому-что начиная с 10-11 ведра, доступ на запись в произвольное место - запрещена (Scoped Storage). Есть способы обхода, но тогда приложение нельзя разместить в GPlay =(
        П.С. после удаления приложения, папка стирается со всем содержимым.*/
        val dir = "${context?.getExternalFilesDir(null)}/"
        // Создаем папку.
        if (File(dir).mkdirs()) return File(dir, "oostap2358.xml")
        return File(dir, "oostap2358.xml")
    }
}