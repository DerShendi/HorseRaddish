package net.shendi.horseraddish.sound;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.shendi.horseraddish.HorseRaddish;

import java.util.function.Supplier;

public class HRSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, HorseRaddish.MODID);

    public static final Supplier<SoundEvent> HORSE_LANDING_1 = registerSoundEvent("horse_landing_1");
    public static final Supplier<SoundEvent> HORSE_LANDING_2 = registerSoundEvent("horse_landing_2");
    public static final Supplier<SoundEvent> HORSE_LANDING_3 = registerSoundEvent("horse_landing_3");
    public static final Supplier<SoundEvent> HORSE_LANDING_4 = registerSoundEvent("horse_landing_4");
    public static final Supplier<SoundEvent> HORSE_LANDING_5 = registerSoundEvent("horse_landing_5");

    public static final DeferredSoundType HORSE_LANDING_SOUNDS = new DeferredSoundType(1f, 1f,
            HRSounds.HORSE_LANDING_1, HRSounds.HORSE_LANDING_2, HRSounds.HORSE_LANDING_3,
            HRSounds.HORSE_LANDING_4, HRSounds.HORSE_LANDING_5);


    private static Supplier<SoundEvent> registerSoundEvent(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(HorseRaddish.MODID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}