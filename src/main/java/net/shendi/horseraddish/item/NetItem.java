package net.shendi.horseraddish.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class NetItem extends Item {
    public static final Logger LOGGER = LoggerFactory.getLogger("HorseRaddish|NetItem");

    // Tag keys
    public static final String HORSE_NBT_TAG = "HorseEntityData";
    public static final String NO_FALL_KEY = "horseraddish:no_fall_damage";
    public static final String PLAY_LANDING_KEY = "horseraddish:play_landing_effects";
    public static final String WAS_ON_GROUND_KEY = "horseraddish:was_on_ground";

    public final boolean filled;

    public NetItem(Properties properties, boolean filled) {
        super(properties);
        this.filled = filled;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72_000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    // Interaction with entities - capture
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        Level level = player.level();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (target instanceof AbstractHorse horse && stack.is(HRItems.EMPTY_NET.get())) {
            // Force override untamed interaction
            if (!horse.isTamed()) {
                horse.tameWithName(player);
            }

            CompoundTag horseNbt = new CompoundTag();
            horse.saveWithoutId(horseNbt);

            ItemStack filled = new ItemStack(HRItems.HORSE_IN_A_NET.get());
            CompoundTag tag = new CompoundTag();
            tag.put(HORSE_NBT_TAG, horseNbt);
            filled.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

            player.setItemInHand(hand, filled);
            horse.discard();

            player.swing(hand, true);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    // Place horse on block
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!filled) return super.useOn(context);

        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockPos clicked = context.getClickedPos();
        BlockPos spawnPos = findFreeSpawnPosition(level, clicked.above());

        ItemStack stack = context.getItemInHand();

        // Read the horse NBT from CustomData
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag horseNbt = custom != null ? custom.copyTag().getCompound(HORSE_NBT_TAG) : null;

        // Create horse: if NBT is missing, spawn default horse
        AbstractHorse horse = createHorseFromNbt(level, horseNbt);
        if (horse == null) return InteractionResult.PASS;

        horse.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

        horse.getPersistentData().putBoolean(NO_FALL_KEY, true);
        horse.getPersistentData().putBoolean(PLAY_LANDING_KEY, true);
        horse.getPersistentData().putBoolean(WAS_ON_GROUND_KEY, horse.onGround());

        level.addFreshEntity(horse);

        Player player = context.getPlayer();
        if (player != null && !player.isCreative()) {
            stack.shrink(1);
            ItemStack empty = new ItemStack(HRItems.EMPTY_NET.get());
            if (!player.getInventory().add(empty)) player.drop(empty, false);
        }

        return InteractionResult.CONSUME;
    }


    public static BlockPos findFreeSpawnPosition(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos mutable = pos.mutable();
        int tries = 10;

        for (int i = 0; i < tries; i++) {
            if (level.getBlockState(mutable).getCollisionShape(level, mutable).isEmpty()
                    && level.getBlockState(mutable.above()).getCollisionShape(level, mutable.above()).isEmpty()) {
                return mutable.immutable();
            }
            mutable.move(Direction.UP);
        }

        return pos;
    }



    // Air use -> throw
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!filled) return InteractionResultHolder.pass(stack);

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;
        if (level.isClientSide) return;

        int used = this.getUseDuration(stack, entity) - timeLeft;
        float power = getBowPower(used);

        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag horseNbt = custom != null ? custom.copyTag().getCompound(HORSE_NBT_TAG) : null;

        AbstractHorse horse = createHorseFromNbt(level, horseNbt); // this now spawns default horse if horseNbt null
        if (horse == null) return;

        Vec3 look = player.getLookAngle();
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 spawnPos = eye.add(look.scale(1.5));

        horse.setPos(spawnPos.x, spawnPos.y - 0.5, spawnPos.z);

        double velocity = power * 4.5D;
        Vec3 vel = look.scale(velocity);

        RandomSource random = player.getRandom();
        float inaccuracy = (1.0f - power) * 0.5f;
        vel = vel.add(
                random.nextGaussian() * 0.0075 * inaccuracy,
                random.nextGaussian() * 0.0075 * inaccuracy,
                random.nextGaussian() * 0.0075 * inaccuracy
        );

        horse.setDeltaMovement(vel);

        horse.getPersistentData().putBoolean(NO_FALL_KEY, true);
        horse.getPersistentData().putBoolean(PLAY_LANDING_KEY, true);
        horse.getPersistentData().putBoolean(WAS_ON_GROUND_KEY, horse.onGround());

        level.addFreshEntity(horse);

        if (!player.isCreative()) {
            stack.shrink(1);
            ItemStack empty = new ItemStack(HRItems.EMPTY_NET.get());
            if (!player.getInventory().add(empty)) player.drop(empty, false);
        }
    }


    // Helpers
    public static float getBowPower(int chargeTicks) {
        float f = (float) chargeTicks / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        return Math.min(f, 1.0F);
    }

    public static AbstractHorse createHorseFromNbt(Level level, CompoundTag horseNbt) {
        try {
            // If the net has no stored horse, just spawn a normal horse
            if (horseNbt == null || horseNbt.isEmpty()) {
                Horse horse = EntityType.HORSE.create(level);
                if (horse != null) {
                    horse.setTamed(false);
                }
                return horse;
            }

            String id = horseNbt.getString("id");
            EntityType<?> type = id.isEmpty()
                    ? EntityType.HORSE
                    : BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.tryParse(id));

            if (type == null) type = EntityType.HORSE;

            Entity entity = type.create(level);
            if (!(entity instanceof AbstractHorse horse)) return null;

            horse.load(horseNbt);
            return horse;
        } catch (Exception e) {
            LOGGER.error("Failed to recreate horse from NBT", e);
            return null;
        }
    }
}
