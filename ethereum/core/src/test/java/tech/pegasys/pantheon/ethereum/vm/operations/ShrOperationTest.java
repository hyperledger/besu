package tech.pegasys.pantheon.ethereum.vm.operations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.mainnet.SpuriousDragonGasCalculator;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.bytes.Bytes32;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ShrOperationTest {
  private final String number;
  private final String shift;
  private final String expectedResult;

  private final GasCalculator gasCalculator = new SpuriousDragonGasCalculator();
  private final ShrOperation operation = new ShrOperation(gasCalculator);

  private MessageFrame frame;

  static String[][] testData = {
    {
      "0x0000000000000000000000000000000000000000000000000000000000000001",
      "0x00",
      "0x0000000000000000000000000000000000000000000000000000000000000001"
    },
    {
      "0x0000000000000000000000000000000000000000000000000000000000000001",
      "0x01",
      "0x0000000000000000000000000000000000000000000000000000000000000000"
    },
    {
      "0x0000000000000000000000000000000000000000000000000000000000000002",
      "0x01",
      "0x0000000000000000000000000000000000000000000000000000000000000001"
    },
    {
      "0x0000000000000000000000000000000000000000000000000000000000000004",
      "0x01",
      "0x0000000000000000000000000000000000000000000000000000000000000002"
    },
    {
      "0x000000000000000000000000000000000000000000000000000000000000000f",
      "0x01",
      "0x0000000000000000000000000000000000000000000000000000000000000007"
    },
    {
      "0x0000000000000000000000000000000000000000000000000000000000000008",
      "0x01",
      "0x0000000000000000000000000000000000000000000000000000000000000004"
    },
    {
      "0x8000000000000000000000000000000000000000000000000000000000000000",
      "0xff",
      "0x0000000000000000000000000000000000000000000000000000000000000001"
    },
    {
      "0x8000000000000000000000000000000000000000000000000000000000000000",
      "0x100",
      "0x0000000000000000000000000000000000000000000000000000000000000000"
    },
    {
      "0x8000000000000000000000000000000000000000000000000000000000000000",
      "0x101",
      "0x0000000000000000000000000000000000000000000000000000000000000000"
    },
    {
      "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
      "0x0",
      "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    },
    {
      "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
      "0x01",
      "0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    },
    {
      "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
      "0xff",
      "0x0000000000000000000000000000000000000000000000000000000000000001"
    },
    {
      "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
      "0x100",
      "0x0000000000000000000000000000000000000000000000000000000000000000"
    },
    {
      "0x0000000000000000000000000000000000000000000000000000000000000000",
      "0x01",
      "0x0000000000000000000000000000000000000000000000000000000000000000"
    },
  };

  @Parameterized.Parameters(name = "{index}: {0}, {1}, {2}")
  public static Iterable<Object[]> data() {
    return Arrays.asList((Object[][]) testData);
  }

  public ShrOperationTest(final String number, final String shift, final String expectedResult) {
    this.number = number;
    this.shift = shift;
    this.expectedResult = expectedResult;
  }

  @Test
  public void shiftOperation() {
    frame = mock(MessageFrame.class);
    when(frame.popStackItem())
        .thenReturn(Bytes32.fromHexStringLenient(shift))
        .thenReturn(Bytes32.fromHexString(number));
    operation.execute(frame);
    verify(frame).pushStackItem(Bytes32.fromHexString(expectedResult));
  }
}
