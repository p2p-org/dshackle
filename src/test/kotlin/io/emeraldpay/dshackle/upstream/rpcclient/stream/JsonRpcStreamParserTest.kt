package io.emeraldpay.dshackle.upstream.rpcclient.stream

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration

class JsonRpcStreamParserTest {
    private val streamParser = JsonRpcStreamParser()

    @Test
    fun `if first part couldn't be parsed then aggregate response`() {
        val statusCode = 200
        val bytes = "{\"strangeResponse\": 2}".toByteArray()
        val stream: Flux<ByteArray> = Flux.just(bytes)

        StepVerifier.create(streamParser.streamParse(statusCode, stream))
            .expectNext(AggregateResponse(bytes, statusCode))
            .expectComplete()
            .verify(Duration.ofSeconds(1))
    }

    @ParameterizedTest
    @MethodSource("data")
    fun `if first part has result field then single response`(
        response: ByteArray,
        result: ByteArray,
    ) {
        val statusCode = 200
        val stream: Flux<ByteArray> = Flux.just(response)

        StepVerifier.create(streamParser.streamParse(statusCode, stream))
            .expectNext(SingleResponse(result, null))
            .expectComplete()
            .verify(Duration.ofSeconds(1))
    }

    @ParameterizedTest
    @MethodSource("dataStream")
    fun `if big result then stream response`(
        response: List<ByteArray>,
        chunks: List<Chunk>,
    ) {
        val statusCode = 200
        val stream: Flux<ByteArray> = Flux.fromIterable(response)

        val result = streamParser.streamParse(statusCode, stream).block()
        assertTrue(result is StreamResponse)
        assertNotNull(result)

        StepVerifier.create((result as StreamResponse).stream)
            .expectNextSequence(chunks)
            .expectComplete()
            .verify(Duration.ofSeconds(5))
    }

    companion object {
        @JvmStatic
        fun data(): List<Arguments> = listOf(
            Arguments.of("{\"id\": 2,\"result\": \"0x12\"}".toByteArray(), "\"0x12\"".toByteArray()),
            Arguments.of("{\"id\": 2,\"result\": null}".toByteArray(), "null".toByteArray()),
            Arguments.of("{\"id\": 2,\"result\": {\"name\": \"value\"}".toByteArray(), "{\"name\": \"value\"}".toByteArray()),
            Arguments.of("{\"id\": 2,\"result\": [{\"name\": \"value\"}]".toByteArray(), "[{\"name\": \"value\"}]".toByteArray()),
        )

        @JvmStatic
        fun dataStream(): List<Arguments> = listOf(
            Arguments.of(
                listOf("{\"id\": 2,\"result\": \"0x12".toByteArray(), "222\"}".toByteArray()),
                listOf(
                    Chunk("\"0x12".toByteArray(), false),
                    Chunk("222\"".toByteArray(), true),
                ),
            ),
            Arguments.of(
                listOf("{\"id\": 2,\"result\": {\"name\": ".toByteArray(), "\"bigName\"".toByteArray(), "}".toByteArray()),
                listOf(
                    Chunk("{\"name\": ".toByteArray(), false),
                    Chunk("\"bigName\"".toByteArray(), false),
                    Chunk("}".toByteArray(), true),
                ),
            ),
            Arguments.of(
                listOf("{\"id\": 2,\"result\": [{\"name\": ".toByteArray(), "\"bigName\"".toByteArray(), "}]".toByteArray()),
                listOf(
                    Chunk("[{\"name\": ".toByteArray(), false),
                    Chunk("\"bigName\"".toByteArray(), false),
                    Chunk("}]".toByteArray(), true),
                ),
            ),
        )
    }
}
