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


import com.codenjoy.dojo.CodenjoyContestApplication;
import com.codenjoy.dojo.client.WebSocketRunner;
import com.codenjoy.dojo.config.meta.SQLiteProfile;
import com.codenjoy.dojo.services.controller.Controller;
import com.codenjoy.dojo.services.controller.PlayerController;
import com.codenjoy.dojo.services.controller.ScreenController;
import com.codenjoy.dojo.services.dao.ActionLogger;
import com.codenjoy.dojo.services.dao.Registration;
import com.codenjoy.dojo.services.hero.HeroDataImpl;
import com.codenjoy.dojo.services.lock.LockedJoystick;
import com.codenjoy.dojo.services.mocks.AISolverStub;
import com.codenjoy.dojo.services.mocks.BoardStub;
import com.codenjoy.dojo.services.multiplayer.*;
import com.codenjoy.dojo.services.nullobj.NullJoystick;
import com.codenjoy.dojo.services.nullobj.NullPlayer;
import com.codenjoy.dojo.services.playerdata.PlayerData;
import com.codenjoy.dojo.services.printer.BoardReader;
import com.codenjoy.dojo.services.printer.CharElements;
import com.codenjoy.dojo.services.printer.PrinterFactory;
import com.codenjoy.dojo.transport.screen.ScreenRecipient;
import com.codenjoy.dojo.transport.screen.ScreenSender;
import org.fest.reflect.core.Reflection;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.*;

import static com.codenjoy.dojo.services.PointImpl.pt;
import static com.codenjoy.dojo.services.settings.SimpleParameter.v;
import static org.fest.reflect.core.Reflection.field;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = CodenjoyContestApplication.class)
@RunWith(SpringRunner.class)
@ActiveProfiles(SQLiteProfile.NAME)
public class PlayerServiceImplTest {

    public static final String VASYA = "vasya@mail.com";
    public static final String VASYA_AI = "vasya-super-ai@codenjoy.com";
    public static final String PETYA = "petya@mail.com";
    public static final String KATYA = "katya@mail.com";
    public static final String OLIA = "olia@mail.com";
    public static final String VASYA_URL = "http://vasya@mail.com:1234";
    public static final String PETYA_URL = "http://petya@mail.com:1234";

    private ArgumentCaptor<Map> screenSendCaptor;
    private ArgumentCaptor<Player> playerCaptor;
    private ArgumentCaptor<Integer> xCaptor;
    private ArgumentCaptor<Integer> yCaptor;
    private ArgumentCaptor<List> plotsCaptor;
    private ArgumentCaptor<String> boardCaptor;

    @MockBean
    private ScreenSender screenSender;

    @MockBean
    private PlayerController playerController;

    @MockBean
    private ScreenController screenController;

    @MockBean
    private AutoSaver autoSaver;

    @MockBean
    private SaveService saveService;

    @MockBean
    private Registration registration;

    @MockBean
    private GameService gameService;

    @MockBean
    private Semifinal semifinal;

    @MockBean
    private ActionLogger actionLogger;

    @SpyBean
    private PlayerGames playerGames;

    @SpyBean
    private PlayerGamesView playerGamesView;

    @Autowired
    private PlayerServiceImpl playerService;

    @Mock
    private GameType gameType;
    
    @Mock
    private PlayerScores playerScores1;
    
    @Mock
    private PlayerScores playerScores2;
    
    @Mock
    private PlayerScores playerScores3;
    
    private InformationCollector informationCollector;
    
    @Mock
    private GraphicPrinter printer;
    private List<Joystick> joysticks = new LinkedList<>();
    private List<GamePlayer> gamePlayers = new LinkedList<>();
    private List<GameField> gameFields = new LinkedList<>();
    private List<Player> players = new LinkedList<>();
    private List<PlayerHero> heroesData = new LinkedList<>();

    @Before
    public void setUp() {
        Mockito.reset(actionLogger, autoSaver, gameService, playerController, playerGames);
        playerGames.clean();

        screenSendCaptor = ArgumentCaptor.forClass(Map.class);
        playerCaptor = ArgumentCaptor.forClass(Player.class);
        xCaptor = ArgumentCaptor.forClass(Integer.class);
        yCaptor = ArgumentCaptor.forClass(Integer.class);
        plotsCaptor = ArgumentCaptor.forClass(List.class);
        boardCaptor = ArgumentCaptor.forClass(String.class);

        when(playerScores1.getScore()).thenReturn(0);

        when(playerScores2.getScore()).thenReturn(0);

        when(playerScores3.getScore()).thenReturn(0);

        when(printer.print(any(), any())).thenReturn("1234");

        when(gameService.getGame(anyString())).thenReturn(gameType);

        when(gameType.getBoardSize()).thenReturn(v(15));
        when(gameType.getPlayerScores(anyInt())).thenReturn(playerScores1, playerScores2, playerScores3);
        when(gameType.createGame(anyInt())).thenAnswer(inv -> {
            GameField gameField = mock(GameField.class);
            gameFields.add(gameField);

            when(gameField.reader()).thenReturn(mock(BoardReader.class));
            return gameField;
        });
        heroesData.addAll(Arrays.asList(heroData(1, 2), heroData(3, 4), heroData(5, 6), heroData(7, 8)));
        when(gameType.createPlayer(any(EventListener.class), anyString()))
                .thenAnswer(inv -> {
                    Joystick joystick = mock(Joystick.class);
                    joysticks.add(joystick);

                    GamePlayer gamePlayer = mock(GamePlayer.class);
                    gamePlayers.add(gamePlayer);

                    when(gamePlayer.getJoystick()).thenReturn(joystick);
                    when(gamePlayer.getHero()).thenReturn(heroesData.get(gamePlayers.size() - 1));
                    when(gamePlayer.isAlive()).thenReturn(true);
                    return gamePlayer;
                });
        when(gameType.name()).thenReturn("game");
        when(gameType.getPlots()).thenReturn(Elements.values());
        when(gameType.getPrinterFactory()).thenReturn(PrinterFactory.get(printer));
        when(gameType.getMultiplayerType()).thenReturn(MultiplayerType.SINGLE);

        doAnswer(inv -> {
            String email = inv.getArgument(0);
            return "readable_" + email.split("@")[0];
        }).when(registration).getNameById(anyString());

        playerGames.clear();
        Mockito.reset(playerController, screenController, actionLogger);
        playerService.openRegistration();

        playerService.init();
    }

    private PlayerHero heroData(int x, int y) {
        return new PlayerHero(pt(x, y)) {
            @Override
            public void down() {

            }

            @Override
            public void up() {

            }

            @Override
            public void left() {

            }

            @Override
            public void right() {

            }

            @Override
            public void act(int... p) {

            }

            @Override
            public void tick() {

            }
        };
    }

    enum Elements implements CharElements {
        A('1'), B('2'), C('3'), D('4');

        private final char ch;

        Elements(char c) {
            this.ch = c;
        }

        @Override
        public String toString() {
            return String.valueOf(ch);
        }

        @Override
        public char ch() {
            return ch;
        }
    }

    @Test
    public void shouldCreatePlayer() {
        createPlayer(VASYA);

        Player player = playerService.get(VASYA);

        assertEquals("game", player.getGameName());
        assertEquals(VASYA, player.getName());
        assertNull(player.getPassword());
        assertNull(player.getCode());
        assertEquals(VASYA_URL, player.getCallbackUrl());
        assertSame(gameType, player.getGameType());
        assertNull(player.getMessage());
        assertEquals(0, player.getScore());
    }

    @Test
    public void shouldNotCreatePlayerWhenRegistrationWasClosed() {
        // given
        assertTrue(playerService.isRegistrationOpened());

        // when
        playerService.closeRegistration();

        // then
        Player player = createPlayer(VASYA);
        assertSame(NullPlayer.INSTANCE, player);

        player = playerService.get(VASYA);
        assertSame(NullPlayer.INSTANCE, player);

        assertFalse(playerService.isRegistrationOpened());

        // when
        playerService.openRegistration();

        // then
        assertTrue(playerService.isRegistrationOpened());

        player = createPlayer(VASYA);
        assertSame(VASYA, player.getName());

        player = playerService.get(VASYA);
        assertSame(VASYA, player.getName());
    }

    @Test
    public void shouldSendCoordinatesToPlayerBoard() throws IOException {
        Player vasia = createPlayer(VASYA);
        when(printer.print(any(), any())).thenReturn("1234");

        playerService.tick();

        assertSentToPlayers(vasia);
        assertEquals("ABCD", getBoardFor(vasia));
    }

    @Test
    public void shouldSendPlayerBoardFromJsonBoard() throws IOException {
        Player vasia = createPlayer(VASYA);
        when(printer.print(any(), any()))
                .thenReturn(new JSONObject("{'layers':['1234','4321']}"));

        playerService.tick();

        assertSentToPlayers(vasia);
        assertEquals("{\"layers\":[\"ABCD\",\"DCBA\"]}", getBoardFor(vasia));
    }

    @Test
    public void shouldRequestControlFromAllPlayers() throws IOException {
        Player vasia = createPlayer(VASYA);
        Player petia = createPlayer(PETYA);

        playerService.tick();

        assertSentToPlayers(vasia, petia);
        verify(playerController, times(2)).requestControl(playerCaptor.capture(), anyString());

        assertHostsCaptured(VASYA_URL, PETYA_URL);
    }

    @Test
    public void shouldRequestControlFromAllPlayersWithGlassState() throws IOException {
        createPlayer(VASYA);
        when(printer.print(any(), any())).thenReturn("1234");

        playerService.tick();

        verify(playerController).requestControl(playerCaptor.capture(), boardCaptor.capture());
        assertEquals("1234", boardCaptor.getValue());
    }

    @Test
    public void shouldSendAdditionalInfoToAllPlayers() throws IOException {
        // given
        createPlayer(VASYA);
        createPlayer(PETYA);

        when(printer.print(any(), any()))
                .thenReturn("1234")
                .thenReturn("4321");
        when(playerScores1.getScore()).thenReturn(123);
        when(playerScores2.getScore()).thenReturn(234);

        // when
        playerService.tick();

        // then
        verify(screenController).requestControlToAll(screenSendCaptor.capture());
        Map<ScreenRecipient, Object> data = screenSendCaptor.getValue();

        assertEquals(
                "{vasya@mail.com=PlayerData[" +
                    "BoardSize:15, Board:'ABCD', GameName:'game', " +
                    "Score:123, Info:'', " +
                    "Scores:'{'vasya@mail.com':123}', " +
                    "HeroesData:'{" +
                        "'coordinates':{'vasya@mail.com':{'coordinate':{'x':1,'y':2},'level':0,'multiplayer':false}}," +
                        "'group':['vasya@mail.com']," +
                        "'readableNames':{'vasya@mail.com':'readable_vasya'}" +
                        "}'], " +
                "petya@mail.com=PlayerData[" +
                    "BoardSize:15, Board:'DCBA', GameName:'game', " +
                    "Score:234, Info:'', " +
                    "Scores:'{'petya@mail.com':234}', " +
                    "HeroesData:'{" +
                        "'coordinates':{'petya@mail.com':{'coordinate':{'x':3,'y':4},'level':0,'multiplayer':false}}," +
                        "'group':['petya@mail.com']," +
                        "'readableNames':{'petya@mail.com':'readable_petya'}" +
                        "}']}",
                data.toString().replaceAll("\"", "'"));
    }

    @Test
    public void shouldNewUserHasZeroScoresWhenLastLoggedIfOtherPlayerHasPositiveScores() {
        // given
        Player vasya = createPlayer(VASYA);
        when(playerScores1.getScore()).thenReturn(10);

        // when
        Player petya = createPlayer(PETYA);

        // then
        assertEquals(0, petya.getScore());
    }

    @Test
    public void shouldNewUserHasMinimumPlayersScoresWhenLastLoggedIfSomePlayersHasNegativeScores() {
        // given
        Player vasya = createPlayer(VASYA);
        when(playerScores1.getScore()).thenReturn(10);

        Player petya = createPlayer(PETYA);
        assertEquals(10, vasya.getScore());
        assertEquals(0, petya.getScore());

        // when
        when(playerScores1.getScore()).thenReturn(5);
        when(playerScores2.getScore()).thenReturn(10);
        Player katya = createPlayer(KATYA);
        assertEquals(5, vasya.getScore());
        assertEquals(10, petya.getScore());

        assertEquals(0, katya.getScore());
    }

    @Test
    public void shouldNewUserHasMinimumPlayersScoresWhenLastLoggedAfterNextStep() {
        // given
        Player vasya = createPlayer(VASYA);
        Player petya = createPlayer(PETYA);
        when(playerScores1.getScore()).thenReturn(5);
        when(playerScores2.getScore()).thenReturn(10);

        // when
        playerService.tick();

        Player katya = createPlayer(KATYA);

        // then
        assertEquals(5, vasya.getScore());
        assertEquals(10, petya.getScore());
        assertEquals(0, katya.getScore());
    }

    @Test
    public void shouldRemoveAllPlayerDataWhenRemovePlayer() {
        // given
        createPlayer(VASYA);
        createPlayer(PETYA);

        // when
        playerService.remove(VASYA);

        //then
        assertEquals(NullPlayer.INSTANCE, playerService.get(VASYA));
        assertNotSame(NullPlayer.INSTANCE, playerService.get(PETYA));
        assertEquals(1, playerGames.size());
    }

    @Test
    public void shouldFindPlayerWhenGet() {
        // given
        Player newPlayer = createPlayer(VASYA);

        // when
        Player player = playerService.get(VASYA);

        //then
        assertSame(newPlayer, player);
    }

    @Test
    public void shouldGetNullPlayerWhenGetByNotExistsIp() {
        // given
        createPlayer(VASYA);

        // when
        Player player = playerService.get(KATYA);

        //then
        assertEquals(NullPlayer.class, player.getClass());
    }

    private Player createPlayer(String userName) {
        Player player = playerService.register(userName, getCallbackUrl(userName),
                userName + "room", userName + "game");
        players.add(player);

        if (player != NullPlayer.INSTANCE) {
            verify(gameType, atLeastOnce()).createGame(anyInt());
        }

        return player;
    }

    private String getCallbackUrl(String userName) {
        return "http://" + userName + ":1234";
    }

    private String getBoardFor(Player vasya) {
        Map sentScreens = screenSendCaptor.getValue();
        Map<Player, PlayerData> value = sentScreens;
        return value.get(vasya).getBoard().toString();
    }

    private void assertSentToPlayers(Player ... players) throws IOException {
        verify(screenController).requestControlToAll(screenSendCaptor.capture());
        Map sentScreens1 = screenSendCaptor.getValue();
        Map sentScreens = sentScreens1;
        assertEquals(players.length, sentScreens.size());
        for (Player player : players) {
            assertTrue(sentScreens.containsKey(player));
        }
    }

    private void assertHostsCaptured(String ... hostUrls) {
        assertEquals(hostUrls.length, playerCaptor.getAllValues().size());
        for (int i = 0; i < hostUrls.length; i++) {
            String hostUrl = hostUrls[i];
            assertEquals(hostUrl, playerCaptor.getAllValues().get(i).getCallbackUrl());
        }
    }

    @Test
    public void shouldCreatePlayerFromSavedPlayerGameWhenPlayerNotRegisterYet() {
        // given
        PlayerSave save = new PlayerSave(VASYA, getCallbackUrl(VASYA), "room", "game", 100, null);

        // when
        playerService.register(save);

        // then
        verify(gameType).getPlayerScores(100);
        when(playerScores1.getScore()).thenReturn(100);

        Player player = playerService.get(VASYA);

        assertVasya(player);
        assertEquals(100, player.getScore());
        assertEquals(null, player.getMessage());
    }

    @Test
    public void shouldUpdatePlayerFromSavedPlayerGameWhenPlayerAlreadyRegistered_whenOtherGameType() {
        // given
        Player registeredPlayer = createPlayer(VASYA);
        assertEquals(VASYA_URL, registeredPlayer.getCallbackUrl());

        PlayerSave save = new PlayerSave(VASYA, getCallbackUrl(VASYA), "other_room", "other_game", 200, null);

        // when
        playerService.register(save);

        // then
        verify(gameType).getPlayerScores(200);
        when(playerScores2.getScore()).thenReturn(200);

        Player player = playerService.get(VASYA);

        assertVasya(player);
        assertEquals(200, player.getScore());
        assertEquals(null, player.getMessage());
    }

    @Test
    public void shouldNotUpdatePlayerFromSavedPlayerGameWhenPlayerAlreadyRegistered_whenSameGameType() {
        // given
        Player registeredPlayer = createPlayer(VASYA);
        assertEquals(VASYA_URL, registeredPlayer.getCallbackUrl());
        assertEquals(0, registeredPlayer.getScore());

        PlayerSave save = new PlayerSave(VASYA, getCallbackUrl(VASYA), "room", "game", 200, null);

        // when
        playerService.register(save);

        // then
        verify(gameType).getPlayerScores(0);
        when(playerScores2.getScore()).thenReturn(0);

        Player player = playerService.get(VASYA);

        assertVasya(player);
        assertEquals(0, player.getScore());
        assertEquals(null, player.getMessage());
    }

    private void assertVasya(Player player) {
        assertNotSame(NullPlayer.class, player.getClass());
        assertEquals(VASYA, player.getName());
        assertEquals(null, player.getPassword());
        assertEquals(null, player.getCode());
        assertEquals(VASYA_URL, player.getCallbackUrl());
    }

    private void assertPetya(Player player) {
        assertNotSame(NullPlayer.class, player.getClass());
        assertEquals(PETYA, player.getName());
        assertEquals(null, player.getPassword());
        assertNull(player.getCode());
        assertEquals(PETYA_URL, player.getCallbackUrl());
    }

    @Test
    public void shouldSendScoresAndLevelUpdateInfoInfoToPlayer_ifNoInfo() throws IOException {
        // given
        createPlayer(VASYA);

        // when, then
        checkInfo("");
    }


    @Test
    public void shouldSendScoresAndLevelUpdateInfoInfoToPlayer_ifPositiveValue() throws IOException {
        // given
        informationCollector = createPlayer(VASYA).getEventListener();

        // when, then
        when(playerScores1.getScore()).thenReturn(10, 13);
        informationCollector.levelChanged(new LevelProgress(2, 1, 1));
        informationCollector.event("event1");
        checkInfo("+3, Level 2");
    }

    @Test
    public void shouldSendScoresAndLevelUpdateInfoInfoToPlayer_ifNegativeValue() throws IOException {
        // given
        informationCollector = createPlayer(VASYA).getEventListener();

        // when, then
        when(playerScores1.getScore()).thenReturn(10, 9);
        informationCollector.event("event1");
        when(playerScores1.getScore()).thenReturn(10, 8);
        informationCollector.event("event2");
        checkInfo("-1, -2");
    }

    @Test
    public void shouldSendScoresAndLevelUpdateInfoInfoToPlayer_ifAdditionalInfo() throws IOException {
        // given
        informationCollector = createPlayer(VASYA).getEventListener();

        // when, then
        when(playerScores1.getScore()).thenReturn(10, 13);
        informationCollector.event("event1");
        checkInfo("+3");
    }

    private void checkInfo(String expected) throws IOException {
        playerService.tick();

        verify(screenController, atLeast(1)).requestControlToAll(screenSendCaptor.capture());
        Map sentScreens = screenSendCaptor.getValue();
        Map<ScreenRecipient, PlayerData> data = sentScreens;
        Iterator<Map.Entry<ScreenRecipient, PlayerData>> iterator = data.entrySet().iterator();
        Map.Entry<ScreenRecipient, PlayerData> next = iterator.next();
        ScreenRecipient key = next.getKey();
        assertEquals(expected, next.getValue().getInfo());
    }

    @Test
    public void shouldInformGameWhenUnregisterPlayer() {
        createPlayer(VASYA);
        createPlayer(PETYA);

        Game game1 = createGame(gameField(VASYA));
        Game game2 = createGame(gameField(PETYA));
        setNewGames(game1, game2);

        playerService.removeAll();

        verify(game1).close();
        verify(game2).close();
    }

    @Test
    public void shouldTickForEachGamesWhenSeparateBordersGameType() {
        // given
        createPlayer(VASYA);
        createPlayer(PETYA);

        Game game1 = createGame(gameField(VASYA));
        Game game2 = createGame(gameField(PETYA));

        setNewGames(game1, game2);

        setup(game1);
        setup(game2);

        when(gameType.getMultiplayerType()).thenReturn(MultiplayerType.SINGLE);

        // when
        playerService.tick();

        // then
        verify(game1.getField()).quietTick();
        verify(game2.getField()).quietTick();
    }

    private Game createGame(GameField gameField) {
        Game game = mock(Game.class);
        when(game.getField()).thenReturn(gameField);
        return game;
    }

    @Test
    public void shouldContinueTicksWhenException() {
        // given
        createPlayer(VASYA);
        createPlayer(PETYA);

        Game game1 = createGame(gameField(VASYA));
        Game game2 = createGame(gameField(PETYA));

        setNewGames(game1, game2);

        setup(game1);
        setup(game2);
        GameField field1 = game1.getField();
        GameField field2 = game2.getField();
        doThrow(new RuntimeException()).when(field1).tick();

        when(gameType.getMultiplayerType()).thenReturn(MultiplayerType.SINGLE);

        // when
        playerService.tick();

        // then
        verify(field1).quietTick();
        verify(field2).quietTick();
    }

    private void setNewGames(Game... games) {
        List<PlayerGame> list = getPlayerGames();
        for (int index = 0; index < list.size(); index++) {
            PlayerGame playerGame = list.get(index);

            field("game").ofType(Game.class).in(playerGame).set(games[index]);
        }
    }

    private List<PlayerGame> getPlayerGames() {
        return field(PlayerGames.Fields.all).ofType(List.class).in(playerGames).get();
    }

    @Test
    public void shouldTickForOneGameWhenSingleBordersGameType() {
        // given
        createPlayer(VASYA);
        createPlayer(PETYA);

        Game game1 = createGame(gameField(VASYA));
        Game game2 = createGame(gameField(PETYA));
        setNewGames(game1, game2);

        setup(game1);
        setup(game2);
        GameField field1 = game1.getField();
        when(game2.getField()).thenReturn(field1);
        doThrow(new RuntimeException()).when(field1).tick();

        when(gameType.getMultiplayerType()).thenReturn(MultiplayerType.MULTIPLE);   // ?????? ?????????????? ?? ?????????????? ????????????

        // when
        playerService.tick();

        // then
        verify(field1, times(1)).quietTick();
    }

    @Test
    public void shouldContinueTicksWhenExceptionInNewGame() {
        // given
        createPlayer(VASYA);
        createPlayer(PETYA);

        Game game1 = createGame(gameField(VASYA));
        Game game2 = createGame(gameField(PETYA));
        setNewGames(game1, game2);

        setup(game1);
        setup(game2);

        when(game1.isGameOver()).thenReturn(true);
        doThrow(new RuntimeException()).when(game1).newGame();

        // when
        playerService.tick();

        // then
        verify(game1.getField()).quietTick();
        verify(game2.getField()).quietTick();
    }

    @Test
    public void shouldContinueTicksWhenExceptionInPlayerGameTick() {
        // given
        createPlayer(VASYA);

        Game game1 = createGame(gameField(VASYA));
        setNewGames(game1);

        setup(game1);

        List list = Reflection.field(PlayerGames.Fields.all).ofType(List.class).in(playerGames).get();
        PlayerGame playerGame = (PlayerGame)list.remove(0);
        PlayerGame spy = spy(playerGame);
        list.add(spy);

        doThrow(new RuntimeException()).when(spy).tick();

        // when
        playerService.tick();

        // then
        verify(game1.getField()).quietTick();
    }

    @Test
    public void shouldContinueTicksWhenException_caseMultiplayer() {
        // given
        createPlayer(VASYA);
        createPlayer(PETYA);

        Game game1 = createGame(gameField(VASYA));
        Game game2 = createGame(gameField(PETYA));
        setNewGames(game1, game2);

        setup(game1);
        setup(game2);

        when(gameType.getMultiplayerType()).thenReturn(MultiplayerType.MULTIPLE); // ?????? ?????????????? ?? ?????????????? ????????????
        GameField field1 = game1.getField();
        when(game2.getField()).thenReturn(field1);

        // when
        playerService.tick();

        // then
        verify(field1, times(1)).quietTick();
    }

    @Test
    public void shouldJoystickWorkAfterFirstGameOver_lazyJoystick() throws IOException {
        // given
        createPlayer(VASYA);

        verify(gameField(VASYA)).newGame(gamePlayer(VASYA));
        reset(gameField(VASYA));

        Joystick j = getJoystick(playerController);

        // when
        j.down();
        playerService.tick();

        // then
        verify(joystick(VASYA)).down();
        verifyNoMoreInteractions(joystick(VASYA));

        // when
        when(gamePlayer(VASYA).isAlive()).thenReturn(false);
        playerService.tick();
        verify(gameField(VASYA)).newGame(gamePlayer(VASYA));

        Joystick joystick2 = mock(Joystick.class);
        when(gamePlayer(VASYA).getJoystick()).thenReturn(joystick2);

        // when
        j.up();
        playerService.tick();

        // then
        verify(joystick2).up();
        verifyNoMoreInteractions(joystick(VASYA));
    }

    private Joystick joystick(String player) {
        return joysticks.get(getIndexOf(player));
    }

    private GamePlayer gamePlayer(String player) {
        return gamePlayers.get(getIndexOf(player));
    }

    private GameField gameField(String player) {
        return gameFields.get(getIndexOf(player));
    }

    private int getIndexOf(String player) {
        Player found = players.stream()
                .filter(p -> p.getName().equals(player))
                .findFirst()
                .orElse(null);
        int indexOf = players.indexOf(found);
        if (indexOf == -1) {
            throw new IllegalArgumentException("Player ???? ????????????");
        }
        return indexOf;
    }


    @Test
    public void shouldAllJoystickCommandsWorks_lazyJoystick() throws IOException {
        // given
        createPlayer(VASYA);

        Joystick j = getJoystick(playerController);

        // when
        j.down();
        j.up();
        j.left();
        j.right();
        verifyNoMoreInteractions(joystick(VASYA));

        playerService.tick();

        // then
        Joystick joystick = joystick(VASYA);
        InOrder inOrder = inOrder(joystick);

        inOrder.verify(joystick).down();
        inOrder.verify(joystick(VASYA)).up();
        inOrder.verify(joystick(VASYA)).left();
        inOrder.verify(joystick(VASYA)).right();
        verifyNoMoreInteractions(joystick(VASYA));
    }

    @Test
    public void shouldFirstActWithDirection_lazyJoystick() throws IOException {
        // given
        createPlayer(VASYA);
        Joystick joystick = joystick(VASYA);

        Joystick j = getJoystick(playerController);

        // when
        j.act(1, 2, 3);
        j.up();
        j.left();
        j.right();
        verifyNoMoreInteractions(joystick);

        playerService.tick();

        // then
        InOrder inOrder = inOrder(joystick);
        inOrder.verify(joystick).act(1, 2, 3);
        inOrder.verify(joystick).up();
        inOrder.verify(joystick).left();
        inOrder.verify(joystick).right();
        verifyNoMoreInteractions(joystick);
    }

    @Test
    public void shouldLastActWithDirection_lazyJoystick() throws IOException {
        // given
        createPlayer(VASYA);
        Joystick joystick = joystick(VASYA);

        Joystick j = getJoystick(playerController);

        // when
        j.right();
        j.left();
        j.up();
        j.act(5);
        j.act(5, 6);
        verifyNoMoreInteractions(joystick);

        playerService.tick();

        // then
        InOrder inOrder = inOrder(joystick);
        inOrder.verify(joystick).right();
        inOrder.verify(joystick).left();
        inOrder.verify(joystick).up();
        inOrder.verify(joystick).act(5);
        inOrder.verify(joystick).act(5, 6);
        verifyNoMoreInteractions(joystick);
    }

    @Test
    public void shouldMixed_lazyJoystick() throws IOException {
        // given
        createPlayer(VASYA);
        Joystick joystick = joystick(VASYA);

        Joystick j = getJoystick(playerController);

        // when
        j.right();
        j.left();
        j.up();
        j.act(5);
        j.act(5, 6);
        j.left();
        verifyNoMoreInteractions(joystick);

        playerService.tick();

        // then
        InOrder inOrder = inOrder(joystick);
        inOrder.verify(joystick).right();
        inOrder.verify(joystick).left();
        inOrder.verify(joystick).up();
        inOrder.verify(joystick).act(5);
        inOrder.verify(joystick).act(5, 6);
        inOrder.verify(joystick).left();
        verifyNoMoreInteractions(joystick);
    }

    @Test
    public void shouldMixed2_lazyJoystick() throws IOException {
        // given
        createPlayer(VASYA);
        Joystick joystick = joystick(VASYA);

        Joystick j = getJoystick(playerController);

        // when
        j.right();
        j.left();
        j.up();
        j.act(5);
        j.act(5, 6);
        j.left();
        j.act(7);
        verifyNoMoreInteractions(joystick);

        playerService.tick();

        // then
        InOrder inOrder = inOrder(joystick);
        inOrder.verify(joystick).right();
        inOrder.verify(joystick).left();
        inOrder.verify(joystick).up();
        inOrder.verify(joystick).act(5);
        inOrder.verify(joystick).act(5, 6);
        inOrder.verify(joystick).left();
        inOrder.verify(joystick).act(7);
        verifyNoMoreInteractions(joystick);
    }

    @Test
    public void shouldOnlyAct_lazyJoystick() throws IOException {
        // given
        createPlayer(VASYA);
        Joystick joystick = joystick(VASYA);

        Joystick j = getJoystick(playerController);

        // when
        j.act();
        verifyNoMoreInteractions(joystick);

        playerService.tick();

        // then
        verify(joystick).act();
        verifyNoMoreInteractions(joystick);
    }

    private void setup(Game game) {
        when(game.getBoardAsString()).thenReturn("123");
        when(game.isGameOver()).thenReturn(false);
        when(game.getHero()).thenReturn(new HeroDataImpl(pt(0, 0),
                MultiplayerType.SINGLE.isSingleplayer()));
    }

    @Test
    public void shouldGetAll() {
        // given
        createPlayer(VASYA);
        createPlayer(PETYA);

        // when
        List<Player> all = playerService.getAll();

        // then
        assertEquals(2, all.size());
        Player player1 = all.get(0);
        Player player2 = all.get(1);

        assertVasya(player1);
        assertEquals(0, player1.getScore());
        assertNull(player1.getMessage());

        assertPetya(player2);
        assertEquals(0, player2.getScore());
        assertNull(player2.getMessage());
    }

    @Test
    public void shouldContains() {
        // given
        createPlayer(VASYA);

        // when then
        assertTrue(playerService.contains(VASYA));
        assertFalse(playerService.contains(PETYA));
    }

    @Test
    public void shouldGetJoystick() {
        // given
        createPlayer(VASYA);
        Joystick joystick1 = joystick(VASYA);
        createPlayer(PETYA);
        Joystick joystick2 = joystick(PETYA);

        // when then
        assertSame(joystick1, ((LockedJoystick)playerService.getJoystick(VASYA)).getWrapped());
        assertSame(joystick2, ((LockedJoystick)playerService.getJoystick(PETYA)).getWrapped());
        assertSame(NullJoystick.INSTANCE, playerService.getJoystick(KATYA));
    }

    @Test
    public void shouldCleanAllScores() {
        // given
        createPlayer(VASYA);
        createPlayer(PETYA);

        verify(gameField(VASYA)).newGame(any());
        verify(gameField(PETYA)).newGame(any());

        // when
        playerService.cleanAllScores();

        // then
        verify(playerScores1).clear();
        verify(playerScores2).clear();
        verifyNoMoreInteractions(playerScores3);

        verify(gameField(VASYA)).clearScore();
        verify(gameField(PETYA)).clearScore();

        verify(semifinal).clean();
    }

    @Test
    public void shouldGetRandom_other() {
        createPlayer(VASYA);
        createPlayer(PETYA);

        assertEquals(VASYA, playerService.getRandom(gameType.name()).getName());
    }

    @Test
    public void shouldUpdateAll_whenNullInfos() {
        // given
        createPlayer(VASYA);
        createPlayer(PETYA);

        // when
        playerService.updateAll(null);

        // then
        List<Player> all = playerService.getAll();
        assertVasyaAndPetya(all);
    }

    @Test
    public void shouldUpdateAll_mainCase() {
        // given
        createPlayer(VASYA);
        createPlayer(PETYA);

        // when
        List<PlayerInfo> infos = new LinkedList<>();
        infos.add(new PlayerInfo("new-vasya", "new-pass1", "new-url1", "new-game"));
        infos.add(new PlayerInfo("new-petya", "new-pass2", "new-url2", "new-game"));
        playerService.updateAll(infos);

        // then
        List<Player> all = playerService.getAll();
        assertUpdatedVasyaAndPetya(all);
    }

    @Test
    public void shouldSendPlayerNameToGame() {
        // given
        createPlayer(VASYA);
        createPlayer(PETYA);

        // when then
        // TODO implement
    }

    private void assertUpdatedVasyaAndPetya(List<Player> all) {
        assertEquals("[new-vasya, new-petya]", all.toString());

        Player player1 = all.get(0);
        assertEquals("new-url1", player1.getCallbackUrl());
        assertNull(player1.getCode());
        assertEquals("game", player1.getGameName());
        assertEquals(null, player1.getPassword());

        Player player2 = all.get(1);
        assertEquals("new-url2", player2.getCallbackUrl());
        assertNull(player2.getCode());
        assertEquals("game", player1.getGameName());
        assertEquals(null, player2.getPassword());
    }

    @Test
    public void shouldUpdateAll_removeNullUsers() {
        // given
        createPlayer(VASYA);
        createPlayer(PETYA);

        // when
        List<PlayerInfo> infos = new LinkedList<>();
        infos.add(new PlayerInfo("new-vasya", "new-pass1", "new-url1", "new-game"));
        infos.add(new PlayerInfo("new-petya", "new-pass2", "new-url2", "new-game"));
        infos.add(new PlayerInfo(null, "new-pass2", "new-url2", "new-game"));
        playerService.updateAll(infos);

        // then
        List<Player> all = playerService.getAll();
        assertUpdatedVasyaAndPetya(all);
    }

    @Test
    public void shouldUpdateAll_exceptionIfCountUsersNotEqual() {
        // given
        createPlayer(VASYA);
        createPlayer(PETYA);

        List<PlayerInfo> infos = new LinkedList<>();
        infos.add(new PlayerInfo("new-vasya", "new-pass1", "new-url1", "new-game"));

        try {
            // when
            playerService.updateAll(infos);
            fail();
        } catch (Exception e) {
            // then
            assertEquals("java.lang.IllegalArgumentException: Diff players count", e.toString());
        }

        List<Player> all = playerService.getAll();
        assertVasyaAndPetya(all);
    }

    private void assertVasyaAndPetya(List<Player> all) {
        assertEquals("[vasya@mail.com, petya@mail.com]", all.toString());

        Player player1 = all.get(0);
        assertEquals(VASYA_URL, player1.getCallbackUrl());
        assertNull(player1.getCode());
        assertEquals(null, player1.getPassword());

        Player player2 = all.get(1);
        assertEquals(PETYA_URL, player2.getCallbackUrl());
        assertNull(player2.getCode());
        assertEquals(null, player2.getPassword());
    }

    @Test
    public void shouldUpdateAll_loadFromSave() {
        // given
        Player player1 = createPlayer(VASYA);
        Player player2 = createPlayer(PETYA);

        // when
        List<PlayerInfo> infos = new LinkedList<PlayerInfo>(){{
            add(new PlayerInfo(player1){{
                setData("{\"some\":\"data1\"}");
            }});
            add(new PlayerInfo(player2){{
                setData("{\"some\":\"data2\"}");
            }});
        }};
        playerService.updateAll(infos);

        // then
        assertSaveLoaded(player1, "[{\"some\":\"data1\"}]");
        assertSaveLoaded(player2, "[{\"some\":\"data2\"}]");
    }

    private void assertSaveLoaded(Player player, String save) {
        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(playerGames).setLevel(eq(player.getName()), captor.capture());
        assertEquals(save, captor.getAllValues().toString());
    }

    @Test
    public void shouldUpdateAll_loadFromSave_onlyIfSaveIsNotSame() {
        // given
        Player player1 = createPlayer(VASYA);
        Player player2 = createPlayer(PETYA);

        // when
        List<PlayerInfo> infos = new LinkedList<PlayerInfo>(){{
            add(new PlayerInfo(player1){{
                setData("{\"some\":\"data1\"}");
            }});
            add(new PlayerInfo(player2){{
                setData("{}"); // same
            }});
        }};
        playerService.updateAll(infos);

        // then
        assertSaveLoaded(player1, "[{\"some\":\"data1\"}]");
        assertSaveNotLoaded(player2);
    }

    @Test
    public void shouldUpdateAll_loadFromSave_onlyIfSaveIsNotEmptyOrNull() {
        // given
        Player player1 = createPlayer(VASYA);
        Player player2 = createPlayer(PETYA);
        Player player3 = createPlayer(KATYA);
        Player player4 = createPlayer(OLIA);

        // when
        List<PlayerInfo> infos = new LinkedList<PlayerInfo>(){{
            add(new PlayerInfo(player1){{
                setData("{\"some\":\"data1\"}");
            }});
            add(new PlayerInfo(player2){{
                setData(""); // empty
            }});
            add(new PlayerInfo(player3){{
                setData(null); // null
            }});
            add(new PlayerInfo(player4){{
                setData("null"); // "null"
            }});
        }};
        playerService.updateAll(infos);

        // then
        assertSaveLoaded(player1, "[{\"some\":\"data1\"}]");
        assertSaveNotLoaded(player2);
        assertSaveNotLoaded(player3);
        assertSaveNotLoaded(player4);
    }

    private void assertSaveNotLoaded(Player player) {
        verify(playerGames, never()).setLevel(eq(player.getName()), any(JSONObject.class));
    }

    @Test
    public void shouldLogActionsOnTick() {
        // given
        createPlayer(VASYA);

        // when
        playerService.tick();

        // then
        verify(actionLogger).log(playerGames);
//        verifyNoMoreInteractions(actionLogger);
    }

    @Test
    public void shouldTickSemifinal_whenTick() {
        // given
        createPlayer(VASYA);
        createPlayer(PETYA);

        // when
        playerService.tick();

        // then
        verify(semifinal, only()).tick();
    }

    private Joystick getJoystick(Controller controller) {
        ArgumentCaptor<Joystick> joystickCaptor = ArgumentCaptor.forClass(Joystick.class);
        verify(controller).registerPlayerTransport(any(Player.class), joystickCaptor.capture());
        return joystickCaptor.getValue();
    }

    @Test
    public void testReloadAI() {
        // given
        WebSocketRunner.ATTEMPTS = 0;
        WebSocketRunner.TIMEOUT = 100;

        when(gameType.getAI()).thenReturn((Class)AISolverStub.class);
        when(gameType.getBoard()).thenReturn((Class)BoardStub.class);

        String gameName = createPlayer(VASYA).getGameName();

        verify(gameType, times(1)).getAI();
        verify(gameType, times(1)).getBoard();

        // when
        playerService.reloadAI(VASYA);

        // then
        verify(gameType, times(2)).getAI();
        verify(gameType, times(2)).getBoard();

        PlayerGame playerGame = playerGames.get(VASYA);
        assertEquals(gameName, playerGame.getPlayer().getGameName());
        Player player = playerGame.getPlayer();
        assertEquals(VASYA, player.getName());
        assertNotNull(VASYA, player.getAi());
    }

    @Test
    public void testLoadPlayersFromSaveAndLoadAI() {
        // given
        when(gameType.getAI()).thenReturn((Class)AISolverStub.class);
        when(gameType.getBoard()).thenReturn((Class)BoardStub.class);
        PlayerSave save = new PlayerSave(VASYA_AI, getCallbackUrl(VASYA_AI), "room", "game", 100, null);

        // when
        playerService.register(save);

        // then
        verify(gameType).getAI();
        verify(gameType).getBoard();

        PlayerGame playerGame = playerGames.get(VASYA_AI);
        assertEquals("game", playerGame.getPlayer().getGameName());
        Player player = playerGame.getPlayer();
        assertEquals(VASYA_AI, player.getName());
        assertNotNull(VASYA, player.getAi());

    }

}
