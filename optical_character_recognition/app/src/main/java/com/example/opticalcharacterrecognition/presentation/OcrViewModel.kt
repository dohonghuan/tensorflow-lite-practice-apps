package com.example.opticalcharacterrecognition.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class OcrViewModel : ViewModel() {
    private val _rootDirectory = MutableLiveData<String>("")
    val rootDirectory: LiveData<String> = _rootDirectory

    fun setRootDirectory(root: String) {
        _rootDirectory.postValue(root)
    }

    fun getRootDirectory(): String {
        return rootDirectory.value.toString()
    }
}