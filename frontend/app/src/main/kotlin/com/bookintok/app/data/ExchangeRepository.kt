package com.bookintok.app.data

import com.bookintok.app.core.database.ExchangeDao
import com.bookintok.app.core.network.BookintokApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface ExchangeRepository {
    /** Cached exchanges, most recently updated first. Works offline. */
    fun observeAll(): Flow<List<Exchange>>

    fun observe(id: Long): Flow<Exchange?>

    /** Downloads the user's exchanges into the cache. Throws if the API cannot be reached. */
    suspend fun refreshAll()

    suspend fun refresh(id: Long)

    suspend fun request(bookId: Long): Exchange

    suspend fun confirm(id: Long): Exchange

    suspend fun cancel(id: Long): Exchange
}

@Singleton
class DefaultExchangeRepository @Inject constructor(
    private val api: BookintokApi,
    private val dao: ExchangeDao,
) : ExchangeRepository {

    override fun observeAll() = dao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observe(id: Long) = dao.observe(id).map { it?.toModel() }

    override suspend fun refreshAll() {
        dao.upsert(api.myExchanges().content.map { it.toEntity() })
    }

    override suspend fun refresh(id: Long) {
        dao.upsert(listOf(api.exchange(id).toEntity()))
    }

    override suspend fun request(bookId: Long) = store(api.requestExchange(bookId).toEntity())

    override suspend fun confirm(id: Long) = store(api.confirmExchange(id).toEntity())

    override suspend fun cancel(id: Long) = store(api.cancelExchange(id).toEntity())

    private suspend fun store(entity: com.bookintok.app.core.database.ExchangeEntity): Exchange {
        dao.upsert(listOf(entity))
        return entity.toModel()
    }
}
