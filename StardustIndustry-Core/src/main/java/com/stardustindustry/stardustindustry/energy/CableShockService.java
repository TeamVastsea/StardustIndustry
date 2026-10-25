package com.stardustindustry.stardustindustry.energy;

import java.util.Collection;

import org.joml.Vector3f;

import com.stardustindustry.stardustindustry.Config;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Applies server-authoritative contact damage from energized cable geometry. */
public final class CableShockService {
    /** Small tolerance for numerical gaps between the rendered tube and the player box. */
    static final float CONTACT_MARGIN = 0.01f;

    private CableShockService() {}

    public static void tick(ServerLevel level, Collection<EnergizedCable> energizedCables) {
        if (!Config.ELECTRIC_SHOCKS_ENABLED.get() || energizedCables.isEmpty()) {
            return;
        }
        ElectricalNetworkSavedData network = ElectricalNetworkSavedData.get(level);
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.getAbilities().invulnerable) {
                continue;
            }
            CableInsulation.Shock strongest = CableInsulation.Shock.NONE;
            for (EnergizedCable energized : energizedCables) {
                CableConnection cable = network.cable(energized.cable().runId());
                if (cable == null || !bothEndsLoaded(level, cable)) {
                    continue;
                }
                CableInsulation.Shock shock = cable.shockAt(energized.volts());
                if (shock == CableInsulation.Shock.NONE) {
                    continue;
                }
                Vector3f from = CableService.anchorOf(level, cable.a());
                Vector3f to = CableService.anchorOf(level, cable.b());
                if (from == null || to == null) {
                    continue;
                }
                float contactRadius = CableGeometry.radiusFor(cable.ampacity()) + CONTACT_MARGIN;
                double distanceSqr = CableGeometry.distanceSqrToPolyline(
                        from, to, player.getBoundingBox(), cable.sagMultiplier());
                if (distanceSqr <= (double) contactRadius * contactRadius) {
                    strongest = stronger(strongest, shock);
                    if (strongest == CableInsulation.Shock.LETHAL) {
                        break;
                    }
                }
            }
            apply(level, player, strongest);
        }
    }

    static CableInsulation.Shock stronger(CableInsulation.Shock first,
                                          CableInsulation.Shock second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }

    static float survivableDamage(float configuredDamage, float currentHealth) {
        return Math.min(Math.max(0.0f, configuredDamage), Math.max(0.0f, currentHealth - 1.0f));
    }

    private static boolean bothEndsLoaded(ServerLevel level, CableConnection cable) {
        return level.isLoaded(cable.a().pos()) && level.isLoaded(cable.b().pos());
    }

    private static void apply(ServerLevel level, ServerPlayer player,
                              CableInsulation.Shock shock) {
        switch (shock) {
            case NONE -> {
            }
            case LIGHT -> {
                float damage = survivableDamage(
                        Config.LIGHT_SHOCK_DAMAGE.get().floatValue(), player.getHealth());
                if (damage > 0.0f && player.hurt(level.damageSources().lightningBolt(), damage)) {
                    Vec3 look = player.getLookAngle();
                    double pushX = look.x;
                    double pushZ = look.z;
                    if (pushX * pushX + pushZ * pushZ < 1.0e-6) {
                        pushX = 1.0;
                        pushZ = 0.0;
                    }
                    player.knockback(0.4, pushX, pushZ);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
                            0.45f, 1.6f);
                }
            }
            case LETHAL -> {
                float damage = Config.LETHAL_SHOCK_DAMAGE.get().floatValue();
                if (damage > 0.0f) {
                    player.hurt(level.damageSources().genericKill(), damage);
                }
            }
        }
    }
}
