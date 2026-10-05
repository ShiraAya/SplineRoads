package com.sora.citybuild.urban.render;

import com.sora.citybuild.urban.block.SignBlocks;
import com.sora.citybuild.urban.block.custom.sign.SignGuideIntersectionWarning4;
import com.sora.citybuild.urban.entity.SignGuideIntersectionWarning4Entity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Map;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Matrix4f;

public class SignGuideIntersectionWarning4EntityRenderer
implements BlockEntityRenderer<SignGuideIntersectionWarning4Entity> {
    private final Font textRenderer;
    private static final ResourceLocation LEFT1 = new ResourceLocation("citybuild", "textures/block/sign/sign_indication_left.png");
    private static final ResourceLocation STRAIGHT1 = new ResourceLocation("citybuild", "textures/block/sign/sign_indication_straight.png");
    private static final ResourceLocation RIGHT1 = new ResourceLocation("citybuild", "textures/block/sign/sign_indication_right.png");
    private static final ResourceLocation LEFT2 = new ResourceLocation("citybuild", "textures/block/sign/sign_guide_intersection_warning_5_left.png");
    private static final ResourceLocation STRAIGHT2 = new ResourceLocation("citybuild", "textures/block/sign/sign_guide_intersection_warning_5_straight.png");
    private static final ResourceLocation RIGHT2 = new ResourceLocation("citybuild", "textures/block/sign/sign_guide_intersection_warning_5_right.png");
    private static final Map<Direction, Map<String, String>> DIRECTION_MAP = Map.of(
            Direction.NORTH, Map.of("cnLeft", "西", "cnRight", "东", "enLeft", "W", "enRight", "E"),
            Direction.SOUTH, Map.of("cnLeft", "东", "cnRight", "西", "enLeft", "E", "enRight", "W"),
            Direction.WEST, Map.of("cnLeft", "南", "cnRight", "北", "enLeft", "S", "enRight", "N"),
            Direction.EAST, Map.of("cnLeft", "北", "cnRight", "南", "enLeft", "N", "enRight", "S")
    );

    public SignGuideIntersectionWarning4EntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.textRenderer = ctx.getFont();
    }

    public void render(SignGuideIntersectionWarning4Entity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        SignGuideIntersectionWarning4Entity.Direction direction1 = entity.getDirection1();
        String text1 = entity.getText1();
        if (text1 == null || text1.isEmpty()) {
            text1 = " ";
        }
        Block currentBlock = entity.getBlockState().getBlock();
        Direction facing = (Direction)entity.getBlockState().getValue(SignGuideIntersectionWarning4.FACING);
        SignGuideIntersectionWarning4.Type type = (SignGuideIntersectionWarning4.Type)(entity.getBlockState().getValue(SignGuideIntersectionWarning4.TYPE));
        if (currentBlock == SignBlocks.SIGN_GUIDE_INTERSECTION_WARNING_4.get()) {
            this.renderDirectionLogo(matrices, vertexConsumers, light, overlay, facing, direction1, -13.0f, 0.0f, type);
            this.renderText1(matrices, vertexConsumers, light, facing, text1, type, 4.0f, 0.0f, direction1);
        } else {
            this.renderText2(matrices, vertexConsumers, light, facing, text1, type, 0.0f, 0.0f);
            this.renderBackgroundLogo(matrices, vertexConsumers, light, overlay, facing, direction1, 0.0f, 0.0f, type);
            this.renderDirectionText(matrices, vertexConsumers, light, facing, "cnLeft", type, true, true);
            this.renderDirectionText(matrices, vertexConsumers, light, facing, "cnRight", type, false, true);
            this.renderDirectionText(matrices, vertexConsumers, light, facing, "enLeft", type, true, false);
            this.renderDirectionText(matrices, vertexConsumers, light, facing, "enRight", type, false, false);
        }
    }

    private void renderDirectionLogo(PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay, Direction facing, SignGuideIntersectionWarning4Entity.Direction direction, float andX, float andY, SignGuideIntersectionWarning4.Type type) {
        ResourceLocation texture = switch (direction) {
            default -> throw new IncompatibleClassChangeError();
            case LEFT -> LEFT1;
            case STRAIGHT -> STRAIGHT1;
            case RIGHT -> RIGHT1;
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
        if (texture == RIGHT1) {
            x = -x;
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

    private void renderBackgroundLogo(PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay, Direction facing, SignGuideIntersectionWarning4Entity.Direction direction, float andX, float andY, SignGuideIntersectionWarning4.Type type) {
        ResourceLocation texture = switch (direction) {
            default -> throw new IncompatibleClassChangeError();
            case LEFT -> LEFT2;
            case STRAIGHT -> STRAIGHT2;
            case RIGHT -> RIGHT2;
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
        float arrowSize = 1.75f;
        float halfSize = arrowSize / 2.0f;
        float x = andX / 16.0f;
        if (texture == RIGHT1) {
            x = -x;
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

    private void renderText2(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignGuideIntersectionWarning4.Type type, float andX, float andY) {
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
        this.textRenderer.drawInBatch((Component)styledText, 0.0f, (float)(-textHeight) / 2.0f, 0xFFFFFF, false, matrices.last().pose(), vertexConsumers, Font.DisplayMode.NORMAL, 0, light);
        matrices.popPose();
    }

    private void renderDirectionText(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String directionKey, SignGuideIntersectionWarning4.Type type, boolean leftTF, boolean cnTF) {
        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        String directionText = DIRECTION_MAP.get(facing).get(directionKey);
        MutableComponent styledText = Component.literal((String)directionText).setStyle(Style.EMPTY.withBold(Boolean.valueOf(true)));
        int textWidth = this.textRenderer.width((FormattedText)styledText);
        Objects.requireNonNull(this.textRenderer);
        int textHeight = 9;
        float zOffset = switch (type) {
            default -> throw new IncompatibleClassChangeError();
            case POLE_L -> -0.75f;
            case POLE_H -> -0.79f;
            case NORMAL -> -0.43f;
        };
        float x = leftTF ? 16.0f : -16.0f;
        float y = cnTF ? 4.0f : -4.0f;
        float scaleValue = RoadSignTextScale.adjust(directionText, 0.02f);
        float centeredX = x / 16.0f - (float)textWidth * scaleValue / 2.0f;
        float centeredY = y / 16.0f;
        matrices.translate(centeredX, centeredY, zOffset);
        matrices.scale(scaleValue, -scaleValue, scaleValue);
        this.textRenderer.drawInBatch((Component)styledText, 0.0f, (float)(-textHeight) / 2.0f, 0xFFFFFF, false, matrices.last().pose(), vertexConsumers, Font.DisplayMode.NORMAL, 0, light);
        matrices.popPose();
    }

    private void renderText1(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignGuideIntersectionWarning4.Type type, float andX, float andY, SignGuideIntersectionWarning4Entity.Direction direction) {
        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        float scaleValue = 0.04f;
        scaleValue = RoadSignTextScale.adjust(text, scaleValue);
        MutableComponent styledText = Component.literal((String)text).setStyle(Style.EMPTY.withBold(Boolean.valueOf(true)));
        int textWidth = this.textRenderer.width((FormattedText)styledText);
        Objects.requireNonNull(this.textRenderer);
        int textHeight = 9;
        float zOffset = switch (type) {
            case POLE_L -> -0.75f;
            case POLE_H -> -0.79f;
            case NORMAL -> -0.43f;
        };
        if (direction == SignGuideIntersectionWarning4Entity.Direction.RIGHT) {
            andX = -andX;
        }
        float centeredX = andX / 16.0f - (float)textWidth * scaleValue / 2.0f;
        float centeredY = andY / 16.0f;
        matrices.translate(centeredX, centeredY, zOffset);
        matrices.scale(scaleValue, -scaleValue, scaleValue);
        this.textRenderer.drawInBatch((Component)styledText, 0.0f, (float)(-textHeight) / 2.0f, 0xFFFFFF, false, matrices.last().pose(), vertexConsumers, Font.DisplayMode.NORMAL, 0, light);
        matrices.popPose();
    }

    public boolean rendersOutsideBoundingBox(SignGuideIntersectionWarning4Entity blockEntity) {
        return true;
    }

    public int getViewDistance() {
        return 256;
    }
}

