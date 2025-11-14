package net.shendi.horseraddish.effect;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;

import java.util.Map;
import java.util.UUID;

public class SpicyEffect extends MobEffect {
    public static final UUID SPEED_MODIFIER_UUID = UUID.fromString("1a2b3c4d-5e67-479a-9e0a-9b0f4a1d7abc");
    public static final UUID STEP_HEIGHT_MODIFIER_UUID = UUID.fromString("7e2b7e1b-fc2e-4f1c-a36b-9b0f4a1d7abc");

    public static final ResourceLocation SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("horseraddish", "spicy.speed.boost");
    public static final ResourceLocation STEP_HEIGHT_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("horseraddish", "spicy.step.height");

    public SpicyEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    public void onEffectStarted(LivingEntity entity, int amplifier, Map<Holder<Attribute>, AttributeModifier> attributeModifiers) {
        super.onEffectStarted(entity, amplifier);

        float damage = (amplifier + 1);
        entity.hurt(entity.damageSources().magic(), damage);

        int speedAmplifier = amplifier + 1;

        AttributeInstance movementSpeed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {

            double speedBonus = 0.2 * (amplifier + 2);
            AttributeModifier speedModifier = new AttributeModifier(ResourceLocation.fromNamespaceAndPath("horseraddish", "spicy.speed.boost"), speedBonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            movementSpeed.addTransientModifier(speedModifier);
        }


        AttributeInstance stepHeight = entity.getAttribute(Attributes.STEP_HEIGHT);
        if (stepHeight != null) {
            if (stepHeight.getModifier(STEP_HEIGHT_MODIFIER_ID) != null) {
                stepHeight.removeModifier(STEP_HEIGHT_MODIFIER_ID);
            }
            AttributeModifier stepModifier = new AttributeModifier(ResourceLocation.fromNamespaceAndPath("horseraddish", "spicy.step.height"), 0.9F, AttributeModifier.Operation.ADD_VALUE);
            stepHeight.addTransientModifier(stepModifier);
        }
    }


    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity.isSprinting()) {
        } else {
            entity.setSprinting(true);
        }
        double forwardSpeed = 0.1 + 0.05 * amplifier;
        double dx = Math.sin(Math.toRadians(entity.yBodyRot)) * forwardSpeed;
        double dz = -Math.cos(Math.toRadians(entity.yBodyRot)) * forwardSpeed;
        entity.setDeltaMovement(entity.getDeltaMovement().add(dx, 0, dz));
        entity.setSprinting(true);

        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
        return true;
    }

    public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributeMap, int amplifier) {

        AttributeInstance movementSpeed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(SPEED_MODIFIER_ID);
        }

        AttributeInstance stepHeight = entity.getAttribute(Attributes.STEP_HEIGHT);
        if (stepHeight != null) {
            stepHeight.removeModifier(STEP_HEIGHT_MODIFIER_ID);
        }

        super.removeAttributeModifiers(attributeMap);
    }
}