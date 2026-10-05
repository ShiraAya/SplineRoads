package com.sora.citybuild.urban.render;

import com.sora.citybuild.urban.block.custom.sign.SignGuideLaneIndicator1;
import com.sora.citybuild.urban.entity.SignGuideLaneIndicator1Entity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Matrix4f;

public class SignGuideLaneIndicator1EntityRenderer
implements BlockEntityRenderer<SignGuideLaneIndicator1Entity> {
    private final Font textRenderer;
    private static final ResourceLocation ARROW_LEFT_TURN = new ResourceLocation("citybuild", "textures/block/sign/sign_guide_lane_arrow_left_turn.png");
    private static final ResourceLocation ARROW_STRAIGHT = new ResourceLocation("citybuild", "textures/block/sign/sign_guide_lane_arrow_straight.png");
    private static final ResourceLocation ARROW_RIGHT_TURN = new ResourceLocation("citybuild", "textures/block/sign/sign_guide_lane_arrow_right_turn.png");
    private static final ResourceLocation ARROW_STRAIGHT_LEFT_TURN = new ResourceLocation("citybuild", "textures/block/sign/sign_guide_lane_arrow_straight_left_turn.png");
    private static final ResourceLocation ARROW_STRAIGHT_RIGHT_TURN = new ResourceLocation("citybuild", "textures/block/sign/sign_guide_lane_arrow_straight_right_turn.png");
    private static final ResourceLocation ARROW_LEFT_TURN_AROUND = new ResourceLocation("citybuild", "textures/block/sign/sign_guide_lane_arrow_straight_left_turn_around.png");

    public SignGuideLaneIndicator1EntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.textRenderer = ctx.getFont();
    }

    public void render(SignGuideLaneIndicator1Entity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        SignGuideLaneIndicator1Entity.Direction direction1 = entity.getDirection1();
        SignGuideLaneIndicator1Entity.Direction direction2 = entity.getDirection2();
        SignGuideLaneIndicator1Entity.Direction direction3 = entity.getDirection3();
        SignGuideLaneIndicator1Entity.Direction direction4 = entity.getDirection4();
        Direction facing = (Direction)entity.getBlockState().getValue(SignGuideLaneIndicator1.FACING);
        SignGuideLaneIndicator1.Type type = (SignGuideLaneIndicator1.Type)(entity.getBlockState().getValue(SignGuideLaneIndicator1.TYPE));
        this.renderArrow(matrices, vertexConsumers, light, overlay, facing, direction1, -17.5f, 3.0f, type);
        this.renderArrow(matrices, vertexConsumers, light, overlay, facing, direction2, -6.0f, 3.0f, type);
        this.renderArrow(matrices, vertexConsumers, light, overlay, facing, direction3, 6.0f, 3.0f, type);
        this.renderArrow(matrices, vertexConsumers, light, overlay, facing, direction4, 17.5f, 3.0f, type);
    }

    private void renderArrow(PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay, Direction facing, SignGuideLaneIndicator1Entity.Direction direction, float andX, float andY, SignGuideLaneIndicator1.Type type) {
        ResourceLocation texture = switch (direction) {
            default -> throw new IncompatibleClassChangeError();
            case LEFT_TURN -> ARROW_LEFT_TURN;
            case STRAIGHT -> ARROW_STRAIGHT;
            case RIGHT_TURN -> ARROW_RIGHT_TURN;
            case STRAIGHT_LEFT_TURN -> ARROW_STRAIGHT_LEFT_TURN;
            case STRAIGHT_RIGHT_TURN -> ARROW_STRAIGHT_RIGHT_TURN;
            case LEFT_TURN_AROUND -> ARROW_LEFT_TURN_AROUND;
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
        float arrowSize = 0.9f;
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

    public boolean rendersOutsideBoundingBox(SignGuideLaneIndicator1Entity blockEntity) {
        return true;
    }

    public int getViewDistance() {
        return 256;
    }
}

