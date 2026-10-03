package leader.module.modules.render;

import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.TickEvent;
import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.world.biome.BiomeGenBase;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnvModifier extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty timeEnabled = new BooleanProperty("Time", false);
    public final ModeProperty timePreset = new ModeProperty("Time Preset", 1,
            new String[]{"Sunrise", "Noon", "Sunset", "Night", "Midnight", "Custom"}, this.timeEnabled::getValue);
    public final IntProperty customTime = new IntProperty("Custom Time", 6000, 0, 23999,
            () -> this.timeEnabled.getValue() && this.timePreset.getValue() == 5);
    public final ModeProperty weather = new ModeProperty("Weather", 0,
            new String[]{"Off", "Clear", "Rain", "Snow", "Thunder"});

    private final Map<BiomeGenBase, Float> snowTemperatures = new HashMap<>();
    private final Map<BiomeGenBase, Boolean> snowFlags = new HashMap<>();
    private static Field snowField;
    private static boolean snowFieldMissing;

    private boolean saved;
    private long savedTime;
    private boolean savedRaining;
    private boolean savedThundering;
    private float savedRainStrength;
    private float savedThunderStrength;

    public EnvModifier() {
        super("EnvModifier", false);
    }

    private static Field getSnowField() {
        if (snowField == null && !snowFieldMissing) {
            try {
                snowField = BiomeGenBase.class.getDeclaredField("enableSnow");
            } catch (NoSuchFieldException ignored) {
                try {
                    snowField = BiomeGenBase.class.getDeclaredField("field_76766_R");
                } catch (NoSuchFieldException ignored2) {
                    snowFieldMissing = true;
                    return null;
                }
            }
            if (snowField != null) {
                snowField.setAccessible(true);
            }
        }
        return snowField;
    }

    private void setBiomeSnow(BiomeGenBase biome, boolean snow) {
        Field field = getSnowField();
        if (field == null) return;
        try {
            field.setBoolean(biome, snow);
        } catch (IllegalAccessException ignored) {
        }
    }

    private long getTargetTime() {
        return switch (this.timePreset.getValue()) {
            case 0 -> 23000L;
            case 1 -> 6000L;
            case 2 -> 12000L;
            case 3 -> 15000L;
            case 4 -> 18000L;
            default -> (long) this.customTime.getValue();
        };
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.theWorld == null) return;
        if (this.weather.getValue() == 3) {
            this.applySnow();
        } else {
            this.clearSnow();
        }
    }

    public void applyWorldState() {
        if (mc.theWorld == null) return;
        this.savedTime = mc.theWorld.getWorldTime();
        this.savedRaining = mc.theWorld.getWorldInfo().isRaining();
        this.savedThundering = mc.theWorld.getWorldInfo().isThundering();
        this.savedRainStrength = mc.theWorld.getRainStrength(1.0F);
        this.savedThunderStrength = mc.theWorld.getThunderStrength(1.0F);
        this.saved = true;

        if (this.timeEnabled.getValue()) {
            mc.theWorld.setWorldTime(this.getTargetTime());
        }

        int mode = this.weather.getValue();
        if (mode == 0) return;

        boolean precipitation = mode != 1;
        boolean thunder = mode == 4;
        mc.theWorld.getWorldInfo().setRaining(precipitation);
        mc.theWorld.getWorldInfo().setThundering(thunder);
        mc.theWorld.setRainStrength(precipitation ? 1.0F : 0.0F);
        mc.theWorld.setThunderStrength(thunder ? 1.0F : 0.0F);
    }

    public void restoreWorldState() {
        if (!this.saved) return;
        this.saved = false;
        if (mc.theWorld == null) return;
        mc.theWorld.setWorldTime(this.savedTime);
        mc.theWorld.getWorldInfo().setRaining(this.savedRaining);
        mc.theWorld.getWorldInfo().setThundering(this.savedThundering);
        mc.theWorld.setRainStrength(this.savedRainStrength);
        mc.theWorld.setThunderStrength(this.savedThunderStrength);
    }

    private void applySnow() {
        if (mc.thePlayer == null) return;
        BiomeGenBase biome = mc.theWorld.getBiomeGenForCoords(
                new BlockPos(mc.thePlayer.posX, 0.0D, mc.thePlayer.posZ));
        if (biome == null || this.snowTemperatures.containsKey(biome)) return;
        this.snowTemperatures.put(biome, biome.temperature);
        this.snowFlags.put(biome, biome.getEnableSnow());
        biome.temperature = 0.0F;
        this.setBiomeSnow(biome, true);
    }

    private void clearSnow() {
        if (this.snowTemperatures.isEmpty()) return;
        for (Map.Entry<BiomeGenBase, Float> entry : this.snowTemperatures.entrySet()) {
            entry.getKey().temperature = entry.getValue();
            this.setBiomeSnow(entry.getKey(), this.snowFlags.get(entry.getKey()));
        }
        this.snowTemperatures.clear();
        this.snowFlags.clear();
    }

    @Override
    public void onDisabled() {
        this.clearSnow();
    }

    @Override
    public String[] getSuffix() {
        List<String> suffix = new ArrayList<>();
        if (this.timeEnabled.getValue()) {
            suffix.add(this.timePreset.getValue() == 5
                    ? String.valueOf(this.customTime.getValue())
                    : this.timePreset.getModeString());
        }
        if (this.weather.getValue() != 0) {
            suffix.add(this.weather.getModeString());
        }
        return suffix.isEmpty() ? new String[]{"Off"} : suffix.toArray(new String[0]);
    }
}
