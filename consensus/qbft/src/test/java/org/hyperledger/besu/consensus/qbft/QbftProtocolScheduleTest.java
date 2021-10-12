package org.hyperledger.besu.consensus.qbft;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hyperledger.besu.consensus.common.bft.BftContextBuilder.setupContextWithBftExtraDataEncoder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.config.QbftConfigOptions;
import org.hyperledger.besu.config.QbftFork;
import org.hyperledger.besu.config.QbftFork.VALIDATOR_SELECTION_MODE;
import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.config.TransitionsConfigOptions;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class QbftProtocolScheduleTest {
  private final BftExtraDataCodec bftExtraDataCodec = mock(BftExtraDataCodec.class);
  private final TransitionsConfigOptions transitionsConfigOptions =
      mock(TransitionsConfigOptions.class);

  private ProtocolContext protocolContext(final Collection<Address> validators) {
    return new ProtocolContext(
        null,
        null,
        setupContextWithBftExtraDataEncoder(
            QbftContext.class, validators, new QbftExtraDataCodec()));
  }

  @Test
  public void contractModeTransitionsCreatesContractModeHeaderValidators() {
    final QbftFork arbitraryFork =
        new QbftFork(
            JsonUtil.objectNodeFromMap(
                Map.of(QbftFork.FORK_BLOCK_KEY, 1, QbftFork.BLOCK_REWARD_KEY, "1")));
    final QbftFork contractModeFork =
        new QbftFork(
            JsonUtil.objectNodeFromMap(
                Map.of(
                    QbftFork.FORK_BLOCK_KEY,
                    2,
                    QbftFork.VALIDATOR_SELECTION_MODE_KEY,
                    VALIDATOR_SELECTION_MODE.CONTRACT,
                    QbftFork.VALIDATOR_CONTRACT_ADDRESS_KEY,
                    "0x1")));

    final QbftConfigOptions qbftConfigOptions =
        new QbftConfigOptions(
            JsonUtil.objectNodeFromMap(Map.of(QbftFork.VALIDATOR_SELECTION_MODE_KEY, "0x1")));

    final List<QbftFork> forks = List.of(arbitraryFork, contractModeFork);
    final StubGenesisConfigOptions genesisConfig = new StubGenesisConfigOptions();
    genesisConfig.transitions(transitionsConfigOptions);
    genesisConfig.qbftConfigOptions(qbftConfigOptions);
    when(transitionsConfigOptions.getQbftForks()).thenReturn(forks);

    ProtocolSchedule schedule =
        QbftProtocolSchedule.create(
            genesisConfig,
            PrivacyParameters.DEFAULT,
            false,
            bftExtraDataCodec,
            EvmConfiguration.DEFAULT);

    final NodeKey proposerNodeKey = NodeKeyUtils.generate();
    final Address proposerAddress = Util.publicKeyToAddress(proposerNodeKey.getPublicKey());
    final List<Address> validators = singletonList(proposerAddress);
    final BlockHeader parentHeader =
        QbftBlockHeaderUtils.createPresetHeaderBuilderForContractMode(
                1, proposerNodeKey, null, null)
            .buildHeader();
    final BlockHeader blockHeader =
        QbftBlockHeaderUtils.createPresetHeaderBuilderForContractMode(
                2, proposerNodeKey, parentHeader, null)
            .buildHeader();

    assertThat(schedule.streamMilestoneBlocks().count()).isEqualTo(3);
    assertThat(validateHeader(schedule, validators, parentHeader, blockHeader, 0)).isTrue();
    assertThat(validateHeader(schedule, validators, parentHeader, blockHeader, 1)).isTrue();
    assertThat(validateHeader(schedule, validators, parentHeader, blockHeader, 2)).isTrue();
  }

  private boolean validateHeader(
      final ProtocolSchedule schedule,
      final List<Address> validators,
      final BlockHeader parentHeader,
      final BlockHeader blockHeader,
      final int block) {
    return schedule
        .getByBlockNumber(block)
        .getBlockHeaderValidator()
        .validateHeader(
            blockHeader, parentHeader, protocolContext(validators), HeaderValidationMode.LIGHT);
  }
}
