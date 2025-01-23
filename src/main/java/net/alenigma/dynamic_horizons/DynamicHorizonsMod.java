package net.alenigma.dynamic_horizons;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

@Mod(DynamicHorizonsMod.MOD_ID)
public class DynamicHorizonsMod {
    public static final String MOD_ID = "dynamic_horizons";

    public DynamicHorizonsMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }
}
