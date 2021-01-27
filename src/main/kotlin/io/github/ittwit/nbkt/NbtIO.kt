package io.github.ittwit.nbkt

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import java.io.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@OptIn(ExperimentalSerializationApi::class)
object NbtIO {
    fun <T, S : KSerializer<T>> writeNbt(serializer: S, value: T, file: File, compressed: Boolean = true, serializersModule: SerializersModule = EmptySerializersModule) {
        writeNbt(serializer, value, file.outputStream().buffered(), compressed, serializersModule)
    }

    fun <T, S : KSerializer<T>> writeNbt(serializer: S, value: T, stream: OutputStream, compressed: Boolean = true, serializersModule: SerializersModule = EmptySerializersModule) {
        val output = if (compressed) GZIPOutputStream(stream) else stream
        DataOutputStream(output).use { writeNbtData(serializer, value, it, serializersModule) }
    }

    fun <T, S : KSerializer<T>> writeNbtData(serializer: S, value: T, output: DataOutput, serializersModule: SerializersModule = EmptySerializersModule) {
        val encoder = NbtEncoder(output, serializersModule = serializersModule)

        serializer.serialize(encoder, value)
    }


    fun <T, S : KSerializer<T>> readNbt(serializer: S, file: File, compressed: Boolean = true, serializersModule: SerializersModule = EmptySerializersModule): T {
        return readNbt(serializer, file.inputStream().buffered(), compressed, serializersModule)
    }
    fun <T, S : KSerializer<T>> readNbt(serializer: S, stream: InputStream, compressed: Boolean = true, serializersModule: SerializersModule = EmptySerializersModule): T {
        val input = if (compressed) GZIPInputStream(stream) else stream
        return DataInputStream(input).use { readNbtData(serializer, it, serializersModule) }
    }

    fun <T, S : KSerializer<T>> readNbtData(serializer: S, input: DataInput, serializersModule: SerializersModule = EmptySerializersModule): T {
        return serializer.deserialize(NbtDecoder(input, serializersModule = serializersModule))
    }
}
