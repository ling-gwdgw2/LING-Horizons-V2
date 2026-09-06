package me.ling.horizons2.engine.renderer.iris;

import net.neoforged.fml.ModList;

/**
 * Classloading-safe helper for querying Iris shader status.
 *
 * Guarantees zero NoClassDefFoundError when Iris is not installed
 * by isolating IrisApi references within a separate lazily loaded class.
 */
public class IrisCompatHelper {
    private static Boolean irisInstalled = null;

    /**
     * Checks if the Iris mod is loaded in the current NeoForge environment.
     */
    public static boolean isIrisInstalled() {
        if (irisInstalled == null) {
            try {
                irisInstalled = ModList.get().isLoaded("iris");
            } catch (Throwable t) {
                irisInstalled = false;
            }
        }
        return irisInstalled;
    }

    /**
     * Returns true if Iris is installed AND an active shaderpack is currently in use.
     */
    public static boolean isShaderpackActive() {
        if (!isIrisInstalled()) {
            return false;
        }
        try {
            return IrisApiHolder.isInUse();
        } catch (Throwable ignored) {
            return false;
        }
    }

    // Inner class is ONLY loaded when isIrisInstalled() is true, preventing NoClassDefFoundError
    private static class IrisApiHolder {
        private static boolean isInUse() {
            return net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse();
        }
    }
}
