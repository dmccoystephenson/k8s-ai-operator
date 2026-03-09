package com.stephenson.k8saioperator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedCommand {

    @JsonProperty("verb")
    private String verb;

    @JsonProperty("resource")
    private String resource;

    @JsonProperty("namespace")
    private String namespace;
}

