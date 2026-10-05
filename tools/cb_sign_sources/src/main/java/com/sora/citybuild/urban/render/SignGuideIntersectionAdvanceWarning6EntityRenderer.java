package com.sora.citybuild.urban.render;

import com.sora.citybuild.urban.block.SignBlocks;
import com.sora.citybuild.urban.block.custom.sign.SignGuideIntersectionAdvanceWarning6;
import com.sora.citybuild.urban.entity.SignGuideIntersectionAdvanceWarning6Entity;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Matrix4f;

public class SignGuideIntersectionAdvanceWarning6EntityRenderer
implements BlockEntityRenderer<SignGuideIntersectionAdvanceWarning6Entity> {
    private final Font textRenderer;
    private static final ResourceLocation LEFT = new ResourceLocation("citybuild", "textures/block/sign/sign_indication_left.png");
    private static final ResourceLocation LEFT_TURN = new ResourceLocation("citybuild", "textures/block/sign/sign_indication_left_turn.png");
    private static final ResourceLocation STRAIGHT = new ResourceLocation("citybuild", "textures/block/sign/sign_indication_straight.png");
    private static final ResourceLocation RIGHT = new ResourceLocation("citybuild", "textures/block/sign/sign_indication_right.png");
    private static final ResourceLocation RIGHT_TURN = new ResourceLocation("citybuild", "textures/block/sign/sign_indication_right_turn.png");

    public SignGuideIntersectionAdvanceWarning6EntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.textRenderer = ctx.getFont();
    }

    public void render(SignGuideIntersectionAdvanceWarning6Entity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        SignGuideIntersectionAdvanceWarning6Entity.Direction direction1 = entity.getDirection1();
        SignGuideIntersectionAdvanceWarning6Entity.Direction direction2 = entity.getDirection2();
        String text1 = entity.getText1();
        String text2 = entity.getText2();
        if (text1 == null || text1.isEmpty()) {
            text1 = " ";
        }
        if (text2 == null || text2.isEmpty()) {
            text2 = " ";
        }
        Direction facing = (Direction)entity.getBlockState().getValue(SignGuideIntersectionAdvanceWarning6.FACING);
        SignGuideIntersectionAdvanceWarning6.Type type = (SignGuideIntersectionAdvanceWarning6.Type)(entity.getBlockState().getValue(SignGuideIntersectionAdvanceWarning6.TYPE));
        Block currentBlock = entity.getBlockState().getBlock();
        if (currentBlock == SignBlocks.SIGN_GUIDE_INTERSECTION_ADVANCE_WARNING_6.get()) {
            this.renderDirectionLogo1(matrices, vertexConsumers, light, overlay, facing, direction1, -13.0f, 6.0f, type);
            this.renderDirectionLogo1(matrices, vertexConsumers, light, overlay, facing, direction2, -13.0f, -6.0f, type);
            this.renderText1(matrices, vertexConsumers, light, facing, text1, type, 6.0f, 6.0f, direction1);
            this.renderText1(matrices, vertexConsumers, light, facing, text2, type, 6.0f, -6.0f, direction2);
        } else {
            this.renderDirectionLogo2(matrices, vertexConsumers, light, overlay, facing, direction1, -9.0f, -3.0f, type);
            this.renderDirectionLogo2(matrices, vertexConsumers, light, overlay, facing, direction2, 9.0f, -3.0f, type);
            this.renderText2(matrices, vertexConsumers, light, facing, text1, type, -9.0f, 6.0f, direction1);
            this.renderText2(matrices, vertexConsumers, light, facing, text2, type, 9.0f, 6.0f, direction2);
        }
    }

    private void renderDirectionLogo1(PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay, Direction facing, SignGuideIntersectionAdvanceWarning6Entity.Direction direction, float andX, float andY, SignGuideIntersectionAdvanceWarning6.Type type) {
        ResourceLocation texture = switch (direction) {
            default -> throw new IncompatibleClassChangeError();
            case LEFT -> LEFT;
            case STRAIGHT -> STRAIGHT;
            case RIGHT -> RIGHT;
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
        if (texture == RIGHT) {
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

    private void renderDirectionLogo2(PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay, Direction facing, SignGuideIntersectionAdvanceWarning6Entity.Direction direction, float andX, float andY, SignGuideIntersectionAdvanceWarning6.Type type) {
        ResourceLocation texture = switch (direction) {
            default -> throw new IncompatibleClassChangeError();
            case LEFT -> LEFT_TURN;
            case STRAIGHT -> STRAIGHT;
            case RIGHT -> RIGHT_TURN;
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

    private void renderText1(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignGuideIntersectionAdvanceWarning6.Type type, float andX, float andY, SignGuideIntersectionAdvanceWarning6Entity.Direction direction) {
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
            case POLE_L -> -0.75f;
            case POLE_H -> -0.79f;
            case NORMAL -> -0.43f;
        };
        if (direction == SignGuideIntersectionAdvanceWarning6Entity.Direction.RIGHT) {
            andX = -andX;
        }
        float centeredX = andX / 16.0f - (float)textWidth * scaleValue / 2.0f;
        float centeredY = andY / 16.0f;
        matrices.translate(centeredX, centeredY, zOffset);
        matrices.scale(scaleValue, -scaleValue, scaleValue);
        this.textRenderer.drawInBatch((Component)styledText, 0.0f, (float)(-textHeight) / 2.0f, 0xFFFFFF, false, matrices.last().pose(), vertexConsumers, Font.DisplayMode.NORMAL, 0, light);
        matrices.popPose();
    }

    private void renderText2(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignGuideIntersectionAdvanceWarning6.Type type, float andX, float andY, SignGuideIntersectionAdvanceWarning6Entity.Direction direction) {
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

    public boolean rendersOutsideBoundingBox(SignGuideIntersectionAdvanceWarning6Entity blockEntity) {
        return true;
    }

    public int getViewDistance() {
        return 256;
    }
}

