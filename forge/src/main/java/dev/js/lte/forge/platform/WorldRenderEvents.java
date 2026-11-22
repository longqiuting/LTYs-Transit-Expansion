package dev.js.lte.forge.platform;

import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Fabric {@code WorldRenderEvents} 的等价物。
 *
 * <p>阶段对照（Fabric → Forge {@code RenderLevelStageEvent.Stage}）：
 * {@code START} → {@code AFTER_SKY}，{@code AFTER_ENTITIES} → {@code AFTER_ENTITIES}，
 * {@code AFTER_TRANSLUCENT} → {@code AFTER_TRANSLUCENT_BLOCKS}，{@code LAST}/{@code END} → {@code AFTER_LEVEL}。
 *
 * <p>嵌套接口名与 Fabric 一一对应（反编译产物里会出现
 * {@code (WorldRenderEvents.Last)(context) -> ...} 这样的强转）。
 */
public final class WorldRenderEvents {

    public interface RenderCallback {
        void onRender(WorldRenderContext context);
    }

    public interface Start extends RenderCallback {
    }

    public interface AfterEntities extends RenderCallback {
    }

    public interface AfterTranslucent extends RenderCallback {
    }

    public interface Last extends RenderCallback {
    }

    public interface End extends RenderCallback {
    }

    public static final FEvent<Start> START = new FEvent<>();
    public static final FEvent<AfterEntities> AFTER_ENTITIES = new FEvent<>();
    public static final FEvent<AfterTranslucent> AFTER_TRANSLUCENT = new FEvent<>();
    public static final FEvent<Last> LAST = new FEvent<>();
    public static final FEvent<End> END = new FEvent<>();

    private WorldRenderEvents() {
    }

    /** 由 {@code ClientBootstrap} 的 {@code RenderLevelStageEvent} 监听器调用。 */
    public static void fire(final RenderLevelStageEvent event) {
        final RenderLevelStageEvent.Stage stage = event.getStage();
        final WorldRenderContext context = new WorldRenderContext(event);
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            START.fire(l -> l.onRender(context));
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            AFTER_ENTITIES.fire(l -> l.onRender(context));
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            AFTER_TRANSLUCENT.fire(l -> l.onRender(context));
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            LAST.fire(l -> l.onRender(context));
            END.fire(l -> l.onRender(context));
        }
    }
}
