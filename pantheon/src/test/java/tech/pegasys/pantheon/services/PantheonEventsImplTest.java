/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeers;
import tech.pegasys.pantheon.ethereum.eth.sync.BlockBroadcaster;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PantheonEventsImplTest {

  @Mock private EthPeers ethPeers;
  @Mock private EthContext mockEthContext;
  private BlockBroadcaster blockBroadcaster;
  private PantheonEventsImpl serviceImpl;

  @Before
  public void setUp() {
    when(ethPeers.streamAvailablePeers()).thenReturn(Stream.empty()).thenReturn(Stream.empty());
    when(mockEthContext.getEthPeers()).thenReturn(ethPeers);

    blockBroadcaster = new BlockBroadcaster(mockEthContext);
    serviceImpl = new PantheonEventsImpl(blockBroadcaster);
  }

  @Test
  public void eventFiresAfterSubscribe() {
    final AtomicReference<String> result = new AtomicReference<>();
    serviceImpl.addNewBlockPropagatedListener(result::set);

    assertThat(result.get()).isNull();
    blockBroadcaster.propagate(generateBlock(), UInt256.of(1));

    assertThat(result.get()).isNotEmpty();
  }

  @Test
  public void eventDoesNotFireAfterUnsubscribe() {
    final AtomicReference<String> result = new AtomicReference<>();
    final Object id = serviceImpl.addNewBlockPropagatedListener(result::set);

    assertThat(result.get()).isNull();
    blockBroadcaster.propagate(generateBlock(), UInt256.of(1));

    serviceImpl.removeNewBlockPropagatedListener(id);
    result.set(null);

    blockBroadcaster.propagate(generateBlock(), UInt256.of(1));
    assertThat(result.get()).isNull();
  }

  @Test
  public void propagationWithoutSubscriptionsCompletes() {
    blockBroadcaster.propagate(generateBlock(), UInt256.of(1));
  }

  @Test
  public void uselessUnsubscribesCompletes() {
    serviceImpl.removeNewBlockPropagatedListener("doesNotExist");
    serviceImpl.removeNewBlockPropagatedListener(5);
    serviceImpl.removeNewBlockPropagatedListener(5L);
  }

  private Block generateBlock() {
    final BlockBody body = new BlockBody(Collections.emptyList(), Collections.emptyList());
    return new Block(new BlockHeaderTestFixture().buildHeader(), body);
  }
}
