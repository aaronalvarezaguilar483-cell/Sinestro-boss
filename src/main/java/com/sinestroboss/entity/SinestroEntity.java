package com.sinestroboss.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Sinestro, jefe volador de 3 fases.
 * Fase 1 (&gt;60%): proyectiles de energía amarilla y combate cercano.
 * Fase 2 (&lt;60%): invoca soldados (vex) y dispara un rayo amarillo cargado.
 * Fase 3 (&lt;25%): enfurecido, más rápido, aura de miedo (Debilidad + Ceguera).
 */
public class SinestroEntity extends HostileEntity {
    private static final UUID ENRAGE_SPEED_ID = UUID.fromString("5f4a3d1c-8b22-4c7e-9a61-2f0c7d9e1a11");
    private static final UUID ENRAGE_FLY_ID = UUID.fromString("a2c91e47-3d85-4b0f-8e12-6b7f50c4d922");
    private static final DustParticleEffect YELLOW = new DustParticleEffect(new Vector3f(1.0f, 0.85f, 0.05f), 1.4f);
    private static final DustParticleEffect YELLOW_BIG = new DustParticleEffect(new Vector3f(1.0f, 0.9f, 0.1f), 2.4f);

    private final ServerBossBar bossBar = new ServerBossBar(this.getDisplayName(), BossBar.Color.YELLOW, BossBar.Style.PROGRESS);
    private final List<VexEntity> minions = new ArrayList<>();

    private int boltCooldown = 60;
    private int minionCooldown = 100;
    private int beamCooldown = 160;
    private int beamCharge = -1;
    private int auraCooldown = 0;
    private Vec3d beamAim = Vec3d.ZERO;
    private boolean enraged = false;

    public SinestroEntity(EntityType<? extends SinestroEntity> type, World world) {
        super(type, world);
        this.moveControl = new FlightMoveControl(this, 10, true);
        this.setNoGravity(true);
        this.setPersistent();
        this.experiencePoints = 500;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 400.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.6)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 10.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
                .add(EntityAttributes.GENERIC_ARMOR, 8.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new BirdNavigation(this, world);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SinestroCombatGoal(this));
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 24.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    /** 1 = más de 60% de vida, 2 = entre 25% y 60%, 3 = menos de 25%. */
    public int getPhase() {
        float ratio = this.getHealth() / this.getMaxHealth();
        if (ratio > 0.60f) {
            return 1;
        }
        if (ratio > 0.25f) {
            return 2;
        }
        return 3;
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        this.bossBar.setPercent(this.getHealth() / this.getMaxHealth());

        int phase = getPhase();
        if (phase >= 3 && !enraged) {
            enrage();
        }

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            beamCharge = -1;
            return;
        }
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        if (boltCooldown > 0) boltCooldown--;
        if (minionCooldown > 0) minionCooldown--;

        // Proyectil de energía (todas las fases) cuando el objetivo no está pegado.
        if (beamCharge < 0 && boltCooldown <= 0 && this.distanceTo(target) > 3.5f && this.canSee(target)) {
            fireBolt(serverWorld, target);
            boltCooldown = phase == 1 ? 40 : (phase == 2 ? 30 : 25);
        }

        // Soldados del Sinestro Corps (fases 2 y 3).
        if (phase >= 2 && minionCooldown <= 0) {
            summonMinions(serverWorld, target);
            minionCooldown = phase == 2 ? 300 : 240;
        }

        // Rayo amarillo cargado (fases 2 y 3).
        if (phase >= 2) {
            if (beamCharge < 0) {
                if (beamCooldown > 0) {
                    beamCooldown--;
                } else if (this.canSee(target)) {
                    beamCharge = 40;
                    beamAim = aimAt(target);
                    serverWorld.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT,
                            SoundCategory.HOSTILE, 2.0f, 0.7f);
                }
            } else {
                beamCharge--;
                if (beamCharge > 10) {
                    beamAim = aimAt(target); // luego se bloquea para que se pueda esquivar
                }
                serverWorld.spawnParticles(YELLOW_BIG, this.getX(), this.getEyeY(), this.getZ(), 4, 0.5, 0.5, 0.5, 0.02);
                if (beamCharge <= 0) {
                    fireBeam(serverWorld);
                    beamCharge = -1;
                    beamCooldown = phase == 2 ? 300 : 200;
                }
            }
        }

        // Aura de miedo (fase 3).
        if (phase >= 3) {
            if (auraCooldown > 0) {
                auraCooldown--;
            } else {
                auraCooldown = 100;
                serverWorld.spawnParticles(YELLOW_BIG, this.getX(), this.getY() + 1.0, this.getZ(), 40, 3.0, 1.2, 3.0, 0.0);
                List<PlayerEntity> players = serverWorld.getEntitiesByClass(PlayerEntity.class,
                        this.getBoundingBox().expand(12.0),
                        p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
                for (PlayerEntity p : players) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 120, 0));
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 30, 0));
                }
            }
        }
    }

    /** Estela de partículas amarillas al volar rápido (solo se dibuja en el cliente). */
    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient && this.limbAnimator.getSpeed() > 0.4f) {
            this.getWorld().addParticle(YELLOW,
                    this.getX() + (this.random.nextDouble() - 0.5) * 0.6,
                    this.getY() + 0.8 + (this.random.nextDouble() - 0.5) * 0.6,
                    this.getZ() + (this.random.nextDouble() - 0.5) * 0.6,
                    0.0, 0.0, 0.0);
        }
    }

    private Vec3d aimAt(LivingEntity target) {
        Vec3d from = this.getEyePos();
        Vec3d to = new Vec3d(target.getX(), target.getBodyY(0.5), target.getZ());
        Vec3d dir = to.subtract(from);
        return dir.lengthSquared() > 1.0E-4 ? dir.normalize() : Vec3d.ZERO;
    }

    private void fireBolt(ServerWorld world, LivingEntity target) {
        Vec3d from = this.getEyePos().add(0, -0.3, 0);
        Vec3d to = new Vec3d(target.getX(), target.getBodyY(0.5), target.getZ());
        Vec3d diff = to.subtract(from);
        double len = diff.length();
        if (len < 0.01) {
            return;
        }
        Vec3d dir = diff.normalize();
        for (double d = 1.0; d < len; d += 0.6) {
            Vec3d p = from.add(dir.multiply(d));
            world.spawnParticles(YELLOW, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.0f, 1.4f);
        target.damage(this.getDamageSources().indirectMagic(this, this), 8.0f);
    }

    private void fireBeam(ServerWorld world) {
        Vec3d from = this.getEyePos();
        Vec3d dir = beamAim;
        if (dir.lengthSquared() < 1.0E-4) {
            return;
        }
        Set<LivingEntity> hit = new HashSet<>();
        for (double d = 1.0; d < 32.0; d += 0.5) {
            Vec3d p = from.add(dir.multiply(d));
            BlockPos bp = BlockPos.ofFloored(p);
            if (world.getBlockState(bp).isSolidBlock(world, bp)) {
                break;
            }
            world.spawnParticles(YELLOW_BIG, p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0.0);
            List<LivingEntity> found = world.getEntitiesByClass(LivingEntity.class,
                    new Box(p, p).expand(1.0),
                    e -> e != this && e.isAlive() && !(e instanceof SinestroEntity) && !minions.contains(e));
            hit.addAll(found);
        }
        world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.5f, 1.6f);
        for (LivingEntity e : hit) {
            e.damage(this.getDamageSources().indirectMagic(this, this), 18.0f);
            e.takeKnockback(1.0, -dir.x, -dir.z);
        }
    }

    private void summonMinions(ServerWorld world, LivingEntity target) {
        minions.removeIf(v -> !v.isAlive());
        if (minions.size() >= 4) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            VexEntity vex = EntityType.VEX.create(world);
            if (vex == null) {
                continue;
            }
            vex.refreshPositionAndAngles(
                    this.getX() + (this.random.nextDouble() - 0.5) * 3.0,
                    this.getY() + 0.5,
                    this.getZ() + (this.random.nextDouble() - 0.5) * 3.0,
                    this.random.nextFloat() * 360.0f, 0.0f);
            vex.initialize(world, world.getLocalDifficulty(vex.getBlockPos()), SpawnReason.MOB_SUMMONED, null, null);
            vex.setOwner(this);
            vex.setLifeTicks(20 * 40);
            vex.setTarget(target);
            world.spawnEntity(vex);
            minions.add(vex);
        }
        world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 1.5f, 0.8f);
    }

    private void enrage() {
        enraged = true;
        addEnrageModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, ENRAGE_SPEED_ID);
        addEnrageModifier(EntityAttributes.GENERIC_FLYING_SPEED, ENRAGE_FLY_ID);
        this.playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.8f);
    }

    private void addEnrageModifier(net.minecraft.entity.attribute.EntityAttribute attribute, UUID id) {
        EntityAttributeInstance inst = this.getAttributeInstance(attribute);
        if (inst != null && inst.getModifier(id) == null) {
            inst.addTemporaryModifier(new EntityAttributeModifier(id, "Sinestro enrage", 0.4,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** Pequeña explosión de partículas amarillas (la usa el ataque cuerpo a cuerpo). */
    public void yellowBurst(double x, double y, double z, int count) {
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(YELLOW, x, y, z, count, 0.4, 0.4, 0.4, 0.05);
        }
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        for (VexEntity vex : minions) {
            vex.discard();
        }
        minions.clear();
    }

    // ----- Barra de jefe -----
    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        this.bossBar.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Text name) {
        super.setCustomName(name);
        this.bossBar.setName(this.getDisplayName());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (this.hasCustomName()) {
            this.bossBar.setName(this.getDisplayName());
        }
        this.enraged = false; // los modificadores temporales no se guardan; se reaplican solos
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    // ----- Sonidos -----
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_EVOKER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_EVOKER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_EVOKER_DEATH;
    }
}
