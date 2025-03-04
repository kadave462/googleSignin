//package com.example.googlesignin
//
//import android.util.Log
//import androidx.lifecycle.ViewModel
//import com.google.firebase.Firebase
//import com.google.firebase.analytics.analytics
//import dagger.hilt.android.lifecycle.HiltViewModel
//import javax.inject.Inject
//
//@HiltViewModel
//class LoggingViewModel @Inject constructor(): ViewModel() {
//    fun onLogButtonClick(){
//        Firebase.analytics.logEvent("button_click", null)
//    }
//}