package dev.monogon.cue.cli;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Service to interact with the cue command line tool.
 */
public interface CueCommandService {
    class LintingError {
        @NotNull
        final public Path file;

        final public int line;

        final public int column;
        @NotNull
        final public String message;

        public LintingError(@NotNull Path file, int line, int column,@NotNull String message) {
            this.file = file;
            this.line = line;
            this.column = column;
            this.message = message;
        }
    }

    /**
     * Calls "cue fmt", writes the given content on STDIN and returns STDOUT on success (exit code 0) or an error in other cased.
     *
     * @return The formatted content, if available.
     */
    @Nullable
    String format(@NotNull String content) throws ExecutionException;

    @NotNull
    List<LintingError> vet(@NotNull Path content) throws ExecutionException;

    @NotNull
    static CueCommandService getInstance() {
        return ServiceManager.getService(CueCommandService.class);
    }

    CueCommandService withTimeout(@NotNull Duration timeout);
}
