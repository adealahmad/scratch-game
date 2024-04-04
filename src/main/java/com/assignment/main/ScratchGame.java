package com.assignment.main;

import com.assignment.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ScratchGame {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java ScratchGame <config_file> <betting_amount>");
            System.exit(1);
        }

        String configFile = args[0];
        int bettingAmount = Integer.parseInt(args[1]);
        try {
            ObjectMapper mapper = new ObjectMapper();
            Config config = mapper.readValue(new File(configFile), Config.class);

            List<List<String>> matrix = generateMatrix(config);
            Map<String, List<String>> appliedWinningCombinations = checkWinningCombinations(matrix, config.getWinCombinations());
            List<String> appliedBonusSymbols = applyBonusSymbols(matrix, config);

            int reward = calculateReward(bettingAmount, appliedWinningCombinations, appliedBonusSymbols, config);
            outputResult(matrix, reward, appliedWinningCombinations, appliedBonusSymbols);
        } catch (IOException e) {
            System.err.println("Error reading the configuration file: " + e.getMessage());
            System.exit(1);
        }
    }

    private static List<List<String>> generateMatrix(Config config) {
        List<List<String>> matrix = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < config.getRows(); i++) {
            List<String> row = new ArrayList<>();
            for (int j = 0; j < config.getColumns(); j++) {
                List<SymbolProbability> symbolProbabilities = config.getProbabilities().getStandardSymbols();
                SymbolProbability symbolProbability = getSymbolProbability(i, j, symbolProbabilities).orElseThrow(() -> new RuntimeException("Symbol Probabilities not found for given row"));
                int totalProbability = getTotalProbability(i, j, symbolProbabilities);
                String chosenSymbol = null;
                int randomValue = random.nextInt(totalProbability) + 1;
                int currentSum = 0;
                for (String symbolName : symbolProbability.getSymbols().keySet()) {
                    currentSum += symbolProbability.getSymbols().get(symbolName);
                    if (randomValue <= currentSum) {
                        chosenSymbol = symbolName;
                    }
                }
                row.add(chosenSymbol);
            }
            matrix.add(row);
        }

        return matrix;
    }


    private static Optional<SymbolProbability> getSymbolProbability(final int row, final int col, List<SymbolProbability> symbolProbabilities) {
        return symbolProbabilities.stream().filter(prob -> row == prob.getRow() && col == prob.getColumn()).findAny();
    }

    private static int getTotalProbability(final int row, final int col, List<SymbolProbability> symbolProbabilities) {
        return symbolProbabilities.stream().filter(prob -> row == prob.getRow() && col == prob.getColumn()).mapToInt(prob -> prob.getSymbols().values().stream().mapToInt(Integer::valueOf).sum()).sum();
    }


    private static Map<String, List<String>> checkWinningCombinations(List<List<String>> matrix, Map<String, WinCombination> winCombinations) {
        Map<String, List<String>> appliedWinningCombinations = new HashMap<>();

        for (Map.Entry<String, WinCombination> entry : winCombinations.entrySet()) {
            String combinationName = entry.getKey();
            WinCombination winCombination = entry.getValue();
            String groupName = winCombination.getGroup();
            List<String> appliedGroups = appliedWinningCombinations.computeIfAbsent(groupName, k -> new ArrayList<>());

            if (winCombination.getCoveredAreas() != null) {
                for (List<String> coveredArea : winCombination.getCoveredAreas()) {
                    String firstSymbol = matrix.get(Integer.parseInt(coveredArea.get(0).split(":")[0]))
                            .get(Integer.parseInt(coveredArea.get(0).split(":")[1]));
                    boolean match = true;
                    for (String cell : coveredArea) {
                        String symbol = matrix.get(Integer.parseInt(cell.split(":")[0]))
                                .get(Integer.parseInt(cell.split(":")[1]));
                        if (!symbol.equals(firstSymbol)) {
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        appliedGroups.add(combinationName);
                    }
                }
            }
        }

        return appliedWinningCombinations;
    }

    private static List<String> applyBonusSymbols(List<List<String>> matrix, Config config) {
        List<String> bonusSymbols = new ArrayList<>();
        Random random = new Random();

        Map<String, BonusSymbol> bonusSymbolMap = new HashMap<>();
        for (Map.Entry<String, Symbol> entry : config.getSymbols().entrySet()) {
            if (entry.getValue().getType().equals("bonus")) {
                bonusSymbolMap.put(entry.getKey(), new BonusSymbol(entry.getValue()));
            }
        }

        for (int i = 0; i < config.getRows(); i++) {
            for (int j = 0; j < config.getColumns(); j++) {
                for (Map.Entry<String, BonusSymbol> entry : bonusSymbolMap.entrySet()) {
                    double rand = random.nextDouble();
                    double probability = entry.getValue().getSymbols().get(entry);
                    if (rand < probability) {
                        bonusSymbols.add(entry.getKey());
                    }
                }
            }
        }

        return bonusSymbols;
    }

    private static int calculateReward(int bettingAmount, Map<String, List<String>> appliedWinningCombinations,
                                       List<String> appliedBonusSymbols, Config config) {
        int reward = 0;

        for (Map.Entry<String, List<String>> entry : appliedWinningCombinations.entrySet()) {
            String group = entry.getKey();
            List<String> appliedCombinations = entry.getValue();

            for (String combination : appliedCombinations) {
                WinCombination winCombination = config.getWinCombinations().get(combination);
                double multiplier = winCombination.getRewardMultiplier();

                for (String symbol : appliedBonusSymbols) {
                    BonusSymbol bonusSymbol = (BonusSymbol) config.getSymbols().get(symbol);
                    switch (bonusSymbol.getImpact()) {
                        case "multiply_reward":
                            multiplier *= bonusSymbol.getRewardMultiplier();
                            break;
                        case "extra_bonus":
                            reward += bonusSymbol.getExtra();
                            break;
                    }
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

    private static void outputResult(List<List<String>> matrix, int reward, Map<String, List<String>> appliedWinningCombinations, List<String> appliedBonusSymbols) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        ObjectNode rootNode = JsonNodeFactory.instance.objectNode();

        ArrayNode matrixNode = rootNode.putArray("matrix");
        for (List<String> row : matrix) {
            ArrayNode rowNode = matrixNode.addArray();
            for (String symbol : row) {
                rowNode.add(symbol);
            }
        }

        rootNode.put("reward", reward);

        if (!appliedWinningCombinations.isEmpty()) {
            ObjectNode winningCombinationsNode = rootNode.putObject("applied_winning_combinations");
            for (Map.Entry<String, List<String>> entry : appliedWinningCombinations.entrySet()) {
                String symbol = entry.getKey();
                ArrayNode combinationNode = winningCombinationsNode.putArray(symbol);
                for (String combination : entry.getValue()) {
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
            System.out.println(mapper.writeValueAsString(rootNode));
        } catch (IOException e) {
            System.err.println("Error outputting result: " + e.getMessage());
        }
    }

}
