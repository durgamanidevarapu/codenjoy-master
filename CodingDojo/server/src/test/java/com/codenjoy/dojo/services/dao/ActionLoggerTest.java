package com.codenjoy.dojo.services.dao;

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


import com.codenjoy.dojo.services.*;
import com.codenjoy.dojo.services.jdbc.SqliteConnectionThreadPoolFactory;
import com.codenjoy.dojo.services.multiplayer.GameField;
import com.codenjoy.dojo.services.multiplayer.MultiplayerType;
import com.codenjoy.dojo.services.printer.BoardReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ActionLoggerTest {

    private static ActionLogger logger;

    @Before
    public void setup() {
        String dbFile = "target/logs.db" + new Random().nextInt();
        logger = new ActionLogger(
                    new SqliteConnectionThreadPoolFactory(dbFile,
                            new ContextPathGetter() {
                                @Override
                                public String getContext() {
                                    return "context";
                                }
                            }))
        {
            @Override
            protected long now() {
                return 123456789L;
            }
        };
        logger.setTicks(1);
    }

    @After
    public void tearDown() {
        logger.removeDatabase();
    }

    @Test
    public void shouldLogWhenEnabled() throws InterruptedException {
        logger.resume();

        act();

        assertEquals("[BoardLog(time=123456789, playerName=player1, gameType=game1, score=123, board=board1, command=[]), " +
                "BoardLog(time=123456789, playerName=player2, gameType=game2, score=234, board=board2, command=[])]", logger.getAll().toString());
    }

    @Test
    public void shouldNotLogWhenNotEnabled() throws InterruptedException {
        act();

        assertEquals("[]", logger.getAll().toString());
    }

    private void act() throws InterruptedException {
        PlayerGames playerGames = new PlayerGames();

        addPlayer(playerGames, "board1", 123, "player1", "room", "game1");
        addPlayer(playerGames, "board2", 234, "player2", "room", "game2");

        logger.log(playerGames);

        Thread.sleep(1000); // потому что сохранение в базу делается асинхронно и надо подождать
    }

    private void addPlayer(PlayerGames playerGames, String board, int value, String name, String roomName, String gameName) {
        PlayerScores score = getScore(value);
        Player player = new Player(name, "127.0.0.1", PlayerTest.mockGameType(gameName), score, null);
        player.setEventListener(mock(InformationCollector.class));

        TestUtils.Env env = TestUtils.getPlayerGame(playerGames, player, roomName,
                inv -> {
                    GameField field = mock(GameField.class);
                    when(field.reader()).thenReturn(mock(BoardReader.class));
                    return field;
                },
                MultiplayerType.SINGLE,
                null,
                parameters -> board);
    }

    private PlayerScores getScore(int value) {
        PlayerScores score = mock(PlayerScores.class);
        when(score.getScore()).thenReturn(value);
        return score;
    }

}