package studios.littlesquad.welcomeflow.action.impl;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import studios.littlesquad.welcomeflow.action.ActionParseException;
import studios.littlesquad.welcomeflow.action.ActionRegistry;
import studios.littlesquad.welcomeflow.action.LambdaActionFactory;
import studios.littlesquad.welcomeflow.compat.Registries;
import studios.littlesquad.welcomeflow.config.ConfigManager;
import studios.littlesquad.welcomeflow.util.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers {@code teleport}, {@code give-item} and {@code open-book}.
 *
 * <p>{@code teleport} uses Paper's {@code teleportAsync}: the plain
 * {@code teleport} would block the main thread while the destination chunk
 * loads, which on a first-join spawn teleport is exactly when the chunk is
 * least likely to be resident.
 */
public final class WorldActions {

    private WorldActions() {
    }

    public static void registerInto(ActionRegistry registry, ConfigManager config) {
        registry.register(new LambdaActionFactory(
                "teleport",
                "teleport: <named-location> | teleport: <world>;;<x>;;<y>;;<z>[;;<yaw>;;<pitch>]",
                spec -> {
                    String first = spec.requireString("location", 0);
                    // One field means a named location from config.yml's
                    // locations block; four or more means literal coordinates.
                    if (spec.fieldCount() <= 1) {
                        return ctx -> {
                            Location target = config.namedLocation(first);
                            if (target == null) {
                                return; // ConfigValidator reports the missing name.
                            }
                            ctx.player().teleportAsync(target);
                        };
                    }
                    String worldName = first;
                    double x = spec.decimal("x", 1, 0);
                    double y = spec.decimal("y", 2, 64);
                    double z = spec.decimal("z", 3, 0);
                    float yaw = (float) spec.decimal("yaw", 4, 0);
                    float pitch = (float) spec.decimal("pitch", 5, 0);
                    return ctx -> {
                        World world = Bukkit.getWorld(worldName);
                        if (world == null) {
                            return; // World may be unloaded; validator warns at load.
                        }
                        ctx.player().teleportAsync(new Location(world, x, y, z, yaw, pitch));
                    };
                }));

        registry.register(new LambdaActionFactory(
                "give-item",
                "give-item: <MATERIAL>;;<amount>;;<display-name>",
                spec -> {
                    String materialName = spec.requireString("material", 0);
                    Material material = Registries.material(materialName);
                    if (material == null) {
                        throw new ActionParseException("Unknown material '" + materialName + "' at "
                                + spec.configPath() + ". Use a Bukkit Material name, e.g. DIAMOND.");
                    }
                    if (material.isAir()) {
                        throw new ActionParseException("Cannot give AIR at " + spec.configPath() + ".");
                    }
                    int amount = Math.max(1, spec.integer("amount", 1, 1));
                    String displayName = spec.string("display-name", 2);

                    return ctx -> {
                        ItemStack item = new ItemStack(material, amount);
                        if (displayName != null && !displayName.isEmpty()) {
                            item.editMeta(meta -> meta.displayName(Text.parse(displayName, ctx.placeholders())));
                        }
                        // Anything that doesn't fit is dropped at the player's
                        // feet rather than silently vanishing.
                        ctx.player().getInventory().addItem(item).values()
                                .forEach(leftover -> ctx.player().getWorld()
                                        .dropItemNaturally(ctx.player().getLocation(), leftover));
                    };
                }));

        registry.register(new LambdaActionFactory(
                "open-book",
                "open-book: <title>;;<page1>;;<page2>;;...",
                spec -> {
                    String title = spec.requireString("title", 0);
                    List<String> pages = new ArrayList<>();
                    // Pages are the remaining ';;' fields, or a 'pages' list in
                    // the expanded map form.
                    for (Object page : spec.list("pages")) {
                        pages.add(String.valueOf(page));
                    }
                    if (pages.isEmpty()) {
                        for (int index = 1; ; index++) {
                            String page = spec.string("page" + index, index);
                            if (page == null || page.isEmpty()) {
                                break;
                            }
                            pages.add(page);
                        }
                    }
                    if (pages.isEmpty()) {
                        throw new ActionParseException("Action 'open-book' at " + spec.configPath()
                                + " needs at least one page after the title.");
                    }
                    return ctx -> {
                        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
                        book.editMeta(BookMeta.class, meta -> {
                            meta.title(Text.parse(title, ctx.placeholders()));
                            meta.author(Component.text("WelcomeFlow"));
                            List<Component> rendered = new ArrayList<>(pages.size());
                            for (String page : pages) {
                                rendered.add(Text.parse(page, ctx.placeholders()));
                            }
                            meta.pages(rendered);
                        });
                        ctx.player().openBook(book);
                    };
                }));
    }
}
