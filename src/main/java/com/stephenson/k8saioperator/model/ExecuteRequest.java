package com.stephenson.k8saioperator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

@Data
public class ExecuteRequest {

    @JsonProperty("request_id")
    private String requestId;

    // Excluded because anything that prints the request, such as Spring's DEBUG logging, must not print the prompt
    @ToString.Exclude
    @JsonProperty("user_prompt")
    private String userPrompt;
}

