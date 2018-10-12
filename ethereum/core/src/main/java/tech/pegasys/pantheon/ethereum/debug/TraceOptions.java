package tech.pegasys.pantheon.ethereum.debug;

public class TraceOptions {

  private final boolean traceStorage;
  private final boolean traceMemory;
  private final boolean traceStack;

  public static final TraceOptions DEFAULT = new TraceOptions(true, true, true);

  public TraceOptions(
      final boolean traceStorage, final boolean traceMemory, final boolean traceStack) {
    this.traceStorage = traceStorage;
    this.traceMemory = traceMemory;
    this.traceStack = traceStack;
  }

  public boolean isStorageEnabled() {
    return traceStorage;
  }

  public boolean isMemoryEnabled() {
    return traceMemory;
  }

  public boolean isStackEnabled() {
    return traceStack;
  }
}
