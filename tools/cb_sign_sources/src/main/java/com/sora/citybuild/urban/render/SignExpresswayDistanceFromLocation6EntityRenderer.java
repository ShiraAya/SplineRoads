package com.sora.citybuild.urban.render;

import com.sora.citybuild.urban.block.custom.sign.SignExpresswayDistanceFromLocation6;
import com.sora.citybuild.urban.entity.SignExpresswayDistanceFromLocation6Entity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Matrix4f;

public class SignExpresswayDistanceFromLocation6EntityRenderer
implements BlockEntityRenderer<SignExpresswayDistanceFromLocation6Entity> {
    private final Font textRenderer;
    private static final ResourceLocation NATIONAL_1 = new ResourceLocation("citybuild", "textures/block/sign/sign_expressway_national_logo_1.png");
    private static final ResourceLocation PROVINCIAL_1 = new ResourceLocation("citybuild", "textures/block/sign/sign_expressway_provicial_logo_1.png");
    private static final ResourceLocation NATIONAL_2 = new ResourceLocation("citybuild", "textures/block/sign/sign_expressway_national_logo_2.png");
    private static final ResourceLocation PROVINCIAL_2 = new ResourceLocation("citybuild", "textures/block/sign/sign_expressway_provicial_logo_2.png");

    public SignExpresswayDistanceFromLocation6EntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.textRenderer = ctx.getFont();
    }

    public void render(SignExpresswayDistanceFromLocation6Entity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        String text1 = entity.getText1();
        String text2 = entity.getText2();
        String text3 = entity.getText3();
        String length1 = entity.getLength1();
        String length2 = entity.getLength2();
        String length3 = entity.getLength3();
        SignExpresswayDistanceFromLocation6Entity.Expressway expressway1 = entity.getExpressway1();
        SignExpresswayDistanceFromLocation6Entity.Expressway expressway2 = entity.getExpressway2();
        String expresswayNumber1 = entity.getExpresswayNumber1();
        String expresswayNumber2 = entity.getExpresswayNumber2();
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
        if (expresswayNumber1 == null || expresswayNumber1.isEmpty()) {
            expresswayNumber1 = " ";
        }
        if (expresswayNumber2 == null || expresswayNumber2.isEmpty()) {
            expresswayNumber2 = " ";
        }
        Direction facing = (Direction)entity.getBlockState().getValue(SignExpresswayDistanceFromLocation6.FACING);
        SignExpresswayDistanceFromLocation6.Type type = (SignExpresswayDistanceFromLocation6.Type)(entity.getBlockState().getValue(SignExpresswayDistanceFromLocation6.TYPE));
        this.renderLeftText(matrices, vertexConsumers, light, facing, text1, type, -7.0f, 8.0f);
        this.renderLeftText(matrices, vertexConsumers, light, facing, text2, type, -7.0f, -3.0f);
        this.renderLeftText(matrices, vertexConsumers, light, facing, text3, type, -7.0f, -10.0f);
        this.renderRightText(matrices, vertexConsumers, light, facing, length1, type, 13.5f, 8.0f, false);
        this.renderRightText(matrices, vertexConsumers, light, facing, length2, type, 13.5f, -3.0f, false);
        this.renderRightText(matrices, vertexConsumers, light, facing, length3, type, 13.5f, -10.0f, false);
        this.renderRightText(matrices, vertexConsumers, light, facing, "km", type, 17.5f, 7.5f, true);
        this.renderRightText(matrices, vertexConsumers, light, facing, "km", type, 17.5f, -3.5f, true);
        this.renderRightText(matrices, vertexConsumers, light, facing, "km", type, 17.5f, -10.5f, true);
        this.renderExpresswayLogo(matrices, vertexConsumers, light, overlay, facing, expressway1, -14.0f, 8.0f, type, expresswayNumber1);
        this.renderExpresswayText(matrices, vertexConsumers, light, facing, expresswayNumber1, type, -14.0f, 7.5f);
        this.renderExpresswayLogo(matrices, vertexConsumers, light, overlay, facing, expressway2, -14.0f, -5.5f, type, expresswayNumber2);
        this.renderExpresswayText(matrices, vertexConsumers, light, facing, expresswayNumber2, type, -14.0f, -6.0f);
    }

    private void renderExpresswayLogo(PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay, Direction facing, SignExpresswayDistanceFromLocation6Entity.Expressway expressway, float andX, float andY, SignExpresswayDistanceFromLocation6.Type type, String expresswayNumber) {
        ResourceLocation texture = switch (expressway) {
            default -> throw new IncompatibleClassChangeError();
            case NATIONAL -> {
                if (expresswayNumber == null || expresswayNumber.trim().isEmpty() || !expresswayNumber.matches(".*\\d.*")) {
                    yield NATIONAL_1;
                }
                String digits = expresswayNumber.replaceAll("[^0-9]", "");
                if (digits.length() == 1) {
                    yield NATIONAL_2;
                }
                yield NATIONAL_1;
            }
            case PROVINCIAL -> {
                if (expresswayNumber == null || expresswayNumber.trim().isEmpty() || !expresswayNumber.matches(".*\\d.*")) {
                    yield PROVINCIAL_1;
                }
                String digits = expresswayNumber.replaceAll("[^0-9]", "");
                if (digits.length() == 1) {
                    yield PROVINCIAL_2;
                }
                yield PROVINCIAL_1;
            }
        };
        matrices.pushPose();
        float zOffset = switch (type) {
            default -> throw new IncompatibleClassChangeError();
            case POLE_L -> -0.75f;
            case POLE_H -> -0.79f;
            case NORMAL -> -0.43f;
        };
        matrices.translate(0.5, 0.5, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        float arrowSize = 0.65f;
        float halfSize = arrowSize / 2.0f;
        float x = andX / 16.0f;
        if (texture == PROVINCIAL_1 || texture == NATIONAL_1) {
            x += 0.0625f;
        }
        float y = andY / 16.0f;
        matrices.translate(x, y, zOffset);
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderType.entityCutout((ResourceLocation)texture));
        Matrix4f matrix = matrices.last().pose();
        consumer.vertex(matrix, -halfSize, -halfSize, 0.0f).color(255, 255, 255, 255).uv(0.0f, 1.0f).overlayCoords(overlay).uv2(light).normal(0.0f, 0.0f, 1.0f).endVertex();
        consumer.vertex(matrix, halfSize, -halfSize, 0.0f).color(255, 255, 255, 255).uv(1.0f, 1.0f).overlayCoords(overlay).uv2(light).normal(0.0f, 0.0f, 1.0f).endVertex();
        consumer.vertex(matrix, halfSize, halfSize, 0.0f).color(255, 255, 255, 255).uv(1.0f, 0.0f).overlayCoords(overlay).uv2(light).normal(0.0f, 0.0f, 1.0f).endVertex();
        consumer.vertex(matrix, -halfSize, halfSize, 0.0f).color(255, 255, 255, 255).uv(0.0f, 0.0f).overlayCoords(overlay).uv2(light).normal(0.0f, 0.0f, 1.0f).endVertex();
        matrices.popPose();
    }

    private void renderLeftText(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignExpresswayDistanceFromLocation6.Type type, float andX, float andY) {
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

    private void renderRightText(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignExpresswayDistanceFromLocation6.Type type, float andX, float andY, boolean isSmallScale) {
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

    private void renderExpresswayText(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignExpresswayDistanceFromLocation6.Type type, float andX, float andY) {
        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        float scaleValue = 0.045f;
        scaleValue = RoadSignTextScale.adjustExpresswayNumber(text, scaleValue);
        MutableComponent styledText = Component.literal((String)text).setStyle(Style.EMPTY.withBold(Boolean.valueOf(true)));
        int textWidth = this.textRenderer.width((FormattedText)styledText);
        Objects.requireNonNull(this.textRenderer);
        int textHeight = 9;
        float zOffset = switch (type) {
            default -> throw new IncompatibleClassChangeError();
            case POLE_L -> -0.74f;
            case POLE_H -> -0.78f;
            case NORMAL -> -0.42f;
        };
        float centeredX = andX / 16.0f - (float)textWidth * scaleValue / 2.0f;
        String digits = text.replaceAll("[^0-9]", "");
        if (text.trim().isEmpty() || !text.matches(".*\\d.*") || digits.length() != 1) {
            centeredX += 0.0625f;
        }
        float centeredY = andY / 16.0f;
        matrices.translate(centeredX, centeredY, zOffset);
        matrices.scale(scaleValue, -scaleValue, scaleValue);
        this.textRenderer.drawInBatch((Component)styledText, 0.0f, (float)(-textHeight) / 2.0f, 0xFFFFFF, false, matrices.last().pose(), vertexConsumers, Font.DisplayMode.NORMAL, 0, light);
        matrices.popPose();
    }

    public boolean rendersOutsideBoundingBox(SignExpresswayDistanceFromLocation6Entity blockEntity) {
        return true;
    }

    public int getViewDistance() {
        return 256;
    }
}

