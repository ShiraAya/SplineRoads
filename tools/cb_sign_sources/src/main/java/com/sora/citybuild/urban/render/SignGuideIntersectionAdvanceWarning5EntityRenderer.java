package com.sora.citybuild.urban.render;

import com.sora.citybuild.urban.block.custom.sign.SignGuideIntersectionAdvanceWarning5;
import com.sora.citybuild.urban.entity.SignGuideIntersectionAdvanceWarning5Entity;
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
import net.minecraft.world.level.block.state.properties.Property;

public class SignGuideIntersectionAdvanceWarning5EntityRenderer
implements BlockEntityRenderer<SignGuideIntersectionAdvanceWarning5Entity> {
    private final Font textRenderer;

    public SignGuideIntersectionAdvanceWarning5EntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.textRenderer = ctx.getFont();
    }

    public void render(SignGuideIntersectionAdvanceWarning5Entity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        String text1 = entity.getText1();
        String text2 = entity.getText2();
        String text3 = entity.getText3();
        String text4 = entity.getText4();
        Float text1AndY = Float.valueOf(entity.getText1AndY());
        Float text2AndY = Float.valueOf(entity.getText2AndY());
        Float text3AndY = Float.valueOf(entity.getText3AndY());
        Float text4AndY = Float.valueOf(entity.getText4AndY());
        if (text1 == null || text1.isEmpty()) {
            text1 = " ";
        }
        if (text2 == null || text2.isEmpty()) {
            text2 = " ";
        }
        if (text3 == null || text3.isEmpty()) {
            text3 = " ";
        }
        if (text4 == null || text4.isEmpty()) {
            text4 = " ";
        }
        Direction facing = (Direction)entity.getBlockState().getValue(SignGuideIntersectionAdvanceWarning5.FACING);
        SignGuideIntersectionAdvanceWarning5.Type type = (SignGuideIntersectionAdvanceWarning5.Type)(entity.getBlockState().getValue(SignGuideIntersectionAdvanceWarning5.TYPE));
        this.renderText(matrices, vertexConsumers, light, facing, text1, type, -10.0f, 10.0f + text1AndY.floatValue());
        this.renderText(matrices, vertexConsumers, light, facing, text2, type, 10.0f, 10.0f + text2AndY.floatValue());
        this.renderText(matrices, vertexConsumers, light, facing, text3, type, -10.0f, -10.0f + text3AndY.floatValue());
        this.renderText(matrices, vertexConsumers, light, facing, text4, type, 10.0f, -10.0f + text4AndY.floatValue());
    }

    private void renderText(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignGuideIntersectionAdvanceWarning5.Type type, float andX, float andY) {
        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        float scaleValue = 0.03f;
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

    public boolean rendersOutsideBoundingBox(SignGuideIntersectionAdvanceWarning5Entity blockEntity) {
        return true;
    }

    public int getViewDistance() {
        return 256;
    }
}

