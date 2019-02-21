package com.github.alexthe666.iceandfire.entity;

import com.github.alexthe666.iceandfire.IceAndFire;
import com.github.alexthe666.iceandfire.api.FoodUtils;
import com.github.alexthe666.iceandfire.client.model.IFChainBuffer;
import com.github.alexthe666.iceandfire.core.ModSounds;
import com.github.alexthe666.iceandfire.entity.ai.*;
import com.google.common.base.Predicate;
import net.ilexiconn.llibrary.server.animation.Animation;
import net.ilexiconn.llibrary.server.animation.AnimationHandler;
import net.ilexiconn.llibrary.server.animation.IAnimatedEntity;
import net.ilexiconn.llibrary.server.entity.multipart.IMultipartEntity;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNavigateSwimmer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.*;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

public class EntitySeaSerpent extends EntityAnimal implements IAnimatedEntity, IMultipartEntity, IVillagerFear, IAnimalFear {

    private static final DataParameter<Integer> VARIANT = EntityDataManager.<Integer>createKey(EntitySeaSerpent.class, DataSerializers.VARINT);
    private static final DataParameter<Float> SCALE = EntityDataManager.<Float>createKey(EntitySeaSerpent.class, DataSerializers.FLOAT);
    private static final DataParameter<Boolean> JUMPING = EntityDataManager.<Boolean>createKey(EntitySeaSerpent.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> BREATHING = EntityDataManager.<Boolean>createKey(EntitySeaSerpent.class, DataSerializers.BOOLEAN);
    public int swimCycle;
    private int animationTick;
    private Animation currentAnimation;
    private EntityMutlipartPart[] segments = new EntityMutlipartPart[9];
    private float lastScale;
    private boolean isLandNavigator;
    private SwimBehavior swimBehavior = SwimBehavior.WANDER;
    private boolean changedSwimBehavior = false;
    private int ticksCircling;
    public float orbitRadius = 0.0F;
    @SideOnly(Side.CLIENT)
    public IFChainBuffer roll_buffer;
    @SideOnly(Side.CLIENT)
    public IFChainBuffer tail_buffer;
    @SideOnly(Side.CLIENT)
    public IFChainBuffer head_buffer;
    @SideOnly(Side.CLIENT)
    public IFChainBuffer pitch_buffer;
    @Nullable
    public BlockPos orbitPos = null;
    public static final Animation ANIMATION_BITE = Animation.create(15);
    public static final Animation ANIMATION_ROAR = Animation.create(40);
    private boolean isArcing = false;
    private float arcingYAdditive = 0F;
    public float jumpProgress = 0.0F;
    public float wantJumpProgress = 0.0F;
    public float jumpRot = 0.0F;
    public float breathProgress = 0.0F;
    private int ticksSinceJump = 0;
    private int ticksSinceRoar = 0;
    private int ticksJumping = 0;
    public static final int TIME_BETWEEN_JUMPS = 170;
    public static final int TIME_BETWEEN_ROARS = 300;
    private static final Predicate NOT_SEA_SERPENT = new Predicate<Entity>() {
        public boolean apply(@Nullable Entity entity) {
            return entity instanceof EntityLivingBase && !(entity instanceof EntitySeaSerpent) && DragonUtils.isAlive((EntityLivingBase) entity);
        }
    };
    //true  = melee, false = ranged
    public boolean attackDecision = false;
    private boolean isBreathing;

    public EntitySeaSerpent(World worldIn) {
        super(worldIn);
        switchNavigator(true);
        this.setSize(0.5F, 0.5F);
        this.ignoreFrustumCheck = true;
        resetParts(1.0F);
        if (FMLCommonHandler.instance().getSide().isClient()) {
            roll_buffer = new IFChainBuffer();
            pitch_buffer = new IFChainBuffer();
            tail_buffer = new IFChainBuffer();
            head_buffer = new IFChainBuffer();
        }
    }

    protected void initEntityAI() {
        this.tasks.addTask(0, new EntitySeaSerpent.AISwimBite());
        this.tasks.addTask(0, new EntitySeaSerpent.AISwimWander());
        this.tasks.addTask(0, new EntitySeaSerpent.AISwimCircle());
        this.tasks.addTask(1, new SeaSerpentAIAttackMelee(this, 1.0D, true));
        this.tasks.addTask(2, new AquaticAIGetInWater(this, 1.0D));
        this.tasks.addTask(3, new EntityAIWatchClosestIgnoreRider(this, EntityLivingBase.class, 6.0F));
        this.targetTasks.addTask(1, new EntityAIHurtByTarget(this, false, new Class[0]));
        this.targetTasks.addTask(2, new FlyingAITarget(this, EntityLivingBase.class, 0, false, false, NOT_SEA_SERPENT));
    }

    private void switchNavigator(boolean onLand) {
        if (onLand) {
            this.moveHelper = new EntityMoveHelper(this);
            this.navigator = new PathNavigateAmphibious(this, world);
            this.isLandNavigator = true;
        } else {
            this.moveHelper = new EntitySeaSerpent.SwimmingMoveHelper();
            this.navigator = new PathNavigateSwimmer(this, world);
            this.isLandNavigator = false;
        }
    }

    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.15D);
        this.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(3.0D);
        this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(Math.min(2048, IceAndFire.CONFIG.dragonTargetSearchLength));
    }

    public void resetParts(float scale) {
        clearParts();
        segments = new EntityMutlipartPart[9];
        for (int i = 0; i < segments.length; i++) {
            if (i > 3) {
                segments[i] = new EntityMutlipartPart(this, (2F - ((i + 1) * 0.55F)) * scale, 0, 0, 0.5F * scale, 0.5F * scale, 1);
            } else {
                segments[i] = new EntityMutlipartPart(this, (1.8F - (i * 0.5F)) * scale, 0, 0, 0.45F * scale, 0.4F * scale, 1);
            }
        }
    }

    public void onUpdateParts() {
        for (Entity entity : segments) {
            if (entity != null) {
                entity.onUpdate();
            }
        }
    }

    private void clearParts() {
        for (Entity entity : segments) {
            if (entity != null) {
                world.removeEntityDangerously(entity);
            }
        }
    }

    public void setDead() {
        clearParts();
        super.setDead();
    }

    @Override
    public void setScaleForAge(boolean par1) {
        this.setScale(this.getSeaSerpentScale());
        if (this.getSeaSerpentScale() != lastScale) {
            resetParts(this.getSeaSerpentScale());
        }
        lastScale = this.getSeaSerpentScale();
    }

    @Override
    public boolean attackEntityAsMob(Entity entityIn) {
        if (this.getAnimation() != ANIMATION_BITE) {
            this.setAnimation(ANIMATION_BITE);
            return true;
        }
        return false;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        this.setScaleForAge(true);
        onUpdateParts();
        if (this.isInWater()) {
            spawnParticlesAroundEntity(EnumParticleTypes.WATER_BUBBLE, this, (int) this.getSeaSerpentScale());
            for (Entity entity : segments) {
                spawnParticlesAroundEntity(EnumParticleTypes.WATER_BUBBLE, entity, (int) this.getSeaSerpentScale());
            }
        }
        AnimationHandler.INSTANCE.updateAnimations(this);
    }

    private void spawnParticlesAroundEntity(EnumParticleTypes type, Entity entity, int count) {
        for (int i = 0; i < count; i++) {
            float d0 = 0;
            float d1 = 0;
            float d2 = 0;
            this.world.spawnParticle(type, entity.posX + (double) (this.rand.nextFloat() * entity.width * 2.0F) - (double) entity.width, entity.posY + 0.5D + (double) (this.rand.nextFloat() * entity.height), entity.posZ + (double) (this.rand.nextFloat() * entity.width * 2.0F) - (double) entity.width, d0, d1, d2);
        }
    }

    private void spawnSlamParticles(EnumParticleTypes type) {
        for (int i = 0; i < this.getSeaSerpentScale() * 3; i++) {
            for (int i1 = 0; i1 < 20; i1++) {
                double motionX = getRNG().nextGaussian() * 0.07D;
                double motionY = getRNG().nextGaussian() * 0.07D;
                double motionZ = getRNG().nextGaussian() * 0.07D;
                float radius = 1.25F * getSeaSerpentScale();
                float angle = (0.01745329251F * this.renderYawOffset) + i1 * 1F;
                double extraX = (double) (radius * MathHelper.sin((float) (Math.PI + angle)));
                double extraY = 0.8F;
                double extraZ = (double) (radius * MathHelper.cos(angle));
                if (world.isRemote) {
                    world.spawnParticle(type, true, this.posX + extraX, this.posY + extraY, this.posZ + extraZ, motionX, motionY, motionZ, new int[]{0});

                }
            }
        }
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(VARIANT, Integer.valueOf(0));
        this.dataManager.register(SCALE, Float.valueOf(0F));
        this.dataManager.register(JUMPING, false);
        this.dataManager.register(BREATHING, false);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setInteger("Variant", this.getVariant());
        compound.setInteger("TicksSinceRoar", ticksSinceRoar);
        compound.setFloat("Scale", this.getSeaSerpentScale());
        compound.setBoolean("JumpingOutOfWater", this.isJumpingOutOfWater());
        compound.setBoolean("Breathing", this.isBreathing());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        this.setVariant(compound.getInteger("Variant"));
        ticksSinceRoar = compound.getInteger("TicksSinceRoar");
        this.setSeaSerpentScale(compound.getFloat("Scale"));
        this.setJumpingOutOfWater(compound.getBoolean("JumpingOutOfWater"));
        this.setBreathing(compound.getBoolean("Breathing"));
    }

    private void setSeaSerpentScale(float scale) {
        this.dataManager.set(SCALE, Float.valueOf(scale));
        this.updateAttributes();
    }

    private void updateAttributes() {
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(Math.min(0.25D, 0.15D * this.getSeaSerpentScale()));
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(Math.max(4, 4 * this.getSeaSerpentScale()));
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(Math.max(10, 10 * this.getSeaSerpentScale()));
        this.heal(30F * this.getSeaSerpentScale());
    }

    public float getSeaSerpentScale() {
        return Float.valueOf(this.dataManager.get(SCALE).floatValue());
    }

    public int getVariant() {
        return Integer.valueOf(this.dataManager.get(VARIANT).intValue());
    }

    public boolean isJumpingOutOfWater() {
        return this.dataManager.get(JUMPING).booleanValue();
    }

    public boolean isBreathing() {
        if (world.isRemote) {
            boolean breathing = this.dataManager.get(BREATHING).booleanValue();
            this.isBreathing = breathing;
            return breathing;
        }
        return isBreathing;
    }

    public void setVariant(int variant) {
        this.dataManager.set(VARIANT, Integer.valueOf(variant));
    }

    public void setJumpingOutOfWater(boolean jump) {
        this.dataManager.set(JUMPING, jump);
    }

    public void setBreathing(boolean breathing) {
        this.dataManager.set(BREATHING, breathing);
        if (!world.isRemote) {
            this.isBreathing = breathing;
        }
    }

    public void onLivingUpdate() {
        super.onLivingUpdate();
        boolean breathing = isBreathing() && this.getAnimation() != ANIMATION_BITE && this.getAnimation() != ANIMATION_ROAR;
        boolean jumping = !this.isInWater() && !this.onGround && this.motionY >= 0;
        boolean wantJumping = false; //(ticksSinceJump > TIME_BETWEEN_JUMPS) && this.isInWater();
        boolean ground = !isInWater() && this.onGround;
        boolean prevJumping = this.isJumpingOutOfWater();
        this.ticksSinceRoar++;
        this.ticksSinceJump++;
        if (this.ticksSinceRoar > TIME_BETWEEN_ROARS && isAtSurface() && this.getAnimation() != ANIMATION_BITE && jumpProgress == 0 && !isJumpingOutOfWater()) {
            this.setAnimation(ANIMATION_ROAR);
            this.ticksSinceRoar = 0;
        }
        if (isJumpingOutOfWater()) {
            ticksJumping++;
        } else {
            ticksJumping = 0;
        }
        if (isJumpingOutOfWater() && isWaterBlock(world, new BlockPos(this).up(2))) {
            setJumpingOutOfWater(false);
        }
        if (!isJumpingOutOfWater() && !isWaterBlock(world, new BlockPos(this).up()) && (ticksSinceJump > TIME_BETWEEN_JUMPS || this.getAttackTarget() != null)) {
            ticksSinceJump = 0;
            setJumpingOutOfWater(true);
        }
        if (this.swimCycle < 38) {
            this.swimCycle += 2;
        } else {
            this.swimCycle = 0;
        }
        if (breathing && breathProgress < 20.0F) {
            breathProgress += 0.5F;
        } else if (!breathing && breathProgress > 0.0F) {
            breathProgress -= 0.5F;
        }

        if (jumping && jumpProgress < 10.0F) {
            jumpProgress += 0.5F;
        } else if (!jumping && jumpProgress > 0.0F) {
            jumpProgress -= 0.5F;
        }
        if (wantJumping && wantJumpProgress < 10.0F) {
            wantJumpProgress += 2F;
        } else if (!wantJumping && wantJumpProgress > 0.0F) {
            wantJumpProgress -= 2F;
        }
        if (this.isJumpingOutOfWater() && jumpRot < 1.0F) {
            jumpRot += 0.1F;
        } else if (!this.isJumpingOutOfWater() && jumpRot > 0.0F) {
            jumpRot -= 0.1F;
        }
        if (prevJumping != this.isJumpingOutOfWater() && !this.isJumpingOutOfWater()) {
            this.playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 5F, 0.75F);
            spawnSlamParticles(EnumParticleTypes.WATER_BUBBLE);
            spawnSlamParticles(EnumParticleTypes.WATER_SPLASH);
            spawnSlamParticles(EnumParticleTypes.WATER_SPLASH);
            spawnSlamParticles(EnumParticleTypes.WATER_SPLASH);
            this.doSplashDamage();
        }
        if (!ground && this.isLandNavigator) {
            switchNavigator(false);
        }
        if (ground && !this.isLandNavigator) {
            switchNavigator(true);
        }
        renderYawOffset = rotationYaw;
        rotationPitch = (float) motionY * 20F;
        if (world.isRemote) {
            pitch_buffer.calculateChainWaveBuffer(90, 10, 10F, 0.5F, this);
            if (!jumping) {
                tail_buffer.calculateChainSwingBuffer(70, 20, 5F, this);
                head_buffer.calculateChainSwingBuffer(70, 20, 5F, this);
            }
        }
        if (changedSwimBehavior) {
            changedSwimBehavior = false;
        }
        if (!world.isRemote) {
            if (this.getAttackTarget() != null && this.getAnimation() != ANIMATION_ROAR) {
                if (!attackDecision) {
                    shoot(this.getAttackTarget());
                } else {
                    if (this.getEntityBoundingBox().expand(this.getSeaSerpentScale() / 3, this.getSeaSerpentScale() / 3, this.getSeaSerpentScale() / 3).intersects(this.getAttackTarget().getEntityBoundingBox())) {
                        attackDecision = true;
                    }
                }
            } else {
                this.setBreathing(false);
            }
            if (this.swimBehavior == SwimBehavior.CIRCLE) {
                ticksCircling++;
            } else {
                ticksCircling = 0;
            }
            if (this.getAttackTarget() != null) {
                if(this.attackDecision){
                    if (isPreyAtSurface()) {
                        this.swimBehavior = SwimBehavior.JUMP;
                    } else {
                        this.swimBehavior = SwimBehavior.ATTACK;
                    }
                }else{
                    this.swimBehavior = SwimBehavior.ATTACK;
                }

            }
            if (this.swimBehavior == SwimBehavior.ATTACK && (this.getAttackTarget() == null || this.getAttackTarget().getDistance(this) > 200)) {
                this.swimBehavior = SwimBehavior.WANDER;
            }
            if (this.swimBehavior != SwimBehavior.JUMP && this.swimBehavior != SwimBehavior.ATTACK && this.ticksSinceJump > TIME_BETWEEN_JUMPS) {
                this.swimBehavior = SwimBehavior.JUMP;
                ticksSinceJump = 300;
            }
            if (this.swimBehavior == SwimBehavior.JUMP && this.ticksSinceJump < 100) {
                //this.swimBehavior = SwimBehavior.WANDER;
            }
            if (swimBehavior != SwimBehavior.ATTACK) {
                arcingYAdditive = 0;
            }

        }

    }

    private void doSplashDamage() {
        double width = 5D * this.getSeaSerpentScale();
        List<Entity> list = world.getEntitiesInAABBexcluding(this, this.getEntityBoundingBox().grow(width, width * 0.5D, width), NOT_SEA_SERPENT);
        for (Entity entity : list) {
            if (entity instanceof EntityLivingBase) {
                entity.attackEntityFrom(DamageSource.causeMobDamage(this), ((int) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue()));
                destroyBoat(entity);
                double xRatio = this.posX - entity.posX;
                double zRatio = this.posZ - entity.posZ;
                float f = MathHelper.sqrt(xRatio * xRatio + zRatio * zRatio);
                float strength = 0.3F * this.getSeaSerpentScale();
                entity.motionX /= 2.0D;
                entity.motionZ /= 2.0D;
                entity.motionX -= xRatio / (double) f * (double) strength;
                entity.motionZ -= zRatio / (double) f * (double) strength;
                entity.motionY += (double) strength;
                if (this.motionY > 0.4000000059604645D) {
                    this.motionY = 0.4000000059604645D;
                }
            }
        }

    }

    public void destroyBoat(Entity sailor) {
        if (sailor.getRidingEntity() != null && sailor.getRidingEntity() instanceof EntityBoat && !world.isRemote) {
            EntityBoat boat = (EntityBoat) sailor.getRidingEntity();
            boat.setDead();
            if (this.world.getGameRules().getBoolean("doEntityDrops")) {
                for (int i = 0; i < 3; ++i) {
                    boat.entityDropItem(new ItemStack(Item.getItemFromBlock(Blocks.PLANKS), 1, boat.getBoatType().getMetadata()), 0.0F);
                }
                for (int j = 0; j < 2; ++j) {
                    boat.dropItemWithOffset(Items.STICK, 1, 0.0F);
                }
            }
        }
    }

    private boolean isPreyAtSurface() {
        if (this.getAttackTarget() != null) {
            BlockPos pos = new BlockPos(this.getAttackTarget());
            return !isWaterBlock(world, pos.up((int) Math.ceil(this.getAttackTarget().height)));
        }
        return false;
    }

    private void hurtMob(EntityLivingBase entity) {
        if (this.getAnimation() == ANIMATION_BITE && entity != null && this.getAnimationTick() > 5 && this.getAnimationTick() < 12) {
            this.getAttackTarget().attackEntityFrom(DamageSource.causeMobDamage(this), ((int) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue()));
            EntitySeaSerpent.this.attackDecision = this.getRNG().nextBoolean();
        }
    }

    public void moveJumping() {
        float velocity = 0.5F;
        double x = -MathHelper.sin(this.rotationYaw * 0.017453292F) * MathHelper.cos(this.rotationPitch * 0.017453292F);
        double z = MathHelper.cos(this.rotationYaw * 0.017453292F) * MathHelper.cos(this.rotationPitch * 0.017453292F);
        float f = MathHelper.sqrt(x * x + z * z);
        x = x / (double) f;
        z = z / (double) f;
        x = x * (double) velocity;
        z = z * (double) velocity;
        this.motionX = x;
        this.motionZ = z;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isInWater() {
        return super.isInWater() || this.isInsideOfMaterial(Material.WATER) || this.isInsideOfMaterial(Material.CORAL);
    }

    @Override
    @Nullable
    public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, @Nullable IEntityLivingData livingdata) {
        livingdata = super.onInitialSpawn(difficulty, livingdata);
        this.setVariant(this.getRNG().nextInt(7));
        this.setSeaSerpentScale(1.5F + this.getRNG().nextFloat() * 4.0F);
        return livingdata;
    }

    @Nullable
    @Override
    public EntityAgeable createChild(EntityAgeable ageable) {
        return null;
    }

    @Override
    public int getAnimationTick() {
        return animationTick;
    }

    @Override
    public void setAnimationTick(int tick) {
        animationTick = tick;
    }

    @Override
    public Animation getAnimation() {
        return currentAnimation;
    }

    @Override
    public void setAnimation(Animation animation) {
        currentAnimation = animation;
    }

    @Override
    public Animation[] getAnimations() {
        return new Animation[]{ANIMATION_BITE, ANIMATION_ROAR};
    }

    @Override
    public boolean shouldAnimalsFear(Entity entity) {
        return true;
    }

    public boolean isBlinking() {
        return this.ticksExisted % 50 > 43;
    }

    private boolean canMove() {
        return true;
    }

    public boolean wantsToJump() {
        return this.ticksSinceJump > TIME_BETWEEN_JUMPS && this.swimBehavior == SwimBehavior.JUMP;
    }

    private boolean shouldStopJumping() {
        return ticksJumping > 30;
    }

    public static BlockPos getPositionRelativeToSeafloor(EntitySeaSerpent entity, World world, double x, double z, Random rand) {
        BlockPos pos;
        BlockPos topY = new BlockPos(x, entity.posY, z);
        BlockPos bottomY = new BlockPos(x, entity.posY, z);
        while (isWaterBlock(world, topY) && topY.getY() < world.getHeight()) {
            topY = topY.up();
        }
        while (isWaterBlock(world, bottomY) && bottomY.getY() > 0) {
            bottomY = bottomY.down();
        }
        if (entity.ticksSinceJump > TIME_BETWEEN_JUMPS) {
            return topY.up((int) Math.ceil(3 * entity.getSeaSerpentScale()));

        }
        if (entity.ticksSinceRoar > TIME_BETWEEN_ROARS || entity.getAnimation() == ANIMATION_ROAR) {
            return topY.down();
        }
        for (int tries = 0; tries < 5; tries++) {
            pos = new BlockPos(x, bottomY.getY() + 1 + rand.nextInt(Math.max(1, topY.getY() - bottomY.getY() - 2)), z);
            if (isWaterBlock(world, pos)) {
                return pos;
            }
        }
        return entity.getPosition();
    }

    public static BlockPos getPositionInOrbit(EntitySeaSerpent entity, World world, BlockPos orbit, Random rand) {
        float possibleOrbitRadius = (entity.orbitRadius + 10.0F);
        float radius = 10 * entity.getSeaSerpentScale();
        float angle = (0.01745329251F * possibleOrbitRadius);
        double extraX = (double) (radius * MathHelper.sin((float) (Math.PI + angle)));
        double extraZ = (double) (radius * MathHelper.cos(angle));
        BlockPos radialPos = new BlockPos(orbit.getX() + extraX, orbit.getY(), orbit.getZ() + extraZ);
        //world.setBlockState(radialPos.down(4), Blocks.QUARTZ_BLOCK.getDefaultState());
        // world.setBlockState(orbit.down(4), Blocks.GOLD_BLOCK.getDefaultState());
        entity.orbitRadius = possibleOrbitRadius;
        return radialPos;
    }

    public static BlockPos getPositionPreyArc(EntitySeaSerpent entity, BlockPos target, World world) {
        float radius = 10 * entity.getSeaSerpentScale();
        entity.renderYawOffset = entity.rotationYaw;
        float angle = (0.01745329251F * entity.renderYawOffset);
        double extraX = (double) (radius * MathHelper.sin((float) (Math.PI + angle)));
        double extraZ = (double) (radius * MathHelper.cos(angle));
        double signum = Math.signum(target.getY() + 0.5F - entity.posY);
        BlockPos pos = new BlockPos(target.getX() + extraX, target.getY() + entity.rand.nextInt(5) * signum, target.getZ() + extraZ);
        entity.isArcing = true;
        return clampBlockPosToWater(entity, world, pos);
    }

    private static BlockPos clampBlockPosToWater(Entity entity, World world, BlockPos pos) {
        BlockPos topY = new BlockPos(pos.getX(), entity.posY, pos.getZ());
        BlockPos bottomY = new BlockPos(pos.getX(), entity.posY, pos.getZ());
        while (isWaterBlock(world, topY) && topY.getY() < world.getHeight()) {
            topY = topY.up();
        }
        while (isWaterBlock(world, bottomY) && bottomY.getY() > 0) {
            bottomY = bottomY.down();
        }
        return new BlockPos(pos.getX(), MathHelper.clamp(pos.getY(), bottomY.getY() + 1, topY.getY() - 1), pos.getZ());
    }

    public static boolean isWaterBlock(World world, BlockPos pos) {
        return world.getBlockState(pos).getMaterial() == Material.WATER;
    }

    public boolean isAtSurface() {
        BlockPos pos = new BlockPos(this);
        return isWaterBlock(world, pos.down()) && !isWaterBlock(world, pos.up());
    }

    @Override
    public void travel(float strafe, float vertical, float forward) {
        if (this.swimBehavior == SwimBehavior.JUMP && this.isJumpingOutOfWater() && this.getAttackTarget() == null) {
            moveJumping();
        }
        super.travel(strafe, vertical, forward);
    }

    private void shoot(EntityLivingBase entity) {
        if (!this.attackDecision) {
            if(!this.isInWater()){
                this.setBreathing(false);
                this.attackDecision = true;
            }
            if (this.isBreathing()) {
                if (this.breathProgress > 10F && this.ticksExisted % 3 == 0) {
                    rotationYaw = renderYawOffset;
                    float f1 = (rand.nextFloat() - 0.5F) * 0.5F;
                    float f2 = (rand.nextFloat() - 0.5F) * 0.5F;
                    float f3 = (rand.nextFloat() - 0.5F) * 0.5F;
                    float headPosX = f1 + (float) (this.segments[0].posX + 0.3F * getSeaSerpentScale() * Math.cos((rotationYaw + 90) * Math.PI / 180));
                    float headPosZ = f2 + (float) (this.segments[0].posZ + 0.3F * getSeaSerpentScale() * Math.sin((rotationYaw + 90) * Math.PI / 180));
                    float headPosY = f3 + (float) (this.segments[0].posY + 0.2F * getSeaSerpentScale());
                    double d2 = entity.posX - headPosX;
                    double d3 = entity.posY - headPosY;
                    double d4 = entity.posZ - headPosZ;
                      this.playSound(ModSounds.ICEDRAGON_BREATH, 4, 1);
                    EntitySeaSerpentBubbles entitylargefireball = new EntitySeaSerpentBubbles(world, this, d2, d3, d4);
                    float size = 0.8F;
                    entitylargefireball.setPosition(headPosX, headPosY, headPosZ);
                    if (!world.isRemote && !entity.isDead) {
                        world.spawnEntity(entitylargefireball);
                    }
                    entitylargefireball.setSizes(size, size);
                    if (entity.isDead || entity == null) {
                        this.setBreathing(false);
                        this.attackDecision = this.getRNG().nextBoolean();
                    }
                }
            } else {
                this.setBreathing(true);
            }
        }
        this.faceEntity(entity, 360, 360);
    }

    enum SwimBehavior {
        CIRCLE,
        WANDER,
        ATTACK,
        JUMP,
        NONE;
    }

    public class SwimmingMoveHelper extends EntityMoveHelper {
        private EntitySeaSerpent serpent = EntitySeaSerpent.this;

        public SwimmingMoveHelper() {
            super(EntitySeaSerpent.this);
        }

        @Override
        public void onUpdateMoveHelper() {

            if (this.action == EntityMoveHelper.Action.MOVE_TO) {
                double d0 = this.posX - EntitySeaSerpent.this.posX;
                double d1 = this.posY - EntitySeaSerpent.this.posY;
                double d2 = this.posZ - EntitySeaSerpent.this.posZ;
                double d3 = d0 * d0 + d1 * d1 + d2 * d2;
                d3 = (double) MathHelper.sqrt(d3);
                if (d3 < 6 && EntitySeaSerpent.this.getAttackTarget() == null) {
                    if (!EntitySeaSerpent.this.changedSwimBehavior && EntitySeaSerpent.this.swimBehavior == EntitySeaSerpent.SwimBehavior.WANDER && EntitySeaSerpent.this.rand.nextInt(20) == 0) {
                        EntitySeaSerpent.this.swimBehavior = EntitySeaSerpent.SwimBehavior.CIRCLE;
                        EntitySeaSerpent.this.changedSwimBehavior = true;
                    }
                    if (!EntitySeaSerpent.this.changedSwimBehavior && EntitySeaSerpent.this.swimBehavior == EntitySeaSerpent.SwimBehavior.CIRCLE && EntitySeaSerpent.this.rand.nextInt(5) == 0 && ticksCircling > 150) {
                        EntitySeaSerpent.this.swimBehavior = EntitySeaSerpent.SwimBehavior.WANDER;
                        EntitySeaSerpent.this.changedSwimBehavior = true;
                    }
                }
                if (d3 < 3 && EntitySeaSerpent.this.getAttackTarget() == null || EntitySeaSerpent.this.swimBehavior == SwimBehavior.JUMP && EntitySeaSerpent.this.shouldStopJumping()) {
                    if (EntitySeaSerpent.this.swimBehavior == SwimBehavior.JUMP) {
                        EntitySeaSerpent.this.swimBehavior = SwimBehavior.WANDER;
                    }
                    this.action = EntityMoveHelper.Action.WAIT;
                    EntitySeaSerpent.this.motionX *= 0.5D;
                    // EntitySeaSerpent.this.motionY *= 0.5D;
                    EntitySeaSerpent.this.motionZ *= 0.5D;
                } else {
                    EntitySeaSerpent.this.motionX += d0 / d3 * 0.5D * this.speed;
                    EntitySeaSerpent.this.motionY += d1 / d3 * 0.5D * this.speed;
                    EntitySeaSerpent.this.motionZ += d2 / d3 * 0.5D * this.speed;
                    float f1 = (float) (-(MathHelper.atan2(d1, d3) * (180D / Math.PI)));
                    this.entity.rotationPitch = f1;
                    if (!EntitySeaSerpent.this.isArcing) {
                        if (EntitySeaSerpent.this.getAttackTarget() == null) {
                            EntitySeaSerpent.this.rotationYaw = -((float) MathHelper.atan2(EntitySeaSerpent.this.motionX, EntitySeaSerpent.this.motionZ)) * (180F / (float) Math.PI);
                            EntitySeaSerpent.this.renderYawOffset = EntitySeaSerpent.this.rotationYaw;
                        } else if (EntitySeaSerpent.this.swimBehavior != SwimBehavior.JUMP) {
                            double d4 = EntitySeaSerpent.this.getAttackTarget().posX - EntitySeaSerpent.this.posX;
                            double d5 = EntitySeaSerpent.this.getAttackTarget().posZ - EntitySeaSerpent.this.posZ;
                            EntitySeaSerpent.this.rotationYaw = -((float) MathHelper.atan2(d4, d5)) * (180F / (float) Math.PI);
                            EntitySeaSerpent.this.renderYawOffset = EntitySeaSerpent.this.rotationYaw;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onKillEntity(EntityLivingBase entity) {
        super.onKillEntity(entity);
        this.attackDecision = this.getRNG().nextBoolean();
    }

    public class AISwimWander extends EntityAIBase {
        BlockPos target;

        public AISwimWander() {
            this.setMutexBits(1);
        }

        public boolean shouldExecute() {
            if (EntitySeaSerpent.this.swimBehavior != EntitySeaSerpent.SwimBehavior.WANDER && EntitySeaSerpent.this.swimBehavior != EntitySeaSerpent.SwimBehavior.JUMP || !EntitySeaSerpent.this.canMove()) {
                return false;
            }
            if (EntitySeaSerpent.this.isInWater()) {
                target = EntitySeaSerpent.getPositionRelativeToSeafloor(EntitySeaSerpent.this, EntitySeaSerpent.this.world, EntitySeaSerpent.this.posX + EntitySeaSerpent.this.rand.nextInt(30) - 15, EntitySeaSerpent.this.posZ + EntitySeaSerpent.this.rand.nextInt(30) - 15, EntitySeaSerpent.this.rand);
                EntitySeaSerpent.this.orbitPos = null;
                return !EntitySeaSerpent.this.getMoveHelper().isUpdating();
            } else {
                return false;
            }
        }

        protected boolean isDirectPathBetweenPoints(BlockPos posVec31, BlockPos posVec32) {
            return true;
            //RayTraceResult raytraceresult = EntitySeaSerpent.this.world.rayTraceBlocks(new Vec3d(posVec31.getX() + 0.5D, posVec31.getY() + 0.5D, posVec31.getZ() + 0.5D), new Vec3d(posVec32.getX() + 0.5D, posVec32.getY() + (double) EntitySeaSerpent.this.height * 0.5D, posVec32.getZ() + 0.5D), false, true, false);
            //return raytraceresult == null || raytraceresult.typeOfHit == RayTraceResult.Type.MISS;
        }

        public boolean shouldContinueExecuting() {
            return false;
        }

        public void updateTask() {
            target = EntitySeaSerpent.getPositionRelativeToSeafloor(EntitySeaSerpent.this, EntitySeaSerpent.this.world, EntitySeaSerpent.this.posX + EntitySeaSerpent.this.rand.nextInt(30) - 15, EntitySeaSerpent.this.posZ + EntitySeaSerpent.this.rand.nextInt(30) - 15, EntitySeaSerpent.this.rand);

            if (EntitySeaSerpent.isWaterBlock(world, target) || EntitySeaSerpent.this.swimBehavior == EntitySeaSerpent.SwimBehavior.JUMP) {
                EntitySeaSerpent.this.moveHelper.setMoveTo((double) target.getX() + 0.5D, (double) target.getY() + 0.5D, (double) target.getZ() + 0.5D, 0.25D);
                if (EntitySeaSerpent.this.getAttackTarget() == null) {
                    EntitySeaSerpent.this.getLookHelper().setLookPosition((double) target.getX() + 0.5D, (double) target.getY() + 0.5D, (double) target.getZ() + 0.5D, 180.0F, 20.0F);

                }
            }
        }
    }

    class AISwimCircle extends EntityAIBase {
        BlockPos target;

        public AISwimCircle() {
            this.setMutexBits(1);
        }

        public boolean shouldExecute() {
            if (EntitySeaSerpent.this.swimBehavior != EntitySeaSerpent.SwimBehavior.CIRCLE || !EntitySeaSerpent.this.canMove()) {
                return false;
            }
            if (EntitySeaSerpent.this.isInWater() && !EntitySeaSerpent.this.isJumpingOutOfWater()) {
                EntitySeaSerpent.this.orbitPos = EntitySeaSerpent.getPositionRelativeToSeafloor(EntitySeaSerpent.this, EntitySeaSerpent.this.world, EntitySeaSerpent.this.posX + EntitySeaSerpent.this.rand.nextInt(30) - 15, EntitySeaSerpent.this.posZ + EntitySeaSerpent.this.rand.nextInt(30) - 15, EntitySeaSerpent.this.rand);
                target = EntitySeaSerpent.getPositionInOrbit(EntitySeaSerpent.this, world, EntitySeaSerpent.this.orbitPos, EntitySeaSerpent.this.rand);
                return true;
            } else {
                return false;
            }
        }

        protected boolean isDirectPathBetweenPoints(BlockPos posVec31, BlockPos posVec32) {
            RayTraceResult raytraceresult = EntitySeaSerpent.this.world.rayTraceBlocks(new Vec3d(posVec31.getX() + 0.5D, posVec31.getY() + 0.5D, posVec31.getZ() + 0.5D), new Vec3d(posVec32.getX() + 0.5D, posVec32.getY() + (double) EntitySeaSerpent.this.height * 0.5D, posVec32.getZ() + 0.5D), false, true, false);
            return raytraceresult == null || raytraceresult.typeOfHit == RayTraceResult.Type.MISS;
        }

        public boolean shouldContinueExecuting() {
            return EntitySeaSerpent.this.getAttackTarget() == null && EntitySeaSerpent.this.swimBehavior == EntitySeaSerpent.SwimBehavior.CIRCLE;
        }

        public void updateTask() {
            if (EntitySeaSerpent.this.getDistance(target.getX(), target.getY(), target.getZ()) < 5) {
                target = EntitySeaSerpent.getPositionInOrbit(EntitySeaSerpent.this, world, EntitySeaSerpent.this.orbitPos, EntitySeaSerpent.this.rand);
            }
            if (EntitySeaSerpent.isWaterBlock(world, target)) {
                EntitySeaSerpent.this.moveHelper.setMoveTo((double) target.getX() + 0.5D, (double) target.getY() + 0.5D, (double) target.getZ() + 0.5D, 0.25D);
                if (EntitySeaSerpent.this.getAttackTarget() == null) {
                    EntitySeaSerpent.this.getLookHelper().setLookPosition((double) target.getX() + 0.5D, (double) target.getY() + 0.5D, (double) target.getZ() + 0.5D, 180.0F, 20.0F);

                }
            }
        }
    }

    public class AISwimBite extends EntityAIBase {
        BlockPos target;
        boolean secondPhase = false;
        boolean isOver = false;
        public int max_distance = 1000;

        public AISwimBite() {
            this.setMutexBits(1);
        }

        public void resetTask() {
            target = null;
            secondPhase = false;
            isOver = false;
            EntitySeaSerpent.this.isArcing = false;
            EntitySeaSerpent.this.arcingYAdditive = 0;
        }

        public boolean shouldExecute() {
            if (EntitySeaSerpent.this.swimBehavior != SwimBehavior.ATTACK && EntitySeaSerpent.this.swimBehavior != SwimBehavior.JUMP || !EntitySeaSerpent.this.canMove() || EntitySeaSerpent.this.getAttackTarget() == null) {
                return false;
            }
            if (EntitySeaSerpent.this.isInWater() && EntitySeaSerpent.this.getAttackTarget() != null) {
                target = new BlockPos(EntitySeaSerpent.this.getAttackTarget());
                EntitySeaSerpent.this.orbitPos = null;
                secondPhase = false;
                EntitySeaSerpent.this.isArcing = false;
                return true;
            } else {
                return false;
            }
        }

        protected boolean isDirectPathBetweenPoints(BlockPos posVec31, BlockPos posVec32) {
            return true;
        }

        public boolean shouldContinueExecuting() {
            if (secondPhase) {
                if (EntitySeaSerpent.this.getAttackTarget() == null || EntitySeaSerpent.this.getDistanceSq(target) > max_distance || isOver) {
                    EntitySeaSerpent.this.swimBehavior = SwimBehavior.WANDER;
                    resetTask();
                    return false;
                } else {
                    return true;
                }
            } else {
                return EntitySeaSerpent.this.getAttackTarget() != null;
            }
        }

        public void updateTask() {
            if (EntitySeaSerpent.this.swimBehavior == SwimBehavior.JUMP) {
                if (EntitySeaSerpent.this.getAttackTarget() != null) {
                    if (EntitySeaSerpent.this.isInWater()) {
                        target = new BlockPos(EntitySeaSerpent.this.getAttackTarget()).up((int) Math.ceil(3 * EntitySeaSerpent.this.getSeaSerpentScale()));
                    }
                    if (EntitySeaSerpent.this.posY > EntitySeaSerpent.this.getAttackTarget().posY + 2 * EntitySeaSerpent.this.getSeaSerpentScale()) {
                        target = new BlockPos(EntitySeaSerpent.this.getAttackTarget());
                    }
                }
            } else {
                if (EntitySeaSerpent.this.getAttackTarget() == null || EntitySeaSerpent.this.getAttackTarget().isDead) {
                    this.secondPhase = true;
                } else {
                    double d0 = EntitySeaSerpent.this.getAttackTarget().posX - EntitySeaSerpent.this.posX;
                    double d1 = EntitySeaSerpent.this.getAttackTarget().posZ - EntitySeaSerpent.this.posZ;
                    double d2 = d0 * d0 + d1 * d1;
                    d2 = (double) MathHelper.sqrt(d2);
                    EntitySeaSerpent.this.arcingYAdditive = (secondPhase ? 1 : -1) * (float) d2;
                }
                if (!secondPhase) {
                    target = new BlockPos(EntitySeaSerpent.this.getAttackTarget());
                    if (!EntitySeaSerpent.this.attackDecision) {
                        if (EntitySeaSerpent.this.getAttackTarget() != null) {
                            if (EntitySeaSerpent.this.getDistanceSq(target) > 10 * EntitySeaSerpent.this.getSeaSerpentScale()) {
                                EntitySeaSerpent.this.setBreathing(true);
                            } else {
                                EntitySeaSerpent.this.attackDecision = true;
                            }
                        }
                    } else {
                        if (EntitySeaSerpent.this.getAttackTarget() != null && EntitySeaSerpent.this.getDistanceSq(target) < 50 * EntitySeaSerpent.this.getSeaSerpentScale()) {
                            EntitySeaSerpent.this.setAnimation(ANIMATION_BITE);
                            if (EntitySeaSerpent.this.getDistanceSq(target) < 20 * EntitySeaSerpent.this.getSeaSerpentScale() && EntitySeaSerpent.this.getAnimation() == ANIMATION_BITE) {
                                EntitySeaSerpent.this.hurtMob(EntitySeaSerpent.this.getAttackTarget());
                            }
                        }
                        if (EntitySeaSerpent.this.getAttackTarget() == null || EntitySeaSerpent.this.getDistanceSq(target) < 30 * EntitySeaSerpent.this.getSeaSerpentScale()) {
                            target = null;
                            secondPhase = true;
                        }
                    }
                }
                if (secondPhase) {
                    if (EntitySeaSerpent.this.getAttackTarget() != null) {
                        if (EntitySeaSerpent.this.getDistanceSq(EntitySeaSerpent.this.getAttackTarget()) > max_distance) {
                            isOver = true;
                            target = EntitySeaSerpent.this.getPosition();
                        } else if (target == null) {
                            target = EntitySeaSerpent.getPositionPreyArc(EntitySeaSerpent.this, new BlockPos(EntitySeaSerpent.this.getAttackTarget()), world);
                        }
                    }
                }
            }

            if (target != null) {
                if (EntitySeaSerpent.isWaterBlock(world, target) || EntitySeaSerpent.this.swimBehavior == SwimBehavior.JUMP) {
                    EntitySeaSerpent.this.moveHelper.setMoveTo((double) target.getX() + 0.5D, (double) target.getY() + 0.5D, (double) target.getZ() + 0.5D, 0.25D);
                    if (EntitySeaSerpent.this.getAttackTarget() == null) {
                        EntitySeaSerpent.this.getLookHelper().setLookPosition((double) target.getX() + 0.5D, (double) target.getY() + 0.5D, (double) target.getZ() + 0.5D, 180.0F, 20.0F);

                    }
                }
            }

        }
    }
}
