package it.woodclone.addon;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.narutomod.entity.EntityClone;
import net.narutomod.procedure.ProcedureUtils;

public class EntityWoodClone extends EntityClone.Base { // Estende la classe base di EntityClone per aggiungere funzionalità specifiche del Wood Clone

    private static final DataParameter<Boolean> SENTINEL_MODE = EntityDataManager.createKey(EntityWoodClone.class, DataSerializers.BOOLEAN);
    private BlockPos guardPos;
    private int fireCooldownTicks = 0;

    // Costruttore di default
    public EntityWoodClone(World world) {
        super(world);
        // Imposta l'altezza del passo per consentire al Wood Clone di salire su blocchi più alti
        this.stepHeight = 3.0F;
    }

    // Costruttore che accetta il summoner come parametro
    public EntityWoodClone(EntityLivingBase summonerIn) {
        super(summonerIn);
        this.stepHeight = 3.0F;
        this.guardPos = new BlockPos(this);
        this.applyWoodCloneScaling(summonerIn);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(SENTINEL_MODE, false);
    }

    // Metodo per verificare se il Wood Clone è in modalità sentinella
    public boolean isSentinelMode() {
        return this.dataManager.get(SENTINEL_MODE);
    }

    public void setSentinelMode(boolean sentinel) {
        this.dataManager.set(SENTINEL_MODE, sentinel);
        if (sentinel) {
            this.guardPos = new BlockPos(this);
            this.getNavigator().clearPath();
        }
    }

    @Override
    protected void initEntityAI() {
        super.initEntityAI();

        // Rimuove istanze preesistenti di follow ereditate
        this.tasks.taskEntries.removeIf(entry -> entry.action instanceof EntityClone.AIFollowSummoner);

        // 1. Combattimento Jutsu (interviene se ha un bersaglio valido)
        this.tasks.addTask(1, new EntityAIWoodCloneJutsu(this));

        // 2. Scorta Padrone (prioritario quando non combatte)
        this.tasks.addTask(2, new EntityAIFollowCustom(this, 1.35D, 3.0F));

        // 3. Pattugliamento libero (solo quando in modalità Sentinella)
        this.tasks.addTask(3, new EntityAIWanderAvoidWater(this, 0.8D) {
            @Override
            public boolean shouldExecute() {
                return EntityWoodClone.this.isSentinelMode() && super.shouldExecute();
            }
        });

        // 4. Guarda il giocatore più vicino entro 6 blocchi
        this.tasks.addTask(4, new EntityAIWatchClosest(this, EntityPlayer.class, 6.0F));
        // 5. Guarda intorno quando non ha un bersaglio
        this.tasks.addTask(5, new EntityAILookIdle(this));

        // Reazione difensiva: risponde se viene colpito
        this.targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));

        // ATTENZIONE: Cerca nemici da solo SOLO ed ESCLUSIVAMENTE in modalità Sentinella
        this.targetTasks.addTask(2, new EntityAINearestAttackableTarget<>(this, EntityLivingBase.class, 10, true, false,
            target -> EntityWoodClone.this.isSentinelMode() 
                      && target instanceof IMob 
                      && !target.equals(EntityWoodClone.this.getSummoner())
        ));
    }

    // Task di scorta fedele con Teletrasporto di emergenza (35+ blocchi o 6s bloccato)
    private static class EntityAIFollowCustom extends EntityClone.AIFollowSummoner {
        private final EntityWoodClone woodClone;
        private int updateDelay;
        private int stuckTicks;

        public EntityAIFollowCustom(EntityWoodClone clone, double speed, float minDist) {
            super(clone, speed, minDist);
            this.woodClone = clone;
        }

        @Override
        public boolean shouldExecute() {
            if (this.woodClone.isSentinelMode()) {
                return false;
            }
            EntityLivingBase summoner = this.woodClone.getSummoner();
            if (summoner == null || !summoner.isEntityAlive()) {
                return false;
            }
            return this.woodClone.getDistanceSq(summoner) > 9.0D;
        }

        @Override
        public boolean shouldContinueExecuting() {
            if (this.woodClone.isSentinelMode()) {
                return false;
            }
            EntityLivingBase summoner = this.woodClone.getSummoner();
            return summoner != null && summoner.isEntityAlive() && this.woodClone.getDistanceSq(summoner) > 4.0D;
        }

        @Override
        public void updateTask() {
            EntityLivingBase summoner = this.woodClone.getSummoner();
            if (summoner == null) return;

            this.woodClone.getLookHelper().setLookPositionWithEntity(summoner, 10.0F, (float) this.woodClone.getVerticalFaceSpeed());

            double distSq = this.woodClone.getDistanceSq(summoner);

            // Teletrasporto solo se supera 35 blocchi (1225.0D) o resta incastrato senza percorso per 6 secondi (120 tick)
            if (distSq > 1225.0D || (distSq > 64.0D && this.woodClone.getNavigator().noPath() && ++this.stuckTicks > 120)) {
                this.teleportToSummoner(summoner);
                this.stuckTicks = 0;
                return;
            } else if (!this.woodClone.getNavigator().noPath()) {
                this.stuckTicks = 0;
            }

            if (--this.updateDelay <= 0) {
                this.updateDelay = 10;
                this.woodClone.getNavigator().tryMoveToEntityLiving(summoner, 1.35D);
            }
        }

        private void teleportToSummoner(EntityLivingBase summoner) {
            double targetX = summoner.posX + (this.woodClone.getRNG().nextDouble() - 0.5D) * 2.0D;
            double targetY = summoner.posY;
            double targetZ = summoner.posZ + (this.woodClone.getRNG().nextDouble() - 0.5D) * 2.0D;

            this.spawnWoodPoof(this.woodClone.posX, this.woodClone.posY, this.woodClone.posZ);
            this.woodClone.setPositionAndUpdate(targetX, targetY, targetZ);
            this.woodClone.getNavigator().clearPath();
            this.spawnWoodPoof(targetX, targetY, targetZ);
        }

        private void spawnWoodPoof(double x, double y, double z) {
            // Suono poof AHZNB con fallback
            net.minecraft.util.SoundEvent poofSound = net.minecraft.util.SoundEvent.REGISTRY.getObject(new net.minecraft.util.ResourceLocation("narutomod:poof"));
            if (poofSound != null) {
                this.woodClone.world.playSound(null, x, y, z, poofSound, SoundCategory.PLAYERS, 1.0F, 1.0F);
            } else {
                this.woodClone.world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.4F, 1.8F);
            }

            // Suoni legnosi e fruscio
            this.woodClone.world.playSound(null, x, y, z, SoundEvents.BLOCK_GRASS_PLACE, SoundCategory.PLAYERS, 1.0F, 0.8F);
            this.woodClone.world.playSound(null, x, y, z, SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.PLAYERS, 0.6F, 1.4F);

            // Nube fumo anime + frammenti di foglie e legno
            if (this.woodClone.world instanceof net.minecraft.world.WorldServer) {
                net.minecraft.world.WorldServer ws = (net.minecraft.world.WorldServer) this.woodClone.world;
                ws.spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL, x, y + 0.8D, z, 14, 0.4D, 0.6D, 0.4D, 0.05D);
                ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, x, y + 0.8D, z, 8, 0.3D, 0.5D, 0.3D, 0.02D);
                ws.spawnParticle(EnumParticleTypes.BLOCK_CRACK, x, y + 1.0D, z, 15, 0.3D, 0.5D, 0.3D, 0.05D, 18);
                ws.spawnParticle(EnumParticleTypes.BLOCK_CRACK, x, y + 0.5D, z, 10, 0.2D, 0.3D, 0.2D, 0.05D, 17);
            }
        }
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(80.0D);
        this.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(10.0D);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.48D);
        this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(36.0D);
    }

    // Applica le modifiche agli attributi del Wood Clone in base agli attributi del summoner
    public void applyWoodCloneScaling(EntityLivingBase summoner) {
        if (summoner == null) return;

        double realDamage = ProcedureUtils.getModifiedAttackDamage(summoner);
        if (realDamage < 8.0D) {
            realDamage = 8.0D;
        }
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(realDamage);

        double maxHp = Math.max(80.0D, summoner.getMaxHealth() * 0.80D);
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(maxHp);
        this.setHealth((float) maxHp);

        double totalArmor = (summoner.getTotalArmorValue() * 0.60D) + 6.0D;
        this.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(totalArmor);

        if (this.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE) != null) {
            this.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(0.6D);
        }
    }

    @Override
    public boolean processInteract(EntityPlayer player, EnumHand hand) {
        if (player.equals(this.getSummoner()) && player.isSneaking() && hand == EnumHand.MAIN_HAND) {
            if (!this.world.isRemote) {
                boolean newMode = !this.isSentinelMode();
                this.setSentinelMode(newMode);

                if (newMode) {
                    player.sendStatusMessage(new TextComponentString(TextFormatting.GREEN + "Wood Clone: Sentinel Mode activated."), true);
                    this.playSound(SoundEvents.BLOCK_WOOD_PLACE, 1.0F, 1.2F);
                } else {
                    player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA + "Wood Clone: Follow Mode activated."), true);
                    this.playSound(SoundEvents.BLOCK_WOOD_STEP, 1.0F, 1.0F);
                }
            }
            return true;
        }
        return super.processInteract(player, hand);
    }

    // Override per consentire al Wood Clone di arrampicarsi su blocchi più alti
    @Override
    public boolean isOnLadder() {
        return this.collidedHorizontally || super.isOnLadder();
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        boolean isOwner = (source.getTrueSource() != null && source.getTrueSource().equals(this.getSummoner()));

        if (source.isFireDamage()) {
            amount *= 1.5F;
            this.fireCooldownTicks = 120;
        }

        boolean result = super.attackEntityFrom(source, amount);

        if (isOwner) {
            this.setRevengeTarget(null);
            this.setAttackTarget(null);
        }

        return result;
    }

    @Override
    public void setRevengeTarget(EntityLivingBase livingBase) {
        // Evita che il Wood Clone attacchi il suo summoner
        if (livingBase != null && livingBase.equals(this.getSummoner())) {
            return;
        }
        super.setRevengeTarget(livingBase);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SoundEvents.BLOCK_WOOD_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BLOCK_WOOD_BREAK;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();

        if (this.collidedHorizontally && this.onGround && !this.world.isRemote) {
            this.motionY = 0.60D;
            this.velocityChanged = true;
        }

        // Se è in Sentinella e non combatte, torna al punto di guardia
        if (!this.world.isRemote && this.isSentinelMode() && this.getAttackTarget() == null && this.guardPos != null) {
            if (this.getDistanceSqToCenter(this.guardPos) > 64.0D && this.getNavigator().noPath()) {
                this.getNavigator().tryMoveToXYZ(this.guardPos.getX() + 0.5D, this.guardPos.getY(), this.guardPos.getZ() + 0.5D, 1.0D);
            }
        }

        if (this.fireCooldownTicks > 0) {
            this.fireCooldownTicks--;
        }

        if (!this.world.isRemote && this.fireCooldownTicks <= 0 && this.ticksExisted % 60 == 0 && this.getHealth() < this.getMaxHealth()) {
            this.heal(1.0F);
        }
    }

    @Override
    public void onDeath(DamageSource cause) {
        super.onDeath(cause);
        if (this.world.isRemote) {
            for (int i = 0; i < 20; ++i) {
                double px = this.posX + (this.rand.nextDouble() - 0.5D) * this.width;
                double py = this.posY + this.rand.nextDouble() * this.height;
                double pz = this.posZ + (this.rand.nextDouble() - 0.5D) * this.width;
                this.world.spawnParticle(EnumParticleTypes.BLOCK_CRACK, px, py, pz, 0.0D, 0.1D, 0.0D, 17);
            }
        }
        this.world.playSound(null, this.posX, this.posY, this.posZ, SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }
}