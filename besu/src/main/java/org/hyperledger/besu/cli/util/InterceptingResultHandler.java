package org.hyperledger.besu.cli.util;

import org.hyperledger.besu.cli.BesuCommand;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

public class InterceptingResultHandler extends CommandLine.AbstractParseResultHandler<List<Object>> {

    private final CommandLine.AbstractParseResultHandler<List<Object>> resultHandler;
    private final CommandLine.IExceptionHandler2<List<Object>> exceptionHandler;
    private final BeforeExecutionHook beforeExecutionHook;

    @FunctionalInterface
    public interface BeforeExecutionHook {
        void beforeExecution(BesuCommand command);
    }

    public InterceptingResultHandler(
            final CommandLine.AbstractParseResultHandler<List<Object>> resultHandler,
            final CommandLine.IExceptionHandler2<List<Object>> exceptionHandler,
            final BeforeExecutionHook beforeExecutionHook) {
        this.resultHandler = resultHandler;
        this.exceptionHandler = exceptionHandler;
        this.beforeExecutionHook = beforeExecutionHook;
        // use the same output as the regular options handler to ensure that outputs are all going
        // in the same place. No need to do this for the exception handler as we reuse it directly.
        this.useOut(resultHandler.out());
    }

    @Override
    protected List<Object> handle(final CommandLine.ParseResult parseResult) throws CommandLine.ExecutionException {
        final CommandLine commandLine = parseResult.asCommandLineList().get(0);
        beforeExecutionHook.beforeExecution(commandLine.getCommand());
        commandLine.parseWithHandlers(resultHandler, exceptionHandler, parseResult.originalArgs().toArray(new String[0]));
        return new ArrayList<>();
    }

    @Override
    protected CommandLine.AbstractParseResultHandler<List<Object>> self() {
        return this;
    }
}
