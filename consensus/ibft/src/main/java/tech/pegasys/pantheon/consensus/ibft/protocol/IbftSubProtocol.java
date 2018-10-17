/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.consensus.ibft.protocol;

import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.SubProtocol;

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
