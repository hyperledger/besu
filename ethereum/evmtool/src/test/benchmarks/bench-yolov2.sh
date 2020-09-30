# Case 1 - https://gist.github.com/holiman/174548cad102096858583c6fbbb0649a#case-1
#
# This checks EXT(codehash,codesize,balance) of precompiles, which should be
# 100, and later checks the same operations twice against some non-precompiles.
# Those are cheaper second time they are accessed. Lastly, it checks the BALANCE
# of origin and this.
../../../build/install/evmtool/bin/evm  \
  --Xberlin-enabled=true \
  --code=0x60013f5060023b506003315060f13f5060f23b5060f3315060f23f5060f33b5060f1315032315030315000 \
  --sender=0x0000000000000000000000000000000000000000 \
  --receiver=0x000000000000000000000000636F6E7472616374 \
  --chain=YOLO_V2 \
  --gas=9223372036854775807 \
  --repeat=10
echo 'expect gasUser="0x21cd" (8653 dec)'

# Case 2 - https://gist.github.com/holiman/174548cad102096858583c6fbbb0649a#case-2
#
# This checks extcodecopy( 0xff,0,0,0,0) twice, (should be expensive first
# time), and then does extcodecopy( this,0,0,0,0)
../../../build/install/evmtool/bin/evm  \
  --Xberlin-enabled=true \
  --code=0x60006000600060ff3c60006000600060ff3c600060006000303c00 \
  --sender=0x0000000000000000000000000000000000000000 \
  --receiver=0x000000000000000000000000636F6E7472616374 \
  --chain=YOLO_V2 \
  --gas=9223372036854775807 \
  --repeat=10
echo 'expect gasUser="0xb13" (2835 dec)'

# Case 3 - https://gist.github.com/holiman/174548cad102096858583c6fbbb0649a#case-3
#
# This checks sload( 0x1) followed by sstore(loc: 0x01, val:0x11), then 'naked'
# sstore:sstore(loc: 0x02, val:0x11) twice, and sload(0x2), sload(0x1).
../../../build/install/evmtool/bin/evm  \
  --Xberlin-enabled=true \
  --code=0x60015450601160015560116002556011600255600254600154 \
  --sender=0x0000000000000000000000000000000000000000 \
  --receiver=0x000000000000000000000000636F6E7472616374 \
  --chain=YOLO_V2 \
  --gas=9223372036854775807 \
  --repeat=10
echo 'expect gasUser="0xadf1" (44529 dec)'

# Case 4 - https://gist.github.com/holiman/174548cad102096858583c6fbbb0649a#case-4
#
# This calls the identity-precompile (cheap), then calls an account (expensive)
# and staticcalls the same account (cheap)
../../../build/install/evmtool/bin/evm  \
  --Xberlin-enabled=true \
  --code=0x60008080808060046000f15060008080808060ff6000f15060008080808060ff6000fa50 \
  --sender=0x0000000000000000000000000000000000000000 \
  --receiver=0x000000000000000000000000636F6E7472616374 \
  --chain=YOLO_V2 \
  --gas=9223372036854775807 \
  --repeat=10
echo 'expect gasUser="0xb35" (2869 dec)'
