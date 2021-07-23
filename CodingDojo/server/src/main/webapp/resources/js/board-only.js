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

pages = pages || {};

pages.boardOnly = function() {
    game.enableDonate = false;
    game.enableJoystick = false;
    game.enableAlways = false;
    game.enablePlayerInfo = false;
    game.enablePlayerInfoLevel = false;
    game.enableLeadersTable = false;
    game.enableForkMe = false;
    game.enableInfo = false;
    game.enableHotkeys = true;
    game.enableAdvertisement = false;
    game.showBody = true;
    game.sprites = null;
    game.heroInfo = null;
    
    if (window.location.href.includes("click=true")) {
        game.canvasCursor = 'pointer';
    
        $('body').click(function() {
            window.open(window.location.href.replace('only=true', 'only=false'));            
        });
    }

    pages.board();
}