package com.codenjoy.dojo.pong.services;

/*-
 * #%L
 * Codenjoy - it's a dojo-like platform from developers to developers.
 * %%
 * Copyright (C) 2018 Codenjoy
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import com.codenjoy.dojo.client.ClientBoard;
import com.codenjoy.dojo.client.Solver;
import com.codenjoy.dojo.pong.client.Board;
import com.codenjoy.dojo.pong.client.ai.AISolver;
import com.codenjoy.dojo.pong.model.*;
import com.codenjoy.dojo.services.*;
import com.codenjoy.dojo.services.multiplayer.GameField;
import com.codenjoy.dojo.services.multiplayer.GamePlayer;
import com.codenjoy.dojo.services.multiplayer.MultiplayerType;
import com.codenjoy.dojo.services.printer.CharElements;
import com.codenjoy.dojo.services.settings.Parameter;

import static com.codenjoy.dojo.services.settings.SimpleParameter.v;

public class GameRunner extends AbstractGameType implements GameType {

    private final Level level;
    private Pong game;

    public GameRunner() {
        new Scores(0, settings);
        level = new LevelImpl(getMap());
    }

    @Override
    public GameField createGame(int levelNumber) {
        return new Pong(level, getDice());
    }

    @Override
    public PlayerScores getPlayerScores(Object score) {
        return new Scores((Integer) score, settings);
    }

    @Override
    public Parameter<Integer> getBoardSize() {
        return v(level.getSize());
    }

    @Override
    public String name() {
        return "pong";
    }

    @Override
    public CharElements[] getPlots() {
        return Elements.values();
    }

    @Override
    public Class<? extends Solver> getAI() {
        return AISolver.class;
    }

    @Override
    public Class<? extends ClientBoard> getBoard() {
        return Board.class;
    }

    @Override
    public MultiplayerType getMultiplayerType() {
        return MultiplayerType.TOURNAMENT;
    }

    @Override
    public GamePlayer createPlayer(EventListener listener, String playerName) {
        return new Player(listener);
    }

    protected String getMap() {
        return "                              " +
                "                              " +
                "                              " +
                "                              " +
                "                              " +
                "                              " +
                "                              " +
                "                              " +
                "                              " +
                "------------------------------" +
                "                              " +
                "                              " +
                "                              " +
                "                              " +
                "                o             " +
                "                              " +
                "                              " +
                "                              " +
                "                              " +
                "                              " +
                "                              " +
                "------------------------------" +
                "                              " +
                "                              " +
                "                              " +
                "                              " +
                "                              " +
                "                              " +
                "                              " +
                "                              ";
    }
}