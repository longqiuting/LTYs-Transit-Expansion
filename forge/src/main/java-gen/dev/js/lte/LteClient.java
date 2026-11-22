package dev.js.lte;

import dev.js.lte.collision.CollisionBootstrap;
import dev.js.lte.mtr.BuiltInTrainsInit;
import dev.js.lte.signal.SignalLinkClient;
import dev.js.lte.forge.platform.ClientModInitializer;

public final class LteClient implements ClientModInitializer {
   public void onInitializeClient() {
      (new MtrHeadlightClient()).onInitializeClient();
      (new CollisionBootstrap()).onInitializeClient();
      (new BuiltInTrainsInit()).onInitializeClient();
      (new VehicleLightsJsonClient()).onInitializeClient();
      (new SignalLinkClient()).onInitializeClient();
   }
}
