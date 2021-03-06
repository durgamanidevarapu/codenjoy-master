package com.codenjoy.dojo.services;

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

import com.codenjoy.dojo.client.*;
import com.codenjoy.dojo.client.WebSocketRunner;
import com.codenjoy.dojo.services.controller.Controller;
import com.codenjoy.dojo.services.dao.ActionLogger;
import com.codenjoy.dojo.services.dao.Registration;
import com.codenjoy.dojo.services.mocks.AISolverStub;
import com.codenjoy.dojo.services.mocks.BoardStub;
import com.codenjoy.dojo.services.multiplayer.GameField;
import com.codenjoy.dojo.services.multiplayer.GamePlayer;
import com.codenjoy.dojo.services.multiplayer.MultiplayerType;
import com.codenjoy.dojo.services.printer.BoardReader;
import com.codenjoy.dojo.services.printer.PrinterFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class PlayerServiceImplIntegrationTest {

    private PlayerService service;

    private Semifinal semifinal;
    private ConfigProperties config;
    private ActionLogger actionLogger;
    private AutoSaver autoSaver;
    private GameSaver saver;
    private GameService gameService;
    private Controller screenController;
    private Controller playerController;
    private PlayerGames playerGames;
    private Registration registration;
    private Map<String, GameType> gameTypes = new HashMap<>();
    private Map<String, WebSocketRunner> runners = new HashMap<>();

    @Before
    public void setup() {
        service = new PlayerServiceImpl() {
            {
                PlayerServiceImplIntegrationTest.this.playerGames
                        = this.playerGames = new PlayerGames();

                PlayerServiceImplIntegrationTest.this.playerController
                        = this.playerController = mock(Controller.class);

                PlayerServiceImplIntegrationTest.this.screenController
                        = this.screenController = mock(Controller.class);

                PlayerServiceImplIntegrationTest.this.gameService
                        = this.gameService = mock(GameService.class);

                PlayerServiceImplIntegrationTest.this.autoSaver
                        = this.autoSaver = mock(AutoSaver.class);

                PlayerServiceImplIntegrationTest.this.saver
                        = this.saver = mock(GameSaver.class);

                PlayerServiceImplIntegrationTest.this.actionLogger
                        = this.actionLogger = mock(ActionLogger.class);

                PlayerServiceImplIntegrationTest.this.registration
                        = this.registration = mock(Registration.class);

                PlayerServiceImplIntegrationTest.this.config
                        = this.config = new ConfigProperties();
                config.setRegistrationOpened(true);

                PlayerServiceImplIntegrationTest.this.semifinal
                        = this.semifinal = mock(Semifinal.class);

                this.isAiNeeded = true;
            }

            @Override
            protected WebSocketRunner runAI(String aiName, String code, Solver solver, ClientBoard board) {
                WebSocketRunner runner = mock(WebSocketRunner.class);
                doAnswer(inv -> {
                    return null; // for debug
                }).when(runner).close();
                runners.put(aiName, runner);
                return runner;
            }

        };
    }

    @Test
    public void test() {
        int ai1 = 0;
        int ai2 = 0;
        int ai3 = 0;
        when(gameService.getGame(anyString())).thenAnswer(
                inv -> getOrCreateGameType(inv.getArgument(0))
        );

        // ???????????? ?????????? ?????????????????? (?? ???????? ???????????? ??????)
        when(saver.loadGame(anyString())).thenReturn(PlayerSave.NULL);
        Player player1 = service.register("player1", "room1", "callback1", "game1");
        assertEquals("[game1-super-ai@codenjoy.com, player1]", service.getAll().toString());
        assertEquals(true, runners.containsKey("game1-super-ai@codenjoy.com"));

        // ?????????? ?????? ???????? ?????????????????? ???? ???? ???? ????????
        Player player2 = service.register("player2", "room1", "callback2", "game1");
        Player player3 = service.register("player3", "room1", "callback2", "game1");
        verify(gameTypes.get("game1"), times(++ai1)).getAI();
        verify(gameTypes.get("game1"), times(ai1)).getBoard();
        assertEquals("[game1-super-ai@codenjoy.com, player1, player2, player3]",
                service.getAll().toString());

        // ???????????? ??????????
        service.remove("player2");
        assertEquals("[game1-super-ai@codenjoy.com, player1, player3]",
                service.getAll().toString());

        // ?? ???????????? ????????
        service.remove("player3");
        assertEquals("[game1-super-ai@codenjoy.com, player1]",
                service.getAll().toString());

        // ?????????????? ???????? ???? ????????????????????????
        assertEquals(true, service.contains("player1"));
        assertEquals(false, service.contains("player2"));

        // ?????????? ???????????????????? ???? ???????????? ????????
        Player player4 = service.register("player4", "room2", "callback4", "game2");
        verify(gameTypes.get("game2"), times(++ai2)).getAI();
        verify(gameTypes.get("game2"), times(ai2)).getBoard();
        Player player5 = service.register("player5", "room2", "callback5", "game2");
        Player player6 = service.register("player6", "room1", "callback6", "game1");
        assertEquals("[game1-super-ai@codenjoy.com, player1, player6]",
                service.getAll("game1").toString());
        assertEquals("[game2-super-ai@codenjoy.com, player4, player5]",
                service.getAll("game2").toString());

        // ?????? ???????? ?? ?????? ???????????? ?????? AI
        assertEquals(true, service.contains("game1-super-ai@codenjoy.com"));
        assertEquals(true, service.contains("game2-super-ai@codenjoy.com"));
        assertEquals(true, runners.containsKey("game1-super-ai@codenjoy.com"));
        assertEquals(true, runners.containsKey("game2-super-ai@codenjoy.com"));

        // ?????????? ???????? ?? ????????????????
        assertEquals("game1", service.getAnyGameWithPlayers().name());

        // ?? ?????????????????? ??????????????
        assertEquals("game1-super-ai@codenjoy.com",
                service.getRandom("game1").toString());
        assertEquals("game2-super-ai@codenjoy.com",
                service.getRandom("game2").toString());

        // ???????????????? ???????????? ?????? ?????????????? ???????????? ???????????? ?? ??????????????
        verifyNoMoreInteractions(runners.get("game2-super-ai@codenjoy.com"));
        service.remove("game2-super-ai@codenjoy.com");
        verify(runners.get("game2-super-ai@codenjoy.com"), times(1)).close();
        assertEquals("player4",
                service.getRandom("game2").toString());
        runners.remove("game2-super-ai@codenjoy.com");

        // ?????????????? ??????????????????????
        assertEquals(true, service.isRegistrationOpened());
        service.closeRegistration();
        assertEquals(false, service.isRegistrationOpened());
        Player player7 = service.register("player7", "room3", "callback7", "game3");
        assertEquals(false, service.contains("player7"));
        assertEquals("[]",
                service.getAll("game3").toString());

        // ?????????????? ??????????????????????
        service.openRegistration();
        assertEquals(true, service.isRegistrationOpened());
        player7 = service.register("player7", "room3", "callback7", "game3");
        verify(gameTypes.get("game3"), times(++ai3)).getAI();
        verify(gameTypes.get("game3"), times(ai3)).getBoard();
        assertEquals(true, service.contains("player7"));
        assertEquals("[game3-super-ai@codenjoy.com, player7]",
                service.getAll("game3").toString());
        assertEquals(true, runners.containsKey("game1-super-ai@codenjoy.com"));
        assertEquals(false, runners.containsKey("game2-super-ai@codenjoy.com"));
        assertEquals(true, runners.containsKey("game3-super-ai@codenjoy.com"));

        // ?????????????????? AI ???????????? ????????????
        service.reloadAI("player7");
        verify(gameTypes.get("game3"), times(++ai3)).getAI();
        verify(gameTypes.get("game3"), times(ai3)).getBoard();
        assertEquals("[game3-super-ai@codenjoy.com, player7]",
                service.getAll("game3").toString());
        assertEquals(true, runners.containsKey("game1-super-ai@codenjoy.com"));
        assertEquals(false, runners.containsKey("game2-super-ai@codenjoy.com"));
        assertEquals(true, runners.containsKey("game3-super-ai@codenjoy.com"));
        assertEquals(true, runners.containsKey("player7"));

        // ???????????????? ???????????????? ??????????
        List<PlayerInfo> infos = service.getAll().stream().map(player -> new PlayerInfo(player.getName() + "_updated",
                player.getCode(), player.getCallbackUrl(), player.getGameName())).collect(toList());
        service.updateAll(infos);
        assertEquals("[game1-super-ai@codenjoy.com_updated, " +
                "player1_updated, player4_updated, player5_updated, " +
                "player6_updated, game3-super-ai@codenjoy.com_updated, " +
                "player7_updated]", service.getAll().toString());

        // ???????????????? ?????????????????????????? ???????????????????????? ?? ???????????? ????????
        assertEquals("[game1-super-ai@codenjoy.com_updated, player1_updated, player6_updated]",
                service.getAll("game1").toString());
        assertEquals("[player4_updated, player5_updated]",
                service.getAll("game2").toString());
        player1 = service.register("player1_updated", "room2", "callback1", "game2");
        assertEquals("[game1-super-ai@codenjoy.com_updated, player6_updated]",
                service.getAll("game1").toString());
        assertEquals("[player4_updated, player5_updated, game2-super-ai@codenjoy.com, player1_updated]",
                service.getAll("game2").toString()); // TODO ???????????? ???????? ???????? AI ??????????????? ?????? ???? ???????? ???????????? ??????

        // ?????????????? ???????? ??????????
        service.removeAll();
        assertEquals("[]", service.getAll("game1").toString());
        assertEquals("[]", service.getAll("game2").toString());
        assertEquals("[]", service.getAll("game3").toString());
        verify(runners.get("game1-super-ai@codenjoy.com"), times(1)).close();
        verify(runners.get("game3-super-ai@codenjoy.com"), times(1)).close();
        verify(runners.get("player7"), times(1)).close();
        runners.clear();

        // ???????????? ???????????? ???? ??????????
        player1 = service.register(new PlayerSave("player1", "callback1", "room1", "game1", 120, "{save:true}"));
        assertEquals("[player1]", service.getAll("game1").toString());
        assertEquals(0, runners.size());

        // ?? ???????????? AI ???? ??????????
        verify(gameTypes.get("game1"), times(ai1)).getAI();
        verify(gameTypes.get("game1"), times(ai1)).getBoard();
        player1 = service.register(new PlayerSave("bot-super-ai@codenjoy.com", "callback", "room1", "game1", 120, "{save:true}"));
        assertEquals("[player1, bot-super-ai@codenjoy.com]", service.getAll("game1").toString());
        verify(gameTypes.get("game1"), times(++ai1)).getAI();
        verify(gameTypes.get("game1"), times(ai1)).getBoard();
        assertEquals(true, runners.containsKey("bot-super-ai@codenjoy.com"));
    }

    private GameType getOrCreateGameType(String name) {
        if (gameTypes.containsKey(name)) {
            return gameTypes.get(name);
        }


        GameType gameType = mock(GameType.class);
        when(gameType.getMultiplayerType()).thenReturn(MultiplayerType.SINGLE);
        when(gameType.createGame(anyInt())).thenAnswer(inv -> {
            GameField field = mock(GameField.class);
            when(field.reader()).thenAnswer(inv2 -> mock(BoardReader.class));
            return field;
        });
        when(gameType.createPlayer(any(EventListener.class), anyString()))
                .thenAnswer(inv -> mock(GamePlayer.class));
        when(gameType.getPrinterFactory()).thenReturn(mock(PrinterFactory.class));
        when(gameType.getAI()).thenReturn((Class)AISolverStub.class);
        when(gameType.getBoard()).thenReturn((Class)BoardStub.class);
        when(gameType.name()).thenReturn(name);

        gameTypes.put(name, gameType);

        return gameType;
    }
}
