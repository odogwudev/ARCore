package com.odogwudev.arcore

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odogwudev.arcore.manager.ClothesManager
import com.odogwudev.arcore.model.ResultImage
import com.odogwudev.arcore.util.wrapEspressoIdlingResource
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import timber.log.Timber

class MainViewModel : ViewModel() {

    private val _processedImage = MutableLiveData<ResultImage>()
    val processedImage: LiveData<ResultImage> = _processedImage

    private lateinit var clothesManager: ClothesManager

    fun setupClothesManager(clothesManager: ClothesManager) {
        this.clothesManager = clothesManager
    }

    fun setupClothes() {
        wrapEspressoIdlingResource {
            viewModelScope.launch {
                val result = async { clothesManager.processImage() }.await()

                _processedImage.value = result
                Timber.i("result !")
            }
        }
    }


}