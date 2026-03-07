package com.wellnet.net;

import com.wellnet.core.NetDiagnosticsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;

public final class ClientPingSource implements NetDiagnosticsModule.Source {
    @Override
    public int currentPingMillis() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return -1;
        }

        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            return -1;
        }

        PlayerInfo selfInfo = connection.getPlayerInfo(player.getUUID());
        if (selfInfo == null) {
            return -1;
        }

        int latency = selfInfo.getLatency();
        return latency < 0 ? -1 : latency;
    }
}
