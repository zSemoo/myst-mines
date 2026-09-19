package me.catalysmrl.catamines.api.events;

import me.catalysmrl.catamines.api.mine.CataMine;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired just before a mine refills. Added for the MystCity features, which
 * need to know when a reset happens (boards close, fossils are re-buried,
 * the last-block winner is decided) — upstream had no such event.
 */
public class CataMineResetEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final CataMine mine;

    public CataMineResetEvent(CataMine mine) { this.mine = mine; }

    public CataMine getCataMine() { return mine; }

    @NotNull public static HandlerList getHandlerList() { return HANDLER_LIST; }
    @NotNull @Override public HandlerList getHandlers() { return HANDLER_LIST; }
}
