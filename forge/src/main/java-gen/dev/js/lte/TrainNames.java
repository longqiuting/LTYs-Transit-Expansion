package dev.js.lte;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import mtr.client.TrainClientRegistry;
import mtr.client.TrainProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

final class TrainNames {
   static final String RESOURCE_PATH = "mtr_custom_resources.json";
   static final String ID_PREFIX = "mtr_custom_train_";
   private static Map<String, String> raw;
   private static boolean attempted;

   private TrainNames() {
   }

   static synchronized void refresh() {
      raw = null;
      attempted = false;
   }

   static String of(String id) {
      if (id != null && !id.isBlank()) {
         try {
            TrainProperties properties = TrainClientRegistry.getTrainProperties(id);
            if (properties != null && properties.name != null) {
               String resolved = resolve(properties.name);
               if (useful(resolved)) {
                  return resolved;
               }
            }
         } catch (Throwable var3) {
         }

         String fromJson = (String)rawNames().get(id);
         if (useful(fromJson)) {
            return fromJson;
         } else {
            return id.startsWith("mtr_custom_train_") ? id.substring("mtr_custom_train_".length()) : id;
         }
      } else {
         return "";
      }
   }

   static String resolve(Object rawText) {
      if (rawText == null) {
         return null;
      } else {
         Component text;
         try {
            text = (Component)rawText;
         } catch (Throwable var8) {
            return null;
         }

         try {
            ComponentContents key = text.getContents();
            if (key instanceof TranslatableContents) {
               TranslatableContents content = (TranslatableContents)key;
               String keyLte = content.getKey();
               if (keyLte != null && !keyLte.isEmpty()) {
                  String translated = Language.getInstance().getOrDefault(keyLte);
                  if (translated != null && !translated.isEmpty() && !translated.equals(keyLte)) {
                     return translated;
                  }

                  String fallback = content.getFallback();
                  if (fallback != null && !fallback.isEmpty()) {
                     return fallback;
                  }

                  return keyLte;
               }
            }
         } catch (Throwable var7) {
         }

         try {
            return text.getString();
         } catch (Throwable var6) {
            return null;
         }
      }
   }

   static synchronized Map<String, String> rawNames() {
      if (attempted) {
         return raw == null ? Collections.emptyMap() : raw;
      } else {
         attempted = true;
         Map<String, String> out = new LinkedHashMap();

         try {
            Minecraft client = Minecraft.getInstance();
            ResourceManager manager = client == null ? null : client.getResourceManager();
            if (manager != null) {
               Optional<Resource> resource = manager.getResource(new ResourceLocation("mtr", "mtr_custom_resources.json"));
               if (resource.isPresent()) {
                  InputStream in = ((Resource)resource.get()).open();

                  String json;
                  try {
                     json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                  } catch (Throwable var9) {
                     if (in != null) {
                        try {
                           in.close();
                        } catch (Throwable var8) {
                           var9.addSuppressed(var8);
                        }
                     }

                     throw var9;
                  }

                  if (in != null) {
                     in.close();
                  }

                  collect(json, out);
               }
            }
         } catch (Throwable var10) {
            LteLog.LOGGER.warn("[lte] beam3d: cannot read {}", "mtr_custom_resources.json", var10);
         }

         raw = out;
         LteLog.LOGGER.info("[lte] beam3d: {} custom train name(s) from {}", out.size(), "mtr_custom_resources.json");
         return out;
      }
   }

   static void collect(String json, Map<String, String> out) {
      try {
         JsonElement root = JsonParser.parseString(json);
         if (root == null || !root.isJsonObject()) {
            return;
         }

         JsonElement trains = root.getAsJsonObject().get("custom_trains");
         if (trains == null || !trains.isJsonObject()) {
            return;
         }

         for(Map.Entry<String, JsonElement> entry : trains.getAsJsonObject().entrySet()) {
            String key = (String)entry.getKey();
            if (key != null && !key.isBlank() && entry.getValue() != null && ((JsonElement)entry.getValue()).isJsonObject()) {
               JsonObject train = ((JsonElement)entry.getValue()).getAsJsonObject();
               JsonElement name = train.get("name");
               String raw = name != null && name.isJsonPrimitive() ? name.getAsString() : key;
               String resolved = resolveRaw(raw);
               if (useful(resolved)) {
                  out.put("mtr_custom_train_" + key, resolved);
               }
            }
         }
      } catch (Throwable var11) {
         LteLog.LOGGER.warn("[lte] beam3d: cannot parse {}", "mtr_custom_resources.json", var11);
      }

   }

   static String resolveRaw(String name) {
      if (name != null && !name.isBlank()) {
         try {
            String translated = Language.getInstance().getOrDefault(name);
            if (translated != null && !translated.isEmpty() && !translated.equals(name)) {
               return translated;
            }
         } catch (Throwable var2) {
         }

         return name;
      } else {
         return null;
      }
   }

   private static boolean useful(String value) {
      return value != null && !value.isBlank();
   }
}
