package dev.js.lte.signal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.js.lte.LteDebug;
import dev.js.lte.LteLog;
import java.util.Arrays;
import java.util.Map;
import mtr.client.ClientData;
import dev.js.lte.forge.platform.EnvType;
import dev.js.lte.forge.platform.Environment;
import dev.js.lte.forge.platform.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class ConnectorNodeHighlight {
   private static final int MAX_NODES = 4000;
   private static final double FRUSTUM_DOT = 0.2D;
   private static final double NEAR_HOLD = 4.0D;
   private static final double NEAR_HOLD_SQ = 16.0D;
   private static final long DIAG_PERIOD_MS = 5000L;
   private static final double INFLATE = 0.002D;
   private static final ConnectorNodeHighlight.Scratch SCRATCH = new ConnectorNodeHighlight.Scratch(4000);
   private static long lastDiagMs;

   private ConnectorNodeHighlight() {
   }

   private static ConnectorNodeHighlight.Held held(Player player) {
      ItemStack main = player.getMainHandItem();
      ItemStack off = player.getOffhandItem();
      Item signal = LteCoreItems.signalLinker();
      if (signal != null) {
         if (main.is(signal)) {
            return new ConnectorNodeHighlight.Held(main, true, "signal");
         }

         if (off.is(signal)) {
            return new ConnectorNodeHighlight.Held(off, true, "signal");
         }
      }

      Item projector = LteCoreItems.projectorConnector();
      if (projector != null) {
         if (main.is(projector)) {
            return new ConnectorNodeHighlight.Held(main, false, "projector");
         }

         if (off.is(projector)) {
            return new ConnectorNodeHighlight.Held(off, false, "projector");
         }
      }

      Item pids = LteCoreItems.pidsConnector();
      if (pids != null) {
         if (main.is(pids)) {
            return new ConnectorNodeHighlight.Held(main, false, "pids");
         }

         if (off.is(pids)) {
            return new ConnectorNodeHighlight.Held(off, false, "pids");
         }
      }

      return null;
   }

   public static void render(WorldRenderContext context) {
      try {
         renderInner(context);
      } catch (Throwable var2) {
      }

   }

   private static void renderInner(WorldRenderContext context) {
      Minecraft client = Minecraft.getInstance();
      Player player = client.player;
      if (player != null && client.level != null) {
         ConnectorNodeHighlight.Held held = held(player);
         if (held != null) {
            PoseStack matrices = context.matrixStack();
            if (matrices != null) {
               Vec3 camera = context.camera().getPosition();
               int viewChunks = client.options.renderDistance().get();
               double range = (double)Math.max(16, viewChunks * 16);
               double rangeSq = range * range;
               Vec3 look = Vec3.directionFromRotation(context.camera().getXRot(), context.camera().getYRot());
               BlockPos selected = held.signal ? SignalLinkItem.selectedNode(held.stack) : null;
               int nodes = collect(camera, look, rangeSq);
               MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
               VertexConsumer lines = buffers.getBuffer(RenderType.lines());
               matrices.pushPose();
               matrices.translate(-camera.x, -camera.y, -camera.z);

               int drawn;
               try {
                  drawn = drawNodes(matrices, lines, selected);
               } finally {
                  matrices.popPose();
               }

               if (drawn > 0) {
                  buffers.endBatch(RenderType.lines());
               }

               diag(held.name, nodes, drawn, range);
            }
         }
      }
   }

   private static int collect(Vec3 camera, Vec3 look, double rangeSq) {
      ConnectorNodeHighlight.Scratch scratch = SCRATCH;
      scratch.clear();
      Map<?, ?> rails = ClientData.RAILS;
      if (rails == null) {
         return 0;
      } else {
         double lx = look.x;
         double ly = look.y;
         double lz = look.z;
         int seen = 0;

         for(Object key : rails.keySet()) {
            if (key instanceof BlockPos) {
               BlockPos node = (BlockPos)key;
               double dx = (double)node.getX() + 0.5D - camera.x;
               double dy = (double)node.getY() + 0.5D - camera.y;
               double dz = (double)node.getZ() + 0.5D - camera.z;
               double distSq = dx * dx + dy * dy + dz * dz;
               if (!(distSq > rangeSq)) {
                  if (distSq > 16.0D) {
                     double inv = 1.0D / Math.sqrt(distSq);
                     if (dx * inv * lx + dy * inv * ly + dz * inv * lz < 0.2D) {
                        continue;
                     }
                  }

                  scratch.offer(node, distSq);
                  ++seen;
               }
            }
         }

         scratch.sortAscending();
         return seen;
      }
   }

   private static int drawNodes(PoseStack matrices, VertexConsumer lines, BlockPos selected) {
      ConnectorNodeHighlight.Scratch scratch = SCRATCH;
      int count = scratch.size;

      for(int i = 0; i < count; ++i) {
         BlockPos node = scratch.pos[i];
         if (selected != null && node.equals(selected)) {
            box(matrices, lines, node, 0.25F, 0.9F, 1.0F, 0.9F);
         } else {
            box(matrices, lines, node, 1.0F, 1.0F, 1.0F, 0.55F);
         }
      }

      return count;
   }

   private static void diag(String item, int nodes, int drawn, double range) {
      if (LteDebug.collision()) {
         long now = System.currentTimeMillis();
         if (lastDiagMs == 0L || now - lastDiagMs >= 5000L) {
            lastDiagMs = now;
            LteLog.LOGGER.info("[lte] node marks: item={} nodes={} drawn={} range={}", new Object[]{item, nodes, drawn, (int)range});
         }
      }
   }

   private static void box(PoseStack matrices, VertexConsumer lines, BlockPos pos, float red, float green, float blue, float alpha) {
      double x = (double)pos.getX();
      double y = (double)pos.getY();
      double z = (double)pos.getZ();
      LevelRenderer.renderLineBox(matrices, lines, x - 0.002D, y - 0.002D, z - 0.002D, x + 1.0D + 0.002D, y + 1.0D + 0.002D, z + 1.0D + 0.002D, red, green, blue, alpha);
   }

   private static final class Held {
      final ItemStack stack;
      final boolean signal;
      final String name;

      Held(ItemStack stack, boolean signal, String name) {
         this.stack = stack;
         this.signal = signal;
         this.name = name;
      }
   }

   private static final class Scratch {
      final BlockPos[] pos;
      final double[] distSq;
      int size;

      Scratch(int capacity) {
         this.pos = new BlockPos[capacity];
         this.distSq = new double[capacity];
      }

      void clear() {
         Arrays.fill(this.pos, (Object)null);
         this.size = 0;
      }

      void offer(BlockPos node, double dist) {
         if (this.size < this.pos.length) {
            this.pos[this.size] = node;
            this.distSq[this.size] = dist;
            this.siftUp(this.size++);
         } else {
            if (dist < this.distSq[0]) {
               this.pos[0] = node;
               this.distSq[0] = dist;
               this.siftDown(0);
            }

         }
      }

      void sortAscending() {
         int end = this.size - 1;

         while(end > 0) {
            this.swap(0, end);
            --end;
            this.siftDownWithin(0, end + 1);
         }

      }

      private void siftUp(int index) {
         int parent;
         for(int i = index; i > 0; i = parent) {
            parent = i - 1 >> 1;
            if (this.distSq[parent] >= this.distSq[i]) {
               return;
            }

            this.swap(i, parent);
         }

      }

      private void siftDown(int index) {
         this.siftDownWithin(index, this.size);
      }

      private void siftDownWithin(int index, int limit) {
         int i = index;

         while(true) {
            int left = (i << 1) + 1;
            int right = left + 1;
            int biggest = i;
            if (left < limit && this.distSq[left] > this.distSq[i]) {
               biggest = left;
            }

            if (right < limit && this.distSq[right] > this.distSq[biggest]) {
               biggest = right;
            }

            if (biggest == i) {
               return;
            }

            this.swap(i, biggest);
            i = biggest;
         }
      }

      private void swap(int a, int b) {
         BlockPos p = this.pos[a];
         this.pos[a] = this.pos[b];
         this.pos[b] = p;
         double d = this.distSq[a];
         this.distSq[a] = this.distSq[b];
         this.distSq[b] = d;
      }
   }
}
