package com.wellnet.client;

import net.minecraft.network.chat.Component;

public record PlayerFacingStatus(
    Component header,
    Component summary,
    Component action,
    Component reason,
    Component tip,
    Component footer
) {
}
