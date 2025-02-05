package net.alenigma.dynamic_horizons.mixin.cosmic_horizons;

import com.llamalad7.mixinextras.sugar.Local;
import net.alenigma.dynamic_horizons.PlanetRotator;
import net.lointain.cosmos.procedures.AtmosphericCollisionDetectorProcedure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(AtmosphericCollisionDetectorProcedure.class)
public class TravelFromPlanetMixin {
    @ModifyArg(method={"execute"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/Commands;performPrefixedCommand(Lnet/minecraft/commands/CommandSourceStack;Ljava/lang/String;)I"), index = 1, remap = false)
//    @ModifyArg(method={"execute"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/Commands;m_230957_(Lnet/minecraft/commands/CommandSourceStack;Ljava/lang/String;)I"), index = 1, remap = false)
    private static String changeTravelDestination(String pCommand, @Local(argsOnly = true) LevelAccessor world, @Local CompoundTag atmospheric_data) {
        return PlanetRotator.redirectTravelDestinationFromPlanet(pCommand, world, atmospheric_data);
    }
}
