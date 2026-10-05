package com.sora.citybuild.urban.render;

import com.sora.citybuild.urban.block.SignBlocks;
import com.sora.citybuild.urban.block.custom.sign.SignGuideIntersectionAdvanceWarning3;
import com.sora.citybuild.urban.entity.SignGuideIntersectionAdvanceWarning3Entity;
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

public class SignGuideIntersectionAdvanceWarning3EntityRenderer
implements BlockEntityRenderer<SignGuideIntersectionAdvanceWarning3Entity> {
    private final Font textRenderer;

    public SignGuideIntersectionAdvanceWarning3EntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.textRenderer = ctx.getFont();
    }

    public void render(SignGuideIntersectionAdvanceWarning3Entity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        String text1 = entity.getText1();
        String cnText2 = entity.getCnText2();
        String enText2 = entity.getEnText2();
        String cnText3 = entity.getCnText3();
        String enText3 = entity.getEnText3();
        String cnText4 = entity.getCnText4();
        String enText4 = entity.getEnText4();
        String cnText5 = entity.getCnText5();
        String enText5 = entity.getEnText5();
        String cnText6 = entity.getCnText6();
        String enText6 = entity.getEnText6();
        String cnText7 = entity.getCnText7();
        String enText7 = entity.getEnText7();
        if (text1 == null || text1.isEmpty()) {
            text1 = " ";
        }
        if (cnText2 == null || cnText2.isEmpty()) {
            cnText2 = " ";
        }
        if (enText2 == null || enText2.isEmpty()) {
            enText2 = " ";
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
        if (cnText6 == null || cnText6.isEmpty()) {
            cnText6 = " ";
        }
        if (enText6 == null || enText6.isEmpty()) {
            enText6 = " ";
        }
        if (cnText7 == null || cnText7.isEmpty()) {
            cnText7 = " ";
        }
        if (enText7 == null || enText7.isEmpty()) {
            enText7 = " ";
        }
        Block currentBlock = entity.getBlockState().getBlock();
        Direction facing = (Direction)entity.getBlockState().getValue(SignGuideIntersectionAdvanceWarning3.FACING);
        SignGuideIntersectionAdvanceWarning3.Type type = (SignGuideIntersectionAdvanceWarning3.Type)(entity.getBlockState().getValue(SignGuideIntersectionAdvanceWarning3.TYPE));
        if (currentBlock == SignBlocks.SIGN_GUIDE_INTERSECTION_ADVANCE_WARNING_3.get()) {
            this.renderText(matrices, vertexConsumers, light, facing, cnText2, type, 0.0f, 8.0f, false);
            this.renderText(matrices, vertexConsumers, light, facing, enText2, type, 0.0f, 4.0f, true);
            this.renderText(matrices, vertexConsumers, light, facing, cnText4, type, -14.0f, 4.0f, false);
            this.renderText(matrices, vertexConsumers, light, facing, enText4, type, -14.0f, 0.0f, true);
            this.renderText(matrices, vertexConsumers, light, facing, cnText5, type, -14.0f, -4.0f, false);
            this.renderText(matrices, vertexConsumers, light, facing, enText5, type, -14.0f, -8.0f, true);
            this.renderText(matrices, vertexConsumers, light, facing, cnText6, type, 14.0f, 4.0f, false);
            this.renderText(matrices, vertexConsumers, light, facing, enText6, type, 14.0f, 0.0f, true);
            this.renderText(matrices, vertexConsumers, light, facing, cnText7, type, 14.0f, -4.0f, false);
            this.renderText(matrices, vertexConsumers, light, facing, enText7, type, 14.0f, -8.0f, true);
        } else {
            this.renderText(matrices, vertexConsumers, light, facing, text1, type, 0.0f, -9.0f, false);
            this.renderText(matrices, vertexConsumers, light, facing, cnText2, type, 0.0f, 6.0f, false);
            this.renderText(matrices, vertexConsumers, light, facing, enText2, type, 0.0f, 3.0f, true);
            this.renderText(matrices, vertexConsumers, light, facing, cnText3, type, 0.0f, 13.0f, false);
            this.renderText(matrices, vertexConsumers, light, facing, enText3, type, 0.0f, 10.0f, true);
            this.renderText(matrices, vertexConsumers, light, facing, cnText4, type, -14.0f, 8.0f, false);
            this.renderText(matrices, vertexConsumers, light, facing, enText4, type, -14.0f, 4.0f, true);
            this.renderText(matrices, vertexConsumers, light, facing, cnText5, type, -14.0f, 0.0f, false);
            this.renderText(matrices, vertexConsumers, light, facing, enText5, type, -14.0f, -4.0f, true);
            this.renderText(matrices, vertexConsumers, light, facing, cnText6, type, 14.0f, 8.0f, false);
            this.renderText(matrices, vertexConsumers, light, facing, enText6, type, 14.0f, 4.0f, true);
            this.renderText(matrices, vertexConsumers, light, facing, cnText7, type, 14.0f, 0.0f, false);
            this.renderText(matrices, vertexConsumers, light, facing, enText7, type, 14.0f, -4.0f, true);
        }
    }

    private void renderText(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignGuideIntersectionAdvanceWarning3.Type type, float andX, float andY, boolean isSmallScale) {
        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        float scaleValue = isSmallScale ? 0.023f : 0.03f;
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

    public boolean rendersOutsideBoundingBox(SignGuideIntersectionAdvanceWarning3Entity blockEntity) {
        return true;
    }

    public int getViewDistance() {
        return 256;
    }
}

