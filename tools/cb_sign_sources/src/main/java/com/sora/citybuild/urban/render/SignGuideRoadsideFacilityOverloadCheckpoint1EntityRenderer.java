package com.sora.citybuild.urban.render;

import com.sora.citybuild.urban.block.custom.sign.SignGuideRoadsideFacilityOverloadCheckpoint1;
import com.sora.citybuild.urban.entity.SignGuideRoadsideFacilityOverloadCheckpoint1Entity;
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

public class SignGuideRoadsideFacilityOverloadCheckpoint1EntityRenderer
implements BlockEntityRenderer<SignGuideRoadsideFacilityOverloadCheckpoint1Entity> {
    private final Font textRenderer;

    public SignGuideRoadsideFacilityOverloadCheckpoint1EntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.textRenderer = ctx.getFont();
    }

    public void render(SignGuideRoadsideFacilityOverloadCheckpoint1Entity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        SignGuideRoadsideFacilityOverloadCheckpoint1Entity.Unit unit1 = entity.getUnit1();
        String length1 = entity.getLength1();
        if (length1 == null || length1.isEmpty()) {
            length1 = " ";
        }
        String unit1_number = switch (unit1) {
            default -> throw new IncompatibleClassChangeError();
            case KILOMETRE -> "km";
            case METRE -> "m";
        };
        Direction facing = (Direction)entity.getBlockState().getValue(SignGuideRoadsideFacilityOverloadCheckpoint1.FACING);
        SignGuideRoadsideFacilityOverloadCheckpoint1.Type type = (SignGuideRoadsideFacilityOverloadCheckpoint1.Type)(entity.getBlockState().getValue(SignGuideRoadsideFacilityOverloadCheckpoint1.TYPE));
        this.renderText(matrices, vertexConsumers, light, facing, length1, type, -1.5f, -13.0f, false);
        this.renderText(matrices, vertexConsumers, light, facing, unit1_number, type, 2.5f, -13.5f, true);
    }

    private void renderText(PoseStack matrices, MultiBufferSource vertexConsumers, int light, Direction facing, String text, SignGuideRoadsideFacilityOverloadCheckpoint1.Type type, float andX, float andY, boolean isSmallScale) {
        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        float scaleValue = isSmallScale ? 0.03f : 0.045f;
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

    public boolean rendersOutsideBoundingBox(SignGuideRoadsideFacilityOverloadCheckpoint1Entity blockEntity) {
        return true;
    }

    public int getViewDistance() {
        return 256;
    }
}

