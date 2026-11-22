package dev.js.mtrheadlight.signal;

import java.util.HashMap;
import java.util.Map;
import dev.js.mtrheadlight.forge.platform.ClientModInitializer;
import dev.js.mtrheadlight.forge.platform.ClientPlayConnectionEvents;
import dev.js.mtrheadlight.forge.platform.ClientPlayNetworking;
import net.minecraft.core.BlockPos;

public final class SignalLinkClient implements ClientModInitializer {
   public void onInitializeClient() {
      try {
         ClientPlayNetworking.registerGlobalReceiver(SignalLinkNet.ID, (client, handler, buf, sender) -> {
            try {
               byte kind = buf.readByte();
               String dim = buf.readUtf();
               switch (kind) {
                  case 0: {

                     int n = buf.readVarInt();
                     Map<BlockPos, BlockPos> links = new HashMap();

                     for(int i = 0; i < n; ++i) {
                        links.put(buf.readBlockPos(), buf.readBlockPos());
                     }

                     client.execute(() -> SignalLinks.replace(dim, links));
                     break;
                  }
                  case 1: {

                     BlockPos signal = buf.readBlockPos();
                     BlockPos node = buf.readBlockPos();
                     client.execute(() -> SignalLinks.put(dim, signal, node));
                     break;
                  }
                  case 2: {

                     BlockPos signal = buf.readBlockPos();
                     client.execute(() -> SignalLinks.remove(dim, signal));
                     break;
                  }
                  case 3: {

                     int n = buf.readVarInt();
                     Map<BlockPos, BlockPos> lamps = new HashMap();

                     for(int i = 0; i < n; ++i) {
                        lamps.put(buf.readBlockPos(), buf.readBlockPos());
                     }

                     client.execute(() -> SignalLinks.replaceLamps(dim, lamps));
                     break;
                  }
                  case 4: {

                     BlockPos lamp = buf.readBlockPos();
                     BlockPos source = buf.readBlockPos();
                     client.execute(() -> SignalLinks.putLamp(dim, lamp, source));
                     break;
                  }
                  case 5: {

                     BlockPos lamp = buf.readBlockPos();
                     client.execute(() -> SignalLinks.removeLamp(dim, lamp));
                  }
               }
            } catch (Throwable var9) {
            }

         });
      } catch (Throwable var3) {
      }

      try {
         ClientPlayConnectionEvents.DISCONNECT.register((ClientPlayConnectionEvents.Disconnect)(handler, client) -> SignalLinks.clear());
      } catch (Throwable var2) {
      }

   }
}
