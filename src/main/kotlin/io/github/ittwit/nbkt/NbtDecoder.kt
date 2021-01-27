@file:OptIn(ExperimentalSerializationApi::class)

package io.github.ittwit.nbkt

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import java.io.DataInput
import kotlin.properties.Delegates

internal open class NbtDecoder(
    val input: DataInput,
    final override val serializersModule: SerializersModule = EmptySerializersModule
) : AbstractDecoder() {
    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        return startStructure(descriptor, true)
    }
    fun startStructure(descriptor: SerialDescriptor, rootTag: Boolean = false): CompositeDecoder {
        val type = input.readNbtType()
        return this.startStructureType(type, descriptor, rootTag)
    }
    fun startStructureType(type: NbtType, descriptor: SerialDescriptor, rootTag: Boolean = false): CompositeDecoder {
        return when (type) {
            NbtType.COMPOUND -> NbtCompoundDecoder(input, rootTag, serializersModule = this.serializersModule)
            NbtType.BYTE_ARRAY,
            NbtType.INT_ARRAY,
            NbtType.LONG_ARRAY,
            NbtType.LIST -> NbtListDecoder(input, type, descriptor, serializersModule = this.serializersModule)
            else -> error("Unable to begin a structure with type $type")
        }
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        error("decodeElementIndex called on base NbtDecoder")
    }

    fun DataInput.readNbtType(): NbtType {
        val id = this.readUnsignedByte()

        return NbtType.fromId(id)
    }
    fun DataInput.readNullableNbtType(): NbtType? {
        val id = this.readUnsignedByte()

        return NbtType.fromIdOrNull(id)
    }
}

private class NbtCompoundDecoder(
    input: DataInput,
    rootTag: Boolean = false,
    serializersModule: SerializersModule = EmptySerializersModule
) : NbtDecoder(input, serializersModule) {
    init {
        if (rootTag) println(input.readUTF())
    }

    override fun decodeByte() = input.readByte()
    override fun decodeShort() = input.readShort()
    override fun decodeInt() = input.readInt()
    override fun decodeLong() = input.readLong()
    override fun decodeFloat() = input.readFloat()
    override fun decodeDouble() = input.readDouble()
    override fun decodeString(): String = input.readUTF()

    private var readType: NbtType by Delegates.notNull()
    private var decodeCount = 0
    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (decodeCount >= descriptor.elementsCount) return CompositeDecoder.DECODE_DONE

        val type = input.readNbtType()
        if (type == NbtType.END) error("Read NbtType whilst attempting to get element index was END. Possible error caused elsewhere?")
        this.readType = type

        val name = input.readUTF()

        val idx = descriptor.getElementIndex(name)
        assert(type.isValidSerialKind(descriptor.getElementDescriptor(idx).kind))

        decodeCount++
        return idx
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        return startStructureType(readType, descriptor, false)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        assert(input.readByte().toInt() == 0)
    }
}

private class NbtListDecoder(
    input: DataInput,
    type: NbtType,
    descriptor: SerialDescriptor,
    serializersModule: SerializersModule = EmptySerializersModule
) : NbtDecoder(input, serializersModule) {
    private val size: Int
    private val elementType: NbtType
    init {
        if (type == NbtType.LIST) {
            this.elementType = input.readNbtType()
        } else {
            val elementKind = descriptor.getElementDescriptor(0).kind

            if (type == NbtType.BYTE_ARRAY || elementKind == PrimitiveKind.BYTE) {
                this.elementType = NbtType.BYTE
            } else if (type == NbtType.INT_ARRAY || elementKind == PrimitiveKind.INT) {
                this.elementType = NbtType.INT
            } else if (type == NbtType.LONG_ARRAY || elementKind == PrimitiveKind.LONG) {
                this.elementType = NbtType.LONG
            } else {
                error("List does not have a recognised type: $type")
            }
        }

        this.size = input.readInt()
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int {
        return this.size
    }

    override fun decodeByte(): Byte {
        if (this.elementType == NbtType.BYTE) {
            return input.readByte()
        }

        return super.decodeByte()
    }

    override fun decodeInt(): Int {
        if (this.elementType == NbtType.INT) {
            return input.readInt()
        }

        return super.decodeInt()
    }

    override fun decodeLong(): Long {
        if (this.elementType == NbtType.LONG) {
            return input.readLong()
        }

        return super.decodeLong()
    }

    override fun decodeSequentially() = true

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        return startStructureType(elementType, descriptor)
    }
}