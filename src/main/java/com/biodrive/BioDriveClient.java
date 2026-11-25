package com.biodrive;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client-side Fabric mod entrypoint for BioDrive.
 */
public class BioDriveClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BioDriveMod.LOGGER.debug("BioDrive client initialized.");
    }
}
