/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.mainnet.precompiles;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.ethereum.mainnet.IstanbulGasCalculator;
import org.hyperledger.besu.ethereum.mainnet.PrecompiledContract;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import org.apache.tuweni.bytes.Bytes;

public class Benchmarks {

  static final Random random = new Random();

  static final long GAS_PER_SECOND_STANDARD = 35_000_000L;

  static final int HASH_WARMUP = 1_000_000;
  static final int HASH_ITERATIONS = 10_000;

  static final int MATH_WARMUP = 10_000;
  static final int MATH_ITERATIONS = 1_000;

  public static void benchSha256() {
    final SHA256PrecompiledContract contract =
        new SHA256PrecompiledContract(new IstanbulGasCalculator());
    final byte[] warmupData = new byte[240];
    final Bytes warmupBytes = Bytes.wrap(warmupData);
    for (int i = 0; i < HASH_WARMUP; i++) {
      contract.compute(warmupBytes, null);
    }
    for (int len = 0; len <= 256; len += 8) {
      final byte[] data = new byte[len];
      random.nextBytes(data);
      final Bytes bytes = Bytes.wrap(data);
      final Stopwatch timer = Stopwatch.createStarted();
      for (int i = 0; i < HASH_ITERATIONS; i++) {
        contract.compute(bytes, null);
      }
      timer.stop();

      final double elapsed = timer.elapsed(TimeUnit.NANOSECONDS) / 1.0e9D;
      final double perCall = elapsed / HASH_ITERATIONS;
      final double gasSpent = perCall * GAS_PER_SECOND_STANDARD;

      System.out.printf(
          "Hashed %,d bytes for %,d gas. Charging %,d gas.%n",
          len, (int) gasSpent, contract.gasRequirement(bytes).asUInt256().toLong());
    }
  }

  private static void benchKeccak256() {
    final byte[] warmupData = new byte[240];
    final Bytes warmupBytes = Bytes.wrap(warmupData);
    for (int i = 0; i < HASH_WARMUP; i++) {
      Hash.keccak256(warmupBytes);
    }
    for (int len = 0; len <= 512; len += 8) {
      final byte[] data = new byte[len];
      random.nextBytes(data);
      final Bytes bytes = Bytes.wrap(data);
      final Stopwatch timer = Stopwatch.createStarted();
      for (int i = 0; i < HASH_ITERATIONS; i++) {
        Hash.keccak256(bytes);
      }
      timer.stop();

      final double elapsed = timer.elapsed(TimeUnit.NANOSECONDS) / 1.0e9D;
      final double perCall = elapsed / HASH_ITERATIONS;
      final double gasSpent = perCall * GAS_PER_SECOND_STANDARD;

      System.out.printf("Hashed %,d bytes for %,d gas.%n", len, (int) gasSpent);
    }
  }

  private static void benchRipeMD() {
    final RIPEMD160PrecompiledContract contract =
        new RIPEMD160PrecompiledContract(new IstanbulGasCalculator());
    final byte[] warmupData = new byte[240];
    final Bytes warmupBytes = Bytes.wrap(warmupData);
    for (int i = 0; i < HASH_WARMUP; i++) {
      contract.compute(warmupBytes, null);
    }
    for (int len = 0; len <= 256; len += 8) {
      final byte[] data = new byte[len];
      random.nextBytes(data);
      final Bytes bytes = Bytes.wrap(data);
      final Stopwatch timer = Stopwatch.createStarted();
      for (int i = 0; i < HASH_ITERATIONS; i++) {
        contract.compute(bytes, null);
      }
      timer.stop();

      final double elapsed = timer.elapsed(TimeUnit.NANOSECONDS) / 1.0e9D;
      final double perCall = elapsed / HASH_ITERATIONS;
      final double gasSpent = perCall * GAS_PER_SECOND_STANDARD;

      System.out.printf(
          "Hashed %,d bytes for %,d gas. Charging %,d gas.%n",
          len, (int) gasSpent, contract.gasRequirement(bytes).asUInt256().toLong());
    }
  }

  private static void benchBNADD() {
    final Bytes g1Point0 =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x17c139df0efee0f766bc0204762b774362e4ded88953a39ce849a8a7fa163fa9"),
            Bytes.fromHexString(
                "0x01e0559bacb160664764a357af8a9fe70baa9258e0b959273ffc5718c6d4cc7c"));

    final Bytes g1Point1 =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x17c139df0efee0f766bc0204762b774362e4ded88953a39ce849a8a7fa163fa9"),
            Bytes.fromHexString(
                "0x2e83f8d734803fc370eba25ed1f6b8768bd6d83887b87165fc2434fe11a830cb"));
    final Bytes arg = Bytes.concatenate(g1Point0, g1Point1);

    final AltBN128AddPrecompiledContract contract =
        AltBN128AddPrecompiledContract.istanbul(new IstanbulGasCalculator());

    final double gasSpent = runBenchmark(arg, contract);

    System.out.printf(
        "BNADD for %,d gas.  Charging %,d gas.%n",
        (int) gasSpent, contract.gasRequirement(arg).asUInt256().toLong());
  }

  private static void benchBNMUL() {
    final Bytes g1Point1 =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"),
            Bytes.fromHexString(
                "0x30644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd45"));
    final Bytes scalar =
        Bytes.fromHexString("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
    final Bytes arg = Bytes.concatenate(g1Point1, scalar);

    final AltBN128MulPrecompiledContract contract =
        AltBN128MulPrecompiledContract.istanbul(new IstanbulGasCalculator());

    final double gasSpent = runBenchmark(arg, contract);

    System.out.printf(
        "BNMUL for %,d gas.  Charging %,d gas.%n",
        (int) gasSpent, contract.gasRequirement(arg).asUInt256().toLong());
  }

  public static void benchBLS12G1Add() {
    final Bytes arg =
        Bytes.fromHexString(
            "0000000000000000000000000000000012196c5a43d69224d8713389285f26b98f86ee910ab3dd668e413738282003cc5b7357af9a7af54bb713d62255e80f56"
                + "0000000000000000000000000000000006ba8102bfbeea4416b710c73e8cce3032c31c6269c44906f8ac4f7874ce99fb17559992486528963884ce429a992fee"
                + "000000000000000000000000000000000001101098f5c39893765766af4512a0c74e1bb89bc7e6fdf14e3e7337d257cc0f94658179d83320b99f31ff94cd2bac"
                + "0000000000000000000000000000000003e1a9f9f44ca2cdab4f43a1a3ee3470fdf90b2fc228eb3b709fcd72f014838ac82a6d797aeefed9a0804b22ed1ce8f7");

    final BLS12G1AddPrecompiledContract contract = new BLS12G1AddPrecompiledContract();

    final double gasSpent = runBenchmark(arg, contract);

    System.out.printf(
        "G1ADD for %,d gas. Charging %,d gas.%n",
        (int) gasSpent, contract.gasRequirement(arg).asUInt256().toLong());
  }

  public static void benchBLS12G1Mul() {
    final Bytes arg =
        Bytes.fromHexString(
            "0000000000000000000000000000000017f1d3a73197d7942695638c4fa9ac0fc3688c4f9774b905a14e3a3f171bac586c55e83ff97a1aeffb3af00adb22c6bb"
                + "0000000000000000000000000000000008b3f481e3aaa0f1a09e30ed741d8ae4fcf5e095d5d00af600db18cb2c04b3edd03cc744a2888ae40caa232946c5e7e1"
                + "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

    final BLS12G1MulPrecompiledContract contract = new BLS12G1MulPrecompiledContract();
    contract.compute(arg, null);

    final double gasSpent = runBenchmark(arg, contract);

    System.out.printf(
        "G1MUL for %,d gas.  Charging %,d gas.%n",
        (int) gasSpent, contract.gasRequirement(arg).asUInt256().toLong());
  }

  public static void benchBLS12G1MultiExp() {
    final Bytes[] args = {
      Bytes.fromHexString(
          "0000000000000000000000000000000012196c5a43d69224d8713389285f26b98f86ee910ab3dd668e413738282003cc5b7357af9a7af54bb713d62255e80f560000000000000000000000000000000006ba8102bfbeea4416b710c73e8cce3032c31c6269c44906f8ac4f7874ce99fb17559992486528963884ce429a992fee"
              + "b3c940fe79b6966489b527955de7599194a9ac69a6ff58b8d99e7b1084f0464e"),
      Bytes.fromHexString(
          "00000000000000000000000000000000117dbe419018f67844f6a5e1b78a1e597283ad7b8ee7ac5e58846f5a5fd68d0da99ce235a91db3ec1cf340fe6b7afcdb0000000000000000000000000000000013316f23de032d25e912ae8dc9b54c8dba1be7cecdbb9d2228d7e8f652011d46be79089dd0a6080a73c82256ce5e4ed2"
              + "4d0e25bf3f6fc9f4da25d21fdc71773f1947b7a8a775b8177f7eca990b05b71d"
              + "0000000000000000000000000000000008ab7b556c672db7883ec47efa6d98bb08cec7902ebb421aac1c31506b177ac444ffa2d9b400a6f1cbdc6240c607ee110000000000000000000000000000000016b7fa9adf4addc2192271ce7ad3c8d8f902d061c43b7d2e8e26922009b777855bffabe7ed1a09155819eabfa87f276f"
              + "973f40c12c92b703d7b7848ef8b4466d40823aad3943a312b57432b91ff68be1"),
      Bytes.fromHexString(
          "0000000000000000000000000000000015ff9a232d9b5a8020a85d5fe08a1dcfb73ece434258fe0e2fddf10ddef0906c42dcb5f5d62fc97f934ba900f17beb330000000000000000000000000000000009cfe4ee2241d9413c616462d7bac035a6766aeaab69c81e094d75b840df45d7e0dfac0265608b93efefb9a8728b98e4"
              + "4c51f97bcdda93904ae26991b471e9ea942e2b5b8ed26055da11c58bc7b5002a"
              + "0000000000000000000000000000000017a17b82e3bfadf3250210d8ef572c02c3610d65ab4d7366e0b748768a28ee6a1b51f77ed686a64f087f36f641e7dca900000000000000000000000000000000077ea73d233ccea51dc4d5acecf6d9332bf17ae51598f4b394a5f62fb387e9c9aa1d6823b64a074f5873422ca57545d3"
              + "8964d5867927bc3e35a0b4c457482373969bff5edff8a781d65573e07fd87b89"
              + "000000000000000000000000000000000c1243478f4fbdc21ea9b241655947a28accd058d0cdb4f9f0576d32f09dddaf0850464550ff07cab5927b3e4c863ce90000000000000000000000000000000015fb54db10ffac0b6cd374eb7168a8cb3df0a7d5f872d8e98c1f623deb66df5dd08ff4c3658f2905ec8bd02598bd4f90"
              + "787c38b944eadbd03fd3187f450571740f6cd00e5b2e560165846eb800e5c944"),
      Bytes.fromHexString(
          "000000000000000000000000000000000328f09584b6d6c98a709fc22e184123994613aca95a28ac53df8523b92273eb6f4e2d9b2a7dcebb474604d54a210719000000000000000000000000000000001220ebde579911fe2e707446aaad8d3789fae96ae2e23670a4fd856ed82daaab704779eb4224027c1ed9460f39951a1b"
              + "aaee7ae2a237e8e53560c79e7baa9adf9c00a0ea4d6f514e7a6832eb15cef1e1"
              + "0000000000000000000000000000000002ebfa98aa92c32a29ebe17fcb1819ba82e686abd9371fcee8ea793b4c72b6464085044f818f1f5902396df0122830cb00000000000000000000000000000000001184715b8432ed190b459113977289a890f68f6085ea111466af15103c9c02467da33e01d6bff87fd57db6ccba442a"
              + "dac6ed3ef45c1d7d3028f0f89e5458797996d3294b95bebe049b76c7d0db317c"
              + "0000000000000000000000000000000009d6424e002439998e91cd509f85751ad25e574830c564e7568347d19e3f38add0cab067c0b4b0801785a78bcbeaf246000000000000000000000000000000000ef6d7db03ee654503b46ff0dbc3297536a422e963bda9871a8da8f4eeb98dedebd6071c4880b4636198f4c2375dc795"
              + "bb30985756c3ca075114c92f231575d6befafe4084517f1166a47376867bd108"
              + "0000000000000000000000000000000002d1cdb93191d1f9f0308c2c55d0208a071f5520faca7c52ab0311dbc9ba563bd33b5dd6baa77bf45ac2c3269e945f4800000000000000000000000000000000072a52106e6d7b92c594c4dacd20ef5fab7141e45c231457cd7e71463b2254ee6e72689e516fa6a8f29f2a173ce0a190"
              + "fb730105809f64ea522983d6bbb62f7e2e8cbf702685e9be10e2ef71f8187672"),
      Bytes.fromHexString(
          "0000000000000000000000000000000000641642f6801d39a09a536f506056f72a619c50d043673d6d39aa4af11d8e3ded38b9c3bbc970dbc1bd55d68f94b50d0000000000000000000000000000000009ab050de356a24aea90007c6b319614ba2f2ed67223b972767117769e3c8e31ee4056494628fb2892d3d37afb6ac943"
              + "b6a9408625b0ca8fcbfb21d34eec2d8e24e9a30d2d3b32d7a37d110b13afbfea"
              + "000000000000000000000000000000000fd4893addbd58fb1bf30b8e62bef068da386edbab9541d198e8719b2de5beb9223d87387af82e8b55bd521ff3e47e2d000000000000000000000000000000000f3a923b76473d5b5a53501790cb02597bb778bdacb3805a9002b152d22241ad131d0f0d6a260739cbab2c2fe602870e"
              + "3b77283d0a7bb9e17a27e66851792fdd605cc0a339028b8985390fd024374c76"
              + "0000000000000000000000000000000002cb4b24c8aa799fd7cb1e4ab1aab1372113200343d8526ea7bc64dfaf926baf5d90756a40e35617854a2079cd07fba40000000000000000000000000000000003327ca22bd64ebd673cc6d5b02b2a8804d5353c9d251637c4273ad08d581cc0d58da9bea27c37a0b3f4961dbafd276b"
              + "dd994eae929aee7428fdda2e44f8cb12b10b91c83b22abc8bbb561310b62257c"
              + "00000000000000000000000000000000024ad70f2b2105ca37112858e84c6f5e3ffd4a8b064522faae1ecba38fabd52a6274cb46b00075deb87472f11f2e67d90000000000000000000000000000000010a502c8b2a68aa30d2cb719273550b9a3c283c35b2e18a01b0b765344ffaaa5cb30a1e3e6ecd3a53ab67658a5787681"
              + "7010b134989c8368c7f831f9dd9f9a890e2c1435681107414f2e8637153bbf6a"
              + "0000000000000000000000000000000000704cc57c8e0944326ddc7c747d9e7347a7f6918977132eea269f161461eb64066f773352f293a3ac458dc3ccd5026a000000000000000000000000000000001099d3c2bb2d082f2fdcbed013f7ac69e8624f4fcf6dfab3ee9dcf7fbbdb8c49ee79de40e887c0b6828d2496e3a6f768"
              + "94c68bc8d91ac8c489ee87dbfc4b94c93c8bbd5fc04c27db8b02303f3a659054")
    };

    final BLS12G1MultiExpPrecompiledContract contract = new BLS12G1MultiExpPrecompiledContract();

    for (int i = 0; i < args.length; i++) {
      final double gasSpent = runBenchmark(args[i], contract);

      System.out.printf(
          "G1MULTIEXP %d for %,d gas.  Charging %,d gas.%n",
          i + 1, (int) gasSpent, contract.gasRequirement(args[i]).asUInt256().toLong());
    }
  }

  public static void benchBLS12G2Add() {
    final Bytes arg =
        Bytes.fromHexString(
            "00000000000000000000000000000000039b10ccd664da6f273ea134bb55ee48f09ba585a7e2bb95b5aec610631ac49810d5d616f67ba0147e6d1be476ea220e0000000000000000000000000000000000fbcdff4e48e07d1f73ec42fe7eb026f5c30407cfd2f22bbbfe5b2a09e8a7bb4884178cb6afd1c95f80e646929d3004"
                + "0000000000000000000000000000000001ed3b0e71acb0adbf44643374edbf4405af87cfc0507db7e8978889c6c3afbe9754d1182e98ac3060d64994d31ef576000000000000000000000000000000001681a2bf65b83be5a2ca50430949b6e2a099977482e9405b593f34d2ed877a3f0d1bddc37d0cec4d59d7df74b2b8f2df"
                + "0000000000000000000000000000000017c9fcf0504e62d3553b2f089b64574150aa5117bd3d2e89a8c1ed59bb7f70fb83215975ef31976e757abf60a75a1d9f0000000000000000000000000000000008f5a53d704298fe0cfc955e020442874fe87d5c729c7126abbdcbed355eef6c8f07277bee6d49d56c4ebaf334848624"
                + "000000000000000000000000000000001302dcc50c6ce4c28086f8e1b43f9f65543cf598be440123816765ab6bc93f62bceda80045fbcad8598d4f32d03ee8fa000000000000000000000000000000000bbb4eb37628d60b035a3e0c45c0ea8c4abef5a6ddc5625e0560097ef9caab208221062e81cd77ef72162923a1906a40");

    final BLS12G2AddPrecompiledContract contract = new BLS12G2AddPrecompiledContract();

    final double gasSpent = runBenchmark(arg, contract);

    System.out.printf(
        "G2ADD for %,d gas.  Charging %,d gas.%n",
        (int) gasSpent, contract.gasRequirement(arg).asUInt256().toLong());
  }

  public static void benchBLS12G2Mul() {
    final Bytes arg =
        Bytes.fromHexString(
            "00000000000000000000000000000000024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb80000000000000000000000000000000013e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e"
                + "000000000000000000000000000000000ce5d527727d6e118cc9cdc6da2e351aadfd9baa8cbdd3a76d429a695160d12c923ac9cc3baca289e193548608b82801000000000000000000000000000000000606c4a02ea734cc32acd2b02bc28b99cb3e287e85a763af267492ab572e99ab3f370d275cec1da1aaa9075ff05f79be"
                + "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

    final BLS12G2MulPrecompiledContract contract = new BLS12G2MulPrecompiledContract();

    final double gasSpent = runBenchmark(arg, contract);

    System.out.printf(
        "G2MUL for %,d gas.  Charging %,d gas.%n",
        (int) gasSpent, contract.gasRequirement(arg).asUInt256().toLong());
  }

  public static void benchBLS12G2MultiExp() {
    final Bytes[] args = {
      Bytes.fromHexString(
          "00000000000000000000000000000000039b10ccd664da6f273ea134bb55ee48f09ba585a7e2bb95b5aec610631ac49810d5d616f67ba0147e6d1be476ea220e0000000000000000000000000000000000fbcdff4e48e07d1f73ec42fe7eb026f5c30407cfd2f22bbbfe5b2a09e8a7bb4884178cb6afd1c95f80e646929d30040000000000000000000000000000000001ed3b0e71acb0adbf44643374edbf4405af87cfc0507db7e8978889c6c3afbe9754d1182e98ac3060d64994d31ef576000000000000000000000000000000001681a2bf65b83be5a2ca50430949b6e2a099977482e9405b593f34d2ed877a3f0d1bddc37d0cec4d59d7df74b2b8f2df"
              + "b3c940fe79b6966489b527955de7599194a9ac69a6ff58b8d99e7b1084f0464e"),
      Bytes.fromHexString(
          "0000000000000000000000000000000018c0ada6351b70661f053365deae56910798bd2ace6e2bf6ba4192d1a229967f6af6ca1c9a8a11ebc0a232344ee0f6d6000000000000000000000000000000000cc70a587f4652039d8117b6103858adcd9728f6aebe230578389a62da0042b7623b1c0436734f463cfdd187d20903240000000000000000000000000000000009f50bd7beedb23328818f9ffdafdb6da6a4dd80c5a9048ab8b154df3cad938ccede829f1156f769d9e149791e8e0cd900000000000000000000000000000000079ba50d2511631b20b6d6f3841e616e9d11b68ec3368cd60129d9d4787ab56c4e9145a38927e51c9cd6271d493d9388"
              + "4d0e25bf3f6fc9f4da25d21fdc71773f1947b7a8a775b8177f7eca990b05b71d"
              + "0000000000000000000000000000000003632695b09dbf86163909d2bb25995b36ad1d137cf252860fd4bb6c95749e19eb0c1383e9d2f93f2791cb0cf6c8ed9d000000000000000000000000000000001688a855609b0bbff4452d146396558ff18777f329fd4f76a96859dabfc6a6f6977c2496280dbe3b1f8923990c1d6407000000000000000000000000000000000c8567fee05d05af279adc67179468a29d7520b067dbb348ee315a99504f70a206538b81a457cce855f4851ad48b7e80000000000000000000000000000000001238dcdfa80ea46e1500026ea5feadb421de4409f4992ffbf5ae59fa67fd82f38452642a50261b849e74b4a33eed70cc"
              + "973f40c12c92b703d7b7848ef8b4466d40823aad3943a312b57432b91ff68be1"),
      Bytes.fromHexString(
          "000000000000000000000000000000000149704960cccf9d5ea414c73871e896b1d4cf0a946b0db72f5f2c5df98d2ec4f3adbbc14c78047961bc9620cb6cfb5900000000000000000000000000000000140c5d25e534fb1bfdc19ba4cecaabe619f6e0cd3d60b0f17dafd7bcd27b286d4f4477d00c5e1af22ee1a0c67fbf177c00000000000000000000000000000000029a1727041590b8459890de736df15c00d80ab007c3aee692ddcdf75790c9806d198e9f4502bec2f0a623491c3f877d0000000000000000000000000000000008a94c98baa9409151030d4fae2bd4a64c6f11ea3c99b9661fdaed226b9a7c2a7d609be34afda5d18b8911b6e015bf49"
              + "4c51f97bcdda93904ae26991b471e9ea942e2b5b8ed26055da11c58bc7b5002a"
              + "000000000000000000000000000000001156d478661337478ab0cbc877a99d9e4d9824a2b3f605d41404d6b557b3ffabbf42635b0bbcb854cf9ed8b8637561a8000000000000000000000000000000001147ed317d5642e699787a7b47e6795c9a8943a34a694007e44f8654ba96390cf19f010dcf695e22c21874022c6ce291000000000000000000000000000000000c6dccdf920fd5e7fae284115511952633744c6ad94120d9cae6acda8a7c23c48bd912cba6c38de5159587e1e6cad519000000000000000000000000000000001944227d462bc2e5dcc6f6db0f83dad411ba8895262836f975b2b91e06fd0e2138862162acc04e9e65050b34ccbd1a4e"
              + "8964d5867927bc3e35a0b4c457482373969bff5edff8a781d65573e07fd87b89"
              + "0000000000000000000000000000000019c31e3ab8cc9c920aa8f56371f133b6cb8d7b0b74b23c0c7201aca79e5ae69dc01f1f74d2492dcb081895b17d106b4e000000000000000000000000000000001789b0d371bd63077ccde3dbbebf3531368feb775bced187fb31cc6821481664600978e323ff21085b8c08e0f21daf72000000000000000000000000000000000009eacfe8f4a2a9bae6573424d07f42bd6af8a9d55f71476a7e3c7a4b2b898550c1e72ec13afd4eff22421a03af1d31000000000000000000000000000000000410bd4ea74dcfa33f2976aa1b571c67cbb596ab10f76a8aaf4548f1097e55b3373bff02683f806cb84e1e0e877819e2"
              + "787c38b944eadbd03fd3187f450571740f6cd00e5b2e560165846eb800e5c944"),
      Bytes.fromHexString(
          "00000000000000000000000000000000147f09986691f2e57073378e8bfd58804241eed7934f6adfe6d0a6bac4da0b738495778a303e52113e1c80e698476d50000000000000000000000000000000000762348b84c92a8ca6de319cf1f8f11db296a71b90fe13e1e4bcd25903829c00a5d2ad4b1c8d98c37eaad7e042ab023d0000000000000000000000000000000011d1d94530d4a2daf0e902a5c3382cd135938557f94b04bccea5e16ea089c5e020e13524c854a316662bd68784fe31f300000000000000000000000000000000070828522bec75b6a492fd9bca7b54dac6fbbf4f0bc3179d312bb65c647439e3868e4d5b21af5a64c93aeee8a9b7e46e"
              + "aaee7ae2a237e8e53560c79e7baa9adf9c00a0ea4d6f514e7a6832eb15cef1e1"
              + "000000000000000000000000000000000690a0869204c8dced5ba0ce13554b2703a3f18afb8fa8fa1c457d79c58fdc25471ae85bafad52e506fc1917fc3becff0000000000000000000000000000000010f7dbb16f8571ede1cec79e3f9ea03ae6468d7285984713f19607f5cab902b9a6b7cbcfd900be5c2e407cc093ea0e6700000000000000000000000000000000151caf87968433cb1f85fc1854c57049be22c26497a86bfbd66a2b3af121d894dba8004a17c6ff96a5843c2719fa32d10000000000000000000000000000000011f0270f2b039409f70392879bcc2c67c836c100cf9883d3dc48d7adbcd52037d270539e863a951acd47ecaa1ca4db12"
              + "dac6ed3ef45c1d7d3028f0f89e5458797996d3294b95bebe049b76c7d0db317c"
              + "0000000000000000000000000000000017fae043c8fd4c520a90d4a6bd95f5b0484acc279b899e7b1d8f7f7831cc6ba37cd5965c4dc674768f5805842d433af30000000000000000000000000000000008ddd7b41b8fa4d29fb931830f29b46f4015ec202d51cb969d7c832aafc0995c875cd45eff4a083e2d5ecb5ad185b64f0000000000000000000000000000000015d384ab7e52420b83a69827257cb52b00f0199ed2240a142812b46cf67e92b99942ac59fb9f9efd7dd822f5a36c799f00000000000000000000000000000000074b3a16a9cc4be9da0ac8e2e7003d9c1ec89244d2c33441b31af76716cce439f805843a9a44701203231efdca551d5b"
              + "bb30985756c3ca075114c92f231575d6befafe4084517f1166a47376867bd108"
              + "000000000000000000000000000000000e25365988664e8b6ade2e5a40da49c11ff1e084cc0f8dca51f0d0578555d39e3617c8cadb2abc2633b28c5895ab0a9e00000000000000000000000000000000169f5fd768152169c403475dee475576fd2cc3788179453b0039ff3cb1b7a5a0fff8f82d03f56e65cad579218486c3b600000000000000000000000000000000087ccd7f92032febc1f75c7115111ede4acbb2e429cbccf3959524d0b79c449d431ff65485e1aecb442b53fec80ecb4000000000000000000000000000000000135d63f264360003b2eb28f126c6621a40088c6eb15acc4aea89d6068e9d5a47f842aa4b4300f5cda5cc5831edb81596"
              + "fb730105809f64ea522983d6bbb62f7e2e8cbf702685e9be10e2ef71f8187672"),
      Bytes.fromHexString(
          "00000000000000000000000000000000159da74f15e4c614b418997f81a1b8a3d9eb8dd80d94b5bad664bff271bb0f2d8f3c4ceb947dc6300d5003a2f7d7a829000000000000000000000000000000000cdd4d1d4666f385dd54052cf5c1966328403251bebb29f0d553a9a96b5ade350c8493270e9b5282d8a06f9fa8d7b1d900000000000000000000000000000000189f8d3c94fdaa72cc67a7f93d35f91e22206ff9e97eed9601196c28d45b69c802ae92bcbf582754717b0355e08d37c000000000000000000000000000000000054b0a282610f108fc7f6736b8c22c8778d082bf4b0d0abca5a228198eba6a868910dd5c5c440036968e977955054196"
              + "b6a9408625b0ca8fcbfb21d34eec2d8e24e9a30d2d3b32d7a37d110b13afbfea"
              + "000000000000000000000000000000000f29b0d2b6e3466668e1328048e8dbc782c1111ab8cbe718c85d58ded992d97ca8ba20b9d048feb6ed0aa1b4139d02d3000000000000000000000000000000000d1f0dae940b99fbfc6e4a58480cac8c4e6b2fe33ce6f39c7ac1671046ce94d9e16cba2bb62c6749ef73d45bea21501a000000000000000000000000000000001902ccece1c0c763fd06934a76d1f2f056563ae6d8592bafd589cfebd6f057726fd908614ccd6518a21c66ecc2f78b660000000000000000000000000000000017f6b113f8872c3187d20b0c765d73b850b54244a719cf461fb318796c0b8f310b5490959f9d9187f99c8ed3e25e42a9"
              + "3b77283d0a7bb9e17a27e66851792fdd605cc0a339028b8985390fd024374c76"
              + "000000000000000000000000000000000576b8cf1e69efdc277465c344cadf7f8cceffacbeca83821f3ff81717308b97f4ac046f1926e7c2eb42677d7afc257c000000000000000000000000000000000cc1524531e96f3c00e4250dd351aedb5a4c3184aff52ec8c13d470068f5967f3674fe173ee239933e67501a9decc6680000000000000000000000000000000001610cfcaea414c241b44cf6f3cc319dcb51d6b8de29c8a6869ff7c1ebb7b747d881e922b42e8fab96bde7cf23e8e4cd0000000000000000000000000000000017d4444dc8b6893b681cf10dac8169054f9d2f61d3dd5fd785ae7afa49d18ebbde9ce8dde5641adc6b38173173459836"
              + "dd994eae929aee7428fdda2e44f8cb12b10b91c83b22abc8bbb561310b62257c"
              + "000000000000000000000000000000000ca8f961f86ee6c46fc88fbbf721ba760186f13cd4cce743f19dc60a89fd985cb3feee34dcc4656735a326f515a729e400000000000000000000000000000000174baf466b809b1155d524050f7ee58c7c5cf728c674e0ce549f5551047a4479ca15bdf69b403b03fa74eb1b26bbff6c0000000000000000000000000000000000e8c8b587c171b1b292779abfef57202ed29e7fe94ade9634ec5a2b3b4692a4f3c15468e3f6418b144674be70780d5b000000000000000000000000000000001865e99cf97d88bdf56dae32314eb32295c39a1e755cd7d1478bea8520b9ff21c39b683b92ae15568420c390c42b123b"
              + "7010b134989c8368c7f831f9dd9f9a890e2c1435681107414f2e8637153bbf6a"
              + "0000000000000000000000000000000017eccd446f10018219a1bd111b8786cf9febd49f9e7e754e82dd155ead59b819f0f20e42f4635d5044ec5d550d847623000000000000000000000000000000000403969d2b8f914ff2ea3bf902782642e2c6157bd2a343acf60ff9125b48b558d990a74c6d4d6398e7a3cc2a16037346000000000000000000000000000000000bd45f61f142bd78619fb520715320eb5e6ebafa8b078ce796ba62fe1a549d5fb9df57e92d8d2795988eb6ae18cf9d9300000000000000000000000000000000097db1314e064b8e670ec286958f17065bce644cf240ab1b1b220504560d36a0b43fc18453ff3a2bb315e219965f5bd3"
              + "94c68bc8d91ac8c489ee87dbfc4b94c93c8bbd5fc04c27db8b02303f3a659054")
    };

    final BLS12G2MultiExpPrecompiledContract contract = new BLS12G2MultiExpPrecompiledContract();

    for (int i = 0; i < args.length; i++) {
      final double gasSpent = runBenchmark(args[i], contract);

      System.out.printf(
          "G2MULTIEXP %d for %,d gas.  Charging %,d gas.%n",
          i + 1, (int) gasSpent, contract.gasRequirement(args[i]).asUInt256().toLong());
    }
  }

  public static void benchBLS12Pair() {
    final Bytes[] args = {
      Bytes.fromHexString(
          "0000000000000000000000000000000012196c5a43d69224d8713389285f26b98f86ee910ab3dd668e413738282003cc5b7357af9a7af54bb713d62255e80f56"
              + "0000000000000000000000000000000006ba8102bfbeea4416b710c73e8cce3032c31c6269c44906f8ac4f7874ce99fb17559992486528963884ce429a992fee0000000000000000000000000000000017c9fcf0504e62d3553b2f089b64574150aa5117bd3d2e89a8c1ed59bb7f70fb83215975ef31976e757abf60a75a1d9f"
              + "0000000000000000000000000000000008f5a53d704298fe0cfc955e020442874fe87d5c729c7126abbdcbed355eef6c8f07277bee6d49d56c4ebaf334848624"
              + "000000000000000000000000000000001302dcc50c6ce4c28086f8e1b43f9f65543cf598be440123816765ab6bc93f62bceda80045fbcad8598d4f32d03ee8fa000000000000000000000000000000000bbb4eb37628d60b035a3e0c45c0ea8c4abef5a6ddc5625e0560097ef9caab208221062e81cd77ef72162923a1906a40"),
      Bytes.fromHexString(
          "000000000000000000000000000000001830f52d9bff64a623c6f5259e2cd2c2a08ea17a8797aaf83174ea1e8c3bd3955c2af1d39bfa474815bfe60714b7cd80"
              + "000000000000000000000000000000000874389c02d4cf1c61bc54c4c24def11dfbe7880bc998a95e70063009451ee8226fec4b278aade3a7cea55659459f1d500000000000000000000000000000000197737f831d4dc7e708475f4ca7ca15284db2f3751fcaac0c17f517f1ddab35e1a37907d7b99b39d6c8d9001cd50e79e"
              + "000000000000000000000000000000000af1a3f6396f0c983e7c2d42d489a3ae5a3ff0a553d93154f73ac770cd0af7467aa0cef79f10bbd34621b3ec9583a834"
              + "000000000000000000000000000000001918cb6e448ed69fb906145de3f11455ee0359d030e90d673ce050a360d796de33ccd6a941c49a1414aca1c26f9e699e0000000000000000000000000000000019a915154a13249d784093facc44520e7f3a18410ab2a3093e0b12657788e9419eec25729944f7945e732104939e7a9e"
              + "000000000000000000000000000000001830f52d9bff64a623c6f5259e2cd2c2a08ea17a8797aaf83174ea1e8c3bd3955c2af1d39bfa474815bfe60714b7cd80"
              + "00000000000000000000000000000000118cd94e36ab177de95f52f180fdbdc584b8d30436eb882980306fa0625f07a1f7ad3b4c38a921c53d14aa9a6ba5b8d600000000000000000000000000000000197737f831d4dc7e708475f4ca7ca15284db2f3751fcaac0c17f517f1ddab35e1a37907d7b99b39d6c8d9001cd50e79e"
              + "000000000000000000000000000000000af1a3f6396f0c983e7c2d42d489a3ae5a3ff0a553d93154f73ac770cd0af7467aa0cef79f10bbd34621b3ec9583a834"
              + "000000000000000000000000000000001918cb6e448ed69fb906145de3f11455ee0359d030e90d673ce050a360d796de33ccd6a941c49a1414aca1c26f9e699e0000000000000000000000000000000019a915154a13249d784093facc44520e7f3a18410ab2a3093e0b12657788e9419eec25729944f7945e732104939e7a9e"),
      Bytes.fromHexString(
          "00000000000000000000000000000000189bf269a72de2872706983835afcbd09f6f4dfcabe0241b4e9fe1965a250d230d6f793ab17ce7cac456af7be4376be6"
              + "000000000000000000000000000000000d4441801d287ba8de0e2fb6b77f766dbff07b4027098ce463cab80e01eb31d9f5dbd7ac935703d68c7032fa5128ff170000000000000000000000000000000011798ea9c137acf6ef9483b489c0273d4f69296959922a352b079857953263372b8d339115f0576cfabedc185abf2086"
              + "000000000000000000000000000000001498b1412f52b07a0e4f91cbf5e1852ea38fc111613523f1e61b97ebf1fd7fd2cdf36d7f73f1e33719c0b63d7bf66b8f"
              + "0000000000000000000000000000000004c56d3ee9931f7582d7eebeb598d1be208e3b333ab976dc7bb271969fa1d6caf8f467eb7cbee4af5d30e5c66d00a4e2000000000000000000000000000000000de29857dae126c0acbe966da6f50342837ef5dd9994ad929d75814f6f33f77e5b33690945bf6e980031ddd90ebc76ce"
              + "00000000000000000000000000000000189bf269a72de2872706983835afcbd09f6f4dfcabe0241b4e9fe1965a250d230d6f793ab17ce7cac456af7be4376be6"
              + "000000000000000000000000000000000cbcd06a1c576af16d0d77ff8bcc3669a486d044cc7b85db03661a92f4c5c44a28d028521dfcfc292d8ecd05aed6ab940000000000000000000000000000000011798ea9c137acf6ef9483b489c0273d4f69296959922a352b079857953263372b8d339115f0576cfabedc185abf2086"
              + "000000000000000000000000000000001498b1412f52b07a0e4f91cbf5e1852ea38fc111613523f1e61b97ebf1fd7fd2cdf36d7f73f1e33719c0b63d7bf66b8f"
              + "0000000000000000000000000000000004c56d3ee9931f7582d7eebeb598d1be208e3b333ab976dc7bb271969fa1d6caf8f467eb7cbee4af5d30e5c66d00a4e2000000000000000000000000000000000de29857dae126c0acbe966da6f50342837ef5dd9994ad929d75814f6f33f77e5b33690945bf6e980031ddd90ebc76ce"
              + "00000000000000000000000000000000189bf269a72de2872706983835afcbd09f6f4dfcabe0241b4e9fe1965a250d230d6f793ab17ce7cac456af7be4376be6"
              + "000000000000000000000000000000000d4441801d287ba8de0e2fb6b77f766dbff07b4027098ce463cab80e01eb31d9f5dbd7ac935703d68c7032fa5128ff170000000000000000000000000000000011798ea9c137acf6ef9483b489c0273d4f69296959922a352b079857953263372b8d339115f0576cfabedc185abf2086"
              + "000000000000000000000000000000001498b1412f52b07a0e4f91cbf5e1852ea38fc111613523f1e61b97ebf1fd7fd2cdf36d7f73f1e33719c0b63d7bf66b8f"
              + "00000000000000000000000000000000153ba4ab4fecc724c843b8f78db2db1943e91051b8cb9be2eb7e610a570f1f5925b7981334951b505cce1a3992ff05c9000000000000000000000000000000000c1e79925e9ebfd99e5d11489c56a994e0f855a759f0652cc9bb5151877cfea5c37896f56b949167b9cd2226f14333dd"),
    };
    final BLS12PairingPrecompiledContract contract = new BLS12PairingPrecompiledContract();

    for (int i = 0; i < args.length; i++) {
      final double gasSpent = runBenchmark(args[i], contract);

      System.out.printf(
          "BLS pairings %d pairs for %,d gas.  Charging %,d gas.%n",
          i * 2 + 2, (int) gasSpent, contract.gasRequirement(args[i]).asUInt256().toLong());
    }
  }

  public static void benchBLS12MapFPTOG1() {
    final Bytes arg =
        Bytes.fromHexString(
            "0000000000000000000000000000000014406e5bfb9209256a3820879a29ac2f62d6aca82324bf3ae2aa7d3c54792043bd8c791fccdb080c1a52dc68b8b69350");

    final BLS12MapFpToG1PrecompiledContract contract = new BLS12MapFpToG1PrecompiledContract();

    final double gasSpent = runBenchmark(arg, contract);

    System.out.printf(
        "MAPFPTOG1 for %,d gas.  Charging %,d gas.%n",
        (int) gasSpent, contract.gasRequirement(arg).asUInt256().toLong());
  }

  public static void benchBLS12MapFP2TOG2() {
    final Bytes arg =
        Bytes.fromHexString(
            "0000000000000000000000000000000014406e5bfb9209256a3820879a29ac2f62d6aca82324bf3ae2aa7d3c54792043bd8c791fccdb080c1a52dc68b8b69350000000000000000000000000000000000e885bb33996e12f07da69073e2c0cc880bc8eff26d2a724299eb12d54f4bcf26f4748bb020e80a7e3794a7b0e47a641");

    final BLS12MapFp2ToG2PrecompiledContract contract = new BLS12MapFp2ToG2PrecompiledContract();

    final double gasSpent = runBenchmark(arg, contract);

    System.out.printf(
        "MAPFP2TOG2 for %,d gas.  Charging %,d gas.%n",
        (int) gasSpent, contract.gasRequirement(arg).asUInt256().toLong());
  }

  private static double runBenchmark(final Bytes arg, final PrecompiledContract contract) {
    if (contract.compute(arg, null) == null) {
      throw new RuntimeException("Input is Invalid");
    }

    for (int i = 0; i < MATH_WARMUP; i++) {
      contract.compute(arg, null);
    }
    final Stopwatch timer = Stopwatch.createStarted();
    for (int i = 0; i < MATH_ITERATIONS; i++) {
      contract.compute(arg, null);
    }
    timer.stop();

    final double elapsed = timer.elapsed(TimeUnit.NANOSECONDS) / 1.0e9D;
    final double perCall = elapsed / MATH_ITERATIONS;
    return perCall * GAS_PER_SECOND_STANDARD;
  }

  public static void main(final String[] args) {
    System.out.println("SHA256");
    benchSha256();
    System.out.println("Keccak256");
    benchKeccak256();
    System.out.println("RIPEMD256");
    benchRipeMD();
    System.out.println("BNADD");
    benchBNADD();
    System.out.println("BNMUL");
    benchBNMUL();
    System.out.println("G1Add");
    benchBLS12G1Add();
    System.out.println("G1Mul");
    benchBLS12G1Mul();
    System.out.println("G1Multiexp");
    benchBLS12G1MultiExp();
    System.out.println("G2Add");
    benchBLS12G2Add();
    System.out.println("G2Mul");
    benchBLS12G2Mul();
    System.out.println("G2Multiexp");
    benchBLS12G2MultiExp();
    System.out.println("BLS Pairing");
    benchBLS12Pair();
    System.out.println("MapFPtoG1");
    benchBLS12MapFPTOG1();
    System.out.println("MapFP2toG2");
    benchBLS12MapFP2TOG2();
  }
}
