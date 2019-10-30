package org.hyperledger.besu.consensus.ibft.queries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.ibft.IbftBlockInterface;
import org.hyperledger.besu.consensus.ibft.IbftExtraData;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.NonBesuBlockHeader;
import org.hyperledger.besu.plugin.services.query.IbftQueryService;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Collections;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

public class IbftQueryServiceImplTest {

  private Blockchain blockchain = mock(Blockchain.class);
  // private BlockHeaderFunctions headerFunctions;

  final int ROUND_NUMBER_IN_BLOCK = 5;
  private IbftExtraData extraData;

  @Before
  public void setup() {
    extraData =
        new IbftExtraData(
            BytesValue.wrap(new byte[32]),
            Collections.emptyList(),
            Optional.empty(),
            ROUND_NUMBER_IN_BLOCK,
            Collections.emptyList());
  }

  @Test
  public void roundNumberFromBlockIsReturned() {
    final BlockHeaderTestFixture blockHeaderTestFixture = new BlockHeaderTestFixture();
    blockHeaderTestFixture.extraData(extraData.encode());
    final BlockHeader header = blockHeaderTestFixture.buildHeader();

    final IbftQueryService service = new IbftQueryServiceImpl(new IbftBlockInterface(), blockchain);
    assertThat(service.getRoundNumberFrom(header)).isEqualTo(ROUND_NUMBER_IN_BLOCK);
  }

  @Test
  public void getRoundNumberThrowsIfBlockIsNotOnTheChain() {
    final Hash hash = Hash.wrap(Bytes32.wrap(new byte[32]));

    final NonBesuBlockHeader header = new NonBesuBlockHeader(hash, extraData.encode());
    when(blockchain.getBlockHeader(hash)).thenReturn(Optional.empty());

    final IbftQueryService service = new IbftQueryServiceImpl(new IbftBlockInterface(), blockchain);
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(() -> service.getRoundNumberFrom(header));
  }
}
