package com.assignment.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Config {
    private int columns;
    private int rows;
    private Map<String, Symbol> symbols;
    @JsonProperty("win_combinations")
    private Map<String, WinCombination> winCombinations;
    private Probabilities probabilities;

}
