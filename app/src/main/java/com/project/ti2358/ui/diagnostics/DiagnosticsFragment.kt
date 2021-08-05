package com.project.ti2358.ui.diagnostics

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.project.ti2358.MainActivity
import com.project.ti2358.R
import com.project.ti2358.TheApplication
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
    private var reqPermissionStatus: Boolean = false

    override fun onDestroy() {
        fragmentDiagnosticsBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentDiagnosticsBinding.bind(view)
        fragmentDiagnosticsBinding = binding

        binding.updateButton.setOnClickListener {
            updateData()
        }
        binding.saveButton.setOnClickListener {
            checkPermission(WRITE_EXTERNAL_STORAGE, 2358)
            if (reqPermissionStatus == true) {
                saveSharedPreferencesToFile(getPathToFile())
            }
        }
        binding.loadButton.setOnClickListener {
            checkPermission(READ_EXTERNAL_STORAGE, 2358)
            if (reqPermissionStatus == true) {
                loadSharedPreferencesFromFile(getPathToFile())
            }
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

    // Функция для проверки и запроса разрешения.
    private fun checkPermission(permission: String, requestCode: Int) {
        val appSpecificExternalDir = File(requireContext().getExternalFilesDir(null), "oostap2358.xml")
        if (ContextCompat.checkSelfPermission(activity as MainActivity, permission) == PackageManager.PERMISSION_DENIED) {
            // Запрашиваем разрешения
            ActivityCompat.requestPermissions(activity as MainActivity, arrayOf(permission), requestCode)
        } else {
            //Права получены, установка статуса
            reqPermissionStatus = true
            //Utils.showToastAlert("Разрешение предоставлено")
        }
    }

    private fun getPathToFile(): File {
        /*Сохраняется в папку приложения по пути: (/storage/emulated/0/Android/data/com.project.ti2358/files/), потому-что начиная с 10-11 ведра, доступ на запись в произвольное место - запрещена (Scoped Storage). Есть способы обхода, но тогда приложение нельзя разместить в GPlay =(
        П.С. после удаления приложения, папка стирается со всем содержимым.*/
        //костыль для 10-11 ведра
        val dir = "${context?.getExternalFilesDir(null)}/"
        // Создаем папку.
        if (File(dir).mkdirs()) return File(dir, "oostap2358.xml")
        return File(dir, "oostap2358.xml")
    }

    private fun saveSharedPreferencesToFile(dst: File): Boolean {
        var res = false
        var output: ObjectOutputStream? = null
        try {
            output = ObjectOutputStream(FileOutputStream(dst))
            val pref = getDefaultSharedPreferences(TheApplication.application.applicationContext)
            output.writeObject(pref.all)
            res = true
            Utils.showToastAlert("Настройки сохранены")
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

    private fun loadSharedPreferencesFromFile(src: File): Boolean {
        var res = false
        var input: ObjectInputStream? = null
        try {
            input = ObjectInputStream(FileInputStream(src))
            val prefEdit: SharedPreferences.Editor = getDefaultSharedPreferences(TheApplication.application.applicationContext).edit()
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
}