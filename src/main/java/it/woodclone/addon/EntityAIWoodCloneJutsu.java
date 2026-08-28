package it.woodclone.addon;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.narutomod.entity.EntityWoodArm;
import net.narutomod.entity.EntityWoodBurial;
import net.narutomod.item.ItemJutsu;

public class EntityAIWoodCloneJutsu extends EntityAIBase { // AI per il Wood Clone
    private final EntityWoodClone clone;
    private EntityLivingBase target;
    private int woodArmCooldown;
    private int woodBurialCooldown;
    private int meleeJutsuCooldown;
    private int jumpCooldown;

    public EntityAIWoodCloneJutsu(EntityWoodClone clone) { // Inizializza l'AI con il Wood Clone
        this.clone = clone;
        this.setMutexBits(0);
    }

    @Override
    public boolean shouldExecute() { 
        EntityLivingBase owner = this.clone.getSummoner();
        EntityLivingBase attackTarget = this.clone.getAttackTarget();

        if (attackTarget != null && attackTarget.equals(owner)) {
            this.clone.setAttackTarget(null);
            attackTarget = null;
        }

        if (attackTarget == null || !attackTarget.isEntityAlive()) {
            EntityLivingBase rev = this.clone.getRevengeTarget();
            if (rev != null && !rev.equals(owner)) {
                attackTarget = rev;
            } else if (rev != null) {
                this.clone.setRevengeTarget(null);
            }
        }

        if ((attackTarget == null || !attackTarget.isEntityAlive()) && owner != null) {
            EntityLivingBase ownerTarget = owner.getLastAttackedEntity();
            if (ownerTarget != null && !ownerTarget.equals(this.clone) && !ownerTarget.equals(owner)) {
                attackTarget = ownerTarget;
            }
        }

        if (attackTarget == null || !attackTarget.isEntityAlive() || attackTarget.equals(owner)) {
            return false;
        }

        this.target = attackTarget;
        return true;
    }

    @Override
    public boolean shouldContinueExecuting() { // Controlla se l'AI deve continuare a eseguire
        EntityLivingBase owner = this.clone.getSummoner();
        if (this.target == null || !this.target.isEntityAlive() || this.target.equals(owner)) {
            return false;
        }
        return this.clone.getDistanceSq(this.target) < 1024.0D; // 32 blocchi lineari
    }

    @Override
    public void resetTask() {
        this.target = null;
    }

    @Override
    public void updateTask() { // Aggiorna l'AI del Wood Clone
        if (this.target == null || !this.target.isEntityAlive()) return;

        if (this.woodArmCooldown > 0) this.woodArmCooldown--;
        if (this.woodBurialCooldown > 0) this.woodBurialCooldown--;
        if (this.meleeJutsuCooldown > 0) this.meleeJutsuCooldown--;
        if (this.jumpCooldown > 0) this.jumpCooldown--;

        double distanceSq = this.clone.getDistanceSq(this.target);
        double heightDiff = this.target.posY - this.clone.posY;
        boolean canSee = this.clone.getEntitySenses().canSee(this.target);

        // Esegue un salto speciale se il bersaglio è più alto di 2,5 blocchi
        if (heightDiff >= 2.5D && distanceSq <= 196.0D && this.jumpCooldown <= 0 && this.clone.onGround) {
            this.executeSuperJump(this.target);
            this.jumpCooldown = 60;
        }

        // Esegue un attacco corpo a corpo se il bersaglio è entro 4 blocchi
        if (distanceSq <= 16.0D && canSee && this.meleeJutsuCooldown <= 0) {
            this.executeMeleeJutsu(this.target);
            this.meleeJutsuCooldown = 60;
            return;
        }

        // Esegue il Wood Burial se il bersaglio è tra 13 e 24 blocchi di distanza
        if (distanceSq >= 169.0D && distanceSq <= 576.0D && canSee && this.woodBurialCooldown <= 0) {
            this.castWoodBurial(this.target);
            this.woodBurialCooldown = 160;
            return;
        }

        // Esegue il Wood Arm se il bersaglio è tra 5 e 12 blocchi di distanza
        if (distanceSq >= 25.0D && distanceSq <= 144.0D && canSee && this.woodArmCooldown <= 0) {
            this.castWoodArm(this.target);
            this.woodArmCooldown = 200;
        }
    }

    private void executeSuperJump(EntityLivingBase target) {
        Vec3d dir = new Vec3d(target.posX - this.clone.posX, 0.0D, target.posZ - this.clone.posZ).normalize();
        this.clone.motionX = dir.x * 0.7D;
        this.clone.motionY = Math.min(1.2D, 0.55D + (target.posY - this.clone.posY) * 0.1D);
        this.clone.motionZ = dir.z * 0.7D;
        this.clone.velocityChanged = true;

        this.clone.world.playSound(null, this.clone.posX, this.clone.posY, this.clone.posZ, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.3F, 2.0F);
    }

    private void executeMeleeJutsu(EntityLivingBase target) {
        if (!this.clone.world.isRemote) {
            Vec3d dir = new Vec3d(target.posX - this.clone.posX, target.posY - this.clone.posY, target.posZ - this.clone.posZ).normalize();
            this.clone.motionX += dir.x * 0.45D;
            this.clone.motionY += 0.12D;
            this.clone.motionZ += dir.z * 0.45D;
            this.clone.velocityChanged = true;

            float realDamage = (float) this.clone.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
            DamageSource ds = ItemJutsu.causeJutsuDamage(this.clone, this.clone.getSummoner() != null ? this.clone.getSummoner() : this.clone);
            target.attackEntityFrom(ds, realDamage);

            float yawRad = this.clone.rotationYaw * 0.017453292F;
            target.knockBack(this.clone, 1.2F, (double) MathHelper.sin(yawRad), (double) (-MathHelper.cos(yawRad)));

            this.clone.world.playSound(null, target.posX, target.posY, target.posZ, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.4F, 1.8F);
            this.clone.world.playSound(null, target.posX, target.posY, target.posZ, SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.PLAYERS, 1.0F, 0.7F);
        } else {
            for (int i = 0; i < 12; ++i) {
                double px = target.posX + (this.clone.world.rand.nextDouble() - 0.5D) * 0.8D;
                double py = target.posY + target.height / 2.0D + (this.clone.world.rand.nextDouble() - 0.5D) * 0.8D;
                double pz = target.posZ + (this.clone.world.rand.nextDouble() - 0.5D) * 0.8D;
                this.clone.world.spawnParticle(EnumParticleTypes.CRIT_MAGIC, px, py, pz, 0.0D, 0.1D, 0.0D);
                this.clone.world.spawnParticle(EnumParticleTypes.BLOCK_CRACK, px, py, pz, 0.0D, 0.1D, 0.0D, 17);
            }
        }
    }

    private void castWoodArm(EntityLivingBase target) {
        if (!this.clone.world.isRemote) {
            EntityWoodArm.EC woodArm = new EntityWoodArm.EC(this.clone, target);
            this.clone.world.spawnEntity(woodArm);
            this.clone.world.playSound(null, this.clone.posX, this.clone.posY, this.clone.posZ, SoundEvents.BLOCK_WOOD_STEP, SoundCategory.PLAYERS, 1.0F, 0.7F);
        }
    }

    private void castWoodBurial(EntityLivingBase target) {
        if (!this.clone.world.isRemote) {
            EntityWoodBurial.EC woodBurial = new EntityWoodBurial.EC(target);
            this.clone.world.spawnEntity(woodBurial);
            this.clone.world.playSound(null, target.posX, target.posY, target.posZ, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.PLAYERS, 1.0F, 0.6F);
        }
    }
}