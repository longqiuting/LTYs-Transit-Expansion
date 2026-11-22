package dev.js.lte.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class LteChannel {
   public static final ResourceLocation ID = new ResourceLocation("lte", "hello");
   public static final int VERSION = 1;

   private LteChannel() {
   }

   public static void writeHello(FriendlyByteBuf buf) {
      buf.writeInt(1);
   }

   public static int readHelloVersion(FriendlyByteBuf buf) {
      try {
         return buf.readableBytes() >= 4 ? buf.readInt() : -1;
      } catch (Throwable var2) {
         return -1;
      }
   }

   public static final class ServerHello {
      public final int version;
      public final boolean trainCollision;
      public final boolean damage;
      public final boolean mobs;
      public final int highDamageMaxKmh;
      public final int pushOnlyMaxKmh;

      public ServerHello(int version, boolean trainCollision, boolean damage, boolean mobs, int highDamageMaxKmh, int pushOnlyMaxKmh) {
         this.version = version;
         this.trainCollision = trainCollision;
         this.damage = damage;
         this.mobs = mobs;
         this.highDamageMaxKmh = highDamageMaxKmh;
         this.pushOnlyMaxKmh = pushOnlyMaxKmh;
      }

      public void write(FriendlyByteBuf buf) {
         buf.writeInt(this.version);
         buf.writeBoolean(this.trainCollision);
         buf.writeBoolean(this.damage);
         buf.writeBoolean(this.mobs);
         buf.writeInt(this.highDamageMaxKmh);
         buf.writeInt(this.pushOnlyMaxKmh);
      }

      public static LteChannel.ServerHello read(FriendlyByteBuf buf) {
         try {
            return buf.readableBytes() < 15 ? null : new LteChannel.ServerHello(buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readInt(), buf.readInt());
         } catch (Throwable var2) {
            return null;
         }
      }
   }
}
