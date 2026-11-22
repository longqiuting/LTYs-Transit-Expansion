package dev.js.lte.server;

import dev.js.lte.HeadlightConfig;
import java.util.function.Supplier;

public final class ServerConfig {
   private static volatile Supplier<HeadlightConfig> supplier;
   private static final Supplier<HeadlightConfig> FALLBACK = HeadlightConfig::defaults;

   private ServerConfig() {
   }

   public static void install(Supplier<HeadlightConfig> provider) {
      if (provider != null) {
         supplier = provider;
      }

   }

   public static HeadlightConfig get() {
      Supplier<HeadlightConfig> s = supplier;
      HeadlightConfig cfg = s == null ? null : (HeadlightConfig)s.get();
      return cfg == null ? (HeadlightConfig)FALLBACK.get() : cfg;
   }
}
