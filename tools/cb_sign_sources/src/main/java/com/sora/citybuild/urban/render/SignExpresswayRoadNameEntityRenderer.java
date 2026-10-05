package com.sora.citybuild.urban.render;

import com.sora.citybuild.urban.block.custom.sign.SignExpresswayRoadName;
import com.sora.citybuild.urban.entity.SignExpresswayRoadNameEntity;
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

public class SignExpresswayRoadNameEntityRenderer
implements BlockEntityRenderer<SignExpresswayRoadNameEntity> {
    private final Font textRenderer;

    public SignExpresswayRoadNameEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.textRenderer = ctx.getFont();
    }

    public void render(SignExpresswayRoadNameEntity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        String text1 = entity.getText1();
        if (text1 == null || text1.isEmpty()) {
            text1 = " ";
        }
        Direction facing = (Direction)entity.getBlockState().getValue(SignExpresswayRoadName.FACING);
        SignExpresswayRoadName.Type type = (SignExpresswayRoadName.Type)(entity.getBlockState().getValue(SignExpresswayRoadName.TYPE));
        this.renderText(matrices, vertexConsumers, light, facing, text1, type, 0.0f, 0.0f, 0xFFFFFF);
    }

    private void renderText(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignExpresswayRoadName.Type type, float andX, float andY, int color) {
        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        float scaleValue = 0.045f;
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
        this.textRenderer.drawInBatch((Component)styledText, 0.0f, (float)(-textHeight) / 2.0f, color, false, matrices.last().pose(), vertexConsumers, Font.DisplayMode.NORMAL, 0, light);
        matrices.popPose();
    }

    public boolean rendersOutsideBoundingBox(SignExpresswayRoadNameEntity blockEntity) {
        return true;
    }

    public int getViewDistance() {
        return 256;
    }
}

