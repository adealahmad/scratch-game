package com.assignment.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SymbolProbability {
    private int column;
    private int row;
    private Map<String, Integer> symbols;

}
