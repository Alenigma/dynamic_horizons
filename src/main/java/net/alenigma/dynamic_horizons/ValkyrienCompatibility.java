package net.alenigma.dynamic_horizons;

import net.lointain.cosmos.network.CosmosModVariables;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.impl.game.ShipTeleportDataImpl;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.GameTickForceApplier;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

public class ValkyrienCompatibility {
    public static void linkShips(ServerLevel level) {
        Tag vars = CosmosModVariables.WorldVariables.get(level).render_data_map.get(level.dimension().location().toString());
        if (vars == null) return;
        for (Tag e : ((ListTag) vars)) {
            CompoundTag obj = (CompoundTag) e;
            if (!obj.contains("ship_slug")) continue;
            Ship ship = VSGameUtilsKt.getAllShips(level).stream()
                    .filter(o -> obj.getString("ship_slug").equals(o.getSlug()))
                    .findAny().orElse(null);
            if (ship == null) continue;
            Vec3 futurePos = PlanetRotator.getCords(
                    level,
                    level.dimension().location().toString(),
                    PlanetRotator.getPartialTick(level)+20,
                    new Vec3(obj.getDouble("x"), obj.getDouble("y"), obj.getDouble("z")),
                    obj
            );
            ServerShip serverShip = VSGameUtilsKt.getShipObjectWorld(level).getAllShips().getById(ship.getId());
            if (serverShip.getAttachment(GameTickForceApplier.class) == null) {
                serverShip.saveAttachment(GameTickForceApplier.class, new GameTickForceApplier());
            }
            GameTickForceApplier forceApplier = serverShip.getAttachment(GameTickForceApplier.class);
            Vec3 currentPosition = VectorConversionsMCKt.toMinecraft(serverShip.getTransform().getPositionInWorld());
            Vec3 velocity = VectorConversionsMCKt.toMinecraft(serverShip.getVelocity());
            Vec3 error = futurePos.subtract(currentPosition);
            double mass = serverShip.getInertiaData().getMass();
            Vec3 force = error.scale(mass*20).subtract(velocity.scale(mass*20));
            Quaterniond futureRot = new Quaterniond().rotateXYZ(0.017453293F * obj.getDouble("pitch"), 0.017453293F * obj.getDouble("yaw"), 0.017453293F * obj.getDouble("roll"));
            if (!futureRot.equals(serverShip.getTransform().getShipToWorldRotation())) VSGameUtilsKt.getShipObjectWorld(level).teleportShip(serverShip, new ShipTeleportDataImpl(serverShip.getTransform().getPositionInWorld(), futureRot, new Vector3d(), new Vector3d(), null, null));
            if (force.length() < 1e12) forceApplier.applyInvariantForce(VectorConversionsMCKt.toJOML(force));
            else forceApplier.applyInvariantForce(VectorConversionsMCKt.toJOML(force.scale(1e12/force.length())));
        }
    }
}
