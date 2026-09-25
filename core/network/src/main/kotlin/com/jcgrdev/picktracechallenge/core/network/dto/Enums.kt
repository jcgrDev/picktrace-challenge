package com.jcgrdev.picktracechallenge.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
enum class OpType { CREATE, UPDATE, DELETE }

@Serializable
enum class EntityType { FIELD_EVENT }
