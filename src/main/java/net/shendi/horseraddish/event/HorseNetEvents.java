package net.shendi.horseraddish.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.shendi.horseraddish.sound.HRSounds;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public class HorseNetEvents {
    private static final String NO_FALL_KEY = "horseraddish:no_fall_damage";
    private static final String PLAY_LANDING_KEY = "horseraddish:play_landing_effects";
    private static final String WAS_ON_GROUND_KEY = "horseraddish:was_on_ground";
    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public void onLivingFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        CompoundTag tag = entity.getPersistentData();

        if (tag.getBoolean(NO_FALL_KEY)) {
            event.setCanceled(true); // cancel fall damage
            tag.putBoolean(NO_FALL_KEY, false); // only cancel once
            entity.setDeltaMovement(entity.getDeltaMovement().x, 0, entity.getDeltaMovement().z); // cancel downward velocity
        }
    }

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractHorse horse)) return;

        CompoundTag tag = horse.getPersistentData();
        if (!tag.getBoolean(PLAY_LANDING_KEY)) return;

        boolean wasOnGround = tag.getBoolean(WAS_ON_GROUND_KEY);
        boolean isOnGround = horse.onGround();

        if (!wasOnGround && isOnGround) {
            Level level = horse.level();
            BlockPos pos = horse.blockPosition();

            // Spawn smoke burst
            for (int i = 0; i < 12; i++) {
                double px = horse.getX() + (RANDOM.nextDouble() - 0.5) * 1.2;
                double py = horse.getY();
                double pz = horse.getZ() + (RANDOM.nextDouble() - 0.5) * 1.2;
                level.addParticle(ParticleTypes.SMOKE, px, py, pz, 0.0, 0.05, 0.0);
            }

            // Play landing sound
            List<Supplier<SoundEvent>> list = List.of(
                    HRSounds.HORSE_LANDING_1,
                    HRSounds.HORSE_LANDING_2,
                    HRSounds.HORSE_LANDING_3,
                    HRSounds.HORSE_LANDING_4,
                    HRSounds.HORSE_LANDING_5
            );
            SoundEvent sound = list.get(RANDOM.nextInt(list.size())).get();
            level.playSound(null, pos, sound, SoundSource.NEUTRAL,
                    1.0f, 1.0f + (RANDOM.nextFloat() - 0.5f) * 0.2f);

            // Reset landing flag
            tag.putBoolean(PLAY_LANDING_KEY, false);

            // Ensure fall velocity does not damage the horse
            horse.setDeltaMovement(horse.getDeltaMovement().x, 0, horse.getDeltaMovement().z);
        }

        tag.putBoolean(WAS_ON_GROUND_KEY, isOnGround);
    }
}
