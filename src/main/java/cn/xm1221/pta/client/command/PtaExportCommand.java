package cn.xm1221.pta.client.command;

import cn.xm1221.pta.PtaBookList;
import cn.xm1221.pta.client.export.PtaExport;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * {@code /pta export} — writes the mirrored guides out as files.
 *
 * <p>A client command, because the mirror itself is client work: the books are read, the documents
 * are generated and the files are written on the client, and a server would have neither the
 * client's configuration nor any reason to write to the player's disk. The command runs where the
 * content lives and needs no permissions.</p>
 */
public final class PtaExportCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("PatchouliToAgeratum");

    /** Offers the books that are actually mirrored, so a typo cannot ask for a book that is not. */
    private static final SuggestionProvider<CommandSourceStack> BOOKS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    PtaBookList.effectiveBooks().stream().map(ResourceLocation::toString), builder);

    private PtaExportCommand() {
    }

    /** Registers the command on the client's dispatcher. */
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("pta")
                .then(Commands.literal("export")
                        .then(Commands.literal("all")
                                .executes(context -> export(context, null, null))
                                .then(directory()
                                        .executes(context -> export(context, null, directory(context)))))
                        .then(Commands.argument("book", ResourceLocationArgument.id())
                                .suggests(BOOKS)
                                .executes(context -> export(context, book(context), null))
                                .then(directory()
                                        .executes(context -> export(context, book(context), directory(context)))))));
    }

    /**
     * The optional trailing directory, greedy so that it may contain spaces and separators.
     */
    private static RequiredArgumentBuilder<CommandSourceStack, String> directory() {
        return Commands.argument("directory", StringArgumentType.greedyString());
    }

    private static @Nullable ResourceLocation book(CommandContext<CommandSourceStack> context) {
        return ResourceLocationArgument.getId(context, "book");
    }

    private static String directory(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "directory");
    }

    /**
     * Writes the guides of one book, or of every mirrored book, under the export directory.
     *
     * @param book      the book to export, or {@code null} for every mirrored book
     * @param directory a directory relative to the game directory, or {@code null} for the default
     * @return how many files were written, which the command reports back
     */
    private static int export(CommandContext<CommandSourceStack> context,
                              @Nullable ResourceLocation book, @Nullable String directory) {
        CommandSourceStack source = context.getSource();
        List<ResourceLocation> books = book == null ? PtaBookList.effectiveBooks() : List.of(book);
        if (books.isEmpty()) {
            source.sendFailure(Component.translatable("commands.pta.export.nothing"));
            return 0;
        }

        Path root = root(directory);
        try {
            int files = PtaExport.write(root, books);
            source.sendSuccess(() -> Component.translatable("commands.pta.export.done", files, root.toString())
                    .withStyle(style -> style.withClickEvent(
                            new ClickEvent(ClickEvent.Action.OPEN_FILE, root.toString()))), false);
            return files;
        } catch (IOException exception) {
            LOGGER.error("[pta] could not export to {}", root, exception);
            source.sendFailure(Component.translatable("commands.pta.export.failed",
                    root.toString(), String.valueOf(exception.getMessage())));
            return 0;
        }
    }

    /** The directory to export into: the game directory plus the requested relative path. */
    private static Path root(@Nullable String directory) {
        Path gameDirectory = Minecraft.getInstance().gameDirectory.toPath();
        String relative = directory == null || directory.isBlank()
                ? PtaExport.DEFAULT_DIRECTORY
                : directory.trim();
        return gameDirectory.resolve(relative).normalize();
    }
}
