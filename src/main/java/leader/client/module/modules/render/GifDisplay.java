package leader.client.module.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import leader.client.event.EventTarget;
import leader.client.events.Render2DEvent;
import leader.client.module.Module;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.ListValue;
import leader.client.module.values.impl.SliderValue;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class GifDisplay extends Module {


    public final ListValue gifMode = new ListValue("GIF", new String[]{"AngryPig", "Best", "CryCat", "Dancer", "DieCat", "DiePig", "Kabo", "NoneBig", "PigFucker", "YaoMao"}, "AngryPig", this);
    public final BoolValue lockRatio = new BoolValue("Lock Ratio", true, this);
    public final SliderValue posX = new SliderValue("X", 200.0, 0.0, 3000.0, Representation.FLOAT, this);
    public final SliderValue posY = new SliderValue("Y", 100.0, 0.0, 3000.0, Representation.FLOAT, this);
    public final SliderValue imgWidth = new SliderValue("Width", 128.0, 1.0, 2000.0, Representation.FLOAT, this);
    public final SliderValue imgHeight = new SliderValue("Height", 128.0, 1.0, 2000.0, () -> !this.lockRatio.getValue(), Representation.FLOAT, this);

    private List<Integer> frameDelays;
    private List<Integer> textureIds;
    private int currentFrame;
    private long lastFrameTime;
    private String loadedGif = "";
    private int originalWidth;
    private int originalHeight;

    public GifDisplay() {
        super("GifDisplay", false, true);
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled()) return;

        String currentGif = this.gifMode.getValue();
        if (!currentGif.equals(this.loadedGif)) {
            this.loadGif(currentGif);
        }

        if (this.textureIds == null || this.textureIds.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - this.lastFrameTime >= this.frameDelays.get(this.currentFrame)) {
            this.currentFrame = (this.currentFrame + 1) % this.textureIds.size();
            this.lastFrameTime = now;
        }

        int texId = this.textureIds.get(this.currentFrame);
        if (texId == 0) return;

        int drawWidth = (int)(float) this.imgWidth.getValue();
        int drawHeight;
        if (this.lockRatio.getValue() && this.originalWidth > 0 && this.originalHeight > 0) {
            drawHeight = (int) ((float) drawWidth * (float) this.originalHeight / (float) this.originalWidth);
        } else {
            drawHeight = (int)(float) this.imgHeight.getValue();
        }
        int x = (int)(float) this.posX.getValue();
        int y = (int)(float) this.posY.getValue();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.bindTexture(texId);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0.0F, 0.0F, drawWidth, drawHeight, (float) drawWidth, (float) drawHeight);
    }

    private void loadGif(String name) {
        this.unloadTextures();
        this.currentFrame = 0;
        this.lastFrameTime = System.currentTimeMillis();

        try {
            IResource resource = mc.getResourceManager().getResource(
                    new ResourceLocation("minecraft", "leader/texture/gif/" + name + ".gif")
            );
            InputStream inputStream = resource.getInputStream();

            ImageReader reader = ImageIO.getImageReadersBySuffix("gif").next();
            ImageInputStream iis = ImageIO.createImageInputStream(inputStream);
            reader.setInput(iis);

            int count = reader.getNumImages(true);
            List<BufferedImage> frames = new ArrayList<>();
            List<Integer> delays = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                BufferedImage frame = reader.read(i);
                frames.add(frame);

                int delay = 100;
                try {
                    IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                    for (int j = 0; j < root.getChildNodes().getLength(); j++) {
                        if (root.getChildNodes().item(j) instanceof IIOMetadataNode) {
                            IIOMetadataNode node = (IIOMetadataNode) root.getChildNodes().item(j);
                            if ("GraphicControlExtension".equals(node.getNodeName())) {
                                String delayStr = node.getAttribute("delayTime");
                                if (!delayStr.isEmpty()) {
                                    delay = Integer.parseInt(delayStr) * 10;
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                delays.add(Math.max(delay, 20));
            }
            iis.close();
            reader.dispose();

            if (frames.isEmpty()) {
                return;
            }

            this.loadedGif = name;
            this.originalWidth = frames.get(0).getWidth();
            this.originalHeight = frames.get(0).getHeight();
            this.frameDelays = delays;
            this.textureIds = new ArrayList<>();
            for (BufferedImage frame : frames) {
                int[] pixels = frame.getRGB(0, 0, frame.getWidth(), frame.getHeight(), null, 0, frame.getWidth());

                int texId = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);

                ByteBuffer buffer = ByteBuffer.allocateDirect(4 * pixels.length).order(ByteOrder.nativeOrder());
                for (int pixel : pixels) {
                    buffer.put((byte) (pixel >> 16 & 0xFF));
                    buffer.put((byte) (pixel >> 8 & 0xFF));
                    buffer.put((byte) (pixel & 0xFF));
                    buffer.put((byte) (pixel >> 24 & 0xFF));
                }
                buffer.flip();

                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, frame.getWidth(), frame.getHeight(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
                this.textureIds.add(texId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisabled() {
        this.unloadTextures();
    }

    private void unloadTextures() {
        if (this.textureIds != null) {
            for (int texId : this.textureIds) {
                if (texId != 0) {
                    GL11.glDeleteTextures(texId);
                }
            }
        }
        this.frameDelays = null;
        this.textureIds = null;
        this.loadedGif = "";
        this.currentFrame = 0;
    }
}
