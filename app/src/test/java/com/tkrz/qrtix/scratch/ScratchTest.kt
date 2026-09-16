package com.tkrz.qrtix.scratch

import com.google.api.client.util.GenericData
import com.google.api.client.http.HttpHeaders

fun main() {
    try {
        val data = GenericData()
        data.set(null, "value")
    } catch (e: Exception) {
        println("GenericData error: " + e.message)
    }

    try {
        val headers = HttpHeaders()
        headers.set(null, "value")
    } catch (e: Exception) {
        println("HttpHeaders error: " + e.message)
    }
}
