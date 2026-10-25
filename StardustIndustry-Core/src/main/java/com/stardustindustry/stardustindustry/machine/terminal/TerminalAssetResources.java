package com.stardustindustry.stardustindustry.machine.terminal;

import java.io.InputStream;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Opens terminal art for common-side collision code. */
final class TerminalAssetResources {

    private TerminalAssetResources() {}

    /**
     * Opens a logical asset path from a server override or from this mod's jar.
     *
     * <p>A server {@link ResourceManager} only exposes {@code data/}; it cannot see
     * the terminal models under {@code assets/}. The old collision readers asked
     * that manager for the files anyway and silently used placeholder boxes. The
     * bundled lookup is therefore the required fallback, not an optional client
     * convenience.</p>
     */
    static InputStream open(ResourceManager manager, ResourceLocation id) throws java.io.IOException {
        if (manager != null) {
            Resource resource = manager.getResource(id).orElse(null);
            if (resource != null) {
                return resource.open();
            }
        }

        String classpath = "/assets/" + id.getNamespace() + "/" + id.getPath();
        InputStream stream = TerminalAssetResources.class.getResourceAsStream(classpath);
        if (stream != null) {
            return stream;
        }

        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        return contextLoader == null ? null : contextLoader.getResourceAsStream(classpath.substring(1));
    }

    static int managerId(ResourceManager manager) {
        return manager == null ? 0 : System.identityHashCode(manager);
    }
}
