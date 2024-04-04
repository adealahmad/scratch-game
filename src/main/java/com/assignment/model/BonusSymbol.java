package com.assignment.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
public class BonusSymbol extends Symbol {
    private Map<String, Integer> symbols;
    private String impact;
    private int extra;

    public BonusSymbol(final Symbol symbol) {
        this.setRewardMultiplier(symbol.getRewardMultiplier());
        this.setType("bonus");
    }
}
