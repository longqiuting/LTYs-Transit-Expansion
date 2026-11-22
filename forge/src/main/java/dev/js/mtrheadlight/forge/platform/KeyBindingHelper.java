package dev.js.mtrheadlight.forge.platform;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

import java.util.ArrayList;
import java.util.List;

/** Fabric {@code KeyBindingHelper} 的等价物：排队 → {@code RegisterKeyMappingsEvent} 回放。 */
public final class KeyBindingHelper {

    private static final List<KeyMapping> PENDING = new ArrayList<>();

    private KeyBindingHelper() {
    }

    public static KeyMapping registerKeyBinding(final KeyMapping binding) {
        if (binding != null) {
            PENDING.add(binding);
        }
        return binding;
    }

    public static void onRegisterKeyMappings(final RegisterKeyMappingsEvent event) {
        for (final KeyMapping binding : PENDING) {
            event.register(binding);
        }
    }
}
