package dev.havoc.taxihud.phone;

import com.anezium.rokidbus.client.plugin.NexusImage;

final class TaxiHudImageFrame {
    final NexusImage image;
    final byte[] bytes;

    TaxiHudImageFrame(NexusImage image, byte[] bytes) {
        this.image = image;
        this.bytes = bytes;
    }
}
