package dev.js.lte.signal;

import dev.js.lte.forge.platform.Reg;
import dev.js.lte.LteLog;
import dev.js.lte.TabIcons;
import dev.js.lte.bind.BeamEditorItem;
import dev.js.lte.bind.LteDashboardItem;
import dev.js.lte.bind.PidsConnectorItem;
import dev.js.lte.bind.ProjectorConnectorItem;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import dev.js.lte.forge.platform.FabricItemGroup;
import dev.js.lte.forge.platform.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class LteCoreItems {
   public static final ResourceLocation SIGNAL_LINKER_ID = new ResourceLocation("lte", "signal_linker");
   public static final ResourceLocation PROJECTOR_CONNECTOR_ID = new ResourceLocation("lte", "projector_connector");
   public static final ResourceLocation PIDS_CONNECTOR_ID = new ResourceLocation("lte", "pids_connector");
   public static final ResourceLocation BEAM_EDITOR_ID = new ResourceLocation("lte", "beam_editor");
   public static final ResourceLocation DASHBOARD_ID = new ResourceLocation("lte", "dashboard");
   public static final ResourceLocation TOOLS_TAB_ID = new ResourceLocation("lte", "tools");
   private static final List<ResourceLocation> TOOLS_TAB_ORDER = List.of(DASHBOARD_ID, BEAM_EDITOR_ID, SIGNAL_LINKER_ID, PROJECTOR_CONNECTOR_ID, PIDS_CONNECTOR_ID);
   private static SignalLinkItem signalLinker;
   private static ProjectorConnectorItem projectorConnector;
   private static PidsConnectorItem pidsConnector;
   private static BeamEditorItem beamEditor;
   private static LteDashboardItem dashboard;
   private static boolean registered;

   private LteCoreItems() {
   }

   public static SignalLinkItem signalLinker() {
      return signalLinker;
   }

   public static ProjectorConnectorItem projectorConnector() {
      return projectorConnector;
   }

   public static PidsConnectorItem pidsConnector() {
      return pidsConnector;
   }

   public static BeamEditorItem beamEditor() {
      return beamEditor;
   }

   public static LteDashboardItem dashboard() {
      return dashboard;
   }

   public static void register() {
      if (!registered) {
         registered = true;
         signalLinker = (SignalLinkItem)Reg.item( SIGNAL_LINKER_ID, new SignalLinkItem((new Item.Properties()).stacksTo(1)));
         projectorConnector = (ProjectorConnectorItem)Reg.item( PROJECTOR_CONNECTOR_ID, new ProjectorConnectorItem((new Item.Properties()).stacksTo(1)));
         pidsConnector = (PidsConnectorItem)Reg.item( PIDS_CONNECTOR_ID, new PidsConnectorItem((new Item.Properties()).stacksTo(1)));
         beamEditor = (BeamEditorItem)Reg.item( BEAM_EDITOR_ID, new BeamEditorItem((new Item.Properties()).stacksTo(1)));
         dashboard = (LteDashboardItem)Reg.item( DASHBOARD_ID, new LteDashboardItem((new Item.Properties()).stacksTo(1)));
         TabIcons.register();
         Reg.tab( TOOLS_TAB_ID, CreativeModeTab.builder().title(Component.translatable("itemGroup.lte.tools")).icon(() -> new ItemStack(TabIcons.tools())).build());
         ItemGroupEvents.modifyEntriesEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, TOOLS_TAB_ID)).register((ItemGroupEvents.ModifyEntries)(entries) -> {
            entries.add(dashboard);
            entries.add(signalLinker);
            entries.add(projectorConnector);
            entries.add(pidsConnector);
            entries.add(beamEditor);
         });
      }
   }

   public static void pinToolsTabOrder() {
      try {
         ItemGroupEvents.modifyEntriesEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, TOOLS_TAB_ID)).register((ItemGroupEvents.ModifyEntries)(entries) -> {
            List<ItemStack> stacks = entries.getDisplayStacks();
            List<ItemStack> front = new ArrayList(TOOLS_TAB_ORDER.size());

            for(ResourceLocation id : TOOLS_TAB_ORDER) {
               Item item = (Item)BuiltInRegistries.ITEM.get(id);
               if (item != null && item != Items.AIR) {
                  Iterator<ItemStack> it = stacks.iterator();

                  while(it.hasNext()) {
                     ItemStack stack = (ItemStack)it.next();
                     if (stack.is(item)) {
                        front.add(stack);
                        it.remove();
                     }
                  }
               }
            }

            if (!front.isEmpty()) {
               stacks.addAll(0, front);
            }

         });
      } catch (Throwable var1) {
         LteLog.LOGGER.warn("[lte] tools tab order: could not pin the LTE tools tab", var1);
      }

   }

   public static CreativeModeTab tab() {
      return (CreativeModeTab)BuiltInRegistries.CREATIVE_MODE_TAB.get(TOOLS_TAB_ID);
   }
}
