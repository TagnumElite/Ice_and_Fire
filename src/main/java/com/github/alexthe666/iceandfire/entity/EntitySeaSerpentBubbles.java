package com.github.alexthe666.iceandfire.entity;

import com.github.alexthe666.iceandfire.IceAndFire;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.ProjectileHelper;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

public class EntitySeaSerpentBubbles extends EntityFireball implements IDragonProjectile {


    public EntitySeaSerpentBubbles(World worldIn) {
        super(worldIn);
    }

    public EntitySeaSerpentBubbles(World worldIn, double posX, double posY, double posZ, double accelX, double accelY, double accelZ) {
        super(worldIn, posX, posY, posZ, accelX, accelY, accelZ);
    }

    public EntitySeaSerpentBubbles(World worldIn, EntitySeaSerpent shooter, double accelX, double accelY, double accelZ) {
        super(worldIn, shooter, accelX, accelY, accelZ);
        this.setSize(0.5F, 0.5F);
        double d0 = (double) MathHelper.sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ);
        this.accelerationX = accelX / d0 * 0.1D;
        this.accelerationY = accelY / d0 * 0.1D;
        this.accelerationZ = accelZ / d0 * 0.1D;
    }

    protected boolean isFireballFiery() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    public void onUpdate() {
        if (this.world.isRemote || (this.shootingEntity == null || !this.shootingEntity.isDead) && this.world.isBlockLoaded(new BlockPos(this))) {
            super.onUpdate();
            for (int i = 0; i < 6; ++i) {
                IceAndFire.PROXY.spawnParticle("serpent_bubble", world, this.posX, this.posY, this.posZ, 0.0D, 0.0D, 0.0D);
            }
            RayTraceResult raytraceresult = ProjectileHelper.forwardsRaycast(this, true, false, this.shootingEntity);
            if (raytraceresult != null && !net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this, raytraceresult)) {
                this.onImpact(raytraceresult);
            }

            this.posX += this.motionX;
            this.posY += this.motionY;
            this.posZ += this.motionZ;
            ProjectileHelper.rotateTowardsMovement(this, 0.2F);
            float f = this.getMotionFactor();

            if (this.isInWater()) {
                for (int i = 0; i < 4; ++i) {
                    float f1 = 0.25F;
                    this.world.spawnParticle(EnumParticleTypes.WATER_BUBBLE, this.posX - this.motionX * 0.25D, this.posY - this.motionY * 0.25D, this.posZ - this.motionZ * 0.25D, this.motionX, this.motionY, this.motionZ);
                }
            } else {
                this.setDead();
            }

            this.motionX += this.accelerationX;
            this.motionY += this.accelerationY;
            this.motionZ += this.accelerationZ;
            this.motionX *= (double) f;
            this.motionY *= (double) f;
            this.motionZ *= (double) f;
            this.world.spawnParticle(this.getParticleType(), this.posX, this.posY + 0.5D, this.posZ, 0.0D, 0.0D, 0.0D);
            this.setPosition(this.posX, this.posY, this.posZ);
        } else {
            this.setDead();
        }
    }

    public boolean isInWater(){
        return this.isInsideOfMaterial(Material.WATER);
    }

    public boolean handleWaterMovement() {
        return true;
    }

    @Override
    protected void onImpact(RayTraceResult result) {
        if(result.entityHit != null && !result.entityHit.isEntityEqual(this.shootingEntity)){
            result.entityHit.attackEntityFrom(DamageSource.causeMobDamage(this.shootingEntity), 1F);
        }
    }

    public void setSizes(float width, float height) {
        this.setSize(width, height);
    }

}