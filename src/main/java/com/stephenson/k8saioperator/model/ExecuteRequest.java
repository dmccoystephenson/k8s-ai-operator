package com.stephenson.k8saioperator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ExecuteRequest {

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("user_prompt")
    private String userPrompt;
}

