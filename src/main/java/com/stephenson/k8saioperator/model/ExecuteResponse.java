package com.stephenson.k8saioperator.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecuteResponse {

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("command")
    private ParsedCommand command;

    @JsonProperty("result")
    private String result;

    @JsonProperty("allowed")
    private boolean allowed;

    @JsonProperty("reason")
    private String reason;
}

