package dev.js.lte;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import mtr.client.ClientData;
import mtr.client.TrainClientRegistry;
import mtr.client.TrainProperties;
import mtr.data.NameColorDataBase;
import mtr.data.TrainClient;
import mtr.model.ModelTrainBase;
import mtr.render.JonModelTrainRenderer;
import mtr.render.TrainRendererBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

final class TrainPreview {
   private static final int FULL_BRIGHT = 15728880;
   private static boolean detail = true;
   private static boolean detailFailed = false;
   private static final boolean MODEL_FLIP_Y180 = false;
   private static final boolean MODEL_FLIP_Z180 = true;
   private static final boolean BEAM_MIRROR_X = false;
   static final float DEFAULT_SCALE = 22.0F;
   static final float MIN_SCALE = 4.0F;
   static final float MAX_SCALE = 160.0F;
   static final int MAX_CARS = 8;
   static final double FALLBACK_PITCH = 10.0D;
   static final int GIZMO_NONE = 0;
   static final int GIZMO_MOVE = 1;
   static final int GIZMO_ROTATE = 2;
   static final int AXIS_X = 0;
   static final int AXIS_Y = 1;
   static final int AXIS_Z = 2;
   private static final float[][] AXIS_RGB = new float[][]{{1.0F, 0.3F, 0.3F}, {0.36F, 1.0F, 0.42F}, {0.34F, 0.6F, 1.0F}};
   static final int MODE_AUTO = 0;
   static final int MODE_PLANE = 1;
   static final int MODE_SCREEN = 2;

   private TrainPreview() {
   }

   static TrainPreview.Subject imported(String id, String pack, ResourceLocation texture, ModelTrainBase model, VehicleLights.Layout layout) {
      if (model == null) {
         return null;
      } else {
         ResourceLocation resolved = texture == null ? new ResourceLocation("mtr:textures/block/transparent.png") : texture;
         return new TrainPreview.Subject(id, pack, resolved, model, layout);
      }
   }

   static double carPitch(TrainPreview.Subject subject) {
      VehicleLights.Layout layout = subject == null ? null : subject.layout;
      double pitch = 0.0D;
      if (layout != null) {
         pitch = layout.sectionLen;
         if (!(pitch > 0.5D)) {
            pitch = layout.length;
         }
      }

      return pitch > 0.5D ? pitch : 10.0D;
   }

   private static double[] alongAxis(TrainPreview.Subject subject) {
      VehicleLights.Layout layout = subject == null ? null : subject.layout;
      boolean alongX = layout != null && "X".equalsIgnoreCase(layout.lengthAxis);
      return alongX ? new double[]{1.0D, 0.0D} : new double[]{0.0D, 1.0D};
   }

   static double[][] carOffsets(TrainPreview.Subject subject, int cars) {
      int count = Math.max(1, Math.min(8, cars));
      double pitch = carPitch(subject);
      double[] axis = alongAxis(subject);
      double[][] out = new double[count][2];

      for(int car = 0; car < count; ++car) {
         double offset = count == 1 ? 0.0D : ((double)car - (double)(count - 1) * 0.5D) * pitch;
         out[car][0] = axis[0] * offset;
         out[car][1] = axis[1] * offset;
      }

      return out;
   }

   static TrainPreview.Subject find(String id) {
      if (id != null && !id.isBlank()) {
         try {
            for(TrainClient train : ClientData.TRAINS) {
               if (train != null) {
                  String tid = train.baseTrainType != null && !train.baseTrainType.isBlank() ? train.baseTrainType : train.trainId;
                  if (id.equalsIgnoreCase(tid)) {
                     TrainPreview.Subject found = of(train.trainRenderer, id);
                     if (found != null) {
                        return found;
                     }
                  }
               }
            }
         } catch (Throwable var6) {
         }

         try {
            TrainProperties properties = TrainClientRegistry.getTrainProperties(id);
            if (properties != null) {
               return of(properties.renderer, id);
            }
         } catch (Throwable var5) {
            LteLog.LOGGER.warn("[lte] preview: no model for {}", id, var5);
         }

         return null;
      } else {
         return null;
      }
   }

   private static TrainPreview.Subject of(Object renderer, String id) {
      if (renderer instanceof JonModelTrainRenderer jon) {
         if (jon.model != null) {
            ResourceLocation texture = textureOf(jon.textureId);
            return new TrainPreview.Subject(id, texture.getNamespace(), texture, jon.model, BeamSession.layoutOf(id));
         }
      }

      return null;
   }

   static String packOf(String id) {
      if (id != null && !id.isBlank()) {
         try {
            TrainProperties properties = TrainClientRegistry.getTrainProperties(id);
            if (properties != null) {
               TrainRendererBase var3 = properties.renderer;
               if (var3 instanceof JonModelTrainRenderer) {
                  JonModelTrainRenderer jon = (JonModelTrainRenderer)var3;
                  return textureOf(jon.textureId).getNamespace();
               }
            }
         } catch (Throwable var4) {
         }

         return "?";
      } else {
         return "?";
      }
   }

   private static ResourceLocation textureOf(String textureId) {
      ResourceLocation transparent = new ResourceLocation("mtr:textures/block/transparent.png");
      if (textureId != null && !textureId.isBlank()) {
         try {
            ResourceLocation id = new ResourceLocation(textureId + ".png");
            if (Minecraft.getInstance().getResourceManager().getResource(id).isPresent()) {
               return id;
            }
         } catch (Throwable var3) {
         }

         return transparent;
      } else {
         return transparent;
      }
   }

   static void draw(TrainPreview.Subject subject, GuiGraphics context, TrainPreview.Camera camera, int x0, int y0, int x1, int y1, List<VehicleLightOverrides.Beam> beams, int selected) {
      draw(subject, context, camera, x0, y0, x1, y1, beams, selected, 1);
   }

   static void draw(TrainPreview.Subject subject, GuiGraphics context, TrainPreview.Camera camera, int x0, int y0, int x1, int y1, List<VehicleLightOverrides.Beam> beams, int selected, int cars) {
      draw(subject, context, camera, x0, y0, x1, y1, beams, selected, cars, 0, -1);
   }

   static void draw(TrainPreview.Subject subject, GuiGraphics context, TrainPreview.Camera camera, int x0, int y0, int x1, int y1, List<VehicleLightOverrides.Beam> beams, int selected, int cars, int gizmo, int highlightAxis) {
      int centreX = (x0 + x1) / 2;
      int centreY = (y0 + y1) / 2;
      int carCount = Math.max(1, Math.min(8, cars));
      VehicleLightOverrides.Beam gizmoBeam = gizmo != 0 && beams != null && selected >= 0 && selected < beams.size() ? (VehicleLightOverrides.Beam)beams.get(selected) : null;
      double[] gizmoAt = gizmoBeam == null ? null : gizmoBeam.pos;
      double[] gizmoRot = gizmoBeam == null ? null : gizmoBeam.rot;
      double gizmoSize = gizmo == 2 ? BeamGizmo.ringSize(camera.scale) : BeamGizmo.shaftSize(camera.scale);
      context.enableScissor(x0, y0, x1, y1);
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      MultiBufferSource.BufferSource immediate = Minecraft.getInstance().renderBuffers().bufferSource();
      if (subject != null && subject.model != null) {
         double[][] offsets = carOffsets(subject, carCount);

         for(int car = 0; car < carCount; ++car) {
            double ox = offsets[car][0];
            double oz = offsets[car][1];
            PoseStack matrices = new PoseStack();
            matrices.mulPoseMatrix(camera.toPixels(centreX, centreY));
            matrices.mulPoseMatrix(camera.rotation());
            matrices.translate((float)ox, 0.0F, (float)oz);
            boolean drawn = true;

            try {
               subject.model.render(matrices, immediate, (NameColorDataBase)null, subject.texture, 15728880, 0.0F, 0.0F, false, car, carCount, true, false, false, detail, false);
            } catch (Throwable var30) {
               drawn = false;
               if (detail && !detailFailed) {
                  detailFailed = true;
                  detail = false;
                  LteLog.LOGGER.warn("[lte] preview: detail render failed, retrying without it", var30);
               } else {
                  LteLog.LOGGER.warn("[lte] preview: model render failed, drawing the box instead", var30);
               }
            }

            immediate.endBatch();
            if (!drawn) {
               drawBounds(subject, immediate, camera, centreX, centreY, ox, oz);
            }

            drawBeams(immediate, camera, centreX, centreY, beams, selected, ox, oz);
            if (gizmoAt != null) {
               drawGizmo(immediate, camera, centreX, centreY, gizmoAt, gizmoRot, ox, oz, gizmo, gizmoSize, highlightAxis);
            }
         }

         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         context.disableScissor();
      } else {
         drawBounds(subject, immediate, camera, centreX, centreY, 0.0D, 0.0D);
         drawBeams(immediate, camera, centreX, centreY, beams, selected, 0.0D, 0.0D);
         if (gizmoAt != null) {
            drawGizmo(immediate, camera, centreX, centreY, gizmoAt, gizmoRot, 0.0D, 0.0D, gizmo, gizmoSize, highlightAxis);
         }

         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         context.disableScissor();
      }
   }

   private static void drawGizmo(MultiBufferSource.BufferSource immediate, TrainPreview.Camera camera, int centreX, int centreY, double[] origin, double[] rot, double ox, double oz, int gizmo, double size, int highlight) {
      if (origin != null) {
         if (gizmo == 1) {
            drawAxes(immediate, camera, centreX, centreY, origin, ox, oz, size, highlight);
         } else if (gizmo == 2) {
            drawRings(immediate, camera, centreX, centreY, origin, rot, ox, oz, size, highlight);
         }

      }
   }

   private static void drawBounds(TrainPreview.Subject subject, MultiBufferSource.BufferSource immediate, TrainPreview.Camera camera, int centreX, int centreY, double ox, double oz) {
      VehicleLights.Layout layout = subject == null ? null : subject.layout;
      double halfLength = (layout == null ? 18.0D : Math.max(4.0D, layout.length)) * 0.5D;
      double halfWidth = layout == null ? 1.5D : Math.max(0.6D, layout.width * 0.5D);
      float low = (float)(layout == null ? 0.0D : layout.minY);
      float high = (float)(layout == null ? 3.2D : layout.maxY);
      VertexConsumer lines = immediate.getBuffer(RenderType.lines());
      Matrix4f matrix = (new Matrix4f(camera.toPixels(centreX, centreY))).mul(beamRotate(camera));
      matrix.translate((float)ox, 0.0F, (float)oz);
      double[][] corners = new double[][]{{-halfWidth, -halfLength}, {halfWidth, -halfLength}, {halfWidth, halfLength}, {-halfWidth, halfLength}};

      for(int i = 0; i < 4; ++i) {
         double sx = corners[i][0];
         double sz = corners[i][1];
         double nx = corners[(i + 1) % 4][0];
         double nz = corners[(i + 1) % 4][1];
         line(lines, matrix, sx, (double)low, sz, sx, (double)high, sz, 0.55F, 0.85F, 1.0F, 0.8F);
         line(lines, matrix, sx, (double)low, sz, nx, (double)low, nz, 0.55F, 0.85F, 1.0F, 0.8F);
         line(lines, matrix, sx, (double)high, sz, nx, (double)high, nz, 0.55F, 0.85F, 1.0F, 0.8F);
      }

      immediate.endBatch();
   }

   private static void drawBeams(MultiBufferSource.BufferSource immediate, TrainPreview.Camera camera, int centreX, int centreY, List<VehicleLightOverrides.Beam> beams, int selected, double ox, double oz) {
      if (beams != null && !beams.isEmpty()) {
         VertexConsumer lines = immediate.getBuffer(RenderType.lines());
         Matrix4f pixels = camera.toPixels(centreX, centreY);
         Matrix4f matrix = (new Matrix4f(pixels)).mul(beamRotate(camera));
         matrix.translate((float)ox, 0.0F, (float)oz);

         for(int i = 0; i < beams.size(); ++i) {
            VehicleLightOverrides.Beam beam = (VehicleLightOverrides.Beam)beams.get(i);
            if (beam != null && beam.pos != null) {
               double[] pos = beam.pos;
               double[] dir = direction(beam);
               double length = beam.length == null ? 8.0D : beam.length;
               double dot = Math.max(0.18D, Math.min(0.45D, (beam.width == null ? 1.0D : beam.width) * 0.22D));
               int rgb = beam.color == null ? 16777215 : beam.color & 16777215;
               float r = (float)(rgb >> 16 & 255) / 255.0F;
               float g = (float)(rgb >> 8 & 255) / 255.0F;
               float b = (float)(rgb & 255) / 255.0F;
               float alpha = i == selected ? 1.0F : 0.6F;
               double ex = pos[0] + dir[0] * length;
               double ey = pos[1] + dir[1] * length;
               double ez = pos[2] + dir[2] * length;
               line(lines, matrix, pos[0], pos[1], pos[2], ex, ey, ez, r, g, b, alpha);
               double[] up = Math.abs(dir[1]) > 0.95D ? new double[]{1.0D, 0.0D, 0.0D} : new double[]{0.0D, 1.0D, 0.0D};
               double[] right = normalise(cross(dir, up));
               double[] realUp = normalise(cross(right, dir));
               double arrow = Math.min(0.9D, Math.max(0.35D, length * 0.12D));
               double wing = arrow * 0.55D;

               for(int k = 0; k < 4; ++k) {
                  double sa = k == 0 ? 1.0D : (k == 1 ? -1.0D : 0.0D);
                  double sb = k == 2 ? 1.0D : (k == 3 ? -1.0D : 0.0D);
                  double bx = ex - dir[0] * arrow + (right[0] * sa + realUp[0] * sb) * wing;
                  double by = ey - dir[1] * arrow + (right[1] * sa + realUp[1] * sb) * wing;
                  double bz = ez - dir[2] * arrow + (right[2] * sa + realUp[2] * sb) * wing;
                  line(lines, matrix, ex, ey, ez, bx, by, bz, r, g, b, alpha);
               }

               double[] sx = normalise(cross(right, realUp));
               float dotAlpha = Math.max(0.75F, alpha);
               line(lines, matrix, pos[0] - right[0] * dot, pos[1] - right[1] * dot, pos[2] - right[2] * dot, pos[0] + right[0] * dot, pos[1] + right[1] * dot, pos[2] + right[2] * dot, r, g, b, dotAlpha);
               line(lines, matrix, pos[0] - realUp[0] * dot, pos[1] - realUp[1] * dot, pos[2] - realUp[2] * dot, pos[0] + realUp[0] * dot, pos[1] + realUp[1] * dot, pos[2] + realUp[2] * dot, r, g, b, dotAlpha);
               line(lines, matrix, pos[0] - sx[0] * dot * 0.6D, pos[1] - sx[1] * dot * 0.6D, pos[2] - sx[2] * dot * 0.6D, pos[0] + sx[0] * dot * 0.6D, pos[1] + sx[1] * dot * 0.6D, pos[2] + sx[2] * dot * 0.6D, r, g, b, dotAlpha * 0.7F);
            }
         }

         immediate.endBatch();
      }
   }

   private static void line(VertexConsumer out, Matrix4f matrix, double x0, double y0, double z0, double x1, double y1, double z1, float r, float g, float b, float alpha) {
      out.vertex(matrix, (float)x0, (float)y0, (float)z0).color(r, g, b, alpha).normal(0.0F, 1.0F, 0.0F).endVertex();
      out.vertex(matrix, (float)x1, (float)y1, (float)z1).color(r, g, b, alpha).normal(0.0F, 1.0F, 0.0F).endVertex();
   }

   static Matrix4f beamRotate(TrainPreview.Camera camera) {
      return new Matrix4f(camera.rotation());
   }

   static double[] axisUnit(int axis) {
      switch (axis) {
         case 0:
            return new double[]{1.0D, 0.0D, 0.0D};
         case 1:
            return new double[]{0.0D, 1.0D, 0.0D};
         default:
            return new double[]{0.0D, 0.0D, 1.0D};
      }
   }

   static String axisName(int axis) {
      return axis == 0 ? "X" : (axis == 1 ? "Y" : "Z");
   }

   private static double[] axisPerpendicular(int axis) {
      return axis == 0 ? axisUnit(2) : axisUnit(0);
   }

   private static void drawAxes(MultiBufferSource.BufferSource immediate, TrainPreview.Camera camera, int centreX, int centreY, double[] origin, double ox, double oz, double len, int highlight) {
      if (origin != null) {
         VertexConsumer lines = immediate.getBuffer(RenderType.lines());
         Matrix4f matrix = (new Matrix4f(camera.toPixels(centreX, centreY))).mul(beamRotate(camera));
         matrix.translate((float)ox, 0.0F, (float)oz);

         for(int axis = 0; axis < 3; ++axis) {
            double[] dir = axisUnit(axis);
            double[] perp = axisPerpendicular(axis);
            float[] rgb = AXIS_RGB[axis];
            boolean on = axis == highlight;
            double bx = origin[0];
            double by = origin[1];
            double bz = origin[2];
            double ex = bx + dir[0] * len;
            double ey = by + dir[1] * len;
            double ez = bz + dir[2] * len;
            double spread = on ? Math.max(0.02D, len * 0.02D) : 0.0D;

            for(int pass = 0; pass < (on ? 3 : 1); ++pass) {
               double shift = pass == 0 ? 0.0D : (pass == 1 ? spread : -spread);
               line(lines, matrix, bx + perp[0] * shift, by + perp[1] * shift, bz + perp[2] * shift, ex + perp[0] * shift, ey + perp[1] * shift, ez + perp[2] * shift, rgb[0], rgb[1], rgb[2], on ? 1.0F : 0.75F);
            }

            double back = len * 0.15D;
            double wing = len * 0.09D;

            for(int side = -1; side <= 1; side += 2) {
               line(lines, matrix, ex, ey, ez, ex - dir[0] * back + perp[0] * wing * (double)side, ey - dir[1] * back + perp[1] * wing * (double)side, ez - dir[2] * back + perp[2] * wing * (double)side, rgb[0], rgb[1], rgb[2], on ? 1.0F : 0.75F);
            }
         }

         immediate.endBatch();
      }
   }

   private static void drawRings(MultiBufferSource.BufferSource immediate, TrainPreview.Camera camera, int centreX, int centreY, double[] origin, double[] rot, double ox, double oz, double radius, int highlight) {
      if (origin != null) {
         VertexConsumer lines = immediate.getBuffer(RenderType.lines());
         Matrix4f matrix = (new Matrix4f(camera.toPixels(centreX, centreY))).mul(beamRotate(camera));
         matrix.translate((float)ox, 0.0F, (float)oz);

         for(int axis = 0; axis < 3; ++axis) {
            double[] normal = BeamGizmo.ringNormal(axis, rot);
            double[][] basis = BeamGizmo.planeBasis(normal);
            double[] u = basis[0];
            double[] v = basis[1];
            float[] rgb = AXIS_RGB[axis];
            boolean on = axis == highlight;
            double[] radii = on ? new double[]{radius, radius * 1.022D} : new double[]{radius};

            for(int ring = 0; ring < radii.length; ++ring) {
               double r = radii[ring];
               double px = 0.0D;
               double py = 0.0D;
               double pz = 0.0D;

               for(int i = 0; i <= 48; ++i) {
                  double a = (Math.PI * 2D) * (double)i / 48.0D;
                  double cos = Math.cos(a);
                  double sin = Math.sin(a);
                  double cx = origin[0] + (u[0] * cos + v[0] * sin) * r;
                  double cy = origin[1] + (u[1] * cos + v[1] * sin) * r;
                  double cz = origin[2] + (u[2] * cos + v[2] * sin) * r;
                  if (i > 0) {
                     line(lines, matrix, px, py, pz, cx, cy, cz, rgb[0], rgb[1], rgb[2], on ? 1.0F : 0.62F);
                  }

                  px = cx;
                  py = cy;
                  pz = cz;
               }
            }
         }

         immediate.endBatch();
      }
   }

   static TrainPreview.RingHit pickRing(TrainPreview.Camera camera, double mouseX, double mouseY, int x0, int y0, int x1, int y1, double[] origin, double[] rot, double[][] offsets) {
      if (origin != null && offsets != null) {
         int centreX = (x0 + x1) / 2;
         int centreY = (y0 + y1) / 2;
         Matrix4f matrix = (new Matrix4f(camera.toPixels(centreX, centreY))).mul(beamRotate(camera));
         double radius = BeamGizmo.ringSize(camera.scale);
         TrainPreview.RingHit best = null;

         for(int car = 0; car < offsets.length; ++car) {
            double ox = offsets[car][0];
            double oz = offsets[car][1];
            double[] base = new double[]{origin[0] + ox, origin[1], origin[2] + oz};

            for(int axis = 0; axis < 3; ++axis) {
               double[] sample = ringSample(camera, matrix, centreX, centreY, mouseX, mouseY, base, BeamGizmo.ringNormal(axis, rot), radius, 0);
               if (sample[0] <= 7.0D && (best == null || !(sample[0] >= best.distance))) {
                  best = new TrainPreview.RingHit(axis, car, ox, oz, sample[0], sample[1], sample[2] > 0.5D);
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   static double ringAngle(TrainPreview.Camera camera, double mouseX, double mouseY, int x0, int y0, int x1, int y1, double[] base, double[] normal, boolean planar) {
      if (base != null && normal != null) {
         int centreX = (x0 + x1) / 2;
         int centreY = (y0 + y1) / 2;
         Matrix4f matrix = (new Matrix4f(camera.toPixels(centreX, centreY))).mul(beamRotate(camera));
         double[] sample = ringSample(camera, matrix, centreX, centreY, mouseX, mouseY, base, normal, BeamGizmo.ringSize(camera.scale), planar ? 1 : 2);
         return sample[1];
      } else {
         return Double.NaN;
      }
   }

   private static double[] ringSample(TrainPreview.Camera camera, Matrix4f matrix, int centreX, int centreY, double mouseX, double mouseY, double[] base, double[] normal, double radius, int mode) {
      double planeErr = Double.POSITIVE_INFINITY;
      double planeAngle = Double.NaN;
      Matrix4f rot = beamRotate(camera);
      double ux = (double)rot.m02();
      double uy = (double)rot.m12();
      double uz = (double)rot.m22();
      double ulen = Math.sqrt(ux * ux + uy * uy + uz * uz);
      if (ulen > 1.0E-9D) {
         ux /= ulen;
         uy /= ulen;
         uz /= ulen;
         double denom = normal[0] * ux + normal[1] * uy + normal[2] * uz;
         if (Math.abs(denom) >= 0.15D) {
            double vx = (mouseX - (double)((float)centreX + camera.panX)) / (double)camera.scale;
            double vy = -(mouseY - (double)((float)centreY + camera.panY)) / (double)camera.scale;
            double oxr = vx * (double)rot.m00() + vy * (double)rot.m01();
            double oyr = vx * (double)rot.m10() + vy * (double)rot.m11() + (double)camera.targetY;
            double ozr = vx * (double)rot.m20() + vy * (double)rot.m21();
            double t = (normal[0] * (base[0] - oxr) + normal[1] * (base[1] - oyr) + normal[2] * (base[2] - ozr)) / denom;
            double rx = oxr + ux * t - base[0];
            double ry = oyr + uy * t - base[1];
            double rz = ozr + uz * t - base[2];
            double radial = Math.sqrt(rx * rx + ry * ry + rz * rz);
            planeErr = Math.abs(radial - radius) * (double)camera.scale;
            if (radial > 1.0E-6D) {
               double[][] basis = BeamGizmo.planeBasis(normal);
               double[] bu = basis[0];
               double[] bv = basis[1];
               planeAngle = Math.atan2(rx * bv[0] + ry * bv[1] + rz * bv[2], rx * bu[0] + ry * bu[1] + rz * bu[2]);
            }
         }
      }

      if (mode == 1) {
         return new double[]{planeErr, planeAngle, 1.0D};
      } else if (mode == 0 && planeErr <= 7.0D) {
         return new double[]{planeErr, planeAngle, 1.0D};
      } else {
         double[][] basis = BeamGizmo.planeBasis(normal);
         double[] bu = basis[0];
         double[] bv = basis[1];
         float cx = projectX(matrix, base[0], base[1], base[2]);
         float cy = projectY(matrix, base[0], base[1], base[2]);
         double screenErr = Double.POSITIVE_INFINITY;
         float bestX = cx;
         float bestY = cy;
         float prevX = 0.0F;
         float prevY = 0.0F;

         for(int i = 0; i <= 48; ++i) {
            double a = (Math.PI * 2D) * (double)i / 48.0D;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            double wx = base[0] + (bu[0] * cos + bv[0] * sin) * radius;
            double wy = base[1] + (bu[1] * cos + bv[1] * sin) * radius;
            double wz = base[2] + (bu[2] * cos + bv[2] * sin) * radius;
            float sx = projectX(matrix, wx, wy, wz);
            float sy = projectY(matrix, wx, wy, wz);
            if (i > 0) {
               double d = pointSegment(mouseX, mouseY, (double)prevX, (double)prevY, (double)sx, (double)sy);
               if (d < screenErr) {
                  screenErr = d;
                  bestX = sx;
                  bestY = sy;
               }
            }

            prevX = sx;
            prevY = sy;
         }

         double screenAngle = Math.atan2((double)(bestY - cy), (double)(bestX - cx));
         return mode != 2 && !(screenErr < planeErr) ? new double[]{planeErr, planeAngle, 1.0D} : new double[]{screenErr, screenAngle, 0.0D};
      }
   }

   static TrainPreview.AxisHit pickAxis(TrainPreview.Camera camera, double mouseX, double mouseY, int x0, int y0, int x1, int y1, double[] origin, double[][] offsets, double len) {
      if (origin != null && offsets != null) {
         int centreX = (x0 + x1) / 2;
         int centreY = (y0 + y1) / 2;
         Matrix4f matrix = (new Matrix4f(camera.toPixels(centreX, centreY))).mul(beamRotate(camera));
         double threshold = 6.0D;
         TrainPreview.AxisHit best = null;

         for(int car = 0; car < offsets.length; ++car) {
            double ox = offsets[car][0];
            double oz = offsets[car][1];
            double bx = origin[0] + ox;
            double by = origin[1];
            double bz = origin[2] + oz;
            float ax = projectX(matrix, bx, by, bz);
            float ay = projectY(matrix, bx, by, bz);

            for(int axis = 0; axis < 3; ++axis) {
               double[] dir = axisUnit(axis);
               float bx2 = projectX(matrix, bx + dir[0] * len, by + dir[1] * len, bz + dir[2] * len);
               float by2 = projectY(matrix, bx + dir[0] * len, by + dir[1] * len, bz + dir[2] * len);
               double d = pointSegment(mouseX, mouseY, (double)ax, (double)ay, (double)bx2, (double)by2);
               if (d <= 6.0D && (best == null || d < best.distance)) {
                  best = new TrainPreview.AxisHit(axis, car, ox, oz, d);
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   static double axisCoordinate(TrainPreview.Camera camera, double mouseX, double mouseY, int x0, int y0, int x1, int y1, double[] base, double[] dir) {
      if (base != null && dir != null) {
         int centreX = (x0 + x1) / 2;
         int centreY = (y0 + y1) / 2;
         Matrix4f rot = camera.rotation();
         double ux = (double)rot.m02();
         double uy = (double)rot.m12();
         double uz = (double)rot.m22();
         double vx = (mouseX - (double)((float)centreX + camera.panX)) / (double)camera.scale;
         double vy = -(mouseY - (double)((float)centreY + camera.panY)) / (double)camera.scale;
         double ox = vx * (double)rot.m00() + vy * (double)rot.m01();
         double oy = vx * (double)rot.m10() + vy * (double)rot.m11() + (double)camera.targetY;
         double oz = vx * (double)rot.m20() + vy * (double)rot.m21();
         double wx = base[0] - ox;
         double wy = base[1] - oy;
         double wz = base[2] - oz;
         double b = dir[0] * ux + dir[1] * uy + dir[2] * uz;
         double denom = 1.0D - b * b;
         if (denom < 1.0E-4D) {
            return Double.NaN;
         } else {
            double dDotW = dir[0] * wx + dir[1] * wy + dir[2] * wz;
            double eDotW = ux * wx + uy * wy + uz * wz;
            return (b * eDotW - dDotW) / denom;
         }
      } else {
         return Double.NaN;
      }
   }

   private static float projectX(Matrix4f m, double x, double y, double z) {
      return m.m00() * (float)x + m.m10() * (float)y + m.m20() * (float)z + m.m30();
   }

   private static float projectY(Matrix4f m, double x, double y, double z) {
      return m.m01() * (float)x + m.m11() * (float)y + m.m21() * (float)z + m.m31();
   }

   static TrainPreview.Hit pick(TrainPreview.Camera camera, double mouseX, double mouseY, int x0, int y0, int x1, int y1, List<VehicleLightOverrides.Beam> beams) {
      if (beams != null && !beams.isEmpty()) {
         int centreX = (x0 + x1) / 2;
         int centreY = (y0 + y1) / 2;
         Matrix4f matrix = (new Matrix4f(camera.toPixels(centreX, centreY))).mul(beamRotate(camera));
         double threshold = Math.max(6.0D, (double)camera.scale * 0.45D);
         TrainPreview.Hit best = null;

         for(int i = 0; i < beams.size(); ++i) {
            VehicleLightOverrides.Beam beam = (VehicleLightOverrides.Beam)beams.get(i);
            if (beam != null && beam.pos != null) {
               double[] dir = direction(beam);
               double length = beam.length == null ? 8.0D : beam.length;
               float ax = matrix.m00() * (float)beam.pos[0] + matrix.m10() * (float)beam.pos[1] + matrix.m20() * (float)beam.pos[2] + matrix.m30();
               float ay = matrix.m01() * (float)beam.pos[0] + matrix.m11() * (float)beam.pos[1] + matrix.m21() * (float)beam.pos[2] + matrix.m31();
               double ex = beam.pos[0] + dir[0] * length;
               double ey = beam.pos[1] + dir[1] * length;
               double ez = beam.pos[2] + dir[2] * length;
               float bx = matrix.m00() * (float)ex + matrix.m10() * (float)ey + matrix.m20() * (float)ez + matrix.m30();
               float by = matrix.m01() * (float)ex + matrix.m11() * (float)ey + matrix.m21() * (float)ez + matrix.m31();
               double d = pointSegment(mouseX, mouseY, (double)ax, (double)ay, (double)bx, (double)by);
               if (d <= threshold && (best == null || d < best.distance)) {
                  best = new TrainPreview.Hit(i, d);
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   private static double pointSegment(double px, double py, double ax, double ay, double bx, double by) {
      double vx = bx - ax;
      double vy = by - ay;
      double lengthSquared = vx * vx + vy * vy;
      double t = lengthSquared < 1.0E-9D ? 0.0D : Math.max(0.0D, Math.min(1.0D, ((px - ax) * vx + (py - ay) * vy) / lengthSquared));
      double dx = px - (ax + vx * t);
      double dy = py - (ay + vy * t);
      return Math.sqrt(dx * dx + dy * dy);
   }

   static double[] direction(VehicleLightOverrides.Beam beam) {
      double[] head = direction(0.0D, beam == null ? null : beam.rot);
      return BeamSession.isHead(beam) ? head : new double[]{-head[0], -head[1], -head[2]};
   }

   static double[] direction(double baseYaw, double[] rot) {
      double yaw = Math.toRadians(baseYaw + (rot == null ? 0.0D : rot[0]));
      double pitch = Math.toRadians(rot == null ? 0.0D : rot[1]);
      double cosPitch = Math.cos(pitch);
      return new double[]{-Math.sin(yaw) * cosPitch, -Math.sin(pitch), -Math.cos(yaw) * cosPitch};
   }

   private static double[] cross(double[] a, double[] b) {
      return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
   }

   private static double[] normalise(double[] v) {
      double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
      return len < 1.0E-9D ? new double[]{1.0D, 0.0D, 0.0D} : new double[]{v[0] / len, v[1] / len, v[2] / len};
   }

   static final class AxisHit {
      final int axis;
      final int car;
      final double ox;
      final double oz;
      final double distance;

      AxisHit(int axis, int car, double ox, double oz, double distance) {
         this.axis = axis;
         this.car = car;
         this.ox = ox;
         this.oz = oz;
         this.distance = distance;
      }
   }

   static final class Camera {
      float yaw = 34.0F;
      float pitch = 16.0F;
      float scale = 22.0F;
      float panX;
      float panY;
      float targetY = 1.1F;

      void orbit(double dx, double dy) {
         this.yaw += (float)dx * 0.6F;
         this.pitch = (float)Math.max(-89.0D, Math.min(89.0D, (double)this.pitch + dy * 0.6D));
      }

      void pan(double dx, double dy) {
         this.panX += (float)dx;
         this.panY += (float)dy;
      }

      void zoom(double amount) {
         this.scale = (float)Math.max(4.0D, Math.min(160.0D, (double)this.scale * Math.pow(1.12D, amount)));
      }

      Matrix4f rotation() {
         Matrix4f out = new Matrix4f();
         out.rotate(Axis.XP.rotationDegrees(this.pitch));
         out.rotate(Axis.YP.rotationDegrees(this.yaw));
         out.rotate(Axis.ZP.rotationDegrees(180.0F));
         out.translate(0.0F, -this.targetY, 0.0F);
         return out;
      }

      Matrix4f toPixels(int centreX, int centreY) {
         return (new Matrix4f()).translate((float)centreX + this.panX, (float)centreY + this.panY, 0.0F).scale(this.scale, -this.scale, this.scale);
      }
   }

   static final class Hit {
      final int index;
      final double distance;

      Hit(int index, double distance) {
         this.index = index;
         this.distance = distance;
      }
   }

   static final class RingHit {
      final int axis;
      final int car;
      final double ox;
      final double oz;
      final double distance;
      final double angle;
      final boolean planar;

      RingHit(int axis, int car, double ox, double oz, double distance, double angle, boolean planar) {
         this.axis = axis;
         this.car = car;
         this.ox = ox;
         this.oz = oz;
         this.distance = distance;
         this.angle = angle;
         this.planar = planar;
      }
   }

   static final class Subject {
      final String id;
      final String pack;
      final ResourceLocation texture;
      final ModelTrainBase model;
      final VehicleLights.Layout layout;

      private Subject(String id, String pack, ResourceLocation texture, ModelTrainBase model, VehicleLights.Layout layout) {
         this.id = id;
         this.pack = pack;
         this.texture = texture;
         this.model = model;
         this.layout = layout;
      }

      double centreY() {
         return this.layout == null ? 1.1D : (this.layout.minY + this.layout.maxY) * 0.5D;
      }

      float fitScale(int viewportHeight) {
         return this.fitScale(viewportHeight, 1);
      }

      float fitScale(int viewportHeight, int cars) {
         int carCount = Math.max(1, Math.min(8, cars));
         double single = this.layout == null ? 20.0D : Math.max(this.layout.length, this.layout.width * 2.0D + 4.0D);
         double span = single + (double)(carCount - 1) * TrainPreview.carPitch(this);
         return (float)Math.max(4.0D, Math.min(160.0D, (double)viewportHeight * 0.55D / Math.max(4.0D, span)));
      }
   }
}
