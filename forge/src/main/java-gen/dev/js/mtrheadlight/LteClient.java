package dev.js.mtrheadlight;

import dev.js.mtrheadlight.collision.CollisionBootstrap;
import dev.js.mtrheadlight.mtr.BuiltInTrainsInit;
import dev.js.mtrheadlight.signal.SignalLinkClient;
import dev.js.mtrheadlight.forge.platform.ClientModInitializer;

public final class LteClient implements ClientModInitializer {
   public void onInitializeClient() {
      (new MtrHeadlightClient()).onInitializeClient();
      (new CollisionBootstrap()).onInitializeClient();
      (new BuiltInTrainsInit()).onInitializeClient();
      (new VehicleLightsJsonClient()).onInitializeClient();
      (new SignalLinkClient()).onInitializeClient();
   }
}
