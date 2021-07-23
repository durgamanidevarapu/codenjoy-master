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


import com.codenjoy.dojo.services.jdbc.JDBCTimeUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.sql.ResultSet;
import java.sql.SQLException;

@Getter
@ToString
@AllArgsConstructor
public class BoardLog {

    private long time;
    private String playerName;
    private String gameType;
    private Object score;
    private String board;
    private String command;

    public BoardLog(ResultSet resultSet) {
        try {
            time = JDBCTimeUtils.getTimeLong(resultSet);
            playerName = resultSet.getString("player_name");
            gameType = resultSet.getString("game_type");
            score = resultSet.getInt("score");
            board = resultSet.getString("board");
            command = resultSet.getString("command");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setBoard(String board) {
        this.board = board;
    }

}