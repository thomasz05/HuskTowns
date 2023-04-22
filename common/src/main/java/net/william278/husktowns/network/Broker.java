/*
 * This file is part of HuskTowns by William278. Do not redistribute!
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  All rights reserved.
 *
 *  This source code is provided as reference to licensed individuals that have purchased the HuskTowns
 *  plugin once from any of the official sources it is provided. The availability of this code does
 *  not grant you the rights to modify, re-distribute, compile or redistribute this source code or
 *  "plugin" outside this intended purpose. This license does not cover libraries developed by third
 *  parties that are utilised in the plugin.
 */

package net.william278.husktowns.network;

import de.themoep.minedown.adventure.MineDown;
import net.kyori.adventure.text.Component;
import net.william278.husktowns.HuskTowns;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Role;
import net.william278.husktowns.town.Town;
import net.william278.husktowns.user.OnlineUser;
import net.william278.husktowns.user.SavedUser;
import net.william278.husktowns.user.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.logging.Level;

/**
 * A broker for dispatching {@link Message}s across the proxy network
 */
public abstract class Broker {

    protected final HuskTowns plugin;

    /**
     * Create a new broker
     *
     * @param plugin the HuskTowns plugin instance
     */
    protected Broker(@NotNull HuskTowns plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle an inbound {@link Message}
     *
     * @param receiver The user who received the message, if a receiver exists
     * @param message  The message
     */
    protected void handle(@Nullable OnlineUser receiver, @NotNull Message message) {
        if (message.getSourceServer().equals(getServer())) {
            return;
        }
        switch (message.getType()) {
            case TOWN_DELETE -> message.getPayload().getInteger()
                    .flatMap(townId -> plugin.getTowns().stream().filter(town -> town.getId() == townId).findFirst())
                    .ifPresent(town -> plugin.runAsync(() -> {
                        plugin.getManager().sendTownMessage(town, plugin.getLocales()
                                .getLocale("town_deleted_notification", town.getName())
                                .map(MineDown::toComponent).orElse(Component.empty()));
                        plugin.getMapHook().ifPresent(mapHook -> mapHook.removeClaimMarkers(town));
                        plugin.removeTown(town);
                        plugin.getClaimWorlds().values().forEach(world -> {
                            if (world.removeTownClaims(town.getId()) > 0) {
                                plugin.getDatabase().updateClaimWorld(world);
                            }
                        });
                    }));
            case TOWN_DELETE_ALL_CLAIMS -> message.getPayload().getInteger()
                    .flatMap(townId -> plugin.getTowns().stream().filter(town -> town.getId() == townId).findFirst())
                    .ifPresent(town -> plugin.runAsync(() -> {
                        plugin.getManager().sendTownMessage(town, plugin.getLocales()
                                .getLocale("deleted_all_claims_notification", town.getName())
                                .map(MineDown::toComponent).orElse(Component.empty()));
                        plugin.getMapHook().ifPresent(mapHook -> mapHook.removeClaimMarkers(town));
                        plugin.getClaimWorlds().values().forEach(world -> world.removeTownClaims(town.getId()));
                    }));
            case TOWN_UPDATE -> plugin.runAsync(() -> message.getPayload().getInteger()
                    .flatMap(id -> plugin.getDatabase().getTown(id))
                    .ifPresentOrElse(
                            plugin::updateTown,
                            () -> plugin.log(Level.WARNING, "Failed to update town: Town not found")
                    ));
            case TOWN_INVITE_REQUEST -> {
                if (receiver == null) {
                    return;
                }
                message.getPayload().getInvite()
                        .ifPresentOrElse(invite -> plugin.getManager().towns().handleInboundInvite(receiver, invite),
                                () -> plugin.log(Level.WARNING, "Failed to handle town invite request: Invalid payload"));
            }
            case TOWN_INVITE_REPLY -> message.getPayload().getBool().ifPresent(accepted -> {
                if (receiver == null) {
                    return;
                }
                final Optional<Member> member = plugin.getUserTown(receiver);
                if (member.isEmpty()) {
                    return;
                }
                final Member townMember = member.get();
                if (!accepted) {
                    plugin.getLocales().getLocale("invite_declined_by", message.getSender())
                            .ifPresent(receiver::sendMessage);
                    return;
                }
                plugin.getLocales().getLocale("user_joined_town", message.getSender(),
                                townMember.town().getName()).map(MineDown::toComponent)
                        .ifPresent(locale -> plugin.getManager().sendTownMessage(townMember.town(), locale));
            });
            case TOWN_CHAT_MESSAGE -> message.getPayload().getString()
                    .ifPresent(text -> plugin.getDatabase().getUser(message.getSender())
                            .flatMap(sender -> plugin.getUserTown(sender.user()))
                            .ifPresent(member -> plugin.getManager().towns().sendLocalChatMessage(text, member, plugin)));
            case TOWN_LEVEL_UP, TOWN_TRANSFERRED, TOWN_RENAMED ->
                    message.getPayload().getInteger().flatMap(id -> plugin.getTowns().stream()
                            .filter(town -> town.getId() == id).findFirst()).ifPresent(town -> {
                        final Component locale = switch (message.getType()) {
                            case TOWN_LEVEL_UP -> plugin.getLocales().getLocale("town_levelled_up",
                                    Integer.toString(town.getLevel())).map(MineDown::toComponent).orElse(Component.empty());
                            case TOWN_RENAMED -> plugin.getLocales().getLocale("town_renamed",
                                    town.getName()).map(MineDown::toComponent).orElse(Component.empty());
                            case TOWN_TRANSFERRED -> plugin.getLocales().getLocale("town_transferred",
                                            town.getName(), plugin.getDatabase().getUser(town.getMayor())
                                                    .map(SavedUser::user).map(User::getUsername).orElse("?"))
                                    .map(MineDown::toComponent).orElse(Component.empty());
                            default -> Component.empty();
                        };
                        plugin.getManager().sendTownMessage(town, locale);
                    });
            case TOWN_DEMOTED, TOWN_PROMOTED, TOWN_EVICTED -> {
                if (receiver == null) {
                    return;
                }
                final Component locale = switch (message.getType()) {
                    case TOWN_DEMOTED -> plugin.getLocales().getLocale("demoted_you",
                                    plugin.getRoles().fromWeight(message.getPayload().getInteger().orElse(-1))
                                            .map(Role::getName).orElse("?"),
                                    message.getSender()).map(MineDown::toComponent)
                            .orElse(Component.empty());
                    case TOWN_PROMOTED -> plugin.getLocales().getLocale("promoted_you",
                                    plugin.getRoles().fromWeight(message.getPayload().getInteger().orElse(-1))
                                            .map(Role::getName).orElse("?"),
                                    message.getSender()).map(MineDown::toComponent)
                            .orElse(Component.empty());
                    case TOWN_EVICTED -> plugin.getLocales().getLocale("evicted_you",
                                    plugin.getUserTown(receiver).map(Member::town).map(Town::getName).orElse("?"),
                                    message.getSender()).map(MineDown::toComponent)
                            .orElse(Component.empty());
                    default -> Component.empty();
                };
                receiver.sendMessage(locale);

            }
            default -> plugin.log(Level.SEVERE, "Received unknown message type: " + message.getType());
        }
    }

    /**
     * Initialize the message broker
     *
     * @throws RuntimeException if the broker fails to initialize
     */
    public abstract void initialize() throws RuntimeException;

    /**
     * Send a message to the broker
     *
     * @param message the message to send
     * @param sender  the sender of the message
     */
    protected abstract void send(@NotNull Message message, @NotNull OnlineUser sender);

    /**
     * Move an {@link OnlineUser} to a new server on the proxy network
     *
     * @param user   the user to move
     * @param server the server to move the user to
     */
    public abstract void changeServer(@NotNull OnlineUser user, @NotNull String server);

    /**
     * Terminate the broker
     */
    public abstract void close();

    @NotNull
    protected String getSubChannelId() {
        final String version = plugin.getVersion().getMajor() + "." + plugin.getVersion().getMinor();
        return plugin.getKey(plugin.getSettings().getClusterId(), version).asString();
    }

    @NotNull
    protected String getServer() {
        return plugin.getServerName();
    }

    /**
     * Identifies types of message brokers
     */
    public enum Type {
        PLUGIN_MESSAGE("Plugin Messages"),
        REDIS("Redis");
        @NotNull
        private final String displayName;

        Type(@NotNull String displayName) {
            this.displayName = displayName;
        }

        @NotNull
        public String getDisplayName() {
            return displayName;
        }
    }

}
