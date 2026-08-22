package com.SwagDev.SwagHub;

import com.SwagDev.SwagAPI.SwagAPI;
import com.SwagDev.SwagAPI.api.IDatabaseService;
import com.SwagDev.SwagAPI.api.IEconomyService;
import com.SwagDev.SwagAPI.api.IEventBusService;
import com.SwagDev.SwagAPI.api.IMessagingService;
import com.SwagDev.SwagAPI.api.IPlayerDataService;
import com.SwagDev.SwagAPI.api.IPrefixService;
import com.SwagDev.SwagAPI.api.IUpdateService;
import com.SwagDev.SwagAPI.api.IWebService;
import com.SwagDev.SwagHub.action.ActionParser;
import com.SwagDev.SwagHub.action.ActionRegistry;
import com.SwagDev.SwagHub.action.types.ActionBarAction;
import com.SwagDev.SwagHub.action.types.CenteredMessageAction;
import com.SwagDev.SwagHub.action.types.CloseMenuAction;
import com.SwagDev.SwagHub.action.types.ConsoleAction;
import com.SwagDev.SwagHub.action.types.CyclePlayerHiderAction;
import com.SwagDev.SwagHub.action.types.EffectAction;
import com.SwagDev.SwagHub.action.types.FireworkAction;
import com.SwagDev.SwagHub.action.types.MessageAction;
import com.SwagDev.SwagHub.action.types.OpenMenuAction;
import com.SwagDev.SwagHub.action.types.ParticleAction;
import com.SwagDev.SwagHub.action.types.PlayerAction;
import com.SwagDev.SwagHub.action.types.ServerAction;
import com.SwagDev.SwagHub.action.types.SoundAction;
import com.SwagDev.SwagHub.action.types.TeleportAction;
import com.SwagDev.SwagHub.action.types.TitleAction;
import com.SwagDev.SwagHub.api.SwagHubAPI;
import com.SwagDev.SwagHub.bedrock.BedrockService;
import com.SwagDev.SwagHub.command.SwagHubCommand;
import com.SwagDev.SwagHub.compat.CompatibilityManager;
import com.SwagDev.SwagHub.config.ConfigManager;
import com.SwagDev.SwagHub.data.SwagHubDatabase;
import com.SwagDev.SwagHub.data.SwagHubPlayerData;
import com.SwagDev.SwagHub.module.ModuleManager;
import com.SwagDev.SwagHub.modules.announcements.AnnouncementsModule;
import com.SwagDev.SwagHub.modules.antiwdl.AntiWdlModule;
import com.SwagDev.SwagHub.modules.chat.ChatControlsModule;
import com.SwagDev.SwagHub.modules.clearchat.ClearChatModule;
import com.SwagDev.SwagHub.modules.doublejump.DoubleJumpModule;
import com.SwagDev.SwagHub.modules.fly.FlyModule;
import com.SwagDev.SwagHub.modules.gamemode.GamemodeModule;
import com.SwagDev.SwagHub.modules.hologram.HologramModule;
import com.SwagDev.SwagHub.modules.join.JoinSettingsModule;
import com.SwagDev.SwagHub.modules.joinitems.JoinItemsModule;
import com.SwagDev.SwagHub.modules.launchpad.LaunchpadModule;
import com.SwagDev.SwagHub.modules.menu.MenuModule;
import com.SwagDev.SwagHub.modules.networkstats.NetworkStatsModule;
import com.SwagDev.SwagHub.modules.playerhider.PlayerHiderModule;
import com.SwagDev.SwagHub.modules.playerstate.PlayerStateModule;
import com.SwagDev.SwagHub.modules.portal.PortalModule;
import com.SwagDev.SwagHub.modules.protection.WorldProtectionModule;
import com.SwagDev.SwagHub.modules.proxy.ProxyModule;
import com.SwagDev.SwagHub.modules.proxy.ProxyService;
import com.SwagDev.SwagHub.modules.scoreboard.ScoreboardModule;
import com.SwagDev.SwagHub.modules.spawn.SpawnModule;
import com.SwagDev.SwagHub.modules.tablist.TablistModule;
import com.SwagDev.SwagHub.modules.teleportbow.TeleportBowModule;
import com.SwagDev.SwagHub.modules.vanish.VanishModule;
import com.SwagDev.SwagHub.modules.webeditor.WebEditorModule;
import com.SwagDev.SwagHub.placeholder.SwagHubExpansion;
import com.SwagDev.SwagHub.placeholder.SwagHubPlaceholders;
import com.SwagDev.SwagHub.util.MessageUtil;
import com.SwagDev.SwagHub.util.PlayerVisibilityCoordinator;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * SwagHub's main class.
 *
 * <p><b>Build step 1 scope</b> (see Gameplan/SwagHub-AI-Prompt-Document (1).md §11):
 * project skeleton, SwagAPI service wiring, {@link ModuleManager}, {@link CompatibilityManager}
 * + server-role, config framework, {@link MessageUtil}, and the action system core.</p>
 *
 * <p><b>Build step 2 scope:</b> the first real feature modules — {@link SpawnModule}
 * (spawn/lobby + persistence), {@link WorldProtectionModule}, and
 * {@link JoinSettingsModule} — are registered with {@link ModuleManager} below. See
 * DECISIONS.md's Step 2 section for the module-boundary reasoning.</p>
 *
 * <p><b>Build step 3 scope:</b> {@link ProxyModule} (the {@code bungeecord:main}
 * plugin-messaging proxy service — Connect/ConnectOther/PlayerCount/PlayerList/
 * GetServers, §3) is registered alongside the step 2 modules, and the {@code
 * [server]} action type is registered as a builtin. See DECISIONS.md's Step 3
 * section.</p>
 *
 * <p><b>Build step 4 scope:</b> {@link JoinItemsModule} (§5.2) and {@link MenuModule}
 * (§5.3 — the server-selector/menu engine) are registered, and {@code [open-menu]}/
 * {@code [close-menu]} are registered as builtin action types. See DECISIONS.md's
 * Step 4 section.</p>
 *
 * <p><b>Build step 5 scope:</b> {@link ScoreboardModule} (§5.4), {@link TablistModule}
 * (§5.5), and {@link AnnouncementsModule} (§5.6) are registered; {@code [actionbar]},
 * {@code [title]}, {@code [sound]}, and {@code [centered-message]} are registered as
 * builtin action types; and SwagHub's own {@link SwagHubPlayerData} module (per-player
 * scoreboard toggle) is initialized and registered with SwagAPI's
 * {@link IPlayerDataService}. See DECISIONS.md's Step 5 section.</p>
 *
 * <p><b>Build step 6 scope:</b> {@link DoubleJumpModule}/{@link LaunchpadModule}/
 * {@link TeleportBowModule}/{@link PlayerHiderModule} (§5.7 Movement &amp; Fun),
 * {@link FlyModule}/{@link GamemodeModule}/{@link VanishModule} (§5.10 Commands &amp;
 * Vanish), and {@link ChatControlsModule}/{@link ClearChatModule}/
 * {@link AntiWdlModule} (§5.1's chat controls + Anti-WorldDownloader, deferred from
 * step 2) are all registered; {@code [cycle-player-hider]} is registered as a builtin
 * action type; and {@link PlayerVisibilityCoordinator} is constructed to arbitrate
 * {@code hidePlayer}/{@code showPlayer} calls between {@link PlayerHiderModule} and
 * {@link VanishModule}. See DECISIONS.md's Step 6 section.</p>
 *
 * <p><b>Build step 7 scope:</b> {@link HologramModule} (§5.8) and {@link PortalModule}
 * (§3.3/§5.8) are registered; {@code [teleport]}, {@code [firework]},
 * {@code [particle]}, and {@code [effect]} — the remaining §5.9 action types that were
 * never registered in any prior step — are registered as builtins. See DECISIONS.md's
 * Step 7 section.</p>
 *
 * <p><b>Build step 8 scope:</b> §5.11 Extras — a formal {@link SwagHubExpansion}
 * ({@code PlaceholderAPI} expansion, registered/unregistered around {@link
 * #hookSwagAPI()}'s runtime presence check, wrapping {@link SwagHubPlaceholders}'s
 * new player-scoped tokens), the developer API ({@link SwagHubAPI}, obtained via
 * {@link #getAPI()}, plus the three new events under {@code
 * com.SwagDev.SwagHub.api.event} — {@code PlayerSendToServerEvent}, {@code
 * PlayerDoubleJumpEvent}, {@code MenuOpenEvent} — fired from {@link ProxyModule}'s
 * service, {@link DoubleJumpModule}, and {@link MenuModule} respectively), toggleable
 * {@code bStats} metrics (a shaded/relocated {@link Metrics} instance, gated on
 * {@code metrics.enabled} in config.yml), and — requiring no new code of its own — the
 * §6.4 {@code IUpdateService} update checker, whose only consumer added this step is a
 * one-line "update available" surface in {@code /ah info}. See DECISIONS.md's Step 8
 * section for the full list of resolved ambiguities.</p>
 *
 * <p><b>Build step 9 scope:</b> {@link WebEditorModule} (§7 — the hub-options web
 * editor, registered against SwagAPI's shared {@code IWebService} at
 * {@code /swagapi/swaghub/}) is registered alongside every module above. Scoped
 * deliberately narrower than SwagCore's own full admin dashboard — read/write editors
 * for core server-role/hub-worlds/module-toggle/compatibility-override settings plus
 * {@link ScoreboardModule}/{@link TablistModule}/{@link AnnouncementsModule}'s own
 * YAML files, but NOT holograms/portals/items/menus this step. See DECISIONS.md's
 * Step 9 section for the full reasoning, the exact JSON contract each editor uses, and
 * the documented online-only permission-check limitation (no Vault dependency).</p>
 *
 * <p><b>Patch 1 scope (post-launch fixes — see DECISIONS.md's Patch 1 section):</b>
 * §8 Bedrock support is implemented ({@link BedrockService}, resolved in {@link
 * #finishStartup(long)}), and the plugin-enable-order hazard that used to make
 * {@link CompatibilityManager}'s auto-yield decisions (and the PlaceholderAPI
 * expansion registration) depend on WHEN Bukkit happened to enable SwagCore/
 * PlaceholderAPI relative to SwagHub itself is fixed: {@link #moduleManager}{@code
 * .enableAll()}, the PlaceholderAPI expansion registration, and {@link BedrockService}
 * detection are all deferred from {@link #onEnable()} into {@link
 * #finishStartup(long)}, which runs on the first server tick — by which point every
 * plugin on the server has already finished its own {@code onEnable()}, regardless of
 * this plugin's position in {@code plugin.yml}'s dependency-sorted enable order.</p>
 *
 * <p><b>Patch 2 scope (post-launch fix — see DECISIONS.md's Patch 2 section):</b>
 * {@link PlayerStateModule} is registered (persists a player's gamemode/flight/
 * fly-speed across a relog, restoring it — behind the new {@code
 * swaghub.bypass.joingamemode} permission — instead of letting {@link
 * JoinSettingsModule}'s forced lobby-gamemode reset silently drop a staff member out
 * of a flying creative session), and {@link FlyModule} gains a new {@code
 * /flyspeed <1-10> [player]} command that writes into the same persisted field.</p>
 */
public final class SwagHub extends JavaPlugin {

    /**
     * Build step 8, resolved ambiguity (DECISIONS.md Step 8): bStats requires a real
     * numeric plugin id registered at
     * <a href="https://bstats.org/what-is-my-plugin-id">bstats.org/what-is-my-plugin-id</a>,
     * which does not exist for SwagHub yet. <b>THIS PLACEHOLDER MUST BE REPLACED WITH
     * A REAL REGISTERED ID BEFORE ANY PUBLIC RELEASE.</b> Left as a loudly-commented
     * placeholder (with a matching runtime warning in {@link #onEnable()}) rather than
     * blocking this build step, per §0 rule 5.
     */
    private static final int BSTATS_PLUGIN_ID = 0;

    private static SwagHub instance;

    // ─── SwagAPI hooks (mirrors SwagCore's hookSwagAPI() pattern exactly) ───
    private SwagAPI swagAPI;
    private IDatabaseService databaseService;
    private IPlayerDataService playerDataService;
    private IEconomyService economyService;
    private IMessagingService messagingService;
    private IEventBusService eventBusService;
    private IUpdateService updateService;
    private IWebService webService;
    private IPrefixService prefixService;

    // ─── Core managers ───
    private ConfigManager configManager;
    private MessageUtil messageUtil;
    private CompatibilityManager compatibilityManager;
    private ModuleManager moduleManager;
    private ActionRegistry actionRegistry;
    private ActionParser actionParser;
    private ProxyModule proxyModule;
    private MenuModule menuModule;
    private JoinItemsModule joinItemsModule;
    private ScoreboardModule scoreboardModule;
    private TablistModule tablistModule;
    private AnnouncementsModule announcementsModule;
    private PlayerVisibilityCoordinator playerVisibilityCoordinator;
    private DoubleJumpModule doubleJumpModule;
    private LaunchpadModule launchpadModule;
    private TeleportBowModule teleportBowModule;
    private PlayerHiderModule playerHiderModule;
    private FlyModule flyModule;
    private GamemodeModule gamemodeModule;
    private VanishModule vanishModule;
    private ChatControlsModule chatControlsModule;
    private ClearChatModule clearChatModule;
    private AntiWdlModule antiWdlModule;
    private HologramModule hologramModule;
    private PortalModule portalModule;
    private WebEditorModule webEditorModule;
    private NetworkStatsModule networkStatsModule;
    private SwagHubAPI api;
    private SwagHubExpansion placeholderExpansion;
    private Metrics metrics;
    private BedrockService bedrockService;

    @Override
    public void onEnable() {
        instance = this;
        long start = System.currentTimeMillis();

        // 1. Hard-fail without SwagAPI (Bukkit's `depend: [SwagAPI]` already guarantees load
        //    order, but SwagAPI could still be present-but-disabled if it failed its own enable).
        if (!hookSwagAPI()) {
            getLogger().severe("SwagAPI not found or not enabled! SwagHub cannot start.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2. SwagHub's own database table(s) + PlayerDataModule registration (§6.4) —
        //    right after hookSwagAPI() succeeds, before any feature module is registered,
        //    so a module that needs per-player data (ScoreboardModule's toggle) can always
        //    call getModuleData/setModuleData safely once it enables.
        new SwagHubDatabase(this, databaseService).init();
        playerDataService.registerModule("swaghub", new SwagHubPlayerData());

        // 3. Config framework.
        configManager = new ConfigManager(this);
        configManager.load();

        // 4. Messages/MiniMessage utility.
        messageUtil = new MessageUtil(this);

        // 5. Compatibility + server-role system — must exist and be loaded BEFORE ModuleManager
        //    enables anything, per §11 step 1's ordering requirement.
        compatibilityManager = new CompatibilityManager(this);
        compatibilityManager.load();

        // 6. Module manager.
        moduleManager = new ModuleManager(this, compatibilityManager);

        // 6b. Shared visibility coordinator (build step 6) — must exist before
        //     registerFeatureModules(), since PlayerHiderModule/VanishModule both
        //     consult it from their own onEnable(). See PlayerVisibilityCoordinator's
        //     javadoc for why this is needed at all.
        playerVisibilityCoordinator = new PlayerVisibilityCoordinator();

        // 7. Action system core, with the self-contained action types registered end-to-end.
        //    Must exist before step 8 registers modules, since JoinSettingsModule's
        //    first-join extras call plugin.getActionParser() from its own onEnable().
        actionRegistry = new ActionRegistry(this);
        actionParser = new ActionParser(this, actionRegistry);
        registerBuiltinActionTypes();

        // 8. Feature modules (build step 2 — see DECISIONS.md's Step 2 section for the
        //    module-boundary reasoning behind this exact three-way split and each
        //    module's isEnabledByDefault() behavior).
        registerFeatureModules();

        // 8b. Build step 8 — SwagHubAPI facade (§5.11). Constructed after
        //     actionRegistry (step 7) and menuModule (inside registerFeatureModules()
        //     above) both exist, since SwagHubAPI#registerActionType/#registerMenu
        //     delegate straight to them.
        api = new SwagHubAPI(this);

        // 8c. Build step 8 — bStats metrics (§5.11), toggleable via config.yml's
        //     "metrics.enabled" (default true).
        //
        //     Patch 1, Fix 3 (resolved ambiguity — see DECISIONS.md's Patch 1 section):
        //     the v1 behavior below sent unattributed metrics to bStats under plugin id
        //     0 whenever BSTATS_PLUGIN_ID's placeholder was never updated, alongside a
        //     warning nobody could actually act on (0 was already being sent either
        //     way). Sending metrics nobody can look up on bstats.org helps nobody — a
        //     placeholder id now means bStats is simply never constructed, with one
        //     clear info line explaining why and how to fix it, instead of a warning
        //     plus silently-useless network traffic every startup.
        if (getConfig().getBoolean("metrics.enabled", true)) {
            if (BSTATS_PLUGIN_ID <= 0) {
                getLogger().info("bStats metrics are enabled in config.yml, but SwagHub.BSTATS_PLUGIN_ID is "
                        + "still a PLACEHOLDER (" + BSTATS_PLUGIN_ID + ") — metrics are disabled until a real "
                        + "id is registered at https://bstats.org/what-is-my-plugin-id and this constant is "
                        + "updated. No metrics are being sent.");
            } else {
                metrics = new Metrics(this, BSTATS_PLUGIN_ID);
            }
        }

        // 9. Root command.
        PluginCommand command = getCommand("swaghub");
        if (command != null) {
            SwagHubCommand executor = new SwagHubCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("Could not find the 'swaghub' command — check plugin.yml.");
        }

        // 10. Patch 1, Fix 5 (resolved ambiguity — see DECISIONS.md's Patch 1 section):
        //     everything that depends on the FINAL, complete plugin set — the
        //     PlaceholderAPI expansion registration, §8 Bedrock/Floodgate detection,
        //     and (most importantly) CompatibilityManager's auto-yield module-enabling
        //     decisions — is deferred to finishStartup(), scheduled for the first
        //     server tick after every plugin on the server (regardless of THIS
        //     plugin's own position in Bukkit's dependency-sorted enable order) has
        //     already finished its own onEnable(). See finishStartup()'s own javadoc
        //     for the live bug this fixes.
        Bukkit.getScheduler().runTask(this, () -> finishStartup(start));
    }

    /**
     * Patch 1, Fix 5 (see DECISIONS.md's Patch 1 section for the full ambiguity
     * write-up). <b>The live bug:</b> SwagHub used to run its auto-yield module-enable
     * decision (and the PlaceholderAPI expansion registration) synchronously inside
     * {@link #onEnable()}. Bukkit enables plugins in dependency-sorted order, but
     * {@code depend}/{@code softdepend} only constrains plugins SwagHub itself
     * declares a dependency on (SwagAPI) or that declare a dependency ON SwagHub —
     * SwagCore declaring no relationship to SwagHub at all meant Bukkit was free to
     * enable SwagHub BEFORE SwagCore, at which point {@code
     * CompatibilityManager#findConflictingPlugin}'s {@code other.isEnabled()} check
     * correctly (by its own logic) saw SwagCore as not-yet-enabled and enabled every
     * module SwagCore should have yielded — producing zero yield lines at boot and two
     * plugins fighting over the scoreboard/tablist/announcements/holograms for as long
     * as nobody happened to run {@code /ah reload}.
     *
     * <p><b>The fix:</b> this method runs on the FIRST SERVER TICK after {@link
     * #onEnable()} returns — scheduled via {@code Bukkit.getScheduler().runTask(...)}
     * at the very end of {@link #onEnable()}. Bukkit enables every plugin on the
     * server (successfully or not) before the very first tick of the game loop ever
     * runs, so by the time this method executes, {@code
     * getPluginManager().getPlugin("SwagCore").isEnabled()} (and PlaceholderAPI's, and
     * Floodgate's) reflects the TRUE, FINAL state of the server's plugin set — not
     * whatever subset happened to already be enabled at SwagHub's own arbitrary
     * position in the enable order. This makes {@link ModuleManager#enableAll()}'s
     * result here provably identical to a subsequent {@code /ah reload}'s result
     * (both call the exact same {@link CompatibilityManager#shouldEnable(String,
     * boolean)} logic against the same, by-then-stable plugin set) — see §6.6's
     * acceptance tests. The one-tick delay (≈50ms) is not observable to players: no
     * client can complete a login handshake in that window, so nobody can ever see a
     * module in its pre-finishStartup() state.</p>
     *
     * <p>Order within this method matters: {@link BedrockService} is resolved BEFORE
     * {@link ModuleManager#enableAll()} runs, since {@link
     * com.SwagDev.SwagHub.modules.doublejump.DoubleJumpModule#onEnable()} consults
     * {@link #getBedrockService()} while reconciling already-online players (a no-op
     * on a fresh boot, but relevant to a hot module re-enable via {@code /ah
     * reload}).</p>
     */
    private void finishStartup(long start) {
        // PlaceholderAPI expansion (§5.11) — moved here from onEnable() itself (Patch 1,
        // Fix 5's audit — see this method's own javadoc): the exact same order hazard
        // applied to this registration as to CompatibilityManager's yield decisions.
        var papi = getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (papi != null && papi.isEnabled()) {
            placeholderExpansion = new SwagHubExpansion(this, new SwagHubPlaceholders(this));
            if (placeholderExpansion.register()) {
                getLogger().info("Registered the PlaceholderAPI expansion ('swaghub').");
            } else {
                getLogger().warning("Failed to register the PlaceholderAPI expansion ('swaghub') — SwagHub placeholders will be unavailable.");
                placeholderExpansion = null;
            }
        }

        // Patch 1, Fix 1 (§8) — Bedrock/Floodgate detection. Uses the exact same
        // order-independent, presence-checked-after-every-plugin-has-enabled pattern
        // as the PlaceholderAPI expansion above and CompatibilityManager's auto-yield
        // decisions below — see BedrockService#create's own javadoc.
        bedrockService = BedrockService.create(this);

        // Patch 1, Fix 5 — the actual fix: module enabling (and therefore every
        // CompatibilityManager#shouldEnable(...) auto-yield decision) now runs here,
        // after every plugin on the server has finished enabling, instead of inside
        // onEnable() itself. See this method's own javadoc for the live bug this closes.
        moduleManager.enableAll();

        long elapsed = System.currentTimeMillis() - start;
        getLogger().info("SwagHub v" + getPluginMeta().getVersion() + " enabled in " + elapsed
                + "ms. Server role: " + compatibilityManager.getServerRole() + ".");
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableAll();
        }
        // Build step 8: unregister the PlaceholderAPI expansion, if it was ever
        // registered — bStats' own Metrics instance needs no explicit shutdown call
        // (its repeating task is tied to this plugin and stops automatically).
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (eventBusService != null) {
            eventBusService.unsubscribeAll(this);
        }
        getLogger().info("SwagHub disabled.");
    }

    /**
     * Hooks SwagAPI exactly like SwagCore's {@code hookSwagAPI()}: look up the plugin by
     * name, cast, and check {@code isEnabled()} for the hard-required services
     * ({@code IDatabaseService}, {@code IPlayerDataService}) pulled directly off the
     * SwagAPI instance; resolve the remaining, optional services via the ServicesManager
     * so their absence never prevents SwagHub from starting.
     */
    private boolean hookSwagAPI() {
        var plugin = getServer().getPluginManager().getPlugin("SwagAPI");
        if (!(plugin instanceof SwagAPI api)) {
            return false;
        }
        if (!plugin.isEnabled()) {
            return false;
        }

        swagAPI = api;
        databaseService = api.getDatabaseService();
        playerDataService = api.getPlayerDataService();

        ServicesManager sm = getServer().getServicesManager();

        var economyProvider = sm.getRegistration(IEconomyService.class);
        if (economyProvider != null) {
            economyService = economyProvider.getProvider();
        }

        var messagingProvider = sm.getRegistration(IMessagingService.class);
        if (messagingProvider != null) {
            messagingService = messagingProvider.getProvider();
        }

        var eventBusProvider = sm.getRegistration(IEventBusService.class);
        if (eventBusProvider != null) {
            eventBusService = eventBusProvider.getProvider();
        }

        // Web service — guarded on isRunning() by whatever registers a module against it (§7.1);
        // not used at all in this build step, resolved here only so it's available later.
        var webProvider = sm.getRegistration(IWebService.class);
        if (webProvider != null) {
            webService = webProvider.getProvider();
        }

        var updateProvider = sm.getRegistration(IUpdateService.class);
        if (updateProvider != null) {
            updateService = updateProvider.getProvider();
        }

        var prefixProvider = sm.getRegistration(IPrefixService.class);
        if (prefixProvider != null) {
            prefixService = prefixProvider.getProvider();
        }

        return true;
    }

    private void registerBuiltinActionTypes() {
        actionRegistry.register(new MessageAction(this));
        actionRegistry.register(new ConsoleAction(this));
        actionRegistry.register(new PlayerAction(this));
        actionRegistry.register(new ServerAction(this));
        actionRegistry.register(new OpenMenuAction(this));
        actionRegistry.register(new CloseMenuAction(this));
        actionRegistry.register(new ActionBarAction(this));
        actionRegistry.register(new TitleAction(this));
        actionRegistry.register(new SoundAction(this));
        actionRegistry.register(new CenteredMessageAction(this));
        actionRegistry.register(new CyclePlayerHiderAction(this));
        actionRegistry.register(new TeleportAction(this));
        actionRegistry.register(new FireworkAction(this));
        actionRegistry.register(new ParticleAction(this));
        actionRegistry.register(new EffectAction(this));
    }

    /**
     * Registers build step 2's feature modules, plus build step 3's {@link
     * ProxyModule} and build step 4's {@link JoinItemsModule}/{@link MenuModule}.
     * Registration order matters for the step 2 trio: {@link SpawnModule} is
     * registered (and therefore enabled/disabled) first so its
     * {@link org.bukkit.event.player.PlayerJoinEvent} handler — which runs at
     * {@code EventPriority.NORMAL} to teleport joining players to the lobby — always
     * has a chance to run before {@link JoinSettingsModule}'s {@code HIGH}-priority
     * join handler evaluates the player's (by-then-final) world. {@link
     * JoinItemsModule}'s own join handler runs at {@code HIGHEST} (later still) so
     * it never has its just-given items wiped by {@link JoinSettingsModule}'s
     * clear-inventory step — see DECISIONS.md Step 4. {@link ProxyModule} and
     * {@link MenuModule} have no such ordering dependency, so they're simply
     * appended after the ordering-sensitive trio.
     */
    private void registerFeatureModules() {
        moduleManager.register(new SpawnModule(this));
        moduleManager.register(new WorldProtectionModule(this));
        moduleManager.register(new JoinSettingsModule(this));
        joinItemsModule = new JoinItemsModule(this);
        moduleManager.register(joinItemsModule);
        proxyModule = new ProxyModule(this);
        moduleManager.register(proxyModule);
        menuModule = new MenuModule(this);
        moduleManager.register(menuModule);
        scoreboardModule = new ScoreboardModule(this);
        moduleManager.register(scoreboardModule);
        tablistModule = new TablistModule(this);
        moduleManager.register(tablistModule);
        announcementsModule = new AnnouncementsModule(this);
        moduleManager.register(announcementsModule);

        // Build step 6 — §5.7 Movement & Fun, §5.10 Commands & Vanish, plus the §5.1
        // chat controls/Anti-WDL deferred from step 2 (see DECISIONS.md Step 2's
        // "Deferred to a later build step" note and Step 6's own section). No ordering
        // dependency among these ten, or against the modules above, EXCEPT: double-jump
        // and fly both independently follow the same "only grant allowFlight when
        // currently false, only revoke your own tracked grants" invariant (see
        // DoubleJumpModule's javadoc) — registration order between them doesn't matter
        // for correctness, since neither module's enable() depends on the other's state.
        doubleJumpModule = new DoubleJumpModule(this);
        moduleManager.register(doubleJumpModule);
        launchpadModule = new LaunchpadModule(this);
        moduleManager.register(launchpadModule);
        teleportBowModule = new TeleportBowModule(this);
        moduleManager.register(teleportBowModule);
        playerHiderModule = new PlayerHiderModule(this);
        moduleManager.register(playerHiderModule);
        flyModule = new FlyModule(this);
        moduleManager.register(flyModule);
        gamemodeModule = new GamemodeModule(this);
        moduleManager.register(gamemodeModule);
        vanishModule = new VanishModule(this);
        moduleManager.register(vanishModule);
        chatControlsModule = new ChatControlsModule(this);
        moduleManager.register(chatControlsModule);
        clearChatModule = new ClearChatModule(this);
        moduleManager.register(clearChatModule);
        antiWdlModule = new AntiWdlModule(this);
        moduleManager.register(antiWdlModule);

        // Build step 7 — §5.8 holograms + §3.3/§5.8 proxy portals. Both are utility
        // modules (always-on regardless of server-role, like proxy/menus/launchpads)
        // with no ordering dependency on anything above or on each other.
        hologramModule = new HologramModule(this);
        moduleManager.register(hologramModule);
        portalModule = new PortalModule(this);
        moduleManager.register(portalModule);

        // Build step 9 — §7 web editor. A utility module like the ones directly above,
        // with no ordering dependency on anything else: it only reads/writes config
        // files that other modules own and calls their public reload() methods, it
        // never touches shared in-memory state at enable time.
        webEditorModule = new WebEditorModule(this);
        moduleManager.register(webEditorModule);

        // Reads other servers' player stats over HTTP (see class javadoc) — a utility module
        // like the ones directly above, no ordering dependency on anything else.
        networkStatsModule = new NetworkStatsModule(this);
        moduleManager.register(networkStatsModule);

        // Patch 2 — persists gamemode/flight/fly-speed across a relog (see
        // PlayerStateModule's own javadoc for the bug this fixes). Always enabled
        // regardless of server-role, like the utility modules above. No ordering
        // dependency on anything else — its onJoin handler runs at
        // EventPriority.MONITOR, which Bukkit always runs after JoinSettingsModule's
        // HIGH-priority handler regardless of registration order. Nothing else in this
        // class needs to reference it later, so it's registered inline (no field),
        // matching the step 2 trio's (SpawnModule/WorldProtectionModule/
        // JoinSettingsModule) inline-registration precedent above.
        moduleManager.register(new PlayerStateModule(this));
    }

    public static SwagHub getInstance() {
        return instance;
    }

    public SwagAPI getSwagAPI() {
        return swagAPI;
    }

    public IDatabaseService getDatabaseService() {
        return databaseService;
    }

    public IPlayerDataService getPlayerDataService() {
        return playerDataService;
    }

    public IEconomyService getEconomyService() {
        return economyService;
    }

    public IMessagingService getMessagingService() {
        return messagingService;
    }

    public IEventBusService getEventBusService() {
        return eventBusService;
    }

    public IUpdateService getUpdateService() {
        return updateService;
    }

    public IWebService getWebService() {
        return webService;
    }

    public IPrefixService getPrefixService() {
        return prefixService;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageUtil getMessageUtil() {
        return messageUtil;
    }

    public CompatibilityManager getCompatibilityManager() {
        return compatibilityManager;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public ActionRegistry getActionRegistry() {
        return actionRegistry;
    }

    public ActionParser getActionParser() {
        return actionParser;
    }

    public ProxyModule getProxyModule() {
        return proxyModule;
    }

    /**
     * Convenience passthrough to {@link ProxyModule#getService()} — the handle
     * {@code ServerAction}, {@code /ah proxy servers}, and later build steps (menus,
     * holograms, scoreboard, the §8 PlaceholderAPI expansion) actually call. May be
     * {@code null} only if called before {@link #onEnable()} has registered {@link
     * #proxyModule} (never true for any code running after plugin startup).
     */
    public ProxyService getProxyService() {
        return proxyModule != null ? proxyModule.getService() : null;
    }

    public MenuModule getMenuModule() {
        return menuModule;
    }

    public JoinItemsModule getJoinItemsModule() {
        return joinItemsModule;
    }

    public ScoreboardModule getScoreboardModule() {
        return scoreboardModule;
    }

    public TablistModule getTablistModule() {
        return tablistModule;
    }

    public AnnouncementsModule getAnnouncementsModule() {
        return announcementsModule;
    }

    public PlayerVisibilityCoordinator getPlayerVisibilityCoordinator() {
        return playerVisibilityCoordinator;
    }

    public DoubleJumpModule getDoubleJumpModule() {
        return doubleJumpModule;
    }

    public LaunchpadModule getLaunchpadModule() {
        return launchpadModule;
    }

    public TeleportBowModule getTeleportBowModule() {
        return teleportBowModule;
    }

    public PlayerHiderModule getPlayerHiderModule() {
        return playerHiderModule;
    }

    public FlyModule getFlyModule() {
        return flyModule;
    }

    public GamemodeModule getGamemodeModule() {
        return gamemodeModule;
    }

    public VanishModule getVanishModule() {
        return vanishModule;
    }

    public ChatControlsModule getChatControlsModule() {
        return chatControlsModule;
    }

    public ClearChatModule getClearChatModule() {
        return clearChatModule;
    }

    public AntiWdlModule getAntiWdlModule() {
        return antiWdlModule;
    }

    public HologramModule getHologramModule() {
        return hologramModule;
    }

    public PortalModule getPortalModule() {
        return portalModule;
    }

    public WebEditorModule getWebEditorModule() {
        return webEditorModule;
    }

    public NetworkStatsModule getNetworkStatsModule() {
        return networkStatsModule;
    }

    /** §5.11 developer API facade — see {@link SwagHubAPI}'s own javadoc. */
    public SwagHubAPI getAPI() {
        return api;
    }

    /**
     * Patch 1, Fix 1 (§8) — the single Bedrock/Floodgate-detection entry point every
     * feature must use instead of touching Floodgate classes directly. {@code null}
     * only if called before {@link #finishStartup(long)} has run (never true for any
     * code running after plugin startup — see that method's own javadoc for exactly
     * when it runs and why).
     */
    public BedrockService getBedrockService() {
        return bedrockService;
    }
}
