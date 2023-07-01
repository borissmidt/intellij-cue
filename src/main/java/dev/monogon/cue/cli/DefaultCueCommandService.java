package dev.monogon.cue.cli;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.progress.ProgressManager;
import dev.monogon.cue.Messages;
import dev.monogon.cue.settings.CueLocalSettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefaultCueCommandService implements CueCommandService {
    public final static Pattern lineErrorRegex = Pattern.compile("^ {4}.*:(\\d+):(\\d+)$");

    @NotNull
    final Duration timeout;

    public DefaultCueCommandService() {
        this(Duration.ofSeconds(5));
    }

    public DefaultCueCommandService(@NotNull Duration timeout) {
        this.timeout = timeout;
    }

    public CueCommandService withTimeout(@NotNull Duration timeout) {
        return new DefaultCueCommandService(timeout);
    }


    private @NotNull String[] merge(@NotNull String o, @NotNull String... arr) {
        String[] newArray = new String[arr.length + 1];
        newArray[0] = o;
        System.arraycopy(arr, 0, newArray, 1, arr.length);

        return newArray;
    }

    @NotNull
    private GeneralCommandLine cueCommand(@NotNull String... args) throws ExecutionException {
        String cuePath = CueLocalSettingsService.getSettings().getCueExecutablePath();
        if (cuePath == null || cuePath.isEmpty()) {
            var envPath = PathEnvironmentVariableUtil.findInPath("cue");
            if (envPath == null || !envPath.canExecute()) {
                throw new ExecutionException(Messages.get("formatter.exeNotFound"));
            }
            cuePath = envPath.getAbsolutePath();
        } else {
            if (!Files.isExecutable(Paths.get(cuePath))) {
                throw new ExecutionException(Messages.get("formatter.userPathNotFound"));
            }
        }

        GeneralCommandLine cmd = new GeneralCommandLine(merge(cuePath, args));
        cmd.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
        cmd.withCharset(StandardCharsets.UTF_8);
        return cmd;
    }

    private ProcessOutput runCueCommand(@Nullable String std_in, @NotNull String... args) throws ExecutionException, IOException {
        var cmd = cueCommand(args);
        // the process handler already calls processTerminated in a background thread,
        // so we don't start another background process
        var processHandler = new CapturingProcessHandler(cmd);
        if (std_in != null) {
            try (var stdin = processHandler.getProcessInput()) {
                stdin.write(std_in.getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            }
        }

        ProcessOutput output;
        var indicator = ProgressManager.getGlobalProgressIndicator();
        if (indicator != null) {
            output = processHandler.runProcessWithProgressIndicator(indicator, (int) timeout.toMillis(), true);
        } else {
            output = processHandler.runProcess((int) timeout.toMillis(), true);
        }

        return output;
    }

    @Override
    public @Nullable String format(@NotNull String content) throws ExecutionException {
        try {
            var output = runCueCommand(content, "fmt", "-");
            if (output.isTimeout() || !output.isExitCodeSet() || output.getExitCode() != 0) {
                return null;
            }
            return output.getStdout();
        } catch (IOException e) {
            throw new ExecutionException(Messages.get("formatter.cueExecuteError"), e);
        }
    }

    /**
     * input: [A, B, C, A, A, D]
     * startNewGroup: x -> x == A
     * output: [[A, B, C], [A], [A, D]]
     *
     * @param data          the sequence of elements you want to iterate over
     * @param startNewGroup the moment you want to start a new group all subsequent elements in the list are added to it.
     * @param <T>           the type parameter of the function
     * @return a list of consecutive elements between startNewGroup elements
     */
    private <T> @NotNull List<List<T>> groupByOrdered(@NotNull Iterable<T> data,@NotNull Function<T, Boolean> startNewGroup) {
        var groups = new ArrayList<List<T>>();
        var currentGroup = new ArrayList<T>();
        for (T x : data) {
            if (startNewGroup.apply(x)) {
                currentGroup = new ArrayList<>();
                groups.add(currentGroup);
            }
            currentGroup.add(x);
        }
        return groups;
    }

    @Override
    public @NotNull List<LintingError> vet(@NotNull Path file) throws ExecutionException {
        try {
            var output = runCueCommand(null, "vet", file.toAbsolutePath().toString());
            if (output.isTimeout() || !output.isExitCodeSet() || output.getExitCode() != 0) {
                return Collections.emptyList();
            }
            var stdout = output.getStdout();
            //missing ',' before newline in list literal:
            //    ./LintingErrors.cue:7:1
            //missing ',' in list literal:
            //    ./LintingErrors.cue:9:3
            var errorGroups = groupByOrdered(Arrays.asList(stdout.split("\r?\n")), (String x) -> !x.startsWith("   "));
            var lintingErrors = errorGroups.stream().flatMap(
                    errorGroup -> {
                        var errorMessage = errorGroup.get(0);
                        return errorGroup.stream().skip(1)
                                .flatMap(
                                        (errorLine) -> {
                                            var m = lineErrorRegex.matcher(errorLine);
                                            if (m.find()) {
                                                return Stream.of(new LintingError(file, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), errorMessage));
                                            } else {
                                                return Stream.empty();
                                            }
                                        }
                                );
                    }
            ).collect(Collectors.toList());

            return lintingErrors;
        } catch (IOException e) {
            throw new ExecutionException(Messages.get("formatter.cueExecuteError"), e);
        }
    }
}
