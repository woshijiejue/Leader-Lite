package leader.client.module.modules.render;

import leader.client.Leader;
import leader.client.event.EventTarget;
import leader.client.events.AttackEvent;
import leader.client.module.Module;
import leader.client.property.properties.BooleanProperty;
import leader.client.property.properties.FloatProperty;
import leader.client.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;

public class Animations extends Module {

        private static final Minecraft mc = Minecraft.getMinecraft();
        private static final float MODEL_SCALE = 0.0625F;
        private static final long ATTACK_DURATION_NS = 390_000_000L;

        private static DragonClawModel dragonClawModel;

        public final ModeProperty mode = new ModeProperty(
                "mode",
                2,
                new String[]{"1.8", "Swing", "Push", "Dragon"}
        );

        public final BooleanProperty cancelEquip =
                new BooleanProperty("Cancel Equip", false);

        public final BooleanProperty cancelEquipBlockingOnly =
                new BooleanProperty(
                        "Cancel Equip Blocking Only",
                        true,
                        () -> this.cancelEquip.getValue()
                );

        public final FloatProperty itemSize =
                new FloatProperty("Item Size", 0.0F, -0.5F, 0.5F);

        public final FloatProperty itemFov =
                new FloatProperty("Item Fov", 0.0F, -5.0F, 5.0F);

        public final FloatProperty itemPosX =
                new FloatProperty("Item Pos X", 0.0F, -1.0F, 1.0F);

        public final FloatProperty itemPosY =
                new FloatProperty("Item Pos Y", 0.0F, -1.0F, 1.0F);

        public final FloatProperty itemPosZ =
                new FloatProperty("Item Pos Z", 0.0F, -1.0F, 1.0F);

        public final FloatProperty blockPosX =
                new FloatProperty("Block Pos X", 0.0F, -1.0F, 1.0F);

        public final FloatProperty blockPosY =
                new FloatProperty("Block Pos Y", 0.0F, -1.0F, 1.0F);

        public final FloatProperty blockPosZ =
                new FloatProperty("Block Pos Z", 0.0F, -1.0F, 1.0F);

        public final FloatProperty swingSpeed =
                new FloatProperty("Swing Speed", 1.0F, 0.1F, 5.0F);

        public final FloatProperty clawScale =
                new FloatProperty(
                        "Claw Scale",
                        0.30F,
                        0.10F,
                        0.60F,
                        () -> mode.getValue() == 3
                );

        private long lastAttackLeft;
        private long lastAttackRight;
        private boolean nextLeft;

        public Animations() {
                super("Animations", false);
        }

        private static void initModel() {
                if (dragonClawModel == null) {
                        dragonClawModel = new DragonClawModel();
                }
        }

        @EventTarget
        public void onAttack(AttackEvent event) {
                if (mode.getValue() != 3) {
                        return;
                }

                long now = System.nanoTime();

                if (nextLeft) {
                        lastAttackLeft = now;
                } else {
                        lastAttackRight = now;
                }

                nextLeft = !nextLeft;
        }

        public static void renderDragonClaws(
                float partialTicks,
                float equipProgress,
                float swingProgress
        ) {
                if (mc.thePlayer == null) {
                        return;
                }

                Animations animations = getInstance();
                if (animations == null || !animations.isEnabled()) {
                        return;
                }

                initModel();

                if (dragonClawModel == null) {
                        return;
                }

                long now = System.nanoTime();

                float speed = Math.max(0.1F, animations.swingSpeed.getValue());
                long duration = (long) (ATTACK_DURATION_NS / speed);

                float leftProgress = getAttackProgress(
                        animations.lastAttackLeft,
                        now,
                        duration
                );

                float rightProgress = getAttackProgress(
                        animations.lastAttackRight,
                        now,
                        duration
                );

                float ticks = mc.thePlayer.ticksExisted + partialTicks;
                float size = Math.max(0.1F, animations.itemSize.getValue() + 1.0F);
                float scale = animations.clawScale.getValue();

                GlStateManager.pushMatrix();

                GlStateManager.translate(
                        animations.itemPosX.getValue(),
                        animations.itemPosY.getValue(),
                        animations.itemPosZ.getValue()
                );

                GlStateManager.scale(size, size, size);

                renderClaw(
                        false,
                        scale,
                        equipProgress,
                        swingProgress,
                        leftProgress,
                        rightProgress,
                        ticks
                );

                renderClaw(
                        true,
                        scale,
                        equipProgress,
                        swingProgress,
                        rightProgress,
                        leftProgress,
                        ticks
                );

                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.popMatrix();
        }

        private static Animations getInstance() {
                if (Leader.moduleManager == null) {
                        return null;
                }

                return (Animations) Leader.moduleManager.modules.get(Animations.class);
        }

        private static float getAttackProgress(
                long attackTime,
                long currentTime,
                long duration
        ) {
                if (attackTime == 0L || duration <= 0L) {
                        return 1.0F;
                }

                float progress = (currentTime - attackTime) / (float) duration;
                return MathHelper.clamp_float(progress, 0.0F, 1.0F);
        }

        private static void renderClaw(
                boolean right,
                float modelScale,
                float equipProgress,
                float vanillaSwing,
                float attackProgress,
                float otherAttackProgress,
                float ticks
        ) {
                AttackPose pose = getAttackPose(attackProgress);
                AttackPose otherPose = getAttackPose(otherAttackProgress);

                float side = right ? 1.0F : -1.0F;

                float idlePhase = ticks * 0.075F + (right ? 1.25F : 0.0F);
                float idleSin = MathHelper.sin(idlePhase);
                float idleCos = MathHelper.cos(idlePhase * 0.72F);

                float vanillaSwingRoot = MathHelper.sin(
                        MathHelper.sqrt_float(vanillaSwing) * (float) Math.PI
                );

                GlStateManager.pushMatrix();

                // Base first-person position.
                GlStateManager.translate(
                        0.68F * side,
                        -0.58F - equipProgress * 0.20F,
                        -0.82F
                );

// Turn both claws slightly toward the center of the screen.
                GlStateManager.rotate(
                        -25.0F * side,
                        0.0F,
                        1.0F,
                        0.0F
                );

                GlStateManager.rotate(
                        10.0F,
                        1.0F,
                        0.0F,
                        0.0F
                );

                GlStateManager.rotate(
                        -10.0F * side,
                        0.0F,
                        0.0F,
                        1.0F
                );

                // Breathing and idle movement.
                GlStateManager.translate(
                        idleSin * 0.006F * side,
                        idleCos * 0.008F,
                        idleSin * 0.004F
                );

                GlStateManager.rotate(
                        idleSin * 1.5F * side,
                        0.0F,
                        1.0F,
                        0.0F
                );

                GlStateManager.rotate(
                        idleCos * 1.2F,
                        1.0F,
                        0.0F,
                        0.0F
                );

                // Very small vanilla swing feedback. It must not control the attack.
                GlStateManager.translate(
                        -0.012F * vanillaSwingRoot * side,
                        0.006F * vanillaSwingRoot,
                        -0.010F * vanillaSwingRoot
                );

                GlStateManager.rotate(
                        -3.0F * vanillaSwingRoot,
                        1.0F,
                        0.0F,
                        0.0F
                );

                // Wind-up.
                GlStateManager.translate(
                        -0.08F * side * pose.windup,
                        0.065F * pose.windup,
                        0.15F * pose.windup
                );

                GlStateManager.rotate(
                        -24.0F * side * pose.windup,
                        0.0F,
                        1.0F,
                        0.0F
                );

                GlStateManager.rotate(
                        21.0F * pose.windup,
                        1.0F,
                        0.0F,
                        0.0F
                );

                GlStateManager.rotate(
                        9.0F * side * pose.windup,
                        0.0F,
                        0.0F,
                        1.0F
                );

                // Forward slash.
                GlStateManager.translate(
                        0.18F * side * pose.strike,
                        -0.13F * pose.strike,
                        -0.50F * pose.strike
                );

                GlStateManager.rotate(
                        82.0F * side * pose.strike,
                        0.0F,
                        1.0F,
                        0.0F
                );

                GlStateManager.rotate(
                        -76.0F * pose.strike,
                        1.0F,
                        0.0F,
                        0.0F
                );

                GlStateManager.rotate(
                        -25.0F * side * pose.strike,
                        0.0F,
                        0.0F,
                        1.0F
                );

                // Small impact kick.
                GlStateManager.translate(
                        0.025F * side * pose.impact,
                        -0.018F * pose.impact,
                        -0.065F * pose.impact
                );

                GlStateManager.rotate(
                        -10.0F * pose.impact,
                        1.0F,
                        0.0F,
                        0.0F
                );

                // The inactive claw reacts slightly instead of attacking with it.
                float reaction = otherPose.impact + otherPose.strike * 0.25F;

                GlStateManager.translate(
                        -0.014F * side * reaction,
                        0.010F * reaction,
                        0.035F * reaction
                );

                GlStateManager.rotate(
                        3.5F * reaction,
                        1.0F,
                        0.0F,
                        0.0F
                );

                if (right) {
                        GlStateManager.scale(-modelScale, modelScale, modelScale);
                } else {
                        GlStateManager.scale(modelScale, modelScale, modelScale);
                }

                float fingerCurl =
                        0.18F
                                + idleSin * 0.025F
                                - pose.windup * 0.20F
                                + pose.strike * 0.72F
                                + pose.impact * 0.16F;

                fingerCurl = MathHelper.clamp_float(fingerCurl, 0.0F, 1.0F);

                float wristCurl =
                        pose.windup * -0.16F
                                + pose.strike * 0.28F
                                + pose.impact * 0.08F;

                dragonClawModel.setPose(
                        fingerCurl,
                        wristCurl,
                        idleSin * 0.018F
                );

                // ModelRenderer normally samples the currently bound item texture.
                // Texture is disabled so the custom model keeps stable solid colors.
                GlStateManager.disableTexture2D();

                GlStateManager.color(
                        0.72F + pose.impact * 0.10F,
                        0.16F + pose.impact * 0.06F,
                        0.055F,
                        1.0F
                );

                dragonClawModel.renderBody(MODEL_SCALE);

                GlStateManager.color(
                        1.0F,
                        0.80F + pose.impact * 0.12F,
                        0.38F + pose.impact * 0.08F,
                        1.0F
                );

                dragonClawModel.renderClaws(MODEL_SCALE);

                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.enableTexture2D();

                GlStateManager.popMatrix();
        }

        private static AttackPose getAttackPose(float progress) {
                if (progress >= 1.0F) {
                        return AttackPose.IDLE;
                }

                float windup;
                float strike;
                float impact;

                if (progress < 0.24F) {
                        windup = smoothStep(progress / 0.24F);
                        strike = 0.0F;
                        impact = 0.0F;
                } else if (progress < 0.49F) {
                        float value = smoothStep((progress - 0.24F) / 0.25F);

                        windup = 1.0F - value;
                        strike = easeOutCubic(value);
                        impact = bellCurve(value, 0.72F, 0.28F);
                } else if (progress < 0.62F) {
                        float value = (progress - 0.49F) / 0.13F;

                        windup = 0.0F;
                        strike = 1.0F - 0.08F * smoothStep(value);
                        impact = 1.0F - smoothStep(value);
                } else {
                        float recovery = smoothStep((progress - 0.62F) / 0.38F);

                        windup = 0.0F;
                        strike = 0.92F * (1.0F - recovery);
                        impact = 0.0F;
                }

                return new AttackPose(
                        clamp01(windup),
                        clamp01(strike),
                        clamp01(impact)
                );
        }

        private static float smoothStep(float value) {
                value = clamp01(value);
                return value * value * (3.0F - 2.0F * value);
        }

        private static float easeOutCubic(float value) {
                value = clamp01(value);
                float inverse = 1.0F - value;
                return 1.0F - inverse * inverse * inverse;
        }

        private static float bellCurve(
                float value,
                float center,
                float width
        ) {
                float distance = Math.abs(value - center) / Math.max(width, 0.001F);
                return 1.0F - smoothStep(distance);
        }

        private static float clamp01(float value) {
                return MathHelper.clamp_float(value, 0.0F, 1.0F);
        }

        @Override
        public String[] getSuffix() {
                return new String[]{this.mode.getModeString()};
        }

        private static final class AttackPose {

                private static final AttackPose IDLE =
                        new AttackPose(0.0F, 0.0F, 0.0F);

                private final float windup;
                private final float strike;
                private final float impact;

                private AttackPose(
                        float windup,
                        float strike,
                        float impact
                ) {
                        this.windup = windup;
                        this.strike = strike;
                        this.impact = impact;
                }
        }

        private static final class DragonClawModel extends ModelBase {

                private static final int TOE_COUNT = 4;

                private final ModelRenderer arm;
                private final ModelRenderer wrist;
                private final ModelRenderer palm;

                private final ModelRenderer[] toeBase =
                        new ModelRenderer[TOE_COUNT];

                private final ModelRenderer[] toeEnd =
                        new ModelRenderer[TOE_COUNT];

                /*
                 * The claw tips use an empty duplicate hierarchy. This allows them to
                 * inherit exactly the same transformations while being rendered with
                 * a different color, without rendering the body twice.
                 */
                private final ModelRenderer clawArm;
                private final ModelRenderer clawWrist;
                private final ModelRenderer clawPalm;

                private final ModelRenderer[] clawToeBase =
                        new ModelRenderer[TOE_COUNT];

                private final ModelRenderer[] clawToeEnd =
                        new ModelRenderer[TOE_COUNT];

                private final ModelRenderer[] clawTips =
                        new ModelRenderer[TOE_COUNT];

                private final ModelRenderer thumb;
                private final ModelRenderer thumbEnd;

                private final ModelRenderer clawThumbRoot;
                private final ModelRenderer clawThumbEnd;
                private final ModelRenderer clawThumbTip;

                private final float[] toeYaw = {
                        -0.28F,
                        -0.09F,
                        0.09F,
                        0.28F
                };

                private DragonClawModel() {
                        this.textureWidth = 64;
                        this.textureHeight = 64;

                        this.arm = new ModelRenderer(this, 0, 0);
                        this.arm.addBox(
                                -3.5F,
                                -3.0F,
                                -3.0F,
                                7,
                                15,
                                7
                        );

                        this.wrist = new ModelRenderer(this, 0, 23);
                        this.wrist.setRotationPoint(0.0F, 10.5F, 0.0F);
                        this.wrist.addBox(
                                -3.0F,
                                0.0F,
                                -3.0F,
                                6,
                                8,
                                6
                        );
                        this.arm.addChild(this.wrist);

                        this.palm = new ModelRenderer(this, 26, 0);
                        this.palm.setRotationPoint(0.0F, 7.0F, -0.5F);
                        this.palm.addBox(
                                -4.7F,
                                -1.5F,
                                -9.0F,
                                9,
                                5,
                                10
                        );
                        this.wrist.addChild(this.palm);

                        this.clawArm = new ModelRenderer(this, 0, 0);

                        this.clawWrist = new ModelRenderer(this, 0, 0);
                        this.clawWrist.setRotationPoint(0.0F, 10.5F, 0.0F);
                        this.clawArm.addChild(this.clawWrist);

                        this.clawPalm = new ModelRenderer(this, 0, 0);
                        this.clawPalm.setRotationPoint(0.0F, 7.0F, -0.5F);
                        this.clawWrist.addChild(this.clawPalm);

                        float[] toeX = {
                                -3.5F,
                                -1.25F,
                                1.25F,
                                3.5F
                        };

                        float[] toeLength = {
                                4.7F,
                                5.5F,
                                5.5F,
                                4.7F
                        };

                        for (int i = 0; i < TOE_COUNT; i++) {
                                this.toeBase[i] = new ModelRenderer(this, 26, 17);
                                this.toeBase[i].setRotationPoint(
                                        toeX[i],
                                        1.0F,
                                        -8.1F
                                );
                                this.toeBase[i].addBox(
                                        -0.9F,
                                        -0.9F,
                                        -toeLength[i],
                                        2,
                                        2,
                                        (int) toeLength[i]
                                );
                                this.toeBase[i].rotateAngleY = toeYaw[i];
                                this.palm.addChild(this.toeBase[i]);

                                this.toeEnd[i] = new ModelRenderer(this, 26, 25);
                                this.toeEnd[i].setRotationPoint(
                                        0.0F,
                                        0.0F,
                                        -toeLength[i] + 0.3F
                                );
                                this.toeEnd[i].addBox(
                                        -0.7F,
                                        -0.7F,
                                        -4.0F,
                                        1,
                                        2,
                                        4
                                );
                                this.toeBase[i].addChild(this.toeEnd[i]);

                                this.clawToeBase[i] =
                                        new ModelRenderer(this, 0, 0);

                                this.clawToeBase[i].setRotationPoint(
                                        toeX[i],
                                        1.0F,
                                        -8.1F
                                );
                                this.clawToeBase[i].rotateAngleY = toeYaw[i];
                                this.clawPalm.addChild(this.clawToeBase[i]);

                                this.clawToeEnd[i] =
                                        new ModelRenderer(this, 0, 0);

                                this.clawToeEnd[i].setRotationPoint(
                                        0.0F,
                                        0.0F,
                                        -toeLength[i] + 0.3F
                                );
                                this.clawToeBase[i].addChild(this.clawToeEnd[i]);

                                this.clawTips[i] =
                                        new ModelRenderer(this, 52, 17);

                                this.clawTips[i].setRotationPoint(
                                        0.0F,
                                        0.0F,
                                        -3.6F
                                );
                                this.clawTips[i].addBox(
                                        -0.5F,
                                        -0.5F,
                                        -3.6F,
                                        1,
                                        1,
                                        4
                                );
                                this.clawToeEnd[i].addChild(this.clawTips[i]);
                        }

                        this.thumb = new ModelRenderer(this, 40, 28);
                        this.thumb.setRotationPoint(
                                -4.1F,
                                0.5F,
                                -4.0F
                        );
                        this.thumb.addBox(
                                -0.9F,
                                -0.9F,
                                -4.5F,
                                2,
                                2,
                                5
                        );
                        this.thumb.rotateAngleY = 1.0F;
                        this.thumb.rotateAngleZ = 0.16F;
                        this.palm.addChild(this.thumb);

                        this.thumbEnd = new ModelRenderer(this, 40, 36);
                        this.thumbEnd.setRotationPoint(
                                0.0F,
                                0.0F,
                                -4.0F
                        );
                        this.thumbEnd.addBox(
                                -0.7F,
                                -0.7F,
                                -3.2F,
                                1,
                                2,
                                3
                        );
                        this.thumb.addChild(this.thumbEnd);

                        this.clawThumbRoot = new ModelRenderer(this, 0, 0);
                        this.clawThumbRoot.setRotationPoint(
                                -4.1F,
                                0.5F,
                                -4.0F
                        );
                        this.clawThumbRoot.rotateAngleY = 1.0F;
                        this.clawThumbRoot.rotateAngleZ = 0.16F;
                        this.clawPalm.addChild(this.clawThumbRoot);

                        this.clawThumbEnd = new ModelRenderer(this, 0, 0);
                        this.clawThumbEnd.setRotationPoint(
                                0.0F,
                                0.0F,
                                -4.0F
                        );
                        this.clawThumbRoot.addChild(this.clawThumbEnd);

                        this.clawThumbTip = new ModelRenderer(this, 52, 24);
                        this.clawThumbTip.setRotationPoint(
                                0.0F,
                                0.0F,
                                -2.8F
                        );
                        this.clawThumbTip.addBox(
                                -0.5F,
                                -0.5F,
                                -3.0F,
                                1,
                                1,
                                3
                        );
                        this.clawThumbEnd.addChild(this.clawThumbTip);
                }

                private void setPose(
                        float curl,
                        float wristCurl,
                        float idle
                ) {
                        this.arm.rotateAngleX = 0.53F + idle;
                        this.arm.rotateAngleZ = idle * 0.35F;

                        this.wrist.rotateAngleX =
                                -0.43F + wristCurl;

                        this.palm.rotateAngleX =
                                0.31F + curl * 0.10F;

                        copyRotation(this.arm, this.clawArm);
                        copyRotation(this.wrist, this.clawWrist);
                        copyRotation(this.palm, this.clawPalm);

                        for (int i = 0; i < TOE_COUNT; i++) {
                                float edge = (i == 0 || i == 3) ? 0.92F : 1.0F;

                                this.toeBase[i].rotateAngleX =
                                        0.12F + curl * 0.68F * edge;

                                this.toeBase[i].rotateAngleY =
                                        toeYaw[i] * (1.0F - curl * 0.22F);

                                this.toeEnd[i].rotateAngleX =
                                        0.12F + curl * 0.92F * edge;

                                this.clawTips[i].rotateAngleX =
                                        0.08F + curl * 0.18F;

                                copyRotation(
                                        this.toeBase[i],
                                        this.clawToeBase[i]
                                );

                                copyRotation(
                                        this.toeEnd[i],
                                        this.clawToeEnd[i]
                                );
                        }

                        this.thumb.rotateAngleX =
                                0.12F + curl * 0.58F;

                        this.thumbEnd.rotateAngleX =
                                0.16F + curl * 0.78F;

                        this.clawThumbTip.rotateAngleX =
                                0.08F + curl * 0.16F;

                        copyRotation(
                                this.thumb,
                                this.clawThumbRoot
                        );

                        copyRotation(
                                this.thumbEnd,
                                this.clawThumbEnd
                        );
                }

                private void renderBody(float scale) {
                        this.arm.render(scale);
                }

                private void renderClaws(float scale) {
                        this.clawArm.render(scale);
                }

                private static void copyRotation(
                        ModelRenderer source,
                        ModelRenderer destination
                ) {
                        destination.rotateAngleX = source.rotateAngleX;
                        destination.rotateAngleY = source.rotateAngleY;
                        destination.rotateAngleZ = source.rotateAngleZ;
                }
        }
}