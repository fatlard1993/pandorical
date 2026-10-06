package justfatlard.pandorical.capture;

import justfatlard.pandorical.api.Capture;
import justfatlard.pandorical.api.CaptureApi;
import justfatlard.pandorical.protocol.CapturePartC2S;
import justfatlard.pandorical.protocol.CaptureRequestS2C;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Server side of captures: asks, gathers the slices, and checks what arrives before handing it on. */
public final class Captures implements CaptureApi {
    public static final Captures INSTANCE = new Captures();

    private static final Logger LOGGER = LoggerFactory.getLogger("pandorical");
    private static final int TIMEOUT_TICKS = 200;

    private static final class Pending {
        final int id;
        final int side;
        final long deadline;
        final CompletableFuture<Optional<Capture>> result = new CompletableFuture<>();
        byte[][] parts;
        int received;

        Pending(int id, int side, long deadline) {
            this.id = id;
            this.side = side;
            this.deadline = deadline;
        }
    }

    /** Server thread only. */
    private final Map<UUID, Pending> pending = new HashMap<>();
    private int nextId;
    private long tick;

    private Captures() {}

    /** Once on each side, before any player connects: it registers the payload types. */
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(CaptureRequestS2C.TYPE, CaptureRequestS2C.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CapturePartC2S.TYPE, CapturePartC2S.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CapturePartC2S.TYPE, (payload, context) ->
            context.server().execute(() -> INSTANCE.receive(context.player(), payload)));
        ServerTickEvents.END_SERVER_TICK.register(server -> INSTANCE.expire());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            server.execute(() -> INSTANCE.finish(handler.player.getUUID(), null)));
    }

    @Override
    public boolean canCapture(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, CaptureRequestS2C.TYPE);
    }

    @Override
    public CompletableFuture<Optional<Capture>> request(ServerPlayer player, int side) {
        if (side < 1 || side > MOST_SIDE) throw new IllegalArgumentException("a capture " + side + " across");
        if (!canCapture(player) || pending.containsKey(player.getUUID())) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Pending asked = new Pending(nextId++, side, tick + TIMEOUT_TICKS);
        pending.put(player.getUUID(), asked);
        ServerPlayNetworking.send(player, new CaptureRequestS2C(asked.id, side));
        return asked.result;
    }

    private void receive(ServerPlayer player, CapturePartC2S part) {
        Pending asked = pending.get(player.getUUID());
        if (asked == null || asked.id != part.id()) return;
        if (part.total() == 0) {
            finish(player.getUUID(), null);
            return;
        }
        if (asked.parts == null) {
            if (part.total() > mostParts(asked.side)) {
                refuse(player, "a capture in " + part.total() + " parts");
                return;
            }
            asked.parts = new byte[part.total()][];
        }
        if (part.total() != asked.parts.length || part.index() < 0 || part.index() >= asked.parts.length
                || asked.parts[part.index()] != null) {
            refuse(player, "capture part " + part.index() + " of " + part.total());
            return;
        }
        asked.parts[part.index()] = part.bytes();
        if (++asked.received < asked.parts.length) return;

        ByteArrayOutputStream whole = new ByteArrayOutputStream();
        for (byte[] bytes : asked.parts) whole.writeBytes(bytes);
        byte[] png = whole.toByteArray();
        int[] argb = decode(png, asked.side);
        if (argb == null) {
            refuse(player, "a capture that is not a " + asked.side + " square PNG");
            return;
        }
        finish(player.getUUID(), new Capture(asked.side, argb, png));
    }

    /** Worst case for a PNG is about its raw pixels; more than that is not a picture. */
    private static int mostParts(int side) {
        long most = (long) side * side * 4 + 4096;
        return (int) ((most + CapturePartC2S.MOST_BYTES - 1) / CapturePartC2S.MOST_BYTES);
    }

    /** The size is read from the header before anything is decoded, so a lying header costs nothing. */
    private static int[] decode(byte[] png, int side) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(png))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
            if (stream == null || !readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                if (reader.getWidth(0) != side || reader.getHeight(0) != side) return null;
                BufferedImage image = reader.read(0);
                int[] argb = image.getRGB(0, 0, side, side, null, 0, side);
                for (int i = 0; i < argb.length; i++) argb[i] |= 0xFF000000;
                return argb;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private void refuse(ServerPlayer player, String what) {
        LOGGER.warn("[Pandorical] {} sent {}; the capture is dropped", player.getName().getString(), what);
        finish(player.getUUID(), null);
    }

    private void finish(UUID player, Capture capture) {
        Pending asked = pending.remove(player);
        if (asked != null) asked.result.complete(Optional.ofNullable(capture));
    }

    private void expire() {
        tick++;
        // Gathered first: completing runs the caller's code, which may ask again.
        List<UUID> late = new ArrayList<>();
        pending.forEach((player, asked) -> {
            if (asked.deadline <= tick) late.add(player);
        });
        for (UUID player : late) finish(player, null);
    }
}
