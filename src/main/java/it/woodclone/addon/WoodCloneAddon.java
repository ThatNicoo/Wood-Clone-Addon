package it.woodclone.addon;

import com.google.common.collect.ImmutableList;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.narutomod.entity.EntityClone;
import net.narutomod.item.ItemJutsu;
import net.narutomod.item.ItemMokuton;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = WoodCloneAddon.MODID,
    name = WoodCloneAddon.NAME,
    version = WoodCloneAddon.VERSION,
    dependencies = "required-after:narutomod"
)
@Mod.EventBusSubscriber
public class WoodCloneAddon {
    public static final String MODID = "woodcloneaddon";
    public static final String NAME = "AHZNB Wood Clone Addon";
    public static final String VERSION = "1.3";

    public static Logger logger;

    // JutsuEnum: Indice 5, Nome ID "wood_clone", Rango 'S', XP Richiesta 600, Costo Chakra 150.0D
    public static final ItemJutsu.JutsuEnum WOODCLONE = new ItemJutsu.JutsuEnum(
        5, 
        "wood_clone", 
        'S', 
        600, 
        150.0D, 
        new JutsuWoodClone()
    );

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        if (event.getSide() == Side.CLIENT) {
            registerEntityRenders();
        }
        logger.info("Pre-inizializzazione Wood Clone Addon completata.");
    }

    @SideOnly(Side.CLIENT)
    private void registerEntityRenders() {
        RenderingRegistry.registerEntityRenderingHandler(
            EntityWoodClone.class, 
            renderManager -> EntityClone.ClientRLM.getInstance().new RenderClone<>(renderManager)
        );
    }

    @SubscribeEvent
    public static void onRegisterEntities(RegistryEvent.Register<EntityEntry> event) {
        EntityEntry entry = EntityEntryBuilder.create()
            .entity(EntityWoodClone.class)
            .id(new ResourceLocation(MODID, "wood_clone"), 401)
            .name("wood_clone")
            .tracker(64, 3, true)
            .build();
        event.getRegistry().register(entry);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new TooltipHandler());
        logger.info("Wood Clone Addon inizializzato.");
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        try {
            if (ItemMokuton.block instanceof ItemJutsu.Base) {
                ItemJutsu.Base mokutonItem = (ItemJutsu.Base) ItemMokuton.block;

                // 1. Assegna il tipo MOKUTON a WOODCLONE
                Field typeField = ItemJutsu.JutsuEnum.class.getDeclaredField("type");
                typeField.setAccessible(true);
                typeField.set(WOODCLONE, ItemJutsu.JutsuEnum.Type.MOKUTON);

                // 2. Inietta WOODCLONE dentro jutsuList di ItemJutsu.Base
                Field jutsuListField = ItemJutsu.Base.class.getDeclaredField("jutsuList");
                jutsuListField.setAccessible(true);

                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(jutsuListField, jutsuListField.getModifiers() & ~Modifier.FINAL);

                @SuppressWarnings("unchecked")
                ImmutableList<ItemJutsu.JutsuEnum> oldList = (ImmutableList<ItemJutsu.JutsuEnum>) jutsuListField.get(mokutonItem);
                
                List<ItemJutsu.JutsuEnum> listCopy = new ArrayList<>(oldList);
                listCopy.add(WOODCLONE);
                jutsuListField.set(mokutonItem, ImmutableList.copyOf(listCopy));

                // 3. Estende defaultCooldownMap
                Field cdMapField = ItemJutsu.Base.class.getDeclaredField("defaultCooldownMap");
                cdMapField.setAccessible(true);
                long[] oldCdMap = (long[]) cdMapField.get(mokutonItem);
                long[] newCdMap = Arrays.copyOf(oldCdMap, oldCdMap.length + 1);
                newCdMap[newCdMap.length - 1] = 0L;
                cdMapField.set(mokutonItem, newCdMap);

                // 4. Estende jutsuXpMap
                Field xpMapField = ItemJutsu.Base.class.getDeclaredField("jutsuXpMap");
                xpMapField.setAccessible(true);
                int[] oldXpMap = (int[]) xpMapField.get(mokutonItem);
                int[] newXpMap = Arrays.copyOf(oldXpMap, oldXpMap.length + 1);
                newXpMap[newXpMap.length - 1] = 0;
                xpMapField.set(mokutonItem, newXpMap);

                logger.info("Wood Clone Jutsu aggiunto con successo al menu Mokuton di AHZNB (XP Richiesta: 1500)!");
            }
        } catch (Exception e) {
            logger.error("Errore durante l'iniezione del jutsu in ItemMokuton", e);
        }
    }
}