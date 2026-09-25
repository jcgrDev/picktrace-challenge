package com.jcgrdev.picktracechallenge.core.sync

import com.jcgrdev.picktracechallenge.core.model.FieldEvent
import com.jcgrdev.picktracechallenge.core.network.dto.EntityType
import com.jcgrdev.picktracechallenge.core.network.dto.FieldEventFields
import com.jcgrdev.picktracechallenge.core.network.dto.OpType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject

const val FIELD_EVENT_SCHEMA_VERSION = 1
val FIELD_EVENT_ENTITY_TYPE: String = EntityType.FIELD_EVENT.name
val OP_TYPE_CREATE: String = OpType.CREATE.name

/** Typed codec for FIELD_EVENT payloads; the wire carries them as a `JsonObject` in `fields`. */
class FieldEventCodec @Inject constructor(private val json: Json) {

    fun encode(event: FieldEvent): JsonObject = json.encodeToJsonElement(event.toFields()).jsonObject

    /** The `pending_op.fields_json` form: exactly what will be pushed. */
    fun encodeToString(event: FieldEvent): String = json.encodeToString(FieldEventFields.serializer(), event.toFields())

    fun decode(fields: JsonObject): FieldEventFields = json.decodeFromJsonElement(FieldEventFields.serializer(), fields)

    private fun FieldEvent.toFields() = FieldEventFields(
        workerId = workerId.value,
        blockId = blockId.value,
        quantity = quantity,
        timestamp = timestamp.toString(),
    )
}
