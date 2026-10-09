package com.sinestroboss;

import com.sinestroboss.entity.SinestroEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {
    public static final EntityType<SinestroEntity> SINESTRO = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(SinestroBoss.MOD_ID, "sinestro"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, SinestroEntity::new)
                    .dimensions(EntityDimensions.fixed(0.8f, 2.4f))
                    .trackRangeBlocks(96)
                    .fireImmune()
                    .build());

    public static void register() {
        FabricDefaultAttributeRegistry.register(SINESTRO, SinestroEntity.createAttributes());
    }
}
