package com.sora.citybuild.urban.render;

import com.sora.citybuild.urban.block.custom.sign.SignExpresswayDistanceFromLocation4;
import com.sora.citybuild.urban.entity.SignExpresswayDistanceFromLocation4Entity;
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

public class SignExpresswayDistanceFromLocation4EntityRenderer
implements BlockEntityRenderer<SignExpresswayDistanceFromLocation4Entity> {
    private final Font textRenderer;
    private static final ResourceLocation ORDINARY_MUNICIPAL_1 = new ResourceLocation("citybuild", "textures/block/sign/sign_ordinary_municipal_road_logo_1.png");
    private static final ResourceLocation ORDINARY_MUNICIPAL_2 = new ResourceLocation("citybuild", "textures/block/sign/sign_ordinary_municipal_road_logo_2.png");

    public SignExpresswayDistanceFromLocation4EntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.textRenderer = ctx.getFont();
    }

    public void render(SignExpresswayDistanceFromLocation4Entity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        String text1 = entity.getText1();
        String text2 = entity.getText2();
        String text3 = entity.getText3();
        SignExpresswayDistanceFromLocation4Entity.RoadType roadType1 = entity.getRoadType1();
        SignExpresswayDistanceFromLocation4Entity.RoadType roadType2 = entity.getRoadType2();
        SignExpresswayDistanceFromLocation4Entity.RoadType roadType3 = entity.getRoadType3();
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
        Direction facing = (Direction)entity.getBlockState().getValue(SignExpresswayDistanceFromLocation4.FACING);
        SignExpresswayDistanceFromLocation4.Type type = (SignExpresswayDistanceFromLocation4.Type)(entity.getBlockState().getValue(SignExpresswayDistanceFromLocation4.TYPE));
        this.renderLogo(matrices, vertexConsumers, light, overlay, facing, roadType1, -8.0f, 12.0f, type, text1);
        this.renderLogo(matrices, vertexConsumers, light, overlay, facing, roadType2, -8.0f, 0.0f, type, text2);
        this.renderLogo(matrices, vertexConsumers, light, overlay, facing, roadType3, -8.0f, -12.0f, type, text3);
        this.renderLeftText(matrices, vertexConsumers, light, facing, text1, type, -17.0f, 12.0f, roadType1);
        this.renderLeftText(matrices, vertexConsumers, light, facing, text2, type, -17.0f, 0.0f, roadType2);
        this.renderLeftText(matrices, vertexConsumers, light, facing, text3, type, -17.0f, -12.0f, roadType3);
        this.renderRightText(matrices, vertexConsumers, light, facing, length1, type, 13.0f, 12.0f, false);
        this.renderRightText(matrices, vertexConsumers, light, facing, length2, type, 13.0f, 0.0f, false);
        this.renderRightText(matrices, vertexConsumers, light, facing, length3, type, 13.0f, -12.0f, false);
        this.renderRightText(matrices, vertexConsumers, light, facing, "km", type, 17.0f, 11.5f, true);
        this.renderRightText(matrices, vertexConsumers, light, facing, "km", type, 17.0f, -0.5f, true);
        this.renderRightText(matrices, vertexConsumers, light, facing, "km", type, 17.0f, -12.5f, true);
    }

    private void renderLogo(PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay, Direction facing, SignExpresswayDistanceFromLocation4Entity.RoadType roadType, float andX, float andY, SignExpresswayDistanceFromLocation4.Type type, String text) {
        ResourceLocation texture = switch (roadType) {
            case EXPRESSWAY -> null;
            case ORDINARY_MUNICIPAL ->
                    text == null || text.trim().isEmpty() || text.length() <= 3
                            ? ORDINARY_MUNICIPAL_1
                            : ORDINARY_MUNICIPAL_2;
        };
        if (texture == null) {
            return;
        }
        matrices.pushPose();
        float zOffset = switch (type) {
            default -> throw new IncompatibleClassChangeError();
            case POLE_L -> -0.75f;
            case POLE_H -> -0.79f;
            case NORMAL -> -0.43f;
        };
        matrices.translate(0.5, 0.5, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        float arrowSize = 1.55f;
        float halfSize = arrowSize / 2.0f;
        float x = andX / 16.0f;
        if (texture == ORDINARY_MUNICIPAL_2) {
            x += 0.1875f;
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

    private void renderLeftText(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignExpresswayDistanceFromLocation4.Type type, float andX, float andY, SignExpresswayDistanceFromLocation4Entity.RoadType roadType) {
        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        float scaleValue = 0.04f;
        scaleValue = RoadSignTextScale.adjust(text, scaleValue);
        MutableComponent styledText = Component.literal((String)text).setStyle(Style.EMPTY.withBold(Boolean.valueOf(true)));
        Objects.requireNonNull(this.textRenderer);
        int textHeight = 9;
        float zOffset = switch (roadType) {
            default -> throw new IncompatibleClassChangeError();
            case EXPRESSWAY -> {
                switch (type) {
                    default: {
                        throw new IncompatibleClassChangeError();
                    }
                    case POLE_L: {
                        yield -0.75f;
                    }
                    case POLE_H: {
                        yield -0.79f;
                    }
                    case NORMAL: 
                }
                yield -0.43f;
            }
            case ORDINARY_MUNICIPAL -> {
                switch (type) {
                    default: {
                        throw new IncompatibleClassChangeError();
                    }
                    case POLE_L: {
                        yield -0.74f;
                    }
                    case POLE_H: {
                        yield -0.78f;
                    }
                    case NORMAL: 
                }
                yield -0.42f;
            }
        };
        float centeredX = andX / 16.0f;
        float centeredY = andY / 16.0f;
        matrices.translate(centeredX, centeredY, zOffset);
        matrices.scale(scaleValue, -scaleValue, scaleValue);
        this.textRenderer.drawInBatch((Component)styledText, 0.0f, (float)(-textHeight) / 2.0f, 0xFFFFFF, false, matrices.last().pose(), vertexConsumers, Font.DisplayMode.NORMAL, 0, light);
        matrices.popPose();
    }

    private void renderRightText(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignExpresswayDistanceFromLocation4.Type type, float andX, float andY, boolean isSmallScale) {
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

    public boolean rendersOutsideBoundingBox(SignExpresswayDistanceFromLocation4Entity blockEntity) {
        return true;
    }

    public int getViewDistance() {
        return 256;
    }
}

