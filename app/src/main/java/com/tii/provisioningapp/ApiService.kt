package com.tii.provisioningapp

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface ApiService {
    @Headers("Content-Type: application/json")
    @POST("/api/devices/provision") // Replace with your actual endpoint
    fun postData(@Body requestData: FraCsr): Call<ProvisioningResponse>

    fun postNatsData(@Body requestNATS: ServerConfig): Call<NATSResponse>
}
