package com.assignment.main;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Random;

import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.when;

class ScratchGameTest {
    @Mock
    Random RANDOM;
    @InjectMocks
    ScratchGame scratchGame;

    String PROJECT_ROOT = "F:\\work\\stsws\\learning\\scratch-game";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void test2x2Matrix() {
        when(RANDOM.nextInt(anyInt())).thenReturn(0);
        when(RANDOM.nextDouble()).thenReturn(0d);

        ScratchGame.main(new String[]{"--betting-amount", "100", "--config", PROJECT_ROOT.concat("\\src\\test\\resources\\config2x2.json"), "100"});
    }

    @Test
    void test3x3Matrix() {
        when(RANDOM.nextInt(anyInt())).thenReturn(0);
        when(RANDOM.nextDouble()).thenReturn(0d);

        ScratchGame.main(new String[]{"--config", PROJECT_ROOT.concat("\\src\\test\\resources\\config3x3.json"), "--betting-amount", "100"});
    }


}