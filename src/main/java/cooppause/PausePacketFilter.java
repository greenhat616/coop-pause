package cooppause;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.packet.*;
import necesse.engine.network.server.Server;
import necesse.engine.registries.PacketRegistry;

/**
 * Server-side guard: drops player actions that would change the world while it is paused.
 *
 * <p>The client already blocks input by holding its pause menu, but the server processes every packet in
 * {@code frameTick} regardless of pause, so anything that slips through would mutate a frozen world. A
 * blocklist rather than an allowlist on purpose: connecting, loading regions, chat, pings and settings must
 * keep working for players who join or stay connected during the pause, and those are far more numerous and
 * varied than the actions listed here.
 *
 * <p>Methods are public because the patch that calls them is inlined into {@code Server}.
 */
public final class PausePacketFilter {

    @SuppressWarnings("unchecked")
    private static final Class<? extends Packet>[] BLOCKED = new Class[]{
        // Attacking and using items
        PacketPlayerAttack.class,
        PacketPlayerAttackHandler.class,
        PacketPlaceAttackHandlerUpdate.class,
        PacketPlayerItemInteract.class,
        PacketPlayerItemMobInteract.class,
        PacketPlayerMobInteract.class,
        PacketObjectInteract.class,
        PacketPlayerAction.class,
        PacketSpawnProjectile.class,
        PacketProjectileHit.class,
        PacketPlayerCollisionHit.class,
        PacketMobAbilityLevelEventHit.class,
        PacketSummonFocus.class,
        PacketFishingStatus.class,
        // Inventory, containers and crafting
        PacketPlayerDropItem.class,
        PacketContainerAction.class,
        PacketContainerCustomAction.class,
        PacketCraftAction.class,
        PacketSwapInventorySlots.class,
        PacketRequestPickupEntity.class,
        PacketPickTicTacToeTile.class,
        // Mounts and abilities
        PacketPlayerUseMount.class,
        PacketMountMobJump.class,
        PacketMountAbility.class,
        PacketBuffAbility.class,
        PacketActiveMountAbility.class,
        PacketActiveSetBuffAbility.class,
        PacketActiveTrinketBuffAbility.class,
        PacketRequestActiveMountAbility.class,
        PacketRequestActiveSetBuffAbility.class,
        PacketRequestActiveTrinketBuffAbility.class,
        // Travel and direct world edits
        PacketRequestTravel.class,
        PacketChangeObject.class,
        PacketChangeObjects.class,
        PacketChangeTile.class,
        PacketChangeWire.class,
        PacketPlacePreset.class,
    };

    private static volatile Set<Integer> blockedIDs;
    private static final AtomicInteger DROPPED = new AtomicInteger();

    private PausePacketFilter() {
    }

    /** @return true to drop the packet unprocessed */
    public static boolean shouldDrop(Server server, NetworkPacket packet) {
        if (packet == null || !PauseState.isPaused(server) || !blockedIDs().contains(packet.type)) {
            return false;
        }

        DROPPED.incrementAndGet();
        return true;
    }

    /** Returns and resets how many packets were dropped since the last call. */
    public static int takeDropped() {
        return DROPPED.getAndSet(0);
    }

    // Resolved lazily: packet IDs are assigned during registration, which must have finished first.
    private static Set<Integer> blockedIDs() {
        Set<Integer> ids = blockedIDs;
        if (ids == null) {
            ids = new HashSet<>();
            for (Class<? extends Packet> packetClass : BLOCKED) {
                ids.add(PacketRegistry.getPacketID(packetClass));
            }

            blockedIDs = ids;
        }

        return ids;
    }
}
