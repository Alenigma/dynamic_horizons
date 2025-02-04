package net.alenigma.dynamic_horizons;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod(DynamicHorizonsMod.MOD_ID)
public class DynamicHorizonsMod {
    public static final String MOD_ID = "dynamic_horizons";

    public DynamicHorizonsMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void tick(TickEvent.LevelTickEvent event) {
        if (!event.level.isClientSide() && event.phase == TickEvent.Phase.END && ModList.get().isLoaded("valkyrienskies")) {
            ValkyrienCompatibility.linkShips((ServerLevel) event.level);
        }
    }
}
