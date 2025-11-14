package net.shendi.horseraddish.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.shendi.horseraddish.item.HRItems;
import net.shendi.horseraddish.item.NetItem;
import net.shendi.horseraddish.sound.HRSounds;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;


public class HorseNetEvents {
    private static final String NO_FALL_KEY = "horseraddish:no_fall_damage";
    private static final String PLAY_LANDING_KEY = "horseraddish:play_landing_effects";
    private static final String WAS_ON_GROUND_KEY = "horseraddish:was_on_ground";
    private static final String SHOT_KEY = "horseraddish:was_shot_from_net";
    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public void onLivingFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        CompoundTag tag = entity.getPersistentData();

        if (tag.getBoolean(NO_FALL_KEY)) {
            event.setCanceled(true);
            tag.putBoolean(NO_FALL_KEY, false);
            entity.setDeltaMovement(entity.getDeltaMovement().x, 0, entity.getDeltaMovement().z);
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

            // Spawn smoke
            for (int i = 0; i < 12; i++) {
                double px = horse.getX() + (RANDOM.nextDouble() - 0.5) * 1.2;
                double py = horse.getY();
                double pz = horse.getZ() + (RANDOM.nextDouble() - 0.5) * 1.2;
                level.addParticle(ParticleTypes.SMOKE, px, py, pz, 0.0, 0.05, 0.0);
            }

            // Select sound based on origin (shot or placed)
            SoundEvent chosen = selectHorseSound(tag.getBoolean(SHOT_KEY));
            if (chosen != null) {
                level.playSound(
                        null,
                        pos,
                        chosen,
                        SoundSource.NEUTRAL,
                        1.0f,
                        1.0f + (RANDOM.nextFloat() - 0.5f) * 0.2f
                );
            }

            tag.putBoolean(PLAY_LANDING_KEY, false);
            tag.putBoolean(SHOT_KEY, false);
            horse.setDeltaMovement(horse.getDeltaMovement().x, 0, horse.getDeltaMovement().z);
        }

        tag.putBoolean(WAS_ON_GROUND_KEY, isOnGround);
    }

    private SoundEvent selectHorseSound(boolean shot) {
        // Weighted chances — sound 4 has 0.4 % chance to play on both lists.
        double r = RANDOM.nextDouble();

        if (r < 0.004) { // 0.4 %
            return HRSounds.HORSE_LANDING_4.get();
        }

        List<Supplier<SoundEvent>> list = shot
                ? List.of(
                HRSounds.HORSE_LANDING_1,
                HRSounds.HORSE_LANDING_2,
                HRSounds.HORSE_LANDING_3,
                HRSounds.HORSE_LANDING_5
        )
                : List.of(
                HRSounds.HORSE_LANDING_2,
                HRSounds.HORSE_LANDING_3
        );

        return list.get(RANDOM.nextInt(list.size())).get();
    }

    // Net Capture Override
    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        ItemStack stack = event.getItemStack();
        Entity target = event.getTarget();

        if (!(target instanceof AbstractHorse horse)) return;
        if (!stack.is(HRItems.EMPTY_NET.get())) return;
        if (level.isClientSide) return;

        // Save horse data
        CompoundTag horseNbt = new CompoundTag();
        horse.save(horseNbt);

        ItemStack filled = new ItemStack(HRItems.HORSE_IN_A_NET.get());
        CompoundTag wrapper = new CompoundTag();
        wrapper.put(NetItem.HORSE_NBT_TAG, horseNbt);
        filled.set(DataComponents.CUSTOM_DATA, CustomData.of(wrapper));

        // Consume ONE empty net
        stack.shrink(1);

        // Try to give the filled net to the player, or drop it if inventory is full
        if (!player.addItem(filled)) {
            player.drop(filled, false);
        }

        // Remove the horse from the world
        horse.discard();

        player.swing(event.getHand(), true);

        // Prevent normal interaction
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

}