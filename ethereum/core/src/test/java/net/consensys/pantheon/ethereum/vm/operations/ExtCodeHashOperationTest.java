package net.consensys.pantheon.ethereum.vm.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import net.consensys.pantheon.ethereum.chain.Blockchain;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.AddressHelpers;
import net.consensys.pantheon.ethereum.core.BlockHeaderTestFixture;
import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.core.WorldUpdater;
import net.consensys.pantheon.ethereum.db.WorldStateArchive;
import net.consensys.pantheon.ethereum.mainnet.ConstantinopleGasCalculator;
import net.consensys.pantheon.ethereum.vm.Code;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.ethereum.vm.MessageFrame.Type;
import net.consensys.pantheon.ethereum.vm.Words;
import net.consensys.pantheon.ethereum.worldstate.KeyValueStorageWorldStateStorage;
import net.consensys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.bytes.BytesValue;
import net.consensys.pantheon.util.uint.UInt256;

import java.util.ArrayDeque;

import org.junit.Test;

public class ExtCodeHashOperationTest {

  private static final Address ADDRESS1 = AddressHelpers.ofValue(11111111);
  private static final Address REQUESTED_ADDRESS = AddressHelpers.ofValue(22222222);

  private final Blockchain blockchain = mock(Blockchain.class);

  private final WorldStateArchive worldStateArchive =
      new WorldStateArchive(new KeyValueStorageWorldStateStorage(new InMemoryKeyValueStorage()));
  private final WorldUpdater worldStateUpdater = worldStateArchive.getMutable().updater();

  private final ExtCodeHashOperation operation =
      new ExtCodeHashOperation(new ConstantinopleGasCalculator());

  @Test
  public void shouldCharge400Gas() {
    assertThat(operation.cost(createMessageFrame(REQUESTED_ADDRESS))).isEqualTo(Gas.of(400));
  }

  @Test
  public void shouldReturnZeroWhenAccountDoesNotExist() {
    final Bytes32 result = executeOperation(REQUESTED_ADDRESS);
    assertThat(result).isEqualTo(Bytes32.ZERO);
  }

  @Test
  public void shouldReturnHashOfEmptyDataWhenAccountExistsButDoesNotHaveCode() {
    worldStateUpdater.getOrCreate(REQUESTED_ADDRESS);
    assertThat(executeOperation(REQUESTED_ADDRESS)).isEqualTo(Hash.EMPTY);
  }

  @Test
  public void shouldReturnZeroWhenPrecompiledContractHasNoBalance() {
    assertThat(executeOperation(Address.ECREC)).isEqualTo(Bytes32.ZERO);
  }

  @Test
  public void shouldReturnEmptyCodeHashWhenPrecompileHasBalance() {
    // Sending money to a precompile causes it to exist in the world state archive.
    worldStateUpdater.getOrCreate(Address.ECREC).setBalance(Wei.of(10));
    assertThat(executeOperation(Address.ECREC)).isEqualTo(Hash.EMPTY);
  }

  @Test
  public void shouldGetHashOfAccountCodeWhenCodeIsPresent() {
    final BytesValue code = BytesValue.fromHexString("0xabcdef");
    worldStateUpdater.getOrCreate(REQUESTED_ADDRESS).setCode(code);
    assertThat(executeOperation(REQUESTED_ADDRESS)).isEqualTo(Hash.hash(code));
  }

  @Test
  public void shouldZeroOutLeftMostBitsToGetAddress() {
    // If EXTCODEHASH of A is X, then EXTCODEHASH of A + 2**160 is X.
    final BytesValue code = BytesValue.fromHexString("0xabcdef");
    worldStateUpdater.getOrCreate(REQUESTED_ADDRESS).setCode(code);
    final Bytes32 value =
        Words.fromAddress(REQUESTED_ADDRESS)
            .asUInt256()
            .plus(UInt256.of(2).pow(UInt256.of(160)))
            .getBytes();
    final MessageFrame frame = createMessageFrame(value);
    operation.execute(frame);
    assertThat(frame.getStackItem(0)).isEqualTo(Hash.hash(code));
  }

  private Bytes32 executeOperation(final Address requestedAddress) {
    final MessageFrame frame = createMessageFrame(requestedAddress);
    operation.execute(frame);
    return frame.getStackItem(0);
  }

  private MessageFrame createMessageFrame(final Address requestedAddress) {
    final Bytes32 stackItem = Words.fromAddress(requestedAddress);
    return createMessageFrame(stackItem);
  }

  private MessageFrame createMessageFrame(final Bytes32 stackItem) {
    final MessageFrame frame =
        new MessageFrame.Builder()
            .type(Type.MESSAGE_CALL)
            .initialGas(Gas.MAX_VALUE)
            .inputData(BytesValue.EMPTY)
            .depth(1)
            .gasPrice(Wei.ZERO)
            .contract(ADDRESS1)
            .address(ADDRESS1)
            .originator(ADDRESS1)
            .sender(ADDRESS1)
            .worldState(worldStateUpdater)
            .messageFrameStack(new ArrayDeque<>())
            .blockHeader(new BlockHeaderTestFixture().buildHeader())
            .value(Wei.ZERO)
            .apparentValue(Wei.ZERO)
            .code(new Code(BytesValue.EMPTY))
            .blockchain(blockchain)
            .completer(messageFrame -> {})
            .build();

    frame.pushStackItem(stackItem);
    return frame;
  }
}
