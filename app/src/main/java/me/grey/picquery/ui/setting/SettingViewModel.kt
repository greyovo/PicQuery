package me.grey.picquery.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import me.grey.picquery.common.showToast
import me.grey.picquery.data.data_source.PreferenceRepository

class SettingViewModel(
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    val enableUploadLog = preferenceRepository.getEnableUploadLog()
    val deviceId = preferenceRepository.getDeviceIdFlow()

    fun setEnableUploadLog(enable: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setEnableUploadLog(enable)
        }
        showToast("将在下次启动APP时生效")
    }
}