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

    /** Opens a chat for the book, or returns the one the user already had (open or cancelled). */
    suspend fun request(bookId: Long): RequestedExchange

    /** Asks the other participant to resume a cancelled exchange. */
    suspend fun requestReopen(id: Long): Exchange

    suspend fun acceptReopen(id: Long): Exchange

    /** Rejects the other participant's request, or withdraws one's own. */
    suspend fun clearReopenRequest(id: Long): Exchange

    suspend fun confirm(id: Long): Exchange

    suspend fun cancel(id: Long): Exchange

    /** Archives or restores the chat for the signed-in user only. */
    suspend fun setArchived(id: Long, archived: Boolean): Exchange
}

data class RequestedExchange(val exchange: Exchange, val created: Boolean)

@Singleton
class DefaultExchangeRepository @Inject constructor(
    private val api: BookintokApi,
    private val dao: ExchangeDao,
) : ExchangeRepository {

    override fun observeAll() = dao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observe(id: Long) = dao.observe(id).map { it?.toModel() }

    override suspend fun refreshAll() {
        val exchanges = api.myExchanges().content.map { it.toEntity() }
        dao.upsert(exchanges)
        dao.deleteMissing(exchanges.map { it.id }.ifEmpty { listOf(-1L) })
    }

    override suspend fun refresh(id: Long) {
        dao.upsert(listOf(api.exchange(id).toEntity()))
    }

    override suspend fun request(bookId: Long): RequestedExchange {
        val result = api.requestExchange(bookId)
        return RequestedExchange(store(result.exchange.toEntity()), result.created)
    }

    override suspend fun requestReopen(id: Long) = store(api.requestReopen(id).toEntity())

    override suspend fun acceptReopen(id: Long) = store(api.acceptReopen(id).toEntity())

    override suspend fun clearReopenRequest(id: Long) = store(api.clearReopenRequest(id).toEntity())

    override suspend fun confirm(id: Long) = store(api.confirmExchange(id).toEntity())

    override suspend fun cancel(id: Long) = store(api.cancelExchange(id).toEntity())

    override suspend fun setArchived(id: Long, archived: Boolean) = store(api.setArchived(id, archived).toEntity())

    private suspend fun store(entity: com.bookintok.app.core.database.ExchangeEntity): Exchange {
        dao.upsert(listOf(entity))
        return entity.toModel()
    }
}
