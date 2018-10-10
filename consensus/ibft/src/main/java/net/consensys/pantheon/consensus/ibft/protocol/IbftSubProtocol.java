package net.consensys.pantheon.consensus.ibft.protocol;

import net.consensys.pantheon.ethereum.p2p.wire.Capability;
import net.consensys.pantheon.ethereum.p2p.wire.SubProtocol;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class IbftSubProtocol implements SubProtocol {

  public static String NAME = "IBF";
  public static final Capability IBFV1 = Capability.create(NAME, 1);

  private static final IbftSubProtocol INSTANCE = new IbftSubProtocol();

  public static IbftSubProtocol get() {
    return INSTANCE;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public int messageSpace(final int protocolVersion) {
    return NotificationType.getMax() + 1;
  }

  @Override
  public boolean isValidMessageCode(final int protocolVersion, final int code) {
    return NotificationType.fromValue(code).isPresent();
  }

  public enum NotificationType {
    PREPREPARE(0),
    PREPARE(1),
    COMMIT(2),
    ROUND_CHANGE(3);

    private final int value;

    NotificationType(final int value) {
      this.value = value;
    }

    public final int getValue() {
      return value;
    }

    public static final int getMax() {
      return Collections.max(
              Arrays.asList(NotificationType.values()),
              Comparator.comparing(NotificationType::getValue))
          .getValue();
    }

    public static final Optional<NotificationType> fromValue(final int i) {
      final List<NotificationType> notifications = Arrays.asList(NotificationType.values());

      return Stream.of(NotificationType.values()).filter(n -> n.getValue() == i).findFirst();
    }
  }
}
