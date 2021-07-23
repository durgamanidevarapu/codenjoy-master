package com.codenjoy.dojo.web.rest;

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


import com.codenjoy.dojo.client.CodenjoyContext;
import com.codenjoy.dojo.services.*;
import com.codenjoy.dojo.services.dao.ActionLogger;
import com.codenjoy.dojo.services.dao.Registration;
import com.codenjoy.dojo.services.nullobj.NullPlayer;
import com.codenjoy.dojo.web.controller.Validator;
import com.codenjoy.dojo.web.rest.pojo.PGameTypeInfo;
import com.codenjoy.dojo.web.rest.pojo.PPlayerWantsToPlay;
import com.codenjoy.dojo.web.rest.pojo.PScoresOf;
import com.codenjoy.dojo.web.rest.pojo.PlayerInfo;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

import static com.codenjoy.dojo.web.controller.Validator.CANT_BE_NULL;
import static com.codenjoy.dojo.web.controller.Validator.CAN_BE_NULL;

@RestController
@RequestMapping("/rest")
@AllArgsConstructor
public class RestBoardController {

    private GameService gameService;
    private RestRegistrationController registrationController;
    private RestGameController gameController;
    private PlayerService playerService;
    private Registration registration;
    private Validator validator;
    private PlayerGames playerGames;
    private PlayerGamesView playerGamesView;
    private TimerService timerService;
    private SaveService saveService;
    private ActionLogger actionLogger;

    @GetMapping("/context")
    public String getContext() {
        return "/" + CodenjoyContext.getContext();
    }

    @GetMapping("/player/{player}/{code}/level/{level}")
    public synchronized boolean changeLevel(@PathVariable("player") String emailOrId,
                                @PathVariable("code") String code,
                                @PathVariable("level") int level)
    {
        String id = validator.checkPlayerCode(emailOrId, code);

        playerGames.changeLevel(id, level);

        return true;
    }

    // TODO test me и вообще где это надо?
//    @GetMapping("/player/all/groups")
    public Map<String, List<List<String>>> getPlayersGroups() {
        Map<String, List<List<String>>> result = new HashMap<>();
        List<Player> players = playerService.getAll();
        List<List<String>> groups = playerGamesView.getGroups();
        for (List<String> group : groups) {
            String playerId = group.get(0);
            Player player = players.stream()
                    .filter(p -> p.getName().equals(playerId))
                    .findFirst()
                    .orElse(NullPlayer.INSTANCE);

            String gameName = player.getGameName();
            if (!result.containsKey(gameName)) {
                result.put(gameName, new LinkedList<>());
            }
            result.get(gameName).add(group);
        }
        return result;
    }

//    @GetMapping("/player/all/scores")
    public Map<String, Object> getPlayersScores() {
        return playerGamesView.getScores();
    }

    @GetMapping("/game/{gameName}/scores")
    public List<PScoresOf> getPlayersScoresForGame(@PathVariable("gameName") String gameName) {
        return playerGamesView.getScoresForGame(gameName);
    }

    // TODO test me
    @GetMapping("/room/{roomName}/scores")
    public List<PScoresOf> getPlayersScoresForRoom(@PathVariable("roomName") String roomName) {
        return playerGamesView.getScoresForRoom(roomName);
    }

    @GetMapping("/scores/clear")
    public boolean clearAllScores() {
        playerService.cleanAllScores();
        return true;
    }

    @GetMapping("/game/enabled/{enabled}")
    public boolean startStopGame(@PathVariable("enabled") boolean enabled) {
        if (enabled) {
            timerService.resume();
        } else {
            timerService.pause();
        }

        return timerService.isPaused();
    }

    // TODO test me
//    @GetMapping("/player/{player}/{code}/reset")
    public synchronized boolean reset(@PathVariable("player") String emailOrId, @PathVariable("code") String code){
        String id = validator.checkPlayerCode(emailOrId, code);

        if (!playerService.contains(id)) {
            return false;
        }

        saveService.save(id);
        Player player = playerService.get(id);

        boolean loaded = saveService.load(id);
        if (!loaded) {
            if (playerService.contains(id)) {
                playerService.remove(id);
            }
            playerService.register(new PlayerSave(player));
        }

        return true;
    }

    // TODO test me
    @GetMapping("/player/{player}/{code}/wantsToPlay/{gameName}")
    public synchronized PPlayerWantsToPlay playerWantsToPlay(
            @PathVariable("player") String emailOrId,
            @PathVariable("code") String code,
            @PathVariable("gameName") String gameName)
    {
        validator.checkPlayerName(emailOrId, CAN_BE_NULL);
        validator.checkCode(code, CAN_BE_NULL);
        validator.checkGameName(gameName, CANT_BE_NULL);

        String context = getContext();
        PGameTypeInfo gameType = gameController.type(gameName);
        boolean registered = registration.checkUser(emailOrId, code) != null;
        List<String> sprites = gameController.spritesNames(gameName);
        String alphabet = gameController.spritesAlphabet();
        List<PlayerInfo> players = registrationController.getGamePlayers(gameName);

        return new PPlayerWantsToPlay(context, gameType,
                registered, sprites, alphabet, players);
    }

    // TODO test me
    @GetMapping("/player/{player}/log/{time}")
    public List<BoardLog> changeLevel(@PathVariable("player") String emailOrId,
                                            @PathVariable("time") Long time)
    {
        validator.checkPlayerName(emailOrId, CANT_BE_NULL);

        String id = registration.checkUser(emailOrId);

        if (time == null || time == 0) {
            time = actionLogger.getLastTime(id);
        }

        List<BoardLog> result = actionLogger.getBoardLogsFor(id, time, 100);

        if (result.isEmpty()) {
            return Arrays.asList();
        }

        // TODO Как-то тут сложно
        GuiPlotColorDecoder decoder = new GuiPlotColorDecoder(gameService.getGame(result.get(0).getGameType()).getPlots());

        result.forEach(log -> {
            String board = log.getBoard().replaceAll("\n", "");
            log.setBoard((String) decoder.encodeForBrowser(board));
        });

        return result;
    }

    @GetMapping("/{gameName}/status")
    public Map<String, Object> checkGameIsActive(@PathVariable("gameName") String gameName) {
        return Collections.singletonMap("active", playerGames.getPlayers(gameName).size() > 0);
    }
}
