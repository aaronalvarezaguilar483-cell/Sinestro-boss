package com.sinestroboss.client;

import com.sinestroboss.entity.SinestroEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * Modelo de jugador con pose de vuelo "heroico": al moverse rápido el cuerpo se inclina
 * (ver SinestroRenderer), los brazos van al frente y las piernas quedan quietas.
 */
public class SinestroModel extends PlayerEntityModel<SinestroEntity> {
    public SinestroModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setAngles(SinestroEntity entity, float limbAngle, float limbDistance,
                          float animationProgress, float headYaw, float headPitch) {
        super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);

        float lean = MathHelper.clamp(limbDistance * 1.2f, 0.0f, 1.0f);
        if (lean > 0.01f) {
            float up = -(float) Math.PI; // brazos estirados hacia adelante en el eje del cuerpo
            this.rightArm.pitch = MathHelper.lerp(lean, this.rightArm.pitch, up);
            this.leftArm.pitch = MathHelper.lerp(lean, this.leftArm.pitch, up);
            this.rightLeg.pitch *= (1.0f - lean);
            this.leftLeg.pitch *= (1.0f - lean);
            this.head.pitch -= lean * 1.2f; // mira al frente aunque el cuerpo esté inclinado
        }

        // PlayerEntityModel ya copió estas capas; las volvemos a copiar tras tocar las partes.
        this.hat.copyTransform(this.head);
        this.leftSleeve.copyTransform(this.leftArm);
        this.rightSleeve.copyTransform(this.rightArm);
        this.leftPants.copyTransform(this.leftLeg);
        this.rightPants.copyTransform(this.rightLeg);
    }
}
