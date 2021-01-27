@file:OptIn(ExperimentalSerializationApi::class)

package io.github.ittwit.nbkt

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import java.io.DataOutput
import java.util.function.Consumer
import kotlin.properties.Delegates

internal open class NbtEncoder(
    val output: DataOutput,
    final override val serializersModule: SerializersModule = EmptySerializersModule
) : AbstractEncoder() {
    protected open val writeTypeIds: Boolean = true
    protected open val writeElementNames: Boolean = false

    private var elementName: String by Delegates.notNull()
    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        this.elementName = descriptor.getElementName(index)
        return super.encodeElement(descriptor, index)
    }

    override fun encodeByte(value: Byte) {
        output.tryWriteTypeId(+NbtType.BYTE)
        output.tryWriteElementName()
        output.writeNumber(value)
    }
    override fun encodeShort(value: Short) {
        output.tryWriteTypeId(+NbtType.SHORT)
        output.tryWriteElementName()
        output.writeNumber(value)
    }
    override fun encodeInt(value: Int) {
        output.tryWriteTypeId(+NbtType.INT)
        output.tryWriteElementName()
        output.writeNumber(value)
    }
    override fun encodeLong(value: Long) {
        output.tryWriteTypeId(+NbtType.LONG)
        output.tryWriteElementName()
        output.writeNumber(value)
    }
    override fun encodeFloat(value: Float) {
        output.tryWriteTypeId(+NbtType.FLOAT)
        output.tryWriteElementName()
        output.writeNumber(value)
    }
    override fun encodeDouble(value: Double) {
        output.tryWriteTypeId(+NbtType.DOUBLE)
        output.tryWriteElementName()
        output.writeNumber(value)
    }

    override fun encodeString(value: String) {
        output.tryWriteTypeId(+NbtType.STRING)
        output.tryWriteElementName()
        output.writeUTF(value)
    }


    private val byteArraySerializer = serializer<ByteArray>()
    private val intArraySerializer = serializer<IntArray>()
    private val longArraySerializer = serializer<LongArray>()
    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {
        when (serializer) {
            byteArraySerializer -> encodeByteArray(value as ByteArray)
            intArraySerializer -> encodeIntArray(value as IntArray)
            longArraySerializer -> encodeLongArray(value as LongArray)

            else -> super.encodeSerializableValue(serializer, value)
        }
    }

    open fun encodeByteArray(value: ByteArray) {
        output.tryWriteTypeId(+NbtType.BYTE_ARRAY)
        output.tryWriteElementName()
        output.writeInt(value.size)
        output.write(value)
    }
    open fun encodeIntArray(value: IntArray) {
        output.tryWriteTypeId(+NbtType.INT_ARRAY)
        output.tryWriteElementName()
        output.writeInt(value.size)

        value.forEach { output.writeInt(it) }
    }
    open fun encodeLongArray(value: LongArray) {
        output.tryWriteTypeId(+NbtType.LONG_ARRAY)
        output.tryWriteElementName()
        output.writeInt(value.size)

        value.forEach { output.writeLong(it) }
    }

    protected fun DataOutput.tryWriteTypeId(id: Int) {
        if (writeTypeIds) {
            this.writeByte(id)
        }
    }
    protected fun DataOutput.tryWriteElementName(name: String = elementName) {
        if (writeElementNames)  {
            this.writeUTF(name)
        }
    }
    protected fun DataOutput.writeNumber(value: Number) {
        when (value) {
            is Byte -> this.writeByte(value.toInt())
            is Short -> this.writeShort(value.toInt())
            is Int -> this.writeInt(value)
            is Long -> this.writeLong(value)
            is Float -> this.writeFloat(value)
            is Double -> this.writeDouble(value)
        }
    }

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
        output.tryWriteTypeId(+NbtType.LIST)
        return NbtListEncoder(output, descriptor, collectionSize, serializersModule = this.serializersModule)
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        return startStructure(descriptor, "")
    }
    fun startStructure(descriptor: SerialDescriptor, rootName: String? = null, writeTypeIds: Boolean? = null): CompositeEncoder {
        return when (val type = NbtType.fromDescriptor(descriptor)) {
            NbtType.COMPOUND -> {
                if (rootName == null) {
                    output.tryWriteTypeId(+NbtType.COMPOUND)
                    output.tryWriteElementName()
                }

                NbtCompoundEncoder(output, rootName, writeTypeIds, serializersModule = this.serializersModule)
            }
            NbtType.BYTE_ARRAY,
            NbtType.INT_ARRAY,
            NbtType.LONG_ARRAY,
            NbtType.LIST -> NbtListEncoder(output, descriptor, serializersModule = this.serializersModule)
            else -> error("Unable to begin a structure with type $type")
        }
    }
}

private class NbtCompoundEncoder(
    output: DataOutput,
    rootName: String?,
    writeTypeIds: Boolean?,
    serializersModule: SerializersModule = EmptySerializersModule
) : NbtEncoder(output, serializersModule) {
    override val writeTypeIds = writeTypeIds ?: true
    override val writeElementNames = true

    init {
        if (rootName != null) {
            output.tryWriteTypeId(+NbtType.COMPOUND)
            output.tryWriteElementName(rootName)
        }
    }

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
        output.tryWriteTypeId(+NbtType.LIST)
        output.tryWriteElementName()
        return NbtListEncoder(output, descriptor, collectionSize, serializersModule = this.serializersModule)
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        return startStructure(descriptor)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        output.writeByte(+NbtType.END)
    }
}

private class NbtListEncoder(
    output: DataOutput,
    descriptor: SerialDescriptor,
    private var size: Int? = null,
    serializersModule: SerializersModule = EmptySerializersModule
) : NbtEncoder(output, serializersModule) {
    private val elementType: NbtType = NbtType.fromDescriptor(descriptor.getElementDescriptor(0))

    val bufferList = mutableListOf<Any>()

    override val writeTypeIds = false

    init {
        output.writeByte(+elementType)

        size?.let { output.writeInt(it) }
    }

    fun <T : Any> tryBufferOrWrite(value: T, consumer: Consumer<T>) {
        if (size == null) {
            bufferList.add(value)
        } else {
            consumer.accept(value)
        }
    }

    override fun encodeByte(value: Byte) {
        tryBufferOrWrite(value) { super.encodeByte(value) }
    }
    override fun encodeShort(value: Short) {
        tryBufferOrWrite(value) { super.encodeShort(value) }
    }
    override fun encodeInt(value: Int) {
        tryBufferOrWrite(value) { super.encodeInt(value) }
    }
    override fun encodeLong(value: Long) {
        tryBufferOrWrite(value) { super.encodeLong(value) }
    }
    override fun encodeFloat(value: Float) {
        tryBufferOrWrite(value) { super.encodeFloat(value) }
    }
    override fun encodeDouble(value: Double) {
        tryBufferOrWrite(value) { super.encodeDouble(value) }
    }
    override fun encodeString(value: String) {
        tryBufferOrWrite(value) { super.encodeString(value) }
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        return startStructure(descriptor)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        if (size == null) {
            output.writeInt(bufferList.size)
            bufferList.forEach { super.encodeSerializableValue(serializer(), it) }
        }
    }
}