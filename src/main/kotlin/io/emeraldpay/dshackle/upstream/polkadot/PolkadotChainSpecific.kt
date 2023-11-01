package io.emeraldpay.dshackle.upstream.polkadot

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.emeraldpay.dshackle.Chain
import io.emeraldpay.dshackle.Global
import io.emeraldpay.dshackle.config.ChainsConfig.ChainConfig
import io.emeraldpay.dshackle.data.BlockContainer
import io.emeraldpay.dshackle.data.BlockId
import io.emeraldpay.dshackle.foundation.ChainOptions.Options
import io.emeraldpay.dshackle.reader.JsonRpcReader
import io.emeraldpay.dshackle.upstream.CachingReader
import io.emeraldpay.dshackle.upstream.EgressSubscription
import io.emeraldpay.dshackle.upstream.EmptyEgressSubscription
import io.emeraldpay.dshackle.upstream.Head
import io.emeraldpay.dshackle.upstream.LabelsDetector
import io.emeraldpay.dshackle.upstream.Multistream
import io.emeraldpay.dshackle.upstream.NoopCachingReader
import io.emeraldpay.dshackle.upstream.Upstream
import io.emeraldpay.dshackle.upstream.UpstreamValidator
import io.emeraldpay.dshackle.upstream.calls.CallMethods
import io.emeraldpay.dshackle.upstream.generic.CachingReaderBuilder
import io.emeraldpay.dshackle.upstream.generic.ChainSpecific
import io.emeraldpay.dshackle.upstream.generic.GenericUpstream
import io.emeraldpay.dshackle.upstream.generic.LocalReader
import io.emeraldpay.dshackle.upstream.rpcclient.JsonRpcRequest
import io.emeraldpay.dshackle.upstream.rpcclient.JsonRpcResponse
import org.springframework.cloud.sleuth.Tracer
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import java.math.BigInteger
import java.time.Instant

object PolkadotChainSpecific : ChainSpecific {
    override fun parseBlock(data: JsonRpcResponse, upstreamId: String): BlockContainer {
        val raw = data.getResult()
        val response = Global.objectMapper.readValue(raw, PolkadotBlockResponse::class.java)

        return BlockContainer(
            height = response.block.header.number.substring(2).toLong(16),
            hash = BlockId.from(response.block.header.parentHash), // todo
            difficulty = BigInteger.ZERO,
            timestamp = Instant.MIN,
            full = false,
            json = raw,
            parsed = response.block,
            transactions = emptyList(),
            upstreamId = upstreamId,
            parentHash = BlockId.from(response.block.header.parentHash),
        )
    }

    override fun latestBlockRequest(): JsonRpcRequest =
        JsonRpcRequest("chain_getBlock", listOf())

    override fun localReaderBuilder(
        cachingReader: CachingReader,
        methods: CallMethods,
        head: Head,
    ): Mono<JsonRpcReader> {
        return Mono.just(LocalReader(methods))
    }

    override fun subscriptionBuilder(headScheduler: Scheduler): (Multistream) -> EgressSubscription {
        return { _ -> EmptyEgressSubscription }
    }

    override fun makeCachingReaderBuilder(tracer: Tracer): CachingReaderBuilder {
        return { _, _, _ -> NoopCachingReader }
    }

    override fun validator(
        chain: Chain,
        upstream: Upstream,
        options: Options,
        config: ChainConfig,
    ): UpstreamValidator? {
        return null
    }

    override fun labelDetector(chain: Chain, reader: JsonRpcReader): LabelsDetector? {
        return null
    }

    override fun subscriptionTopics(upstream: GenericUpstream): List<String> {
        return emptyList()
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PolkadotBlockResponse(
    @JsonProperty("block") var block: PolkadotBlock,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PolkadotBlock(
    @JsonProperty("header") var header: PolkadotHeader,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PolkadotHeader(
    @JsonProperty("parentHash") var parentHash: String,
    @JsonProperty("number") var number: String,
    @JsonProperty("stateRoot") var stateRoot: String,
    @JsonProperty("extrinsicsRoot") var extrinsicsRoot: String,
    @JsonProperty("digest") var digest: PolkadotDigest,
)

data class PolkadotDigest(
    @JsonProperty("logs") var logs: List<String>,
)
