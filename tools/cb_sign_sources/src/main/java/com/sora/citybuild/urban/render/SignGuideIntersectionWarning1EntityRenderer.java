package com.sora.citybuild.urban.render;

import com.sora.citybuild.urban.block.SignBlocks;
import com.sora.citybuild.urban.block.custom.sign.SignGuideIntersectionWarning1;
import com.sora.citybuild.urban.entity.SignGuideIntersectionWarning1Entity;
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

public class SignGuideIntersectionWarning1EntityRenderer
implements BlockEntityRenderer<SignGuideIntersectionWarning1Entity> {
    private final Font textRenderer;

    public SignGuideIntersectionWarning1EntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.textRenderer = ctx.getFont();
    }

    public void render(SignGuideIntersectionWarning1Entity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        String text1 = entity.getText1();
        if (text1 == null || text1.isEmpty()) {
            text1 = " ";
        }
        Block currentBlock = entity.getBlockState().getBlock();
        Direction facing = (Direction)entity.getBlockState().getValue(SignGuideIntersectionWarning1.FACING);
        SignGuideIntersectionWarning1.Type type = (SignGuideIntersectionWarning1.Type)(entity.getBlockState().getValue(SignGuideIntersectionWarning1.TYPE));
        if (currentBlock == SignBlocks.SIGN_GUIDE_INTERSECTION_WARNING_1.get() || currentBlock == SignBlocks.SIGN_GUIDE_INTERSECTION_WARNING_6.get()) {
            this.renderText(matrices, vertexConsumers, light, facing, text1, type, 0.0f, 0.0f, true);
        } else if (currentBlock == SignBlocks.SIGN_GUIDE_INTERSECTION_WARNING_2.get()) {
            this.renderText(matrices, vertexConsumers, light, facing, text1, type, -2.5f, 0.0f, true);
        } else if (currentBlock == SignBlocks.SIGN_GUIDE_INTERSECTION_WARNING_3.get()) {
            this.renderText(matrices, vertexConsumers, light, facing, text1, type, 0.0f, 0.0f, true);
        } else if (currentBlock == SignBlocks.SIGN_GUIDE_DISTANCE_TO_TUNNEL_EXIT_1.get() || currentBlock == SignBlocks.SIGN_GUIDE_DISTANCE_TO_TUNNEL_EXIT_2.get() || currentBlock == SignBlocks.SIGN_GUIDE_DISTANCE_TO_TUNNEL_EXIT_3.get()) {
            this.renderUnitText(matrices, vertexConsumers, light, facing, text1, type, 5.0f, 0.0f, false);
            this.renderUnitText(matrices, vertexConsumers, light, facing, "km", type, 9.0f, -0.5f, true);
        } else if (currentBlock == SignBlocks.SIGN_GUIDE_DISTANCE_TO_TUNNEL_EXIT_4.get() || currentBlock == SignBlocks.SIGN_GUIDE_DISTANCE_TO_TUNNEL_EXIT_5.get() || currentBlock == SignBlocks.SIGN_GUIDE_DISTANCE_TO_TUNNEL_EXIT_6.get()) {
            this.renderUnitText(matrices, vertexConsumers, light, facing, text1, type, -0.5f, -4.0f, false);
            this.renderUnitText(matrices, vertexConsumers, light, facing, "km", type, 3.5f, -4.5f, true);
        } else if (currentBlock == SignBlocks.SIGN_GUIDE_ODOMETER.get()) {
            this.renderText(matrices, vertexConsumers, light, facing, text1, type, 0.0f, 3.0f, false);
        }
    }

    private void renderUnitText(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignGuideIntersectionWarning1.Type type, float andX, float andY, boolean isSmallScale) {
        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        float scaleValue = isSmallScale ? 0.03f : 0.045f;
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
        this.textRenderer.drawInBatch((Component)styledText, (float)(-textWidth), (float)(-textHeight) / 2.0f, 2579112, false, matrices.last().pose(), vertexConsumers, Font.DisplayMode.NORMAL, 0, light);
        matrices.popPose();
    }

    private void renderText(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignGuideIntersectionWarning1.Type type, float andX, float andY, boolean isSmallScale) {
        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        float scaleValue = isSmallScale ? 0.035f : 0.055f;
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
        float centeredX = andX / 16.0f - (float)textWidth * scaleValue / 2.0f;
        float centeredY = andY / 16.0f;
        matrices.translate(centeredX, centeredY, zOffset);
        matrices.scale(scaleValue, -scaleValue, scaleValue);
        this.textRenderer.drawInBatch((Component)styledText, 0.0f, (float)(-textHeight) / 2.0f, 0xFFFFFF, false, matrices.last().pose(), vertexConsumers, Font.DisplayMode.NORMAL, 0, light);
        matrices.popPose();
    }

    public boolean rendersOutsideBoundingBox(SignGuideIntersectionWarning1Entity blockEntity) {
        return true;
    }

    public int getViewDistance() {
        return 256;
    }
}

