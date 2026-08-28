package it.woodclone.addon;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.narutomod.Chakra;
import net.narutomod.PlayerTracker;
import net.narutomod.item.ItemJutsu;

// Classe per gestire l'evocazione del Wood Clone tramite jutsu
public class JutsuWoodClone implements ItemJutsu.IJutsuCallback { 

    // Implementazione del metodo per creare il jutsu del Wood Clone
    @Override
    public boolean createJutsu(ItemStack stack, EntityLivingBase entity, float power) {
        // Verifica se l'entità è un giocatore
        if (!(entity instanceof EntityPlayer)) {
            return false;
        }

        EntityPlayer player = (EntityPlayer) entity;

        // Calcolo dinamico: Base 150 + 10% del Chakra Massimo del player
        double maxChakra = Chakra.pathway(player).getMax();
        double dynamicChakraCost = 150.0D + (maxChakra * 0.10D);

        // Verifica se il giocatore ha abbastanza chakra per sostenere il costo dinamico
        if (Chakra.pathway(player).getAmount() < dynamicChakraCost) {
            if (!player.world.isRemote) {
                player.sendStatusMessage(
                    new TextComponentString(TextFormatting.RED + "Chakra insufficiente! Richiesti: " + (int) dynamicChakraCost), 
                    true
                );
            }
            return false;
        }

        // Evoca il Wood Clone solo se il mondo non è remoto (server-side)
        if (!player.world.isRemote) {
            // Drena la porzione extra di chakra dinamico oltre a quella base
            Chakra.pathway(player).consume(dynamicChakraCost);

            // Evoca il Clone di Legno
            EntityWoodClone clone = new EntityWoodClone(player);
            clone.setPosition(player.posX + player.getLookVec().x * 1.5D, player.posY, player.posZ + player.getLookVec().z * 1.5D);
            player.world.spawnEntity(clone);

            // Cooldown al jutsu (15 secondi = 300 tick)
            if (stack.getItem() instanceof ItemJutsu.Base) {
                ((ItemJutsu.Base) stack.getItem()).setCurrentJutsuCooldown(stack, 300L);
            }
        }

        return true;
    }
}