package org.hyperledger.besu.ethereum.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionProcessor;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.math.BigInteger;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public class PrivateTransactionSimulatorTest {
  private static final SECP256K1.KeyPair KEY_PAIR =
      SECP256K1.KeyPair.create(
          SECP256K1.PrivateKey.create(
              new BigInteger(
                  "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));
  private static final String PRIVACY_GROUP_ID = "tJw12cPM6EZRF5zfHv2zLePL0cqlaDjLn0x1T/V0yzE=";
  private static final PrivateTransaction VALID_SIGNED_PRIVATE_TRANSACTION =
      PrivateTransaction.builder()
          .nonce(0)
          .gasPrice(Wei.of(1000))
          .gasLimit(3000000)
          .to(null)
          .value(Wei.ZERO)
          .payload(
              BytesValue.fromHexString(
                  "0x608060405234801561001057600080fd5b5060d08061001f6000396000"
                      + "f3fe60806040526004361060485763ffffffff7c010000000000"
                      + "0000000000000000000000000000000000000000000000600035"
                      + "04166360fe47b18114604d5780636d4ce63c146075575b600080"
                      + "fd5b348015605857600080fd5b50607360048036036020811015"
                      + "606d57600080fd5b50356099565b005b348015608057600080fd"
                      + "5b506087609e565b60408051918252519081900360200190f35b"
                      + "600055565b6000549056fea165627a7a72305820cb1d0935d14b"
                      + "589300b12fcd0ab849a7e9019c81da24d6daa4f6b2f003d1b018"
                      + "0029"))
          .sender(
              Address.wrap(BytesValue.fromHexString("0x1c9a6e1ee3b7ac6028e786d9519ae3d24ee31e79")))
          .chainId(BigInteger.valueOf(4))
          .privateFrom(BytesValues.fromBase64("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="))
          .privacyGroupId(BytesValues.fromBase64(PRIVACY_GROUP_ID))
          .restriction(Restriction.RESTRICTED)
          .signAndBuild(KEY_PAIR);

  private Block genesis;
  @Mock private PrivateTransactionSimulator privateTransactionSimulator;
  @Mock private MutableBlockchain blockchain;
  @Mock private WorldStateArchive publicWorldStateArchive;
  @Mock private MutableWorldState publicWorldState;
  @Mock private WorldStateArchive privateWorldStateArchive;
  @Mock private MutableWorldState privateWorldState;
  @Mock private PrivateStateRootResolver privateStateRootResolver;
  @Mock private ProtocolSchedule<?> protocolSchedule;
  @Mock private ProtocolSpec protocolSpec;
  @Mock private PrivateTransactionProcessor transactionProcessor;

  @Before
  public void setUp() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    genesis = gen.genesisBlock();
    privateTransactionSimulator =
        new PrivateTransactionSimulator(
            blockchain,
            publicWorldStateArchive,
            privateWorldStateArchive,
            privateStateRootResolver,
            protocolSchedule);
  }

  @Test
  public void returnsEmptyWhenBlockHeaderIsNull() {
    assertThat(
            privateTransactionSimulator.process(
                VALID_SIGNED_PRIVATE_TRANSACTION, BytesValues.fromBase64(PRIVACY_GROUP_ID), null))
        .isEmpty();
  }

  @Test
  public void shouldReturnSuccessfulResultWhenProcessingIsSuccessfulByHash() {
    mockBlockchainForBlockHeader(Hash.ZERO, 1L);
    mockPublicWorldStateForAccount(Hash.ZERO);
    mockPrivateWorldStateForAccount(Hash.ZERO);
    mockProcessorStatusForTransaction(
        1L, VALID_SIGNED_PRIVATE_TRANSACTION, TransactionProcessor.Result.Status.SUCCESSFUL);

    final Optional<PrivateTransactionSimulatorResult> result =
        privateTransactionSimulator.process(
            VALID_SIGNED_PRIVATE_TRANSACTION,
            BytesValues.fromBase64(PRIVACY_GROUP_ID),
            blockchain.getChainHeadHeader());

    assertThat(result.get().isSuccessful()).isTrue();
    verifyTransactionWasProcessed(VALID_SIGNED_PRIVATE_TRANSACTION);
  }

  @Test
  public void shouldReturnFailureResultWhenProcessingFails() {
    mockBlockchainForBlockHeader(Hash.ZERO, 1L);
    mockPublicWorldStateForAccount(Hash.ZERO);
    mockPrivateWorldStateForAccount(Hash.ZERO);

    mockProcessorStatusForTransaction(
        1L, VALID_SIGNED_PRIVATE_TRANSACTION, TransactionProcessor.Result.Status.FAILED);

    final Optional<PrivateTransactionSimulatorResult> result =
        privateTransactionSimulator.process(
            VALID_SIGNED_PRIVATE_TRANSACTION,
            BytesValues.fromBase64(PRIVACY_GROUP_ID),
            blockchain.getChainHeadHeader());

    assertThat(result.get().isSuccessful()).isFalse();
    verifyTransactionWasProcessed(VALID_SIGNED_PRIVATE_TRANSACTION);
  }

  private void mockBlockchainForBlockHeader(final Hash stateRoot, final long blockNumber) {
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getStateRoot()).thenReturn(stateRoot);
    when(blockHeader.getNumber()).thenReturn(blockNumber);
    when(blockHeader.getParentHash()).thenReturn(genesis.getHash());
    when(blockchain.getChainHeadHeader()).thenReturn(blockHeader);
  }

  private void mockPublicWorldStateForAccount(final Hash stateRoot) {
    when(publicWorldStateArchive.getMutable(eq(stateRoot)))
        .thenReturn(Optional.of(publicWorldState));
  }

  private void mockPrivateWorldStateForAccount(final Hash stateRoot) {
    when(privateStateRootResolver.resolveLastStateRoot(any(), any(), any())).thenReturn(stateRoot);
    when(privateWorldStateArchive.getMutable(eq(stateRoot)))
        .thenReturn(Optional.of(privateWorldState));
  }

  private void mockProcessorStatusForTransaction(
      final long blockNumber,
      final PrivateTransaction transaction,
      final TransactionProcessor.Result.Status status) {
    when(protocolSchedule.getByBlockNumber(eq(blockNumber))).thenReturn(protocolSpec);
    when(protocolSpec.getPrivateTransactionProcessor()).thenReturn(transactionProcessor);
    when(protocolSpec.getMiningBeneficiaryCalculator()).thenReturn(BlockHeader::getCoinbase);

    final PrivateTransactionProcessor.Result result =
        mock(PrivateTransactionProcessor.Result.class);
    switch (status) {
      case SUCCESSFUL:
        when(result.isSuccessful()).thenReturn(true);
        break;
      case INVALID:
      case FAILED:
        when(result.isSuccessful()).thenReturn(false);
        break;
    }

    when(transactionProcessor.processTransaction(
            any(), any(), any(), any(), eq(transaction), any(), any(), any(), any()))
        .thenReturn(result);
  }

  private void verifyTransactionWasProcessed(final PrivateTransaction expectedTransaction) {
    verify(transactionProcessor)
        .processTransaction(
            any(), any(), any(), any(), eq(expectedTransaction), any(), any(), any(), any());
  }
}
