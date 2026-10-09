package com.sinestroboss.client;

import com.sinestroboss.SinestroBoss;
import com.sinestroboss.entity.SinestroEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Skin de Sinestro con modelo de jugador (brazos normales) y vuelo estilo Lantern. */
public class SinestroRenderer extends BipedEntityRenderer<SinestroEntity, SinestroModel> {
    private static final Identifier TEXTURE =
            new Identifier(SinestroBoss.MOD_ID, "textures/entity/sinestro.png");

    public SinestroRenderer(EntityRendererFactory.Context context) {
        super(context, new SinestroModel(context.getPart(EntityModelLayers.PLAYER)), 0.6f);
    }

    @Override
    public Identifier getTexture(SinestroEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(SinestroEntity entity, MatrixStack matrices, float amount) {
        matrices.scale(1.2f, 1.2f, 1.2f);
    }

    @Override
    protected void setupTransforms(SinestroEntity entity, MatrixStack matrices,
                                   float animationProgress, float bodyYaw, float tickDelta) {
        super.setupTransforms(entity, matrices, animationProgress, bodyYaw, tickDelta);

        // Flotación suave cuando está quieto.
        matrices.translate(0.0, Math.sin((entity.age + tickDelta) * 0.1) * 0.06, 0.0);

        // Inclinación hacia adelante según la velocidad (pose de vuelo rápido).
        float lean = MathHelper.clamp(entity.limbAnimator.getSpeed(tickDelta) * 1.2f, 0.0f, 1.0f);
        if (lean > 0.01f) {
            matrices.translate(0.0, 1.0, 0.0);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-lean * 75.0f));
            matrices.translate(0.0, -1.0, 0.0);
        }
    }
}
