package tech.pegasys.pantheon.ethereum.mainnet;

/** Tuple that associates a {@link ProtocolSpec} with a given block level starting point */
public class ScheduledProtocolSpec<C> {
  private final long block;
  private final ProtocolSpec<C> spec;

  public ScheduledProtocolSpec(final long block, final ProtocolSpec<C> spec) {
    this.block = block;
    this.spec = spec;
  }

  public long getBlock() {
    return block;
  }

  public ProtocolSpec<C> getSpec() {
    return spec;
  }
}
