package net.shendi.horseraddish.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.shendi.horseraddish.sound.HRSounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Supplier;


public class NetItem extends Item {
    public static final Logger LOGGER = LoggerFactory.getLogger("HorseRaddish|NetItem");

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

    // Capture horse
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        Level level = player.level();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (target instanceof AbstractHorse horse && stack.is(HRItems.EMPTY_NET.get())) {

            if (!horse.isTamed()) {
                horse.setTamed(true);
                horse.setOwnerUUID(player.getUUID());
                horse.setTemper(horse.getMaxTemper());
            }

            CompoundTag horseNbt = new CompoundTag();
            horse.save(horseNbt);

            ItemStack filled = new ItemStack(HRItems.HORSE_IN_A_NET.get());
            CompoundTag tag = new CompoundTag();
            tag.put(HORSE_NBT_TAG, horseNbt);
            filled.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

            // Copy horse name to net
            if (horse.hasCustomName()) {
                filled.set(DataComponents.CUSTOM_NAME, horse.getCustomName());
            }

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
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag horseNbt = custom != null ? custom.copyTag().getCompound(HORSE_NBT_TAG) : null;

        AbstractHorse horse = createHorseFromNbt(level, horseNbt);
        if (horse == null) return InteractionResult.PASS;

        horse.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

        // Apply net's custom name if present
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            horse.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
        }

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
        for (int i = 0; i < 10; i++) {
            if (level.getBlockState(mutable).getCollisionShape(level, mutable).isEmpty()
                    && level.getBlockState(mutable.above()).getCollisionShape(level, mutable.above()).isEmpty()) {
                return mutable.immutable();
            }
            mutable.move(Direction.UP);
        }
        return pos;
    }

    // Air use (throw horse)
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
        boolean playLaunchSound = power >= 0.3f;

        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag horseNbt = custom != null ? custom.copyTag().getCompound(HORSE_NBT_TAG) : null;

        AbstractHorse horse = createHorseFromNbt(level, horseNbt);
        if (horse == null) return;

        // Apply net's custom name
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            horse.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
        }

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
        horse.getPersistentData().putBoolean(WAS_ON_GROUND_KEY, horse.onGround());

        level.addFreshEntity(horse);

        // Play sound & particles
        if (playLaunchSound) {
            List<Supplier<SoundEvent>> soundList = List.of(
                    HRSounds.HORSE_LANDING_1,
                    HRSounds.HORSE_LANDING_2,
                    HRSounds.HORSE_LANDING_3,
                    HRSounds.HORSE_LANDING_4,
                    HRSounds.HORSE_LANDING_5
            );

            SoundEvent sound = soundList.get(random.nextInt(soundList.size())).get();
            level.playSound(null, horse.blockPosition(), sound, SoundSource.NEUTRAL,
                    1.0f, 1.0f + (random.nextFloat() - 0.5f) * 0.2f);

            for (int i = 0; i < 12; i++) {
                double px = horse.getX() + (random.nextDouble() - 0.5) * 1.2;
                double py = horse.getY();
                double pz = horse.getZ() + (random.nextDouble() - 0.5) * 1.2;
                level.addParticle(ParticleTypes.SMOKE, px, py, pz, 0.0, 0.05, 0.0);
            }
        }

        if (!player.isCreative()) {
            stack.shrink(1);
            ItemStack empty = new ItemStack(HRItems.EMPTY_NET.get());
            if (!player.getInventory().add(empty)) player.drop(empty, false);
        }
    }

    public static float getBowPower(int chargeTicks) {
        float f = (float) chargeTicks / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        return Math.min(f, 1.0F);
    }

    public static AbstractHorse createHorseFromNbt(Level level, CompoundTag horseNbt) {
        try {
            if (horseNbt == null || horseNbt.isEmpty()) {
                Horse horse = EntityType.HORSE.create(level);
                if (horse != null) horse.setTamed(false);
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
            LOGGER.error("Failed to recreate horse from NBT. Dont worry this should only happen if you cheat a Horsenet in or if the horse data is corrupted");
            return null;
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // Let vanilla add basic lines first
        super.appendHoverText(stack, context, tooltip, flag);

        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return;

        CompoundTag horseNbt = custom.copyTag().getCompound(HORSE_NBT_TAG);
        if (horseNbt == null || horseNbt.isEmpty()) return;

        // --- NAME ---
        Component name;
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            name = stack.get(DataComponents.CUSTOM_NAME);
        } else if (horseNbt.contains("CustomName")) {
            String raw = horseNbt.getString("CustomName");
            Component parsed = null;
            try {
                // Try fromJson
                try {
                    var m = Component.Serializer.class.getMethod("fromJson", String.class);
                    parsed = (Component) m.invoke(null, raw);
                } catch (NoSuchMethodException ignored) {}
                // Try fromJsonLenient
                if (parsed == null) {
                    try {
                        var m2 = Component.Serializer.class.getMethod("fromJsonLenient", String.class);
                        parsed = (Component) m2.invoke(null, raw);
                    } catch (NoSuchMethodException ignored) {}
                }
                // Fallback to literal if parsing fails
                if (parsed == null) {
                    String candidate = raw;
                    if (candidate.length() >= 2 && candidate.charAt(0) == '"' && candidate.charAt(candidate.length()-1) == '"') {
                        candidate = candidate.substring(1, candidate.length() - 1)
                                .replace("\\\"", "\"")
                                .replace("\\\\", "\\");
                    }
                    parsed = Component.literal(candidate);
                }
            } catch (Exception e) {
                parsed = null;
            }
            name = parsed != null ? parsed : Component.literal("Unnamed").withStyle(ChatFormatting.GRAY);
        } else {
            name = Component.literal("Unnamed").withStyle(ChatFormatting.GRAY);
        }
        tooltip.add(Component.literal("Name: ").withStyle(ChatFormatting.GOLD).append(name));

        // --- TYPE ---
        String id = horseNbt.getString("id");
        if (id == null || id.isEmpty()) id = "minecraft:horse";
        String type = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        if (!type.isEmpty()) type = Character.toUpperCase(type.charAt(0)) + type.substring(1);
        tooltip.add(Component.literal("Type: ").withStyle(ChatFormatting.AQUA).append(Component.literal(type)));

        // --- HEALTH ---
        double currentHealth = horseNbt.contains("Health") ? horseNbt.getDouble("Health") : Double.NaN;
        double maxHealth = 15.0;
        double moveSpeed = Double.NaN;
        double jumpStrength = Double.NaN;

        if (horseNbt.contains("Attributes")) {
            try {
                var list = horseNbt.getList("Attributes", 10); // list of compounds
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag attr = list.getCompound(i);
                    if (!attr.contains("Name")) continue;
                    String attrName = attr.getString("Name").toLowerCase();

                    if (attrName.endsWith("max_health") || attrName.contains("maxhealth")) {
                        maxHealth = attr.getDouble("Base");
                    } else if (attrName.endsWith("movement_speed") || attrName.contains("movespeed")) {
                        moveSpeed = attr.getDouble("Base");
                    } else if (attrName.endsWith("jump_strength") || attrName.contains("jumpstrength")) {
                        jumpStrength = attr.getDouble("Base");
                    }
                }
            } catch (Exception ignored) {}
        }

        if (Double.isNaN(currentHealth)) currentHealth = maxHealth;
        tooltip.add(Component.literal(String.format("Health: %.1f / %.1f", currentHealth, maxHealth))
                .withStyle(ChatFormatting.RED));

        // --- SPEED & JUMP ---
        if (!Double.isNaN(moveSpeed)) {
            double blocksPerSecond = moveSpeed * 43.17; // approximate horse speed conversion
            tooltip.add(Component.literal(String.format("Speed: %.2f b/s", blocksPerSecond))
                    .withStyle(ChatFormatting.YELLOW));
        }

        if (!Double.isNaN(jumpStrength)) {
            double jumpBlocks = -0.1817584952 * Math.pow(jumpStrength, 3)
                    + 3.689713992 * Math.pow(jumpStrength, 2)
                    + 2.128599134 * jumpStrength
                    - 0.343930367; // vanilla jump curve approximation
            tooltip.add(Component.literal(String.format("Jump: %.2f blocks", jumpBlocks))
                    .withStyle(ChatFormatting.GREEN));
        }

        // --- ARMOR ---
        try {
            if (horseNbt.contains("ArmorItem")) {
                CompoundTag armorTag = horseNbt.getCompound("ArmorItem");
                if (armorTag != null && !armorTag.isEmpty()) {
                    ItemStack armor = ItemStack.parseOptional(context.registries(), armorTag);
                    if (!armor.isEmpty()) {
                        tooltip.add(Component.literal("Armor: ").withStyle(ChatFormatting.BLUE).append(armor.getHoverName()));
                    }
                }
            } else if (horseNbt.contains("Armor")) {
                CompoundTag armorTag = horseNbt.getCompound("Armor");
                if (armorTag != null && !armorTag.isEmpty()) {
                    ItemStack armor = ItemStack.parseOptional(context.registries(), armorTag);
                    if (!armor.isEmpty()) {
                        tooltip.add(Component.literal("Armor: ").withStyle(ChatFormatting.BLUE).append(armor.getHoverName()));
                    }
                }
            } else {
                // Some versions save horse body armor into the inventory slot; try "SaddleItem" for saddle display (optional)
                if (horseNbt.contains("SaddleItem")) {
                    CompoundTag saddleTag = horseNbt.getCompound("SaddleItem");
                    if (saddleTag != null && !saddleTag.isEmpty()) {
                        ItemStack saddle = ItemStack.parseOptional(context.registries(), saddleTag);
                        if (!saddle.isEmpty()) {
                            tooltip.add(Component.literal("Saddle: ").withStyle(ChatFormatting.GRAY).append(saddle.getHoverName()));
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to parse horse armor in tooltip: {}", e.getMessage());
        }
    }
}