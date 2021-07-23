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


import com.codenjoy.dojo.services.nullobj.NullGameType;
import com.codenjoy.dojo.services.printer.CharElements;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.reflections.Reflections;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Component("gameService")
public class GameServiceImpl implements GameService {

    public static final String ROOMS_SEPARATOR = "-";
    
    private Map<String, GameType> cache = new TreeMap<>();

    public GameServiceImpl() {
        for (Class<? extends GameType> clazz : allGames()) {
            GameType gameType = loadGameType(clazz);
            cache.put(gameType.name(), gameType);
        }
    }

    private List<Class> allGames() {
        List<Class> result = new LinkedList<>(
                findInPackage("com.codenjoy.dojo"));

        result.sort(Comparator.comparing(Class::getName));

        result.remove(NullGameType.class);
        result.remove(AbstractGameType.class);

        remove(result,
                it -> ConstructorUtils.getMatchingAccessibleConstructor(it) == null);

        remove(result, it -> Stream.of("chess", "sokoban")
                .anyMatch(name -> it.getPackage().toString().contains(name)));

        return result;
    }

    private void remove(List<Class> result, Predicate<Class> predicate) {
        result.removeAll(result.stream()
                .filter(predicate)
                .collect(Collectors.toList()));
    }

    protected Collection<? extends Class> findInPackage(String packageName) {
        return new Reflections(packageName).getSubTypesOf(GameType.class);
    }

    @Override
    public List<String> getGameNames() {
        return new LinkedList<>(cache.keySet());
    }

    @Override
    public List<String> getOnlyGameNames() {
        return getGameNames().stream()
                .map(GameServiceImpl::removeNumbers)
                .collect(Collectors.toList());
    }

    public static String removeNumbers(String gameName) {
        return gameName.split(ROOMS_SEPARATOR)[0];
    }

    @Override
    public Map<String, List<String>> getSpritesNames() {
        return getStringListMap(plot -> plot.name().toLowerCase());
    }

    @Override
    public Map<String, List<String>> getSpritesValues() {
        return getStringListMap(plot -> String.valueOf(plot.ch()));
    }

    // TODO может сделать универсальную версию метода с CharElements и ну его два вурхних метода?
    @Override
    public Map<String, List<String>> getSprites() {
        return getStringListMap(plot -> plot.name().toLowerCase() + "=" + plot.ch());
    }

    private Map<String, List<String>> getStringListMap(Function<CharElements, String> mapper) {
        return cache.entrySet().stream()
                .map(entry -> 
                    new HashMap.SimpleEntry<>(
                            entry.getValue().name(),
                            Arrays.stream(entry.getValue().getPlots())
                                    .map(mapper)
                                    .collect(toList())
                    )
                )
                .collect(toMap(
                        AbstractMap.SimpleEntry::getKey,
                        AbstractMap.SimpleEntry::getValue
                ));
    }

    @SneakyThrows
    private GameType loadGameType(Class<? extends GameType> gameType) {
        return gameType.newInstance();
    }

    @Override
    public GameType getGame(String name) {
        if (cache.containsKey(name)) {
            return cache.get(name);
        }

        return NullGameType.INSTANCE;
    }

    @Override
    public String getDefaultGame() {
        return getGameNames().iterator().next();
    }
}
