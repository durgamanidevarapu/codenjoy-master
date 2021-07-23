package com.codenjoy.dojo.icancode.model.interfaces;

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


import com.codenjoy.dojo.services.State;
import com.codenjoy.dojo.icancode.model.Elements;
import com.codenjoy.dojo.icancode.model.Player;
import com.codenjoy.dojo.icancode.model.enums.FeatureItem;

import java.util.List;

/**
 * Created by Mikhail_Udalyi on 01.07.2016.
 */
public interface IItem extends State<Elements, Player> {
    void action(IItem item);

    ICell getCell();

    List<IItem> getItemsInSameCell();

    void setCell(ICell value);

    boolean hasFeatures(FeatureItem[] features);

    void removeFromCell();
}
