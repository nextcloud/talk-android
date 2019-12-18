package com.nextcloud.talk.newarch.utils

import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import java.util.*
import kotlin.collections.ArrayList

class LiveDataTransformations {

    /**
     * Creates a new [LiveData] object that does not emit a value until the source LiveData
     * value has been changed.  The value is considered changed if `equals()` yields
     * `false`.
     *
     * @param source the input [LiveData]
     * @param <X>    the generic type parameter of `source`
     * @return       a new [LiveData] of type `X`
    </X> */
    @MainThread
    fun <X> distinctUntilChangedForArray(source: LiveData<X>): LiveData<X> {
        val outputLiveData = MediatorLiveData<X>()
        outputLiveData.addSource(source, object : Observer<X> {

            var mFirstTime = true

            override fun onChanged(currentValue: X?) {
                val previousValue = outputLiveData.value
                if (mFirstTime && currentValue != null
                        || (previousValue == null && currentValue != null)) {
                    mFirstTime = false
                    outputLiveData.value = currentValue
                } else if (previousValue != null) {
                    if (currentValue == null) {
                        outputLiveData.value = currentValue
                    } else {
                        val previousArray: Array<X> = (previousValue as ArrayList<X>).toArray() as Array<X>
                        val currentArray: Array<X> = (currentValue as ArrayList<X>).toArray() as Array<X>

                        if (previousArray.size != currentArray.size) {
                            outputLiveData.value = currentValue
                        } else {
                            var counter = 0
                            for(element in previousArray) {
                                if (!element!!.equals(currentArray[counter])) {
                                    outputLiveData.value = currentValue
                                    return
                                }

                                counter++
                            }
                        }
                    }
                }
            }
        })

        return outputLiveData
    }
}
