package net.consensys.pantheon.ethereum.vm;

import static net.consensys.pantheon.ethereum.core.InMemoryWorldState.createInMemoryWorldStateArchive;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.MutableAccount;
import net.consensys.pantheon.ethereum.core.MutableWorldState;
import net.consensys.pantheon.ethereum.core.WorldUpdater;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.uint.UInt256;

import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.junit.Test;

public class EntriesFromIntegrationTest {

  @Test
  public void shouldCollectStateEntries() {
    final MutableWorldState worldState = createInMemoryWorldStateArchive().getMutable();
    final WorldUpdater updater = worldState.updater();
    MutableAccount account = updater.getOrCreate(Address.fromHexString("0x56"));
    final Map<Bytes32, UInt256> expectedValues = new TreeMap<>();
    final int nodeCount = 100_000;
    final Random random = new Random(42989428249L);

    // Create some storage entries in the committed, underlying account.
    for (int i = 0; i <= nodeCount; i++) {
      addExpectedValue(
          account, expectedValues, UInt256.of(Math.abs(random.nextLong())), UInt256.of(i * 10 + 1));
    }
    updater.commit();

    // Add some changes on top that AbstractWorldUpdater.UpdateTrackingAccount will have to merge.
    account = worldState.updater().getOrCreate(Address.fromHexString("0x56"));
    for (int i = 0; i <= nodeCount; i++) {
      addExpectedValue(
          account, expectedValues, UInt256.of(Math.abs(random.nextLong())), UInt256.of(i * 10 + 1));
    }

    final Map<Bytes32, UInt256> values =
        account.storageEntriesFrom(Bytes32.ZERO, Integer.MAX_VALUE);
    assertThat(values).isEqualTo(expectedValues);
  }

  private void addExpectedValue(
      final MutableAccount account,
      final Map<Bytes32, UInt256> expectedValues,
      final UInt256 key,
      final UInt256 value) {
    account.setStorageValue(key, value);
    expectedValues.put(Hash.hash(key.getBytes()), value);
  }
}
