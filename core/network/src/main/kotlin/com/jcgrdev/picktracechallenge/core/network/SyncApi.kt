package com.jcgrdev.picktracechallenge.core.network

import com.jcgrdev.picktracechallenge.core.network.dto.PullResponse
import com.jcgrdev.picktracechallenge.core.network.dto.PushRequest
import com.jcgrdev.picktracechallenge.core.network.dto.PushResponse
import com.jcgrdev.picktracechallenge.core.network.dto.SnapshotResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * The sync contract (CLAUDE.md "Backend contract"). Methods return [Response] so the client can
 * read the status code and `Retry-After` (contracts/sync-api.md).
 */
interface SyncApi {
    @POST("sync/push")
    suspend fun push(@Body body: PushRequest): Response<PushResponse>

    @GET("sync/pull")
    suspend fun pull(@Query("cursor") cursor: Long, @Query("limit") limit: Int): Response<PullResponse>

    @GET("sync/snapshot")
    suspend fun snapshot(@Query("page") page: Int): Response<SnapshotResponse>
}
