package io.emeraldpay.dshackle.startup.configure

import io.emeraldpay.dshackle.Chain
import io.emeraldpay.dshackle.Global
import io.emeraldpay.dshackle.config.ChainsConfig
import io.emeraldpay.dshackle.config.IndexConfig
import io.emeraldpay.dshackle.config.UpstreamsConfig
import io.emeraldpay.dshackle.foundation.ChainOptions
import io.emeraldpay.dshackle.upstream.CallTargetsHolder
import io.emeraldpay.dshackle.upstream.calls.CallMethods
import io.emeraldpay.dshackle.upstream.calls.ManagedCallMethods
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.function.Function
import kotlin.math.abs

abstract class UpstreamCreator(
    private val chainsConfig: ChainsConfig,
    private val indexConfig: IndexConfig,
    private val callTargets: CallTargetsHolder,
) {
    protected val log: Logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        fun getHash(nodeId: Int?, obj: Any, hashes: MutableMap<Byte, Boolean>): Byte =
            nodeId?.toByte() ?: (obj.hashCode() % 255).let {
                if (it == 0) 1 else it
            }.let { nonZeroHash ->
                listOf<Function<Int, Int>>(
                    Function { i -> i },
                    Function { i -> (-i) },
                    Function { i -> 127 - abs(i) },
                    Function { i -> abs(i) - 128 },
                ).map {
                    it.apply(nonZeroHash).toByte()
                }.firstOrNull {
                    hashes[it] != true
                }?.let {
                    hashes[it] = true
                    it
                } ?: (Byte.MIN_VALUE..Byte.MAX_VALUE).first {
                    it != 0 && hashes[it.toByte()] != true
                }.toByte()
            }
    }

    fun createUpstream(
        upstreamsConfig: UpstreamsConfig.Upstream<*>,
        defaultOptions: Map<Chain, ChainOptions.PartialOptions>,
    ): UpstreamCreationData {
        val chain = Global.chainById(upstreamsConfig.chain)
        if (chain == Chain.UNSPECIFIED) {
            throw IllegalArgumentException("Chain is unknown: ${upstreamsConfig.chain}")
        }
        val chainConfig = chainsConfig.resolve(upstreamsConfig.chain!!)
        val options = chainConfig.options
            .merge(defaultOptions[chain] ?: ChainOptions.PartialOptions.getDefaults())
            .merge(upstreamsConfig.options ?: ChainOptions.PartialOptions())
            .buildOptions()

        return createUpstream(upstreamsConfig, chain, options, chainConfig)
    }

    protected abstract fun createUpstream(
        upstreamsConfig: UpstreamsConfig.Upstream<*>,
        chain: Chain,
        options: ChainOptions.Options,
        chainConf: ChainsConfig.ChainConfig,
    ): UpstreamCreationData

    protected fun buildMethods(config: UpstreamsConfig.Upstream<*>, chain: Chain): CallMethods {
        return if (config.methods != null || config.methodGroups != null) {
            ManagedCallMethods(
                delegate = callTargets.getDefaultMethods(chain, indexConfig.isChainEnabled(chain)),
                enabled = config.methods?.enabled?.map { it.name }?.toSet() ?: emptySet(),
                disabled = config.methods?.disabled?.map { it.name }?.toSet() ?: emptySet(),
                groupsEnabled = config.methodGroups?.enabled ?: emptySet(),
                groupsDisabled = config.methodGroups?.disabled ?: emptySet(),
            ).also {
                config.methods?.enabled?.forEach { m ->
                    if (m.quorum != null) {
                        it.setQuorum(m.name, m.quorum)
                    }
                    if (m.static != null) {
                        it.setStaticResponse(m.name, m.static)
                    }
                }
            }
        } else {
            callTargets.getDefaultMethods(chain, indexConfig.isChainEnabled(chain))
        }
    }
}
