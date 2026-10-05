package com.sora.citybuild.urban.render;

import com.sora.citybuild.urban.block.SignBlocks;
import com.sora.citybuild.urban.block.custom.sign.SignGuideConfirmation1;
import com.sora.citybuild.urban.entity.SignGuideConfirmation1Entity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

public class SignGuideConfirmation1EntityRenderer
implements BlockEntityRenderer<SignGuideConfirmation1Entity> {
    private final Font textRenderer;

    public SignGuideConfirmation1EntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.textRenderer = ctx.getFont();
    }

    public void render(SignGuideConfirmation1Entity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        SignGuideConfirmation1Entity.Unit unit1 = entity.getUnit1();
        SignGuideConfirmation1Entity.Unit unit2 = entity.getUnit2();
        SignGuideConfirmation1Entity.Unit unit3 = entity.getUnit3();
        String text1 = entity.getText1();
        String text2 = entity.getText2();
        String text3 = entity.getText3();
        String length1 = entity.getLength1();
        String length2 = entity.getLength2();
        String length3 = entity.getLength3();
        if (text1 == null || text1.isEmpty()) {
            text1 = " ";
        }
        if (text2 == null || text2.isEmpty()) {
            text2 = " ";
        }
        if (text3 == null || text3.isEmpty()) {
            text3 = " ";
        }
        if (length1 == null || length1.isEmpty()) {
            length1 = " ";
        }
        if (length2 == null || length2.isEmpty()) {
            length2 = " ";
        }
        if (length3 == null || length3.isEmpty()) {
            length3 = " ";
        }
        String unit1_number = switch (unit1) {
            default -> throw new IncompatibleClassChangeError();
            case KILOMETRE -> "km";
            case METRE -> "m";
        };
        String unit2_number = switch (unit2) {
            default -> throw new IncompatibleClassChangeError();
            case KILOMETRE -> "km";
            case METRE -> "m";
        };
        String unit3_number = switch (unit3) {
            default -> throw new IncompatibleClassChangeError();
            case KILOMETRE -> "km";
            case METRE -> "m";
        };
        Direction facing = (Direction)entity.getBlockState().getValue(SignGuideConfirmation1.FACING);
        SignGuideConfirmation1.Type type = (SignGuideConfirmation1.Type)(entity.getBlockState().getValue(SignGuideConfirmation1.TYPE));
        Block currentBlock = entity.getBlockState().getBlock();
        if (currentBlock == SignBlocks.SIGN_GUIDE_CONFIRMATION_1.get()) {
            this.renderLeftText(matrices, vertexConsumers, light, facing, text1, type, -17.0f, 9.0f);
            this.renderLeftText(matrices, vertexConsumers, light, facing, text2, type, -17.0f, 0.0f);
            this.renderLeftText(matrices, vertexConsumers, light, facing, text3, type, -17.0f, -9.0f);
            this.renderRightText(matrices, vertexConsumers, light, facing, length1, type, 13.0f, 9.0f, false);
            this.renderRightText(matrices, vertexConsumers, light, facing, length2, type, 13.0f, 0.0f, false);
            this.renderRightText(matrices, vertexConsumers, light, facing, length3, type, 13.0f, -9.0f, false);
            this.renderRightText(matrices, vertexConsumers, light, facing, unit1_number, type, 17.0f, 8.5f, true);
            this.renderRightText(matrices, vertexConsumers, light, facing, unit2_number, type, 17.0f, -0.5f, true);
            this.renderRightText(matrices, vertexConsumers, light, facing, unit3_number, type, 17.0f, -9.5f, true);
        } else {
            this.renderLeftText(matrices, vertexConsumers, light, facing, text1, type, -15.0f, 9.0f);
            this.renderLeftText(matrices, vertexConsumers, light, facing, text2, type, -15.0f, 0.0f);
            this.renderLeftText(matrices, vertexConsumers, light, facing, text3, type, -15.0f, -9.0f);
            this.renderRightText(matrices, vertexConsumers, light, facing, length1, type, 11.0f, 9.0f, false);
            this.renderRightText(matrices, vertexConsumers, light, facing, length2, type, 11.0f, 0.0f, false);
            this.renderRightText(matrices, vertexConsumers, light, facing, length3, type, 11.0f, -9.0f, false);
            this.renderRightText(matrices, vertexConsumers, light, facing, unit1_number, type, 15.0f, 8.5f, true);
            this.renderRightText(matrices, vertexConsumers, light, facing, unit2_number, type, 15.0f, -0.5f, true);
            this.renderRightText(matrices, vertexConsumers, light, facing, unit3_number, type, 15.0f, -9.5f, true);
        }
    }

    private void renderLeftText(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignGuideConfirmation1.Type type, float andX, float andY) {
        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        float scaleValue = 0.04f;
        scaleValue = RoadSignTextScale.adjust(text, scaleValue);
        MutableComponent styledText = Component.literal((String)text).setStyle(Style.EMPTY.withBold(Boolean.valueOf(true)));
        Objects.requireNonNull(this.textRenderer);
        int textHeight = 9;
        float zOffset = switch (type) {
            default -> throw new IncompatibleClassChangeError();
            case POLE_L -> -0.75f;
            case POLE_H -> -0.79f;
            case NORMAL -> -0.43f;
        };
        float centeredX = andX / 16.0f;
        float centeredY = andY / 16.0f;
        matrices.translate(centeredX, centeredY, zOffset);
        matrices.scale(scaleValue, -scaleValue, scaleValue);
        this.textRenderer.drawInBatch((Component)styledText, 0.0f, (float)(-textHeight) / 2.0f, 0xFFFFFF, false, matrices.last().pose(), vertexConsumers, Font.DisplayMode.NORMAL, 0, light);
        matrices.popPose();
    }

    private void renderRightText(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignGuideConfirmation1.Type type, float andX, float andY, boolean isSmallScale) {
        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        float scaleValue = isSmallScale ? 0.025f : 0.04f;
        scaleValue = RoadSignTextScale.adjust(text, scaleValue);
        MutableComponent styledText = Component.literal((String)text).setStyle(Style.EMPTY.withBold(Boolean.valueOf(true)));
        int textWidth = this.textRenderer.width((FormattedText)styledText);
        Objects.requireNonNull(this.textRenderer);
        int textHeight = 9;
        float zOffset = switch (type) {
            default -> throw new IncompatibleClassChangeError();
            case POLE_L -> -0.75f;
            case POLE_H -> -0.79f;
            case NORMAL -> -0.43f;
        };
        float centeredX = andX / 16.0f;
        float centeredY = andY / 16.0f;
        matrices.translate(centeredX, centeredY, zOffset);
        matrices.scale(scaleValue, -scaleValue, scaleValue);
        this.textRenderer.drawInBatch((Component)styledText, (float)(-textWidth), (float)(-textHeight) / 2.0f, 0xFFFFFF, false, matrices.last().pose(), vertexConsumers, Font.DisplayMode.NORMAL, 0, light);
        matrices.popPose();
    }

    public boolean rendersOutsideBoundingBox(SignGuideConfirmation1Entity blockEntity) {
        return true;
    }

    public int getViewDistance() {
        return 256;
    }
}

