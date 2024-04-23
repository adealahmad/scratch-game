package com.assignment.main;

import com.assignment.model.Config;
import com.assignment.model.Probabilities;
import com.assignment.model.Symbol;
import com.assignment.model.WinCombination;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;

@Slf4j
public class ScratchGame {
    private static final Random RANDOM = new SecureRandom();

    public static Map<String, String> parse(String[] args) {
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    params.put(key, args[i + 1]);
                    i++; // Skip the next argument as it's the value for the current key
                } else {
                    params.put(key, null); // No value provided for the key
                }
            }
        }
        return params;
    }

    public static void main(String[] args) {

        Map<String, String> params = parse(args);

        if (params.size() != 2) {
            System.err.println("Usage: java -jar scratch-game-1.0.0.jar --config <config_file> --betting-amount <betting_amount>");
            System.exit(1);
        }

        String configFile = params.get("config");
        int bettingAmount = Integer.parseInt(params.get("betting-amount"));
        try {
            ObjectMapper mapper = new ObjectMapper();
            Config config = mapper.readValue(new File(configFile), Config.class);
            String[][] matrix = generateMatrix(config);
            Map<String, List<String>> appliedWinningCombinations = checkWinningCombinations(matrix, config);
            Set<String> appliedBonusSymbols = applyBonusSymbols(matrix, config, appliedWinningCombinations);
            int reward = calculateReward(bettingAmount, appliedWinningCombinations, appliedBonusSymbols, config);
            outputResult(matrix, reward, appliedWinningCombinations, appliedBonusSymbols);
        } catch (IOException e) {
            log.error("Error reading the configuration file: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String[][] generateMatrix(final Config config) {
        String[][] matrix = new String[config.getRows()][config.getColumns()];
        for (int row = 0; row < config.getRows(); row++) {
            for (int col = 0; col < config.getColumns(); col++) {
                Map<String, Integer> symbolProbabilities = getSymbolProbabilities(config, col, row);
                matrix[row][col] = getRandomSymbol(symbolProbabilities);
            }
        }
        return matrix;
    }

    private static Map<String, Integer> getSymbolProbabilities(final Config config, final int col, final int row) {
        Map<String, Integer> symbolProbabilities = config.getProbabilities().getStandardSymbols().stream()
                .filter(symbol -> symbol.getColumn() == col && symbol.getRow() == row)
                .findFirst().orElseThrow(() -> new RuntimeException("Missing probability for cell")).getSymbols();
        return symbolProbabilities;
    }

    private static String getRandomSymbol(final Map<String, Integer> symbolProbabilities) {
        int totalProbability = symbolProbabilities.values().stream().mapToInt(value -> value).sum();
        int randomValue = RANDOM.nextInt(totalProbability) + 1;

        int currentSum = 0;
        for (String symbol : symbolProbabilities.keySet()) {
            currentSum += symbolProbabilities.get(symbol);
            if (currentSum >= randomValue) {
                return symbol;
            }
        }
        throw new RuntimeException("Error generating random symbol");
    }


    private static Map<String, List<String>> checkWinningCombinations(final String[][] matrix, final Config config) {
        Map<String, List<String>> appliedCombinations = new HashMap<>();
        Set<String> appliedGroups = new HashSet<>();
        for (String symbol : config.getSymbols().keySet()) {
            appliedCombinations.put(symbol, new ArrayList<>());
            checkSameSymbolWins(symbol, matrix, config, appliedCombinations, appliedGroups);
            checkLinearWins(symbol, matrix, config, appliedCombinations, appliedGroups);
        }
        return appliedCombinations;
    }

    private static void checkSameSymbolWins(final String symbol, final String[][] matrix, final Config config, final Map<String, List<String>> appliedCombinations, final Set<String> appliedGroups) {
        for (String combination : config.getWinCombinations().keySet()) {
            WinCombination winCombination = config.getWinCombinations().get(combination);
            if (winCombination.getWhen().equals("same_symbols") && !appliedGroups.contains(winCombination.getGroup())) {
                int count = 0;
                for (int row = 0; row < matrix.length; row++) {
                    for (int col = 0; col < matrix[row].length; col++) {
                        if (matrix[row][col].equals(symbol)) {
                            count++;
                        }
                        if (count == winCombination.getCount()) {
                            appliedCombinations.get(symbol).add(combination);
                            appliedGroups.add(winCombination.getGroup());
                            break;
                        }
                    }
                }
            }
        }
    }

    private static void checkLinearWins(final String symbol, final String[][] matrix, final Config config, final Map<String, List<String>> appliedCombinations, final Set<String> appliedGroups) {
        for (String combination : config.getWinCombinations().keySet()) {
            WinCombination winCombination = config.getWinCombinations().get(combination);
            if (winCombination.getWhen().equals("linear_symbols") && !appliedGroups.contains(winCombination.getGroup())) {
                for (List<String> coveredArea : winCombination.getCoveredAreas()) {
                    int matchingSymbols = 0;
                    for (String position : coveredArea) {
                        int row = Integer.parseInt(position.split(":")[0]);
                        int col = Integer.parseInt(position.split(":")[1]);
                        if (row >= 0 && row < matrix.length && col >= 0 && col < matrix[row].length && matrix[row][col].equals(symbol)) {
                            matchingSymbols++;
                        }
                    }
                    if (matchingSymbols == coveredArea.size()) {
                        appliedCombinations.get(symbol).add(combination);
                        appliedGroups.add(winCombination.getGroup());
                        break;
                    }
                }
            }
        }
    }

    private static Set<String> applyBonusSymbols(final String[][] matrix, final Config config, final Map<String, List<String>> appliedWinningCombinations) {
        Set<String> bonusSymbols = new HashSet<>();
        int appliedWinCombinitionsCount = appliedWinningCombinations.values().stream().mapToInt(value -> value.size()).sum();
        if (appliedWinCombinitionsCount == 0) {
            return bonusSymbols;
        }

        Map<String, Symbol> bonusSymbolMap = getBonusSymbolMap(config);
        List<String> nonSelectedIndexes = new ArrayList<>();
        for (int i = 0; i < config.getRows(); i++) {
            for (int j = 0; j < config.getColumns(); j++) {
                if (appliedWinningCombinations.containsKey(matrix[i][j]) && appliedWinningCombinations.get(matrix[i][j]).size() > 0) {
                    continue;
                }
                nonSelectedIndexes.add(i + ":" + j);
            }
        }

        int bonusSymbolIndex = RANDOM.nextInt(bonusSymbolMap.size());
        int randomIndex = RANDOM.nextInt(nonSelectedIndexes.size());
        log.info("selected index {}", bonusSymbolIndex);
        log.info("selected symbol {}", new ArrayList(bonusSymbolMap.keySet()).get(bonusSymbolIndex));

        log.info("selected random {}", randomIndex);
        log.info("selected random index {}", nonSelectedIndexes.get(randomIndex));
        String selectedRandomRCPair = nonSelectedIndexes.get(randomIndex);
        int row = Integer.parseInt(selectedRandomRCPair.split(":")[0]);
        int col = Integer.parseInt(selectedRandomRCPair.split(":")[1]);
        String appliedBonusSymbol = String.valueOf(new ArrayList(bonusSymbolMap.keySet()).get(bonusSymbolIndex));
        matrix[row][col] = appliedBonusSymbol;
        bonusSymbols.add(appliedBonusSymbol);
        return bonusSymbols;
    }

    private static Map<String, Symbol> getBonusSymbolMap(final Config config) {
        Map<String, Symbol> bonusSymbolMap = new HashMap<>();
        for (Map.Entry<String, Symbol> entry : config.getSymbols().entrySet()) {
            if (entry.getValue().getType().equals("bonus")) {
                bonusSymbolMap.put(entry.getKey(), entry.getValue());
            }
        }
        return bonusSymbolMap;
    }

    private static int calculateReward(final int bettingAmount, final Map<String, List<String>> appliedWinningCombinations,
                                       final Set<String> appliedBonusSymbols, final Config config) {
        int reward = 0;

        Map<String, Symbol> bonusSymbolMap = getBonusSymbolMap(config);

        for (Map.Entry<String, List<String>> entry : appliedWinningCombinations.entrySet()) {
            String group = entry.getKey();
            List<String> appliedCombinations = entry.getValue();

            for (String combination : appliedCombinations) {
                WinCombination winCombination = config.getWinCombinations().get(combination);
                double multiplier = winCombination.getRewardMultiplier();
                for (String symbol : appliedBonusSymbols) {
                    Symbol bonusSymbol = bonusSymbolMap.get(symbol);
                    switch (bonusSymbol.getImpact()) {
                        case "multiply_reward":
                            multiplier *= bonusSymbol.getRewardMultiplier();
                            break;
                        case "extra_bonus":
                            reward += bonusSymbol.getExtra();
                            break;
                    }
                    log.info("reward : {}", reward);
                    log.info("multiplier : {}", multiplier);
                }

                for (String symbol : config.getSymbols().keySet()) {
                    if (appliedCombinations.contains(symbol)) {
                        multiplier *= config.getSymbols().get(symbol).getRewardMultiplier();
                    }
                }

                reward += bettingAmount * multiplier;
            }
        }

        return reward;
    }

    private static void outputResult(final String[][] matrix, final int reward, final Map<String, List<String>> appliedWinningCombinations, final Set<String> appliedBonusSymbols) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        ObjectNode rootNode = JsonNodeFactory.instance.objectNode();

        ArrayNode matrixNode = rootNode.putArray("matrix");
        for (int row = 0; row < matrix.length; row++) {
            ArrayNode rowNode = matrixNode.addArray();
            for (int col = 0; col < matrix[row].length; col++) {
                rowNode.add(matrix[row][col]);
            }
        }

        rootNode.put("reward", reward);

        if (!appliedWinningCombinations.isEmpty()) {
            ObjectNode winningCombinationsNode = rootNode.putObject("applied_winning_combinations");
            for (Map.Entry<String, List<String>> entry : appliedWinningCombinations.entrySet()) {
                String symbol = entry.getKey();

                for (String combination : entry.getValue()) {
                    ArrayNode combinationNode = winningCombinationsNode.putArray(symbol);
                    combinationNode.add(combination);
                }
            }
        }

        if (!appliedBonusSymbols.isEmpty()) {
            ArrayNode bonusSymbolsNode = rootNode.putArray("applied_bonus_symbol");
            for (String symbol : appliedBonusSymbols) {
                bonusSymbolsNode.add(symbol);
            }
        }

        try {
            log.info(mapper.writeValueAsString(rootNode));
        } catch (IOException e) {
            log.error("Error outputting result: " + e.getMessage());
        }
    }

    private static Integer getBonusSymbolProbability(final Probabilities probabilities, final String bonusSymbol) {
        return probabilities.getBonusSymbol().getSymbols().get(bonusSymbol);
    }


}
