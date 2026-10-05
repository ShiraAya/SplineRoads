package com.sora.citybuild.urban.render;

import com.sora.citybuild.urban.block.SignBlocks;
import com.sora.citybuild.urban.block.custom.sign.SignGuideIntersectionAdvanceWarning1;
import com.sora.citybuild.urban.entity.SignGuideIntersectionAdvanceWarning1Entity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

public class SignGuideIntersectionAdvanceWarning1EntityRenderer
implements BlockEntityRenderer<SignGuideIntersectionAdvanceWarning1Entity> {
    private final Font textRenderer;

    public SignGuideIntersectionAdvanceWarning1EntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.textRenderer = ctx.getFont();
    }

    public void render(SignGuideIntersectionAdvanceWarning1Entity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        String text1 = entity.getText1();
        String text2 = entity.getText2();
        if (text1 == null || text1.isEmpty()) {
            text1 = " ";
        }
        if (text2 == null || text2.isEmpty()) {
            text2 = " ";
        }
        Block currentBlock = entity.getBlockState().getBlock();
        Direction facing = (Direction)entity.getBlockState().getValue(SignGuideIntersectionAdvanceWarning1.FACING);
        SignGuideIntersectionAdvanceWarning1.Type type = (SignGuideIntersectionAdvanceWarning1.Type)(entity.getBlockState().getValue(SignGuideIntersectionAdvanceWarning1.TYPE));
        if (currentBlock == SignBlocks.SIGN_GUIDE_INTERSECTION_ADVANCE_WARNING_2.get()) {
            this.renderText(matrices, vertexConsumers, light, facing, text1, type, -17.0f, 10.0f);
            this.renderText(matrices, vertexConsumers, light, facing, text2, type, 3.0f, -1.0f);
        } else {
            this.renderText(matrices, vertexConsumers, light, facing, text1, type, -12.0f, 12.0f);
            this.renderText(matrices, vertexConsumers, light, facing, text2, type, -5.0f, -7.0f);
        }
    }

    private void renderText(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignGuideIntersectionAdvanceWarning1.Type type, float andX, float andY) {
        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        float scaleValue = 0.035f;
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
        float leftX = andX / 16.0f;
        float centeredY = andY / 16.0f;
        matrices.translate(leftX, centeredY, zOffset);
        matrices.scale(scaleValue, -scaleValue, scaleValue);
        this.textRenderer.drawInBatch((Component)styledText, 0.0f, (float)(-textHeight) / 2.0f, 0xFFFFFF, false, matrices.last().pose(), vertexConsumers, Font.DisplayMode.NORMAL, 0, light);
        matrices.popPose();
    }

    public boolean rendersOutsideBoundingBox(SignGuideIntersectionAdvanceWarning1Entity blockEntity) {
        return true;
    }

    public int getViewDistance() {
        return 256;
    }
}

