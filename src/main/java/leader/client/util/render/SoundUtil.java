package leader.client.util.render;

import leader.client.util.InstanceAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.util.ResourceLocation;

public class SoundUtil implements InstanceAccess {

    public static void playSound(String soundName) {
        SoundHandler soundHandler = mc.getSoundHandler();
        if (soundHandler != null) {
            PositionedSoundRecord positionedSoundRecord = PositionedSoundRecord.create(new ResourceLocation(soundName));
            soundHandler.playSound(positionedSoundRecord);
        }
    }
}
