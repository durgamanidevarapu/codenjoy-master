package com.codenjoy.dojo.snakebattle.model.board;

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
import com.codenjoy.dojo.services.printer.BoardReader;
import com.codenjoy.dojo.services.settings.Parameter;
import com.codenjoy.dojo.snakebattle.model.Player;
import com.codenjoy.dojo.snakebattle.model.hero.Hero;
import com.codenjoy.dojo.snakebattle.model.level.Level;
import com.codenjoy.dojo.snakebattle.model.objects.*;
import com.codenjoy.dojo.snakebattle.services.Events;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.codenjoy.dojo.services.PointImpl.pt;
import static com.codenjoy.dojo.snakebattle.model.hero.Hero.NEXT_TICK;
import static java.util.stream.Collectors.toList;

public class SnakeBoard implements Field {

    private List<Wall> walls;
    private List<StartFloor> starts;
    private List<Apple> apples;
    private List<Stone> stones;
    private List<FlyingPill> flyingPills;
    private List<FuryPill> furyPills;
    private List<Gold> gold;

    private List<Player> players;
    private List<Player> theWalkingDead;

    private Timer startTimer;
    private Timer roundTimer;
    private int round;
    private Timer winnerTimer;

    private Parameter<Integer> roundsPerMatch;
    private Parameter<Integer> flyingCount;
    private Parameter<Integer> furyCount;
    private Parameter<Integer> stoneReduced;
    private Parameter<Integer> minTicksForWin;

    private int size;
    private Dice dice;

    public SnakeBoard(Level level, Dice dice, Timer startTimer, Timer roundTimer, Timer winnerTimer, Parameter<Integer> roundsPerMatch, Parameter<Integer> flyingCount, Parameter<Integer> furyCount, Parameter<Integer> stoneReduced, Parameter<Integer> minTicksForWin) {
        this.flyingCount = flyingCount;
        this.furyCount = furyCount;
        this.stoneReduced = stoneReduced;
        this.roundsPerMatch = roundsPerMatch;
        this.minTicksForWin = minTicksForWin;

        this.dice = dice;
        round = 0;
        walls = level.getWalls();
        starts = level.getStartPoints();
        apples = level.getApples();
        stones = level.getStones();
        flyingPills = level.getFlyingPills();
        furyPills = level.getFuryPills();
        gold = level.getGold();
        size = level.getSize();
        players = new LinkedList<>();
        theWalkingDead = new LinkedList<>();

        this.startTimer = startTimer.start();
        this.roundTimer = roundTimer.stop();
        this.winnerTimer = winnerTimer.stop();
    }

    @Override
    public void tick() {
        snakesClear();

        startTimer.tick(this::sendTimerStatus);
        roundTimer.tick(() -> {});
        winnerTimer.tick(() -> {});

        if (startTimer.justFinished()) {
            round++;
            players.forEach(p -> p.start(round));
            roundTimer.start();
        }

        if (!startTimer.done()) {
            return;
        }

        if (roundTimer.justFinished()) {
            rewardWinnersByTimeout();

            startTimer.start();
            return;
        }


        if (isNoOneOnBoard() || winnerTimer.justFinished()) {
            if (isLastOnBoard()) {
                reset(getLast());
            }

            startTimer.start();
            return;
        }

        if (!startTimer.unlimited() && winnerTimer.done() && isLastOnBoard()) {
            winnerTimer.start();
        }

        snakesMove();
        snakesFight();
        snakesEat();
        // после еды у змеек отрастают хвосты, поэтому столкновения нужно повторить
        // чтобы обработать ситуацию "кусь за растущий хвост", иначе eatTailThatGrows тесты не пройдут
        snakesFight();
        rewardTheWinner();
        setNewObjects();
    }

    private void rewardWinnersByTimeout() {
        Integer max = aliveActive().stream()
                .map(p -> p.getHero().size())
                .max(Comparator.comparingInt(i1 -> i1))
                .orElse(Integer.MAX_VALUE);

        aliveActive().forEach(p -> {
                    if (p.getHero().size() == max
                        && roundTimer.time() > minTicksForWin.getValue())
                    {
                        p.event(Events.WIN);
                    } else {
                        p.printMessage("Time is over");
                    }
                });

        aliveActive().forEach(player -> reset(player));
    }

    private void sendTimerStatus() {
        String pad = StringUtils.leftPad("", startTimer.countdown(), '.');
        String message = pad + startTimer.countdown() + pad;
        players.forEach(player -> player.printMessage(message));
    }

    private void snakesClear() {
        players.stream()
                .filter(p -> p.isActive() && !p.isAlive())
                .forEach(p -> p.getHero().clear());
    }

    @Override
    public void clearScore() {
        round = 0;

        players.forEach(p -> newGame(p));
    }

    private void setNewObjects() {
        int max = (players.size() / 2) + 1;
        int i = dice.next(50);
        if (i == 42 && furyPills.size() < max)
            setFuryPill(getFreeRandom());
        if (i == 32 && flyingPills.size() < max)
            setFlyingPill(getFreeRandom());
        if (i == 21 && gold.size() < max*2)
            setGold(getFreeRandom());
        if ((i == 11 && stones.size() < size / 2) || stones.isEmpty())
            setStone(getFreeRandom());
        if ((i < 10 && apples.size() < max*10) || apples.size() < max*2)
            setApple(getFreeRandom());
    }

    @Override
    public Point getFreeRandom() {
        return BoardUtils.getFreeRandom(size, dice, pt -> isFree(pt));
    }

    private void rewardTheWinner() {
        if (theWalkingDead.isEmpty()) {
            return;
        }
        theWalkingDead.clear();

        if (isLastOnBoard()) {
            if (roundTimer.time() >= minTicksForWin.getValue()) {
                getLast().event(Events.WIN);
            }
        }
    }

    private Player getLast() {
        return aliveActive().iterator().next();
    }

    private List<Player> aliveActive() {
        return players.stream()
                .filter(p -> p.isAlive() && p.isActive())
                .collect(toList());
    }

    private void snakesMove() {
        for (Player player : aliveActive()) {
            Hero hero = player.getHero();
            hero.tick();
        }
    }

    private static class ReduceInfo {
        Hero attacker;
        Hero pray;
        int reduce;

        public ReduceInfo(Hero attacker, Hero pray, int reduce) {
            this.attacker = attacker;
            this.pray = pray;
            this.reduce = reduce;
        }
    }

    @FunctionalInterface
    private interface Reduce {
        void doit(Hero attacker, Hero pray, int size);
    }

    private static class FightDetails  {
        private List<ReduceInfo> info = new LinkedList<>();

        public void cutOff(Hero attacker, Hero pray, int size) {
            info.add(new ReduceInfo(attacker, pray, size));
        }

        public boolean alreadyCut(Hero pray) {
            return info.stream()
                    .filter(info -> info.pray == pray)
                    .findAny()
                    .isPresent();
        }

        public void forEach(Reduce action) {
            info.forEach(info -> action.doit(info.attacker, info.pray, info.reduce));
        }
    }

    private void snakesFight() {
        FightDetails info = new FightDetails();

        notFlyingHeroes().forEach(hero -> {
            Hero enemy = enemyCrossedWith(hero);
            if (enemy != null) {
                if (enemy.isFlying()) {
                    return;
                }
                if (hero.isFury() && !enemy.isFury()) {
                    if (enemy.isAlive()) {
                        enemy.die();
                        info.cutOff(hero, enemy, enemy.size());
                    }
                } else if (!hero.isFury() && enemy.isFury()) {
                    if (hero.isAlive()) {
                        hero.die();
                        info.cutOff(enemy, hero, hero.size());
                    }
                } else {
                    if (!info.alreadyCut(hero)) {
                        int len1 = hero.reduce(enemy.size(), NEXT_TICK);
                        info.cutOff(enemy, hero, len1);
                    }

                    if (!info.alreadyCut(enemy)) {
                        int len2 = enemy.reduce(hero.size(), NEXT_TICK);
                        info.cutOff(hero, enemy, len2);
                    }
                }
                return;
            }

            enemy = enemyEatenWith(hero);
            if (enemy != null) {
                if (hero.isFury()) {
                    int len = enemy.reduceFrom(hero.head());
                    info.cutOff(hero, enemy, len);
                } else {
                    hero.die();
                    info.cutOff(enemy, hero, hero.size());
                }
            }
        });

        info.forEach((attacker, pray, reduce) -> {
            if (attacker.isAlive()) {
                attacker.event(Events.EAT.apply(reduce));
            }
        });
    }

    private void snakesEat() {
        for (Player player : aliveActive()) {
            Hero hero = player.getHero();
            Point head = hero.head();
            hero.eat();

            if (apples.contains(head)) {
                apples.remove(head);
                player.event(Events.APPLE);
            }
            if (stones.contains(head) && !hero.isFlying()) {
                stones.remove(head);
                if (player.isAlive()) {
                    player.event(Events.STONE);
                }
            }
            if (gold.contains(head)) {
                gold.remove(head);
                player.event(Events.GOLD);
            }
            if (flyingPills.contains(head)) {
                flyingPills.remove(head);
            }
            if (furyPills.contains(head)) {
                furyPills.remove(head);
            }
        }
    }

    private Stream<Hero> notFlyingHeroes() {
        return aliveActive().stream()
                .map(Player::getHero)
                .filter(h -> !h.isFlying());
    }

    private boolean isNoOneOnBoard() {
        return aliveActive().size() == 0;
    }

    private boolean isLastOnBoard() {
        return aliveActive().size() == 1;
    }

    private void reset(Player player) {
        if (isMatchOver()) {
            player.getHero().setAlive(false);
            player.leaveBoard();
        } else {
            newGame(player);
        }
    }

    private boolean isMatchOver() {
        // тут >= а не == потому что на админке можно поменять roundsPerMatch
        // в меньшую сторону и тут можно зациклится в противном случае
        return round >= roundsPerMatch.getValue();
    }

    public int size() {
        return size;
    }

    @Override
    public boolean isBarrier(Point p) {
        return p.isOutOf(size) || walls.contains(p) || starts.contains(p);
    }

    @Override
    public Point getFreeStart() {
        for (int i = 0; i < 10 && !starts.isEmpty(); i++) {
            StartFloor start = starts.get(dice.next(starts.size()));
            if (freeOfHero(start))
                return start;
        }
        for (StartFloor start : starts)
            if (freeOfHero(start))
                return start;
        return pt(0, 0);
    }

    public boolean isFree(Point pt) {
        return isFreeOfObjects(pt) && freeOfHero(pt);
    }

    public boolean isFreeForStone(Point pt) {
        Point leftSide = pt.copy();
        leftSide.change(Direction.LEFT);
        return isFree(pt) && !starts.contains(leftSide);
    }

    public boolean isFreeOfObjects(Point pt) {
        return !(apples.contains(pt) ||
                stones.contains(pt) ||
                walls.contains(pt) ||
                starts.contains(pt) ||
                flyingPills.contains(pt) ||
                furyPills.contains(pt) ||
                gold.contains(pt));
    }

    private boolean freeOfHero(Point pt) {
        for (Hero h : getHeroes()) {
            if (h != null && h.body().contains(pt) &&
                    !pt.equals(h.getTailPoint()))
                return false;
        }
        return true;
    }

    @Override
    public boolean isApple(Point p) {
        return apples.contains(p);
    }

    @Override
    public boolean isStone(Point p) {
        return stones.contains(p);
    }

    @Override
    public boolean isFlyingPill(Point p) {
        return flyingPills.contains(p);
    }

    @Override
    public boolean isFuryPill(Point p) {
        return furyPills.contains(p);
    }

    @Override
    public boolean isGold(Point p) {
        return gold.contains(p);
    }

    @Override
    public Hero enemyEatenWith(Hero me) {
        return aliveEnemies(me)
                .filter(h -> !h.isFlying())
                .filter(h -> h.body().contains(me.head()))
                .findFirst()
                .orElse(null);
    }

    private Stream<Hero> aliveEnemies(Hero me) {
        return aliveActive().stream()
                .map(Player::getHero)
                .filter(h -> !h.equals(me));
    }

    @Override
    public void oneMoreDead(Player player) {
        player.die(isMatchOver());
        theWalkingDead.add(player);
    }

    @Override
    public Parameter<Integer> flyingCount() {
        return flyingCount;
    }

    @Override
    public Parameter<Integer> furyCount() {
        return furyCount;
    }

    @Override
    public Parameter<Integer> stoneReduced() {
        return stoneReduced;
    }

    private Hero enemyCrossedWith(Hero me) {
        return aliveEnemies(me)
                .filter(h -> me.isHeadIntersect(h))
                .findFirst()
                .orElse(null);
    }

    public void addToPoint(Point p) {
        if (p instanceof Apple)
            setApple(p);
        else if (p instanceof Stone)
            setStone(p);
        else if (p instanceof FlyingPill)
            setFlyingPill(p);
        else if (p instanceof FuryPill)
            setFuryPill(p);
        else if (p instanceof Gold)
            setGold(p);
        else
            fail("Невозможно добавить на поле объект типа " + p.getClass());
    }

    @Override
    public void setApple(Point p) {
        if (isFree(p))
            apples.add(new Apple(p));
    }

    @Override
    public boolean setStone(Point p) {
        if (isFreeForStone(p)) {
            stones.add(new Stone(p));
            return true;
        }
        return false;
    }

    @Override
    public void setFlyingPill(Point p) {
        if (isFree(p))
            flyingPills.add(new FlyingPill(p));
    }

    @Override
    public void setFuryPill(Point p) {
        if (isFree(p))
            furyPills.add(new FuryPill(p));
    }

    @Override
    public void setGold(Point p) {
        if (isFree(p))
            gold.add(new Gold(p));
    }

    public List<Apple> getApples() {
        return apples;
    }

    public List<Hero> getHeroes() {
        return players.stream()
                .map(Player::getHero)
                .collect(toList());
    }

    public void newGame(Player player) {
        if (!players.contains(player)) {
            players.add(player);
        }
        player.newHero(this);
    }

    public void remove(Player player) {
        if (players.contains(player)) {
            // кто уходит из игры не лишает коллег очков за победу
            player.getHero().die();
            players.remove(player);
            rewardTheWinner();
        }
    }

    public List<Wall> getWalls() {
        return walls;
    }

    public List<StartFloor> getStarts() {
        return starts;
    }

    public List<FlyingPill> getFlyingPills() {
        return flyingPills;
    }

    public List<FuryPill> getFuryPills() {
        return furyPills;
    }

    public List<Gold> getGold() {
        return gold;
    }

    public List<Stone> getStones() {
        return stones;
    }

    public BoardReader reader() {
        return new BoardReader() {
            private int size = SnakeBoard.this.size;

            @Override
            public int size() {
                return size;
            }

            @Override
            public Iterable<? extends Point> elements() {
                return new LinkedHashSet<Point>(){{
                    drawHeroes(hero -> !hero.isAlive(), hero -> Arrays.asList(hero.head()));
                    drawHeroes(hero -> hero.isFlying(), hero -> hero.reversedBody());
                    drawHeroes(hero -> !hero.isFlying(), hero -> hero.reversedBody());

                    addAll(SnakeBoard.this.getWalls());
                    addAll(SnakeBoard.this.getApples());
                    addAll(SnakeBoard.this.getStones());
                    addAll(SnakeBoard.this.getFlyingPills());
                    addAll(SnakeBoard.this.getFuryPills());
                    addAll(SnakeBoard.this.getGold());
                    addAll(SnakeBoard.this.getStarts());

                    for (Point p : this.toArray(new Point[0])) {
                        if (p.isOutOf(SnakeBoard.this.size())) {
                            remove(p);
                        }
                    }
                }

                    private void drawHeroes(Predicate<Hero> filter,
                                            Function<Hero, List<? extends Point>> getElements)
                    {
                        SnakeBoard.this.getHeroes().stream()
                                .filter(filter)
                                .sorted(Comparator.comparingInt(Hero::size))
                                .forEach(hero -> addAll(getElements.apply(hero)));
                    }
                };
            }
        };
    }

    private void fail(String message) {
        throw new RuntimeException(message);
    }

    public Point getOn(Point pt) {
        if (apples.contains(pt)) {
            return new Apple(pt);
        }
        if (stones.contains(pt)) {
            return new Stone(pt);
        }
        if (flyingPills.contains(pt)) {
            return new FlyingPill(pt);
        }
        if (furyPills.contains(pt)) {
            return new FuryPill(pt);
        }
        if (gold.contains(pt)) {
            return new Gold(pt);
        }
        if (starts.contains(pt)) {
            return new StartFloor(pt);
        }
        if (walls.contains(pt)) {
            return new Wall(pt);
        }
        for (Player player : players) {
            if (player.getHero().body().contains(pt)) {
                return player.getHero().neck(); // это просто любой объект типа Tail
            }
        }
        return null;
    }
}
