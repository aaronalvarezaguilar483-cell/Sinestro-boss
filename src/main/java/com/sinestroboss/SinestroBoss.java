package com.sinestroboss;

import net.fabricmc.api.ModInitializer;

public class SinestroBoss implements ModInitializer {
    public static final String MOD_ID = "sinestro_boss";

    @Override
    public void onInitialize() {
        ModEntities.register();
        ModItems.register();
    }
}
