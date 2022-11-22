package io.emeraldpay.dshackle.upstream

import io.emeraldpay.dshackle.upstream.calls.CallMethods
import io.emeraldpay.dshackle.upstream.calls.DefaultBitcoinMethods
import io.emeraldpay.dshackle.upstream.calls.DefaultEthereumMethods
import io.emeraldpay.grpc.BlockchainType
import io.emeraldpay.grpc.Chain
import org.springframework.stereotype.Component
import java.util.HashMap

@Component
class CallTargetsHolder {
    private val callTargets = HashMap<Chain, CallMethods>()

    fun getDefaultMethods(chain: Chain): CallMethods {
        return callTargets[chain] ?: return setupDefaultMethods(chain)
    }

    private fun setupDefaultMethods(chain: Chain): CallMethods {
        val created = when (BlockchainType.from(chain)) {
            BlockchainType.EVM_POW -> DefaultEthereumMethods(chain)
            BlockchainType.BITCOIN -> DefaultBitcoinMethods()
            BlockchainType.EVM_POS -> DefaultEthereumMethods(chain)
            else -> throw IllegalStateException("Unsupported chain: $chain")
        }
        callTargets[chain] = created
        return created
    }
}
