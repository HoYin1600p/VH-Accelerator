package dev.hoyin1600p.vhaccelerator.client.update;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.SharedConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ScreenOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.internal.BrandingControl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Client-only, source-copyable update notification unit.
 *
 * <p>The remote request and version comparison run asynchronously and remain
 * independent of Forge's global update-check preference. This class adds a
 * coordinated main-menu row and a persistent in-world reminder schedule
 * without blocking the render thread.</p>
 */
public final class UpdateNoticeService {
    public static final String ENABLED_PROPERTY = "hoyinUpdateNotifier";
    public static final String NAME_PROPERTY = "hoyinUpdateName";
    private static final Logger LOGGER = LogManager.getLogger(
            UpdateNoticeService.class
    );

    private static Registration registration;
    private static IModInfo modInfo;
    private static UpdateNoticeStateStore stateStore;
    private static final UpdateNoticeSession SESSION =
            new UpdateNoticeSession();
    private static CompletableFuture<Optional<UpdateNotice>> updateRequest;
    private static UpdateNotice currentNotice;
    private static List<IModInfo> coordinatedMods;
    private static boolean resultResolved;
    private static int refreshTicks;
    private static boolean enabled;

    private UpdateNoticeService() {
    }

    public static synchronized void initialize(
            String modId,
            String displayName,
            String manifestUrl,
            String downloadUrl,
            boolean initiallyEnabled
    ) {
        if (registration != null) {
            return;
        }

        registration = new Registration(
                modId,
                displayName,
                manifestUrl,
                downloadUrl
        );
        modInfo = ModList.get()
                .getModContainerById(modId)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing active mod container for " + modId
                ))
                .getModInfo();
        stateStore = new UpdateNoticeStateStore(modId);
        coordinatedMods = discoverCoordinatedMods();
        enabled = initiallyEnabled;

        MinecraftForge.EVENT_BUS.addListener(
                UpdateNoticeService::onScreenOpened
        );
        MinecraftForge.EVENT_BUS.addListener(
                UpdateNoticeService::onScreenDrawn
        );
        MinecraftForge.EVENT_BUS.addListener(
                UpdateNoticeService::onPlayerLoggedIn
        );
        MinecraftForge.EVENT_BUS.addListener(
                UpdateNoticeService::onPlayerLoggedOut
        );
        MinecraftForge.EVENT_BUS.addListener(
                UpdateNoticeService::onLevelRendered
        );
        MinecraftForge.EVENT_BUS.addListener(
                UpdateNoticeService::onClientTick
        );
        if (enabled) {
            startUpdateRequest();
        } else {
            resultResolved = true;
        }
    }

    public static synchronized void setEnabled(boolean requestedEnabled) {
        if (registration == null || enabled == requestedEnabled) {
            return;
        }

        enabled = requestedEnabled;
        if (enabled) {
            startUpdateRequest();
            return;
        }

        if (updateRequest != null) {
            updateRequest.cancel(true);
        }
        updateRequest = null;
        currentNotice = null;
        refreshTicks = 0;
        resultResolved = true;
        SESSION.disable();
    }

    private static void startUpdateRequest() {
        currentNotice = null;
        refreshTicks = 0;
        resultResolved = false;
        updateRequest = UpdateManifestFetcher.fetch(
                registration.manifestUri(),
                registration.modId(),
                registration.displayName(),
                modInfo.getVersion().toString(),
                SharedConstants.getCurrentVersion().getName(),
                registration.downloadUrl()
        );
        refreshUpdateResult();
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (stateStore != null) {
            stateStore.tick();
        }
        if (!enabled || resultResolved) {
            return;
        }
        refreshTicks++;
        if (refreshTicks >= 20) {
            refreshTicks = 0;
            refreshUpdateResult();
        }
    }

    private static void onScreenOpened(ScreenOpenEvent event) {
        if (event.getScreen() instanceof ReceivingLevelScreen) {
            SESSION.markReceivingLevel();
        }
    }

    private static void onPlayerLoggedIn(
            ClientPlayerNetworkEvent.LoggedInEvent event
    ) {
        if (event.getPlayer() == null) {
            return;
        }
        SESSION.markPlayerLoggedIn();
    }

    private static void onPlayerLoggedOut(
            ClientPlayerNetworkEvent.LoggedOutEvent event
    ) {
        SESSION.markPlayerLoggedOut();
    }

    private static void onLevelRendered(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!SESSION.needsPlayableFrame()
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER
                || minecraft.level == null
                || minecraft.player == null
                || minecraft.screen instanceof ReceivingLevelScreen) {
            return;
        }

        SESSION.markPlayableFrame();
        refreshUpdateResult();
        recordEligibleLaunchAndNotify();
    }

    private static void onScreenDrawn(ScreenEvent.DrawScreenEvent.Post event) {
        if (!enabled
                || !(event.getScreen() instanceof TitleScreen)
                || currentNotice == null) {
            return;
        }

        int slot = updateNoticeSlot(modInfo.getModId());
        if (slot < 0) {
            return;
        }

        int[] brandingLines = {0};
        BrandingControl.forEachLine(
                true,
                true,
                (line, text) -> brandingLines[0] = line + 1
        );
        int launchTimerRows = ModList.get().isLoaded("vhaccelerator") ? 1 : 0;
        int y = event.getScreen().height
                - (10 + brandingLines[0] * 10)
                - ((slot + launchTimerRows) * 10);
        int color = currentNotice.severity()
                == UpdateNotice.Severity.CRITICAL
                ? 0xFF5555
                : 0xFFAA00;
        String text = currentNotice.displayName() + " - Update Available";
        if (!currentNotice.message().isBlank()) {
            text += " - " + currentNotice.message();
        }

        event.getPoseStack().pushPose();
        GuiComponent.drawString(
                event.getPoseStack(),
                Minecraft.getInstance().font,
                text,
                2,
                y,
                color
        );
        event.getPoseStack().popPose();
    }

    private static synchronized void refreshUpdateResult() {
        if (!enabled || resultResolved || modInfo == null) {
            return;
        }

        if (updateRequest == null || !updateRequest.isDone()) {
            return;
        }

        resultResolved = true;
        try {
            currentNotice = updateRequest.join().orElse(null);
        } catch (CompletionException exception) {
            currentNotice = null;
            Throwable cause = exception.getCause() == null
                    ? exception
                    : exception.getCause();
            LOGGER.warn(
                    "Failed to fetch update manifest for {} from {}: {}",
                    registration.displayName(),
                    registration.manifestUri(),
                    cause.toString()
            );
        }
        if (currentNotice == null) {
            return;
        }
        recordEligibleLaunchAndNotify();
    }

    private static synchronized void recordEligibleLaunchAndNotify() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!enabled
                || !resultResolved
                || currentNotice == null) {
            return;
        }

        if (SESSION.claimEligibleLaunch()) {
            SESSION.armReminder(
                    stateStore.recordEligibleLaunch(currentNotice)
            );
        }

        if (minecraft.player != null
                && minecraft.level != null
                && SESSION.claimReminderDelivery()) {
            showChatNotice(minecraft, currentNotice);
        }
    }

    private static void showChatNotice(
            Minecraft minecraft,
            UpdateNotice notice
    ) {
        ChatFormatting noticeColor = notice.severity()
                == UpdateNotice.Severity.CRITICAL
                ? ChatFormatting.RED
                : ChatFormatting.GOLD;
        MutableComponent text = new TextComponent(
                notice.displayName() + " Update Available"
        ).withStyle(noticeColor);
        if (!notice.message().isBlank()) {
            text.append(new TextComponent(
                    " - " + notice.message()
            ).withStyle(noticeColor));
        }
        text.append(new TextComponent(" [Download on CurseForge]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.OPEN_URL,
                                notice.downloadUrl()
                        ))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                new TextComponent(
                                        "Open the " + notice.displayName()
                                                + " CurseForge page"
                                )
                        ))));
        minecraft.player.displayClientMessage(text, false);
    }

    private static int updateNoticeSlot(String ownModId) {
        List<IModInfo> orderedMods = new ArrayList<>(coordinatedMods);
        orderedMods.sort(Comparator.comparing(
                UpdateNoticeService::coordinatedDisplayName,
                String.CASE_INSENSITIVE_ORDER
        ).thenComparing(IModInfo::getModId));
        for (int index = 0; index < orderedMods.size(); index++) {
            if (orderedMods.get(index).getModId().equals(ownModId)) {
                return index;
            }
        }
        return -1;
    }

    private static List<IModInfo> discoverCoordinatedMods() {
        List<IModInfo> result = new ArrayList<>();
        for (IModInfo candidate : ModList.get().getMods()) {
            Object enabled = candidate.getModProperties().get(ENABLED_PROPERTY);
            if ((enabled instanceof Boolean && (Boolean) enabled)
                    || (enabled instanceof String
                    && Boolean.parseBoolean((String) enabled))) {
                result.add(candidate);
            }
        }
        return List.copyOf(result);
    }

    private static String coordinatedDisplayName(IModInfo candidate) {
        Object configuredName = candidate.getModProperties().get(NAME_PROPERTY);
        if (configuredName instanceof String
                && !((String) configuredName).isBlank()) {
            return ((String) configuredName).trim();
        }
        return candidate.getDisplayName();
    }

    private record Registration(
            String modId,
            String displayName,
            URI manifestUri,
            String downloadUrl
    ) {
        private Registration {
            Objects.requireNonNull(modId, "modId");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(manifestUri, "manifestUri");
            Objects.requireNonNull(downloadUrl, "downloadUrl");
            if (modId.isBlank() || displayName.isBlank()) {
                throw new IllegalArgumentException(
                        "Update notification identity cannot be blank"
                );
            }

            validateManifestUri(manifestUri);

            URI downloadUri = URI.create(downloadUrl);
            String host = downloadUri.getHost();
            if (!"https".equalsIgnoreCase(downloadUri.getScheme())
                    || host == null
                    || !(host.equalsIgnoreCase("curseforge.com")
                    || host.toLowerCase(Locale.ROOT)
                    .endsWith(".curseforge.com"))) {
                throw new IllegalArgumentException(
                        "Update download URL must be an HTTPS CurseForge URL"
                );
            }
        }

        private Registration(
                String modId,
                String displayName,
                String manifestUrl,
                String downloadUrl
        ) {
            this(modId, displayName, URI.create(manifestUrl), downloadUrl);
        }

        private static void validateManifestUri(URI uri) {
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"raw.githubusercontent.com".equalsIgnoreCase(
                    uri.getHost()
            )) {
                throw new IllegalArgumentException(
                        "Update manifest URL must be an HTTPS raw GitHub URL"
                );
            }
        }
    }
}
