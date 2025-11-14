package net.shendi.horseraddish.item;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.shendi.horseraddish.HorseRaddish;

public class HRItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HorseRaddish.MODID);

    public static final DeferredItem<Item> EMPTY_NET = ITEMS.register("empty_net",
            () -> new NetItem(new Item.Properties().stacksTo(16), false));

    public static final DeferredItem<Item> HORSE_IN_A_NET = ITEMS.register("horse_in_a_net",
            () -> new NetItem(new Item.Properties().stacksTo(1), true));

    public static final DeferredItem<Item> RADISH = ITEMS.register("radish",
            () -> new Item(new Item.Properties().food(HRFoods.RADISH)));

    public static final DeferredItem<Item> HORSE_RADISH = ITEMS.register("horse_radish",
            () -> new Item(new Item.Properties().food(HRFoods.HORSE_RADISH)));


    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}