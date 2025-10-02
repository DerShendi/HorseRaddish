package net.shendi.horseraddish.event;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.shendi.horseraddish.item.HRItems;
import net.shendi.horseraddish.item.NetItem;


public class HRDispenserEvents {

    public static void register() {
        registerNetDispenser();
    }

    private static void registerNetDispenser() {
        DispenserBlock.registerBehavior(HRItems.HORSE_IN_A_NET.get(), new DispenseItemBehavior() {
            @Override
            public ItemStack dispense(BlockSource source, ItemStack stack) {
                Level level = source.level();
                BlockPos pos = source.pos();
                Direction facing = source.state().getValue(DispenserBlock.FACING);

                CompoundTag horseNbt = null;
                if (stack.getItem() instanceof NetItem net) {
                    CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
                    if (custom != null) horseNbt = custom.copyTag().getCompound(NetItem.HORSE_NBT_TAG);
                }

                AbstractHorse horse = NetItem.createHorseFromNbt(level, horseNbt);
                if (horse == null) horse = new Horse(EntityType.HORSE, level);

                double offsetX = facing.getStepX() * 1.0;
                double offsetY = facing.getStepY() * 1.0;
                double offsetZ = facing.getStepZ() * 1.0;

                horse.setPos(pos.getX() + 0.5 + offsetX, pos.getY() + 0.5 + offsetY, pos.getZ() + 0.5 + offsetZ);

                double velocity = 4.5D;
                horse.setDeltaMovement(facing.getStepX() * velocity, facing.getStepY() * velocity, facing.getStepZ() * velocity);

                horse.getPersistentData().putBoolean(NetItem.NO_FALL_KEY, true);
                horse.getPersistentData().putBoolean(NetItem.PLAY_LANDING_KEY, true);
                horse.getPersistentData().putBoolean(NetItem.WAS_ON_GROUND_KEY, horse.onGround());

                level.addFreshEntity(horse);

                stack = new ItemStack(HRItems.EMPTY_NET.get());

                return stack;
            }
        });
    }
}