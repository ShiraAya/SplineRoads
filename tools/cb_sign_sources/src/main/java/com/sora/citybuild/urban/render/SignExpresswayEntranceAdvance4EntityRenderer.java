package com.sora.citybuild.urban.render;

import com.sora.citybuild.urban.block.custom.sign.SignExpresswayEntranceAdvance4;
import com.sora.citybuild.urban.entity.SignExpresswayEntranceAdvance4Entity;
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

public class SignExpresswayEntranceAdvance4EntityRenderer
implements BlockEntityRenderer<SignExpresswayEntranceAdvance4Entity> {
    private final Font textRenderer;
    private static final ResourceLocation NATIONAL_1 = new ResourceLocation("citybuild", "textures/block/sign/sign_expressway_national_logo_1.png");
    private static final ResourceLocation PROVINCIAL_1 = new ResourceLocation("citybuild", "textures/block/sign/sign_expressway_provicial_logo_1.png");
    private static final ResourceLocation NATIONAL_2 = new ResourceLocation("citybuild", "textures/block/sign/sign_expressway_national_logo_2.png");
    private static final ResourceLocation PROVINCIAL_2 = new ResourceLocation("citybuild", "textures/block/sign/sign_expressway_provicial_logo_2.png");
    private static final ResourceLocation NORTH = new ResourceLocation("citybuild", "textures/block/sign/sign_expressway_north.png");
    private static final ResourceLocation EAST = new ResourceLocation("citybuild", "textures/block/sign/sign_expressway_east.png");
    private static final ResourceLocation SOUTH = new ResourceLocation("citybuild", "textures/block/sign/sign_expressway_south.png");
    private static final ResourceLocation WEST = new ResourceLocation("citybuild", "textures/block/sign/sign_expressway_west.png");

    public SignExpresswayEntranceAdvance4EntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.textRenderer = ctx.getFont();
    }

    public void render(SignExpresswayEntranceAdvance4Entity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        SignExpresswayEntranceAdvance4Entity.Direction direction1 = entity.getDirection1();
        SignExpresswayEntranceAdvance4Entity.Expressway expressway1 = entity.getExpressway1();
        String text1 = entity.getText1();
        String expresswayNumber1 = entity.getExpresswayNumber1();
        if (text1 == null || text1.isEmpty()) {
            text1 = " ";
        }
        if (expresswayNumber1 == null || expresswayNumber1.isEmpty()) {
            expresswayNumber1 = " ";
        }
        Direction facing = (Direction)entity.getBlockState().getValue(SignExpresswayEntranceAdvance4.FACING);
        SignExpresswayEntranceAdvance4.Type type = (SignExpresswayEntranceAdvance4.Type)(entity.getBlockState().getValue(SignExpresswayEntranceAdvance4.TYPE));
        this.renderExpresswayLogo(matrices, vertexConsumers, light, overlay, facing, expressway1, -3.0f, 7.0f, type, expresswayNumber1);
        this.renderDirectionLogo(matrices, vertexConsumers, light, overlay, facing, direction1, 6.0f, 7.0f, type);
        this.renderText(matrices, vertexConsumers, light, facing, text1 + "\u65b9\u5411", type, 0.0f, -2.0f, 0xFFFFFF);
        this.renderExpresswayText(matrices, vertexConsumers, light, facing, expresswayNumber1, type, -3.0f, 6.5f);
    }

    private void renderDirectionLogo(PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay, Direction facing, SignExpresswayEntranceAdvance4Entity.Direction direction, float andX, float andY, SignExpresswayEntranceAdvance4.Type type) {
        ResourceLocation texture = switch (direction) {
            default -> throw new IncompatibleClassChangeError();
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
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
        float arrowSize = 0.4f;
        float halfSize = arrowSize / 2.0f;
        float x = andX / 16.0f;
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

    private void renderExpresswayLogo(PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay, Direction facing, SignExpresswayEntranceAdvance4Entity.Expressway expressway, float andX, float andY, SignExpresswayEntranceAdvance4.Type type, String expresswayNumber) {
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

    private void renderText(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignExpresswayEntranceAdvance4.Type type, float andX, float andY, int color) {
        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        float scaleValue = 0.035f;
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

    private void renderExpresswayText(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignExpresswayEntranceAdvance4.Type type, float andX, float andY) {
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
        float centeredY = andY / 16.0f;
        matrices.translate(centeredX, centeredY, zOffset);
        matrices.scale(scaleValue, -scaleValue, scaleValue);
        this.textRenderer.drawInBatch((Component)styledText, 0.0f, (float)(-textHeight) / 2.0f, 0xFFFFFF, false, matrices.last().pose(), vertexConsumers, Font.DisplayMode.NORMAL, 0, light);
        matrices.popPose();
    }

    public boolean rendersOutsideBoundingBox(SignExpresswayEntranceAdvance4Entity blockEntity) {
        return true;
    }

    public int getViewDistance() {
        return 256;
    }
}

