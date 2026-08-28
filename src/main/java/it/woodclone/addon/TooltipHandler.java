package it.woodclone.addon;

import java.util.List;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class TooltipHandler {

    // Aggiunge un tooltip per il Wood Clone per indicare come cambiare la modalità da sentinella a guardia del corpo
    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (event.getItemStack() != null && !event.getItemStack().isEmpty()) {
            List<String> tooltip = event.getToolTip();
            for (int i = 0; i < tooltip.size(); i++) {
                String line = tooltip.get(i);
                if (line.contains("Wood Clone") && !line.contains("SHIFT+RMB")) {
                    tooltip.set(i, line + " \u00a78[SHIFT+RMB on clone: Switch Mode]");
                }
            }
        }
    }
}