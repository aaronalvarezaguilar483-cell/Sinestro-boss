package com.sinestroboss.item;

import com.sinestroboss.ModEntities;
import com.sinestroboss.entity.SinestroEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Tótem del Miedo: al usarlo sobre un bloque invoca a Sinestro. */
public class SummonItem extends Item {
    public SummonItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (!(world instanceof ServerWorld serverWorld)) {
            return ActionResult.SUCCESS;
        }
        BlockPos pos = context.getBlockPos().offset(context.getSide());
        SinestroEntity boss = ModEntities.SINESTRO.create(serverWorld);
        if (boss == null) {
            return ActionResult.FAIL;
        }
        boss.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                context.getPlayerYaw() + 180.0f, 0.0f);
        boss.initialize(serverWorld, serverWorld.getLocalDifficulty(pos), SpawnReason.MOB_SUMMONED, null, null);
        serverWorld.spawnEntity(boss);
        serverWorld.playSound(null, pos, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.0f, 0.8f);
        PlayerEntity player = context.getPlayer();
        if (player == null || !player.getAbilities().creativeMode) {
            context.getStack().decrement(1);
        }
        return ActionResult.CONSUME;
    }
}
