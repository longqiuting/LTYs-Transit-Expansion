package dev.js.lte.forge.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Fabric {@code WorldRenderContext} 的等价物：包一层 Forge 的 {@link RenderLevelStageEvent}。
 *
 * <p>本模组只用到 {@link #matrixStack()} 与 {@link #camera()}；其余保留成同名方法，
 * 便于以后往 shim 里补东西而不用再动 java-gen。
 */
public class WorldRenderContext {

    private final RenderLevelStageEvent event;
    private final PoseStack poseStack;
    private final Camera camera;
    private final float tickDelta;
    private final Level world;

    public WorldRenderContext(final RenderLevelStageEvent event) {
        this.event = event;
        this.poseStack = event.getPoseStack();
        this.camera = event.getCamera();
        this.tickDelta = event.getPartialTick();
        final Minecraft mc = Minecraft.getInstance();
        this.world = mc.level;
    }

    public PoseStack matrixStack() {
        return poseStack;
    }

    public Camera camera() {
        return camera;
    }

    public float tickDelta() {
        return tickDelta;
    }

    public Level world() {
        return world;
    }

    public Matrix4f projectionMatrix() {
        return event.getProjectionMatrix();
    }

    public Frustum frustum() {
        return event.getFrustum();
    }

    public LevelRenderer worldRenderer() {
        return event.getLevelRenderer();
    }

    public boolean shouldRenderTranslucent() {
        return true;
    }

    public RenderLevelStageEvent.Stage stage() {
        return event.getStage();
    }
}
