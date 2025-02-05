package net.alenigma.dynamic_horizons;

import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.lointain.cosmos.network.CosmosModVariables;
import net.lointain.cosmos.procedures.BrightnessProviderProcedure;
import net.lointain.cosmos.procedures.CubeVertexOrientorProcedure;
import net.lointain.cosmos.procedures.JsontomapconverterProcedure;
import net.lointain.cosmos.procedures.SimpleOcclusionProviderProcedure;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.minecraft.world.level.GameRules.RULE_DAYLIGHT;

public class PlanetRotator {
    public static float getPartialTick(LevelAccessor world) {
        return world.isClientSide() ? getClientPartialTick() : 0;
    }

    @OnlyIn(Dist.CLIENT)
    private static float getClientPartialTick() {
        return Minecraft.getInstance().getPartialTick();
    }

    public static Vec3 getCords(LevelAccessor world, String dimension, double partialTick, Vec3 pos, CompoundTag obj) {
        if (obj.contains("static") && obj.getByte("static") == 1) return pos;
        double ticks = world.getLevelData().getDayTime();
        CompoundTag anchor = obj.contains("anchor_name") ?
                (CompoundTag) ((ListTag) Objects.requireNonNull(CosmosModVariables.WorldVariables.get(world).render_data_map.get(dimension))).stream().filter(e -> ((CompoundTag) e).getString("object_name").equals(obj.getString("anchor_name"))).findFirst().orElse(new CompoundTag())
                : (CompoundTag) ((ListTag) Objects.requireNonNull(CosmosModVariables.WorldVariables.get(world).render_data_map.get(dimension))).get(0);
        double anchor_x = anchor.getDouble("x");
        double anchor_y = anchor.getDouble("y");
        double anchor_z = anchor.getDouble("z");
        Vec3 chain_cords = new Vec3(anchor_x, anchor_y, anchor_z);
        double r = chain_cords.distanceTo(pos);
        if (obj.contains("anchor_name")) {
            Vec3 new_chain_cords = getCords(world, dimension, partialTick, chain_cords, anchor);
            r = new_chain_cords.subtract(chain_cords).add(pos).distanceTo(new_chain_cords);
            chain_cords = new_chain_cords;
        }
        if (r == (double) 0.0F) return pos;
        if (!world.getLevelData().getGameRules().getBoolean(RULE_DAYLIGHT)) partialTick = 0;
        CompoundTag dim_data = ((CompoundTag) CosmosModVariables.WorldVariables.get(world).dimensional_data.get(dimension));
        double speed = obj.contains("speed") ? obj.getDouble("speed") : dim_data.contains("default_speed") ? dim_data.getDouble("default_speed") : 0.1;
        double a = (ticks + partialTick) * Math.PI * (((double) 1.0F)/r) * speed;
        Vec3 rotation = new Vec3(r*Math.sin(a), 0, r*Math.cos(a));
        rotation = rotation.xRot(0.017453293F * obj.getFloat("orbit_pitch"));
        rotation = rotation.yRot(0.017453293F * obj.getFloat("orbit_yaw"));
        rotation = rotation.zRot(0.017453293F * obj.getFloat("orbit_roll"));
        pos = chain_cords.add(rotation);
        return pos;
    }

    public static List<Object> changeOrder(LevelAccessor world, Entity entity, double partialTick, ListTag map, double order, String dimension, Vec3 position) {
        if (map == null || dimension == null || position == null)
            return new ArrayList<>();
        Map<Object, Object> starting_map = new HashMap<>();
        List<Object> sorted_order = new ArrayList<>();
        for (int iter = 0; iter < map.size(); iter++) {
            CompoundTag mint = (CompoundTag) map.get(iter);
            Vec3 start_pos = getCords(world, entity.level().dimension().location().toString(), partialTick, new Vec3(((DoubleTag) mint.get("x")).getAsDouble(), ((DoubleTag) mint.get("y")).getAsDouble(), ((DoubleTag) mint.get("z")).getAsDouble()), mint);
            if (mint.contains("function") && mint.get("function").getAsString().equals("ring")) {
                Vec3 ring_pos = switch (mint.get("type").getAsString()) {
                    case "ring1" -> new Vec3(-((DoubleTag) mint.get("radius")).getAsDouble(), 0.0F, 0.0F);
                    case "ring2" -> new Vec3(((DoubleTag) mint.get("radius")).getAsDouble(), 0.0F, 0.0F);
                    case "ring3" -> new Vec3(0.0F, 0.0F, -((DoubleTag) mint.get("radius")).getAsDouble());
                    default -> new Vec3(0.0F, 0.0F, ((DoubleTag) mint.get("radius")).getAsDouble());
                };
                start_pos = start_pos.add(ring_pos.zRot(0.017453293F * (float)(((DoubleTag) mint.get("roll")).getAsDouble())).xRot(-0.017453293F * (float)((DoubleTag) mint.get("pitch")).getAsDouble()).yRot(0.017453293F * (float)(-((DoubleTag) mint.get("yaw")).getAsDouble())));
            }
            starting_map.put(position.distanceTo(start_pos), iter);
        }
        for (Object _listValueIterator : starting_map.keySet().stream().sorted().toList()) sorted_order.add(starting_map.get(_listValueIterator));
        if (order == (double)-1.0F) Collections.reverse(sorted_order);
        return sorted_order;
    }

    public static Tag recalculateLight(CompoundTag instance, String pKey, Operation<Tag> original, LevelAccessor world, Entity entity, double partialTick) {
        if (!pKey.equals("light_data") && !pKey.equals("alpha_data") && !pKey.equals("i_alpha_data")) return original.call(instance, pKey);
        ListTag light_source_list = (ListTag) CosmosModVariables.WorldVariables.get(world).light_source_map.get(entity.level().dimension().location().toString());
        ListTag opaque_object_list = (ListTag) CosmosModVariables.WorldVariables.get(world).opaque_object_map.get(entity.level().dimension().location().toString());
        Vec3 objPos = PlanetRotator.getCords(world, entity.level().dimension().location().toString(), partialTick, new Vec3(((DoubleTag) instance.get("x")).getAsDouble(), ((DoubleTag) instance.get("y")).getAsDouble(), ((DoubleTag) instance.get("z")).getAsDouble()), instance);
        double scale;
        Vec3 objScale = Vec3.ZERO;
        if (!instance.get("function").getAsString().equals("ring")) {
            scale = ((DoubleTag) instance.get("scale")).getAsDouble();
            objScale = new Vec3(scale, scale, scale);
        }
        Vec3 objRot = new Vec3(((DoubleTag) instance.get("pitch")).getAsDouble(), ((DoubleTag) instance.get("yaw")).getAsDouble(), ((DoubleTag) instance.get("roll")).getAsDouble());
        int i = 0;
        switch (pKey) {
            case "light_data":
                JsonObject light_data = new JsonObject();
                if (instance.get("function").getAsString().equals("ring")) {
                    for (int seq = 0; seq < 4; seq++) {
                        float ring_rot = switch (instance.get("type").getAsString()) {
                            case "ring1" -> 0.0F;
                            case "ring2" -> 180.0F;
                            case "ring3" -> 270.0F;
                            default -> 90.0F;
                        };
                        float ring_scale = (float) ((DoubleTag) instance.get("scale_radius")).getAsDouble();
                        light_data.addProperty((new DecimalFormat("##.##")).format(seq * 4), SimpleOcclusionProviderProcedure.execute(light_source_list, objRot.x(), objRot.z(), ring_scale*0.25, objRot.y(), objPos, seq == 1 ? objPos.add((new Vec3(-0.5F, 0.0F, 0.0F)).xRot(-(float)Math.PI).yRot(0.017453293F * ring_rot).scale(ring_scale).zRot(-0.017453292F * (float)(-objRot.z())).xRot(-0.017453292F * (float)objRot.x()).yRot(0.017453293F * (float)(-objRot.y()))) : (seq == 2 ? objPos.add((new Vec3(-0.25F, 0.0F, 0.0F)).yRot(0.017453293F * ring_rot).scale(ring_scale).zRot(-0.017453292F * (float)(-objRot.z())).xRot(-0.017453292F * (float)objRot.x()).yRot(0.017453293F * (float)(-objRot.y()))) : (seq == 3 ? objPos.add((new Vec3(-0.25F, 0.0F, 0.0F)).xRot(-(float)Math.PI).yRot(0.017453293F * ring_rot).scale(ring_scale).zRot(-0.017453292F * (float)(-objRot.z())).xRot(-0.017453292F * (float)objRot.x()).yRot(0.017453293F * (float)(-objRot.y()))) : objPos.add((new Vec3(-0.5F, 0.0F, 0.0F)).yRot(0.017453293F * ring_rot).scale(ring_scale).zRot(-0.017453292F * (float)(-objRot.z())).xRot(-0.017453292F * (float)objRot.x()).yRot(0.017453293F * (float)(-objRot.y())))))));
                        light_data.addProperty((new DecimalFormat("##.##")).format(1 + seq * 4), SimpleOcclusionProviderProcedure.execute(light_source_list, objRot.x(), objRot.z(), ring_scale*0.25, objRot.y(), objPos, seq == 1 ? objPos.add((new Vec3(-0.5F, 0.0F, -0.5F)).xRot(-(float)Math.PI).yRot(0.017453293F * ring_rot).scale(ring_scale).zRot(-0.017453292F * (float)(-objRot.z())).xRot(-0.017453292F * (float)objRot.x()).yRot(0.017453293F * (float)(-objRot.y()))) : (seq == 2 ? objPos.add((new Vec3(-0.25F, 0.0F, 0.25F)).yRot(0.017453293F * ring_rot).scale(ring_scale).zRot(-0.017453292F * (float)(-objRot.z())).xRot(-0.017453292F * (float)objRot.x()).yRot(0.017453293F * (float)(-objRot.y()))) : (seq == 3 ? objPos.add((new Vec3(-0.25F, 0.0F, 0.25F)).xRot(-(float)Math.PI).yRot(0.017453293F * ring_rot).scale(ring_scale).zRot(-0.017453292F * (float)(-objRot.z())).xRot(-0.017453292F * (float)objRot.x()).yRot(0.017453293F * (float)(-objRot.y()))) : objPos.add((new Vec3(-0.5F, 0.0F, -0.5F)).yRot(0.017453293F * ring_rot).scale(ring_scale).zRot(-0.017453292F * (float)(-objRot.z())).xRot(-0.017453292F * (float)objRot.x()).yRot(0.017453293F * (float)(-objRot.y())))))));
                        light_data.addProperty((new DecimalFormat("##.##")).format(2 + seq * 4), SimpleOcclusionProviderProcedure.execute(light_source_list, objRot.x(), objRot.z(), ring_scale*0.25, objRot.y(), objPos, seq == 1 ? objPos.add((new Vec3(-0.25F, 0.0F, -0.25F)).xRot(-(float)Math.PI).yRot(0.017453293F * ring_rot).scale(ring_scale).zRot(-0.017453292F * (float)(-objRot.z())).xRot(-0.017453292F * (float)objRot.x()).yRot(0.017453293F * (float)(-objRot.y()))) : (seq == 2 ? objPos.add((new Vec3(-0.5F, 0.0F, 0.5F)).yRot(0.017453293F * ring_rot).scale(ring_scale).zRot(-0.017453292F * (float)(-objRot.z())).xRot(-0.017453292F * (float)objRot.x()).yRot(0.017453293F * (float)(-objRot.y()))) : (seq == 3 ? objPos.add((new Vec3(-0.5F, 0.0F, 0.5F)).xRot(-(float)Math.PI).yRot(0.017453293F * ring_rot).scale(ring_scale).zRot(-0.017453292F * (float)(-objRot.z())).xRot(-0.017453292F * (float)objRot.x()).yRot(0.017453293F * (float)(-objRot.y()))) : objPos.add((new Vec3(-0.25F, 0.0F, -0.25F)).yRot(0.017453293F * ring_rot).scale(ring_scale).zRot(-0.017453292F * (float)(-objRot.z())).xRot(-0.017453292F * (float)objRot.x()).yRot(0.017453293F * (float)(-objRot.y())))))));
                        light_data.addProperty((new DecimalFormat("##.##")).format(3 + seq * 4), SimpleOcclusionProviderProcedure.execute(light_source_list, objRot.x(), objRot.z(), ring_scale*0.25, objRot.y(), objPos, seq == 1 ? objPos.add((new Vec3(-0.25F, 0.0F, 0.0F)).xRot(-(float)Math.PI).yRot(0.017453293F * ring_rot).scale(ring_scale).zRot(-0.017453292F * (float)(-objRot.z())).xRot(-0.017453292F * (float)objRot.x()).yRot(0.017453293F * (float)(-objRot.y()))) : (seq == 2 ? objPos.add((new Vec3(-0.5F, 0.0F, 0.0F)).yRot(0.017453293F * ring_rot).scale(ring_scale).zRot(-0.017453292F * (float)(-objRot.z())).xRot(-0.017453292F * (float)objRot.x()).yRot(0.017453293F * (float)(-objRot.y()))) : (seq == 3 ? objPos.add((new Vec3(-0.5F, 0.0F, 0.0F)).xRot(-(float)Math.PI).yRot(0.017453293F * ring_rot).scale(ring_scale).zRot(-0.017453292F * (float)(-objRot.z())).xRot(-0.017453292F * (float)objRot.x()).yRot(0.017453293F * (float)(-objRot.y()))) : objPos.add((new Vec3(-0.25F, 0.0F, 0.0F)).yRot(0.017453293F * ring_rot).scale(ring_scale).zRot(-0.017453292F * (float)(-objRot.z())).xRot(-0.017453292F * (float)objRot.x()).yRot(0.017453293F * (float)(-objRot.y())))))));
                    }
                    return JsontomapconverterProcedure.execute(light_data);
                }
                for (Direction directioniterator : Direction.values()) {
                    Vec3 direction_vector = new Vec3(directioniterator.step());
                    light_data.addProperty((new DecimalFormat("##.##")).format(i * (double)4.0F), BrightnessProviderProcedure.execute(light_source_list, opaque_object_list, -1.0F, "color", "none", direction_vector, objPos, objRot, objScale, CubeVertexOrientorProcedure.execute(directioniterator, 0.0F, new Vec3(-0.5F, 0.5F, -0.5F))));
                    light_data.addProperty((new DecimalFormat("##.##")).format((double)1.0F + i * (double)4.0F), BrightnessProviderProcedure.execute(light_source_list, opaque_object_list, -1.0F, "color", "none", direction_vector, objPos, objRot, objScale, CubeVertexOrientorProcedure.execute(directioniterator, 0.0F, new Vec3(-0.5F, 0.5F, 0.5F))));
                    light_data.addProperty((new DecimalFormat("##.##")).format((double)2.0F + i * (double)4.0F), BrightnessProviderProcedure.execute(light_source_list, opaque_object_list, -1.0F, "color", "none", direction_vector, objPos, objRot, objScale, CubeVertexOrientorProcedure.execute(directioniterator, 0.0F, new Vec3(0.5F, 0.5F, 0.5F))));
                    light_data.addProperty((new DecimalFormat("##.##")).format((double)3.0F + i * (double)4.0F), BrightnessProviderProcedure.execute(light_source_list, opaque_object_list, -1.0F, "color", "none", direction_vector, objPos, objRot, objScale, CubeVertexOrientorProcedure.execute(directioniterator, 0.0F, new Vec3(0.5F, 0.5F, -0.5F))));
                    i++;
                }
                return JsontomapconverterProcedure.execute(light_data);
            case "alpha_data":
                JsonObject transparency_data = new JsonObject();
                for (Direction directioniterator : Direction.values()) {
                    Vec3 direction_vector = new Vec3(directioniterator.step());
                    transparency_data.addProperty((new DecimalFormat("##.##")).format(i * (double)4.0F), (int)BrightnessProviderProcedure.execute(light_source_list, opaque_object_list, -1.0F, "alpha", "none", direction_vector, objPos, objRot, objScale, CubeVertexOrientorProcedure.execute(directioniterator, 0.0F, new Vec3(-0.5F, 0.5F, -0.5F))) >>> 24);
                    transparency_data.addProperty((new DecimalFormat("##.##")).format((double)1.0F + i * (double)4.0F), (int)BrightnessProviderProcedure.execute(light_source_list, opaque_object_list, -1.0F, "alpha", "none", direction_vector, objPos, objRot, objScale, CubeVertexOrientorProcedure.execute(directioniterator, 0.0F, new Vec3(-0.5F, 0.5F, 0.5F))) >>> 24);
                    transparency_data.addProperty((new DecimalFormat("##.##")).format((double)2.0F + i * (double)4.0F), (int)BrightnessProviderProcedure.execute(light_source_list, opaque_object_list, -1.0F, "alpha", "none", direction_vector, objPos, objRot, objScale, CubeVertexOrientorProcedure.execute(directioniterator, 0.0F, new Vec3(0.5F, 0.5F, 0.5F))) >>> 24);
                    transparency_data.addProperty((new DecimalFormat("##.##")).format((double)3.0F + i * (double)4.0F), (int)BrightnessProviderProcedure.execute(light_source_list, opaque_object_list, -1.0F, "alpha", "none", direction_vector, objPos, objRot, objScale, CubeVertexOrientorProcedure.execute(directioniterator, 0.0F, new Vec3(0.5F, 0.5F, -0.5F))) >>> 24);
                    i++;
                }
                return JsontomapconverterProcedure.execute(transparency_data);
            case "i_alpha_data":
                JsonObject i_alpha_data = new JsonObject();
                for (Direction directioniterator : Direction.values()) {
                    Vec3 direction_vector = new Vec3(directioniterator.step());
                    i_alpha_data.addProperty((new DecimalFormat("##.##")).format(i * (double)4.0F), (int)BrightnessProviderProcedure.execute(light_source_list, opaque_object_list, -1.0F, "i_alpha", "none", direction_vector, objPos, objRot, objScale, CubeVertexOrientorProcedure.execute(directioniterator, 0.0F, new Vec3(-0.5F, 0.5F, -0.5F))) >>> 24);
                    i_alpha_data.addProperty((new DecimalFormat("##.##")).format((double)1.0F + i * (double)4.0F), (int)BrightnessProviderProcedure.execute(light_source_list, opaque_object_list, -1.0F, "i_alpha", "none", direction_vector, objPos, objRot, objScale, CubeVertexOrientorProcedure.execute(directioniterator, 0.0F, new Vec3(-0.5F, 0.5F, 0.5F))) >>> 24);
                    i_alpha_data.addProperty((new DecimalFormat("##.##")).format((double)2.0F + i * (double)4.0F), (int)BrightnessProviderProcedure.execute(light_source_list, opaque_object_list, -1.0F, "i_alpha", "none", direction_vector, objPos, objRot, objScale, CubeVertexOrientorProcedure.execute(directioniterator, 0.0F, new Vec3(0.5F, 0.5F, 0.5F))) >>> 24);
                    i_alpha_data.addProperty((new DecimalFormat("##.##")).format((double)3.0F + i * (double)4.0F), (int)BrightnessProviderProcedure.execute(light_source_list, opaque_object_list, -1.0F, "i_alpha", "none", direction_vector, objPos, objRot, objScale, CubeVertexOrientorProcedure.execute(directioniterator, 0.0F, new Vec3(0.5F, 0.5F, -0.5F))) >>> 24);
                    i++;
                }
                return JsontomapconverterProcedure.execute(i_alpha_data);
            default:
                return original.call(instance, pKey);
        }
    }

    public static String redirectTravelDestination (String pCommand, LevelAccessor world, CompoundTag atmospheric_data) {
        if (pCommand.contains("tp") && atmospheric_data.contains("object_name")) {
            CompoundTag obj = (CompoundTag) ((ListTag) Objects.requireNonNull(CosmosModVariables.WorldVariables.get(world).render_data_map.get(atmospheric_data.get("travel_to").getAsString()))).stream().filter(e -> Objects.requireNonNullElse(((CompoundTag) e).get("object_name"), new CompoundTag()).getAsString().equals(atmospheric_data.get("object_name").getAsString())).findFirst().orElse(new CompoundTag());
            String regex = "(-?\\d*) (-?\\d*) (-?\\d*)$";
            Matcher matcher = Pattern.compile(regex).matcher(pCommand);
            if (matcher.find()) {
                Vec3 newPos = PlanetRotator.getCords(world, atmospheric_data.get("travel_to").getAsString(), getPartialTick(world), new Vec3(((DoubleTag) obj.get("x")).getAsDouble(), ((DoubleTag) obj.get("y")).getAsDouble(), ((DoubleTag) obj.get("z")).getAsDouble()), obj);
                return pCommand.replaceFirst(regex, newPos.x + " " + (newPos.y+((DoubleTag) obj.get("scale")).getAsDouble()) + " " + newPos.z);
            }
        }
        return pCommand;
    }
}