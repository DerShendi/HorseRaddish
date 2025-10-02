package net.shendi.horseraddish;

import net.shendi.horseraddish.event.HRDispenserEvents;
import net.shendi.horseraddish.event.HorseNetEvents;
import net.shendi.horseraddish.item.HRItems;
import net.shendi.horseraddish.sound.HRSounds;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(HorseRaddish.MODID)
public class HorseRaddish {
    public static final String MODID = "horseraddish";
    public static final Logger LOGGER = LogUtils.getLogger();


    public HorseRaddish(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
        modEventBus.addListener(this::addCreative);

        HRItems.register(modEventBus);
        HRSounds.register(modEventBus);
        NeoForge.EVENT_BUS.register(new HorseNetEvents());


        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }



    private void commonSetup(final FMLCommonSetupEvent event) {
        HRDispenserEvents.register();
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {

        if(event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(HRItems.EMPTY_NET);
            event.accept(HRItems.HORSE_IN_A_NET);
        }
    }


    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

    }
}
