package com.sora.citybuild.urban.render;

import com.sora.citybuild.urban.block.custom.sign.SignExpresswayNamingNumber;
import com.sora.citybuild.urban.entity.SignExpresswayNamingNumberEntity;
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

public class SignExpresswayNamingNumberEntityRenderer
implements BlockEntityRenderer<SignExpresswayNamingNumberEntity> {
    private final Font textRenderer;

    public SignExpresswayNamingNumberEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.textRenderer = ctx.getFont();
    }

    public void render(SignExpresswayNamingNumberEntity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        String expresswayNumber = entity.getExpresswayNumber();
        String expresswayName = entity.getExpresswayName();
        if (expresswayNumber == null || expresswayNumber.isEmpty()) {
            expresswayNumber = " ";
        }
        if (expresswayName == null || expresswayName.isEmpty()) {
            expresswayName = " ";
        }
        Direction facing = (Direction)entity.getBlockState().getValue(SignExpresswayNamingNumber.FACING);
        SignExpresswayNamingNumber.Type type = (SignExpresswayNamingNumber.Type)(entity.getBlockState().getValue(SignExpresswayNamingNumber.TYPE));
        this.renderText(matrices, vertexConsumers, light, facing, expresswayNumber, type, 0.0f, 1.5f, false);
        this.renderText(matrices, vertexConsumers, light, facing, SignExpresswayNamingNumberEntityRenderer.insertSpaceBetweenChars(expresswayName), type, 0.0f, -6.5f, true);
    }

    private void renderText(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignExpresswayNamingNumber.Type type, float andX, float andY, boolean isSmallScale) {
        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        float scaleValue = isSmallScale ? 0.02f : 0.08f;
        scaleValue = isSmallScale
                ? RoadSignTextScale.adjust(text, scaleValue)
                : RoadSignTextScale.adjustExpresswayNumber(text, scaleValue);
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

    public static String insertSpaceBetweenChars(String str) {
        if (str == null || str.isEmpty() || str.equals(" ")) {
            return str;
        }
        return str.replaceAll(".(?!$)", "$0 ");
    }

    public boolean rendersOutsideBoundingBox(SignExpresswayNamingNumberEntity blockEntity) {
        return true;
    }

    public int getViewDistance() {
        return 256;
    }
}

