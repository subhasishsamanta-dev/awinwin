package com.brainium.service;

import com.brainium.schema.SkillResponse;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface ApiService {

    @POST("/")
    @Headers({
        "Content-Type: application/json",
        "Accept: application/json"
    })
    Call<SkillResponse> getSkills(@Body RequestBody body);
    
}

