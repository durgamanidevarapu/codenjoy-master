package com.codenjoy.dojo.services.dao;

/*-
 * #%L
 * Codenjoy - it's a dojo-like platform from developers to developers.
 * %%
 * Copyright (C) 2018 - 2019 Codenjoy
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

import com.codenjoy.dojo.services.ConfigProperties;
import com.codenjoy.dojo.services.DLoggerFactory;
import com.codenjoy.dojo.services.entity.server.PlayerDetailInfo;
import com.codenjoy.dojo.services.entity.server.PlayerInfo;
import com.codenjoy.dojo.services.entity.server.User;
import com.codenjoy.dojo.services.hash.Hash;
import com.codenjoy.dojo.services.httpclient.GameClientResolver;
import com.codenjoy.dojo.services.httpclient.GameServerClientException;
import com.codenjoy.dojo.web.controller.GlobalExceptionHandler;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GameServer {

    private static Logger logger = DLoggerFactory.getLogger(GameServer.class);

    @Autowired ConfigProperties config;
    @Autowired GameClientResolver gameClientResolver;

    public List<PlayerInfo> getPlayersInfos(String server) {
        return gameClientResolver.resolveClient(server).getPlayerInfos(config.getGameType());
    }

    public String createNewPlayer(String server, String email, String name,
                                  String password, String callbackUrl,
                                  String score, String save)
    {
        String id = config.getId(email);
        String code = Hash.getCode(email, password);

        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Create new player {} ({}) for '{}' on server {} with save {} and score {}",
                        id, code, name, server, save, score);
            }

            PlayerDetailInfo player = new PlayerDetailInfo(
                id,
                name,
                callbackUrl,
                config.getGameType(),
                score,
                save,
                new User(
                    id,
                    email,
                    name,
                    1,
                    password,
                    code,
                    null)
            );

            return gameClientResolver.resolveClient(server).registerPlayer(player);
        } catch (GameServerClientException e) {
            String message = "Cant create new player. Status is: " + e.getMessage();
            logger.error(message);
            throw e;
        }
    }

    public boolean existsOnServer(String server, String email) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Check is player {} exists on server {}",
                        email, server);
            }

            return gameClientResolver.resolveClient(server).checkPlayerExists(email);
        } catch (GameServerClientException e) {
            logger.error("Error check player exists on server: " + server, e);
            return false;
        }
    }

    public String clearScores(String server) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Clear all scores on server {}",
                        server);
            }

            gameClientResolver.resolveClient(server).clearScores();

            return "Successful";

        } catch (GameServerClientException e) {
            logger.error("Error clearing scores on server: " + server, e);

            return GlobalExceptionHandler.getPrintableMessage(e);
        }
    }

    public String gameEnable(String server, boolean enable) {
        String status = enable ? "start" : "stop";
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Set status {} of game on server {}",
                        status, server);
            }

            Boolean enabled = gameClientResolver.resolveClient(server).checkGameEnabled(enable);
            return "Successful; game: " + enabled;
        } catch (GameServerClientException e) {
            logger.error("Error " + status + " game on server: " + server, e);

            return GlobalExceptionHandler.getPrintableMessage(e);
        }
    }

    public Boolean remove(String server, String email, String code) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Remove player {} ({}) on server {}",
                        email, code, server);
            }
            Boolean removed = gameClientResolver.resolveClient(server).removePlayer(config.getId(email), code);
            return removed;
        } catch (GameServerClientException e) {
            String message = "Cant remove player. Status is: " + e.getMessage();
            logger.error(message);
            return false;
        }
    }


}
