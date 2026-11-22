package dev.js.lte;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import dev.js.lte.forge.platform.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.storage.LevelResource;

public final class VehicleLightPacks {
   public static final String CONFIG_DIR = "lte_lights";
   private static final int CONFIG_DEPTH = 3;
   private static final int DATAPACK_DEPTH = 9;
   private static String lastLogged = "";

   private VehicleLightPacks() {
   }

   public static void reloadClient(ResourceManager manager) {
      List<VehicleLightJson.Source> sources = new ArrayList();
      sources.addAll(configSources());
      if (manager != null) {
         try {
            for(String namespace : manager.getNamespaces()) {
               ResourceLocation id = new ResourceLocation(namespace, "lte_vehicle_lights.json");

               for(Resource resource : manager.getResourceStack(id)) {
                  String json = read(resource);
                  if (json != null) {
                     sources.add(new VehicleLightJson.Source(resource.sourcePackId(), namespace + "/lte_vehicle_lights.json", json));
                  }
               }
            }
         } catch (Throwable var8) {
            LteLog.LOGGER.warn("[lte] lights json: pack scan failed", var8);
         }
      }

      apply(sources);
   }

   public static void reloadServer(MinecraftServer server) {
      List<VehicleLightJson.Source> sources = new ArrayList(configSources());
      if (server != null) {
         try {
            Path datapacks = server.getWorldPath(LevelResource.DATAPACK_DIR);
            sources.addAll(VehicleLightJson.scan(datapacks, "datapacks", 9));
         } catch (Throwable var3) {
            LteLog.LOGGER.warn("[lte] lights json: datapack scan failed", var3);
         }
      }

      apply(sources);
   }

   public static List<VehicleLightJson.Source> configSources() {
      try {
         Path dir = FabricLoader.getInstance().getConfigDir().resolve("lte_lights");
         return VehicleLightJson.scan(dir, "config", 3);
      } catch (Throwable var1) {
         return new ArrayList();
      }
   }

   public static void apply(List<VehicleLightJson.Source> sources) {
      VehicleLightOverrides.load(sources);
      logOnce();
   }

   public static void reload() {
      VehicleLightOverrides.reload();
      apply(configSources());
   }

   private static synchronized void logOnce() {
      String line = VehicleLightOverrides.diagnosticLine();
      if (!line.equals(lastLogged)) {
         lastLogged = line;
         LteLog.LOGGER.info("[lte] {}", line);
      }
   }

   private static String read(Resource resource) {
      try {
         InputStream in = resource.open();

         String var2;
         try {
            var2 = new String(in.readAllBytes(), StandardCharsets.UTF_8);
         } catch (Throwable var5) {
            if (in != null) {
               try {
                  in.close();
               } catch (Throwable var4) {
                  var5.addSuppressed(var4);
               }
            }

            throw var5;
         }

         if (in != null) {
            in.close();
         }

         return var2;
      } catch (Throwable var6) {
         LteLog.LOGGER.warn("[lte] lights json: cannot read {} from {}", resource.sourcePackId(), "lte_vehicle_lights.json");
         return null;
      }
   }
}
