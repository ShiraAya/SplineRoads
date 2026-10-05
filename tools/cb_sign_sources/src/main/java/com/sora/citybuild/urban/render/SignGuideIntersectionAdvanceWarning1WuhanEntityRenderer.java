package com.sora.citybuild.urban.render;

import com.sora.citybuild.urban.block.SignBlocks;
import com.sora.citybuild.urban.block.custom.sign.SignGuideIntersectionAdvanceWarning1Wuhan;
import com.sora.citybuild.urban.entity.SignGuideIntersectionAdvanceWarning1WuhanEntity;
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

public class SignGuideIntersectionAdvanceWarning1WuhanEntityRenderer
implements BlockEntityRenderer<SignGuideIntersectionAdvanceWarning1WuhanEntity> {
    private final Font textRenderer;

    public SignGuideIntersectionAdvanceWarning1WuhanEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.textRenderer = ctx.getFont();
    }

    public void render(SignGuideIntersectionAdvanceWarning1WuhanEntity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        String text1 = entity.getText1();
        String text2 = entity.getText2();
        String cnText3 = entity.getCnText3();
        String enText3 = entity.getEnText3();
        String cnText4 = entity.getCnText4();
        String enText4 = entity.getEnText4();
        String cnText5 = entity.getCnText5();
        String enText5 = entity.getEnText5();
        if (text1 == null || text1.isEmpty()) {
            text1 = " ";
        }
        if (text2 == null || text2.isEmpty()) {
            text2 = " ";
        }
        if (cnText3 == null || cnText3.isEmpty()) {
            cnText3 = " ";
        }
        if (enText3 == null || enText3.isEmpty()) {
            enText3 = " ";
        }
        if (cnText4 == null || cnText4.isEmpty()) {
            cnText4 = " ";
        }
        if (enText4 == null || enText4.isEmpty()) {
            enText4 = " ";
        }
        if (cnText5 == null || cnText5.isEmpty()) {
            cnText5 = " ";
        }
        if (enText5 == null || enText5.isEmpty()) {
            enText5 = " ";
        }
        Block currentBlock = entity.getBlockState().getBlock();
        Direction facing = (Direction)entity.getBlockState().getValue(SignGuideIntersectionAdvanceWarning1Wuhan.FACING);
        SignGuideIntersectionAdvanceWarning1Wuhan.Type type = (SignGuideIntersectionAdvanceWarning1Wuhan.Type)(entity.getBlockState().getValue(SignGuideIntersectionAdvanceWarning1Wuhan.TYPE));
        if (currentBlock == SignBlocks.SIGN_GUIDE_INTERSECTION_ADVANCE_WARNING_1_WUHAN_RIGHT.get()) {
            this.renderText(matrices, vertexConsumers, light, facing, text1, type, true, 16.5f, 12.0f, false);
            this.renderText(matrices, vertexConsumers, light, facing, text2, type, true, 16.5f, -12.0f, false);
            this.renderText(matrices, vertexConsumers, light, facing, cnText3, type, false, -6.0f, 12.0f, true);
            this.renderText(matrices, vertexConsumers, light, facing, enText3, type, false, -6.0f, 8.0f, true);
            this.renderText(matrices, vertexConsumers, light, facing, cnText4, type, false, -6.0f, 1.0f, true);
            this.renderText(matrices, vertexConsumers, light, facing, enText4, type, false, -6.0f, -3.0f, true);
            this.renderText(matrices, vertexConsumers, light, facing, cnText5, type, false, -6.0f, -10.0f, true);
            this.renderText(matrices, vertexConsumers, light, facing, enText5, type, false, -6.0f, -14.0f, true);
        } else {
            this.renderText(matrices, vertexConsumers, light, facing, text1, type, true, -16.5f, 12.0f, false);
            this.renderText(matrices, vertexConsumers, light, facing, text2, type, true, -16.5f, -12.0f, false);
            this.renderText(matrices, vertexConsumers, light, facing, cnText3, type, false, 6.0f, 12.0f, true);
            this.renderText(matrices, vertexConsumers, light, facing, enText3, type, false, 6.0f, 8.0f, true);
            this.renderText(matrices, vertexConsumers, light, facing, cnText4, type, false, 6.0f, 1.0f, true);
            this.renderText(matrices, vertexConsumers, light, facing, enText4, type, false, 6.0f, -3.0f, true);
            this.renderText(matrices, vertexConsumers, light, facing, cnText5, type, false, 6.0f, -10.0f, true);
            this.renderText(matrices, vertexConsumers, light, facing, enText5, type, false, 6.0f, -14.0f, true);
        }
    }

    private void renderText(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignGuideIntersectionAdvanceWarning1Wuhan.Type type, boolean isBlue, float andX, float andY, boolean isSmallScale) {
        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        MutableComponent styledText = Component.literal((String)text).setStyle(Style.EMPTY.withBold(Boolean.valueOf(true)));
        int textWidth = this.textRenderer.width((FormattedText)styledText);
        Objects.requireNonNull(this.textRenderer);
        int textHeight = 9;
        float scale = isSmallScale ? 0.023f : 0.035f;
        scale = RoadSignTextScale.adjust(text, scale);
        float zOffset = switch (type) {
            default -> throw new IncompatibleClassChangeError();
            case POLE_L -> -0.75f;
            case POLE_H -> -0.79f;
            case NORMAL -> -0.43f;
        };
        int textColor = isBlue ? 2579112 : 0xFFFFFF;
        matrices.translate(andX / 16.0f, andY / 16.0f, zOffset);
        matrices.scale(scale, -scale, scale);
        this.textRenderer.drawInBatch((Component)styledText, 0.0f, (float)(-textHeight) / 2.0f, textColor, false, matrices.last().pose(), vertexConsumers, Font.DisplayMode.NORMAL, 0, light);
        matrices.popPose();
    }

    public boolean rendersOutsideBoundingBox(SignGuideIntersectionAdvanceWarning1WuhanEntity blockEntity) {
        return true;
    }

    public int getViewDistance() {
        return 256;
    }
}

