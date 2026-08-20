package dev.argorice.underlay.client.gui;

import dev.argorice.underlay.client.ClientProxy;
import dev.argorice.underlay.core.UnderlayOverrides;
import dev.argorice.underlay.core.UnderlayRules;
import dev.argorice.underlay.network.OverridesPayloads;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * In-game editor for which blocks accept an underlay.
 *
 * <p>Blocks are grouped by owning mod, groups collapse, a tri-state checkbox
 * toggles a whole group, and the search box matches both display names and
 * registry ids. What the user edits here is an <em>override</em> applied on
 * top of the datapack tags — the tags stay the source of defaults.
 */
public class UnderlayConfigScreen extends Screen {
    static final int LIST_TOP = 48;
    static final int LIST_BOTTOM_MARGIN = 36;

    public enum Filter {
        ALL, ENABLED, DISABLED;

        Component label() {
            return Component.translatable("underlay.config.filter." + name().toLowerCase(Locale.ROOT));
        }
    }

    record BlockRow(ResourceLocation id, Block block, ItemStack icon, Component name,
            String searchKey, boolean inBaseTag) {}

    record ModGroup(String namespace, String displayName, List<BlockRow> rows) {}

    @Nullable
    private final Screen parent;

    private List<ModGroup> groups = List.of();
    private final Map<ResourceLocation, BlockRow> rowIndex = new HashMap<>();
    /** Pending, not-yet-applied checkbox changes: id → desired enabled state. */
    final Map<ResourceLocation, Boolean> pending = new HashMap<>();
    /** Namespaces whose group is currently expanded. */
    final Set<String> expanded = new HashSet<>();

    private BlockListWidget list;
    private EditBox searchBox;
    private Filter filter = Filter.ALL;
    private String search = "";

    public UnderlayConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("underlay.config.title"));
        this.parent = parent;
        // Outside a world there is no server to mirror — show the local file.
        if (Minecraft.getInstance().getConnection() == null) {
            UnderlayOverrides local = UnderlayOverrides.server();
            UnderlayOverrides.setClientView(local.added(), local.removed());
        }
    }

    @Override
    protected void init() {
        if (groups.isEmpty()) {
            buildGroups();
        }

        int searchWidth = Math.min(220, width - 150);
        int rowLeft = width / 2 - (searchWidth + 110) / 2;
        searchBox = new EditBox(font, rowLeft, 22, searchWidth, 18,
                Component.translatable("underlay.config.search"));
        searchBox.setHint(Component.translatable("underlay.config.search"));
        searchBox.setValue(search);
        searchBox.setResponder(text -> {
            search = text;
            refreshList();
        });
        addRenderableWidget(searchBox);

        addRenderableWidget(CycleButton.<Filter>builder(Filter::label)
                .withValues(Filter.values())
                .withInitialValue(filter)
                .create(rowLeft + searchWidth + 10, 22, 100, 18,
                        Component.translatable("underlay.config.filter"),
                        (button, value) -> {
                            filter = value;
                            refreshList();
                        }));

        list = new BlockListWidget(Minecraft.getInstance(), width,
                height - LIST_TOP - LIST_BOTTOM_MARGIN, LIST_TOP, 20, this);
        addRenderableWidget(list);

        int buttonY = height - 26;
        addRenderableWidget(Button.builder(Component.translatable("underlay.config.reset"),
                button -> onReset()).bounds(width / 2 - 155, buttonY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("underlay.config.apply"),
                button -> onApply()).bounds(width / 2 - 50, buttonY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                button -> onClose()).bounds(width / 2 + 55, buttonY, 100, 20).build());

        refreshList();
    }

    private void buildGroups() {
        Map<String, List<BlockRow>> byNamespace = new TreeMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block.asItem() == Items.AIR) {
                continue; // technical blocks without an item are rarely useful hosts
            }
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            Component name = block.getName();
            boolean inTag = UnderlayRules.isDefaultHost(block.defaultBlockState());
            String searchKey = (name.getString() + " " + id).toLowerCase(Locale.ROOT);
            byNamespace.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>())
                    .add(new BlockRow(id, block, new ItemStack(block), name, searchKey, inTag));
        }

        List<ModGroup> result = new ArrayList<>();
        byNamespace.forEach((namespace, rows) -> {
            rows.sort(Comparator.comparing(row -> row.name().getString()));
            String displayName = ModList.get().getModContainerById(namespace)
                    .map(container -> container.getModInfo().getDisplayName())
                    .orElse(namespace);
            result.add(new ModGroup(namespace, displayName, rows));
        });
        // "minecraft" first, then mods alphabetically.
        result.sort(Comparator
                .comparing((ModGroup g) -> !g.namespace().equals("minecraft"))
                .thenComparing(g -> g.displayName().toLowerCase(Locale.ROOT)));
        groups = result;
        rowIndex.clear();
        for (ModGroup group : groups) {
            for (BlockRow row : group.rows()) {
                rowIndex.put(row.id(), row);
            }
        }
    }

    // --- state -------------------------------------------------------------

    boolean isEnabled(BlockRow row) {
        Boolean desired = pending.get(row.id());
        if (desired != null) {
            return desired;
        }
        UnderlayOverrides overrides = UnderlayOverrides.clientView();
        if (overrides.removed().contains(row.id())) {
            return false;
        }
        return row.inBaseTag() || overrides.added().contains(row.id());
    }

    void toggle(BlockRow row) {
        boolean now = isEnabled(row);
        pending.put(row.id(), !now);
        refreshCountsOnly();
    }

    void setGroup(ModGroup group, boolean enabled) {
        for (BlockRow row : group.rows()) {
            pending.put(row.id(), enabled);
        }
        refreshCountsOnly();
    }

    void toggleExpanded(ModGroup group) {
        if (!expanded.add(group.namespace())) {
            expanded.remove(group.namespace());
        }
        refreshList();
    }

    boolean isExpanded(ModGroup group) {
        return !search.isEmpty() || expanded.contains(group.namespace());
    }

    List<BlockRow> visibleRows(ModGroup group) {
        List<BlockRow> rows = new ArrayList<>();
        String query = search.toLowerCase(Locale.ROOT).trim();
        for (BlockRow row : group.rows()) {
            if (!query.isEmpty() && !row.searchKey().contains(query)) {
                continue;
            }
            boolean enabled = isEnabled(row);
            if (filter == Filter.ENABLED && !enabled) {
                continue;
            }
            if (filter == Filter.DISABLED && enabled) {
                continue;
            }
            rows.add(row);
        }
        return rows;
    }

    List<ModGroup> groups() {
        return groups;
    }

    private void refreshList() {
        if (list != null) {
            list.rebuild();
        }
    }

    private void refreshCountsOnly() {
        // Counts are computed during render; tri-state boxes update on their own.
        if (filter != Filter.ALL) {
            refreshList(); // the filtered set may have changed
        }
    }

    // --- actions -------------------------------------------------------------

    private void onApply() {
        UnderlayOverrides view = UnderlayOverrides.clientView();
        Set<ResourceLocation> added = new LinkedHashSet<>(view.added());
        Set<ResourceLocation> removed = new LinkedHashSet<>(view.removed());
        pending.forEach((id, desired) -> {
            BlockRow row = rowIndex.get(id);
            boolean inTag = row != null && row.inBaseTag();
            if (desired) {
                removed.remove(id);
                if (!inTag) {
                    added.add(id);
                } else {
                    added.remove(id);
                }
            } else {
                added.remove(id);
                if (inTag) {
                    removed.add(id);
                } else {
                    removed.remove(id);
                }
            }
        });
        pending.clear();
        submit(added, removed);
    }

    private void onReset() {
        pending.clear();
        submit(Set.of(), Set.of());
    }

    private void submit(Set<ResourceLocation> added, Set<ResourceLocation> removed) {
        UnderlayOverrides.setClientView(added, removed);
        if (Minecraft.getInstance().getConnection() != null && ClientProxy.serverHasUnderlay()) {
            PacketDistributor.sendToServer(new OverridesPayloads.Serverbound(added, removed));
        } else {
            UnderlayOverrides local = UnderlayOverrides.server();
            local.setAll(added, removed);
            local.save();
        }
        refreshList();
    }

    // --- rendering -----------------------------------------------------------

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        if (Minecraft.getInstance().level == null) {
            guiGraphics.drawCenteredString(font,
                    Component.translatable("underlay.config.open_world_hint"),
                    width / 2, height - LIST_BOTTOM_MARGIN + 1, 0xA0A0A0);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
