package io.github.ittwit.nbkt

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.*
import kotlin.reflect.KClass

@OptIn(ExperimentalSerializationApi::class)
enum class NbtType(
    val id: Int,
    val clazz: KClass<out Any>? = null,
    val serialKinds: List<SerialKind>
) {
    END(0, Nothing::class, emptyList()),
    BYTE(1, Byte::class, listOf(PrimitiveKind.BYTE)),
    SHORT(2, Short::class, listOf(PrimitiveKind.SHORT)),
    INT(3, Int::class, listOf(PrimitiveKind.INT)),
    LONG(4, Long::class, listOf(PrimitiveKind.LONG)),
    FLOAT(5, Float::class, listOf(PrimitiveKind.FLOAT)),
    DOUBLE(6, Double::class, listOf(PrimitiveKind.DOUBLE)),
    BYTE_ARRAY(7, ByteArray::class, listOf(StructureKind.LIST)),
    STRING(8, String::class, listOf(PrimitiveKind.STRING)),
    LIST(9, serialKinds = listOf(StructureKind.LIST)),
    COMPOUND(10, serialKinds = listOf(StructureKind.CLASS, StructureKind.MAP)),
    INT_ARRAY(11, IntArray::class, listOf(StructureKind.LIST)),
    LONG_ARRAY(12, ByteArray::class, listOf(StructureKind.LIST));

    companion object {
        private val ID_MAP = NbtType.values().associateBy { it.id }
        fun fromId(id: Int): NbtType = fromIdOrNull(id) ?: error("No type with id $id was found")
        fun fromIdOrNull(id: Int) : NbtType? = ID_MAP[id]

        fun fromDescriptor(descriptor: SerialDescriptor): NbtType {
            return fromDescriptorOrNull(descriptor) ?: error("No type was found from the descriptor $descriptor")
        }
        fun fromDescriptorOrNull(descriptor: SerialDescriptor): NbtType? {
            return when (descriptor.kind) {
                PrimitiveKind.BYTE -> BYTE
                PrimitiveKind.SHORT -> SHORT
                PrimitiveKind.INT -> INT
                PrimitiveKind.LONG -> LONG
                PrimitiveKind.FLOAT -> FLOAT
                PrimitiveKind.DOUBLE -> DOUBLE

                PrimitiveKind.STRING -> STRING

                StructureKind.CLASS, StructureKind.MAP, PolymorphicKind.OPEN, PolymorphicKind.SEALED -> COMPOUND

                StructureKind.LIST -> listTypeFromDescriptor(descriptor)

                else -> null
            }
        }

        private fun listTypeFromDescriptor(descriptor: SerialDescriptor): NbtType? {
            val elementType = descriptor.getElementDescriptor(0)

            return when (elementType.kind) {
                PrimitiveKind.BYTE -> BYTE_ARRAY

                PrimitiveKind.INT -> INT_ARRAY
                PrimitiveKind.LONG -> LONG_ARRAY

                else -> LIST
            }
        }
    }

    operator fun unaryPlus() = id

    fun isValidSerialKind(kind: SerialKind): Boolean = kind in this.serialKinds
}