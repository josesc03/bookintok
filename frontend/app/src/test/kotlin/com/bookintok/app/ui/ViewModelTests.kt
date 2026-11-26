package com.bookintok.app.ui

import com.bookintok.app.MainDispatcherRule
import com.bookintok.app.core.auth.AuthRepository
import com.bookintok.app.core.auth.AuthUser
import com.bookintok.app.core.network.ApiException
import com.bookintok.app.core.network.BookCondition
import com.bookintok.app.core.network.BookDto
import com.bookintok.app.core.network.BookOwnerDto
import com.bookintok.app.core.network.BookRequest
import com.bookintok.app.core.network.BookSearch
import com.bookintok.app.core.network.BookStatus
import com.bookintok.app.core.network.CoverType
import com.bookintok.app.core.network.MyProfileDto
import com.bookintok.app.core.network.PageDto
import com.bookintok.app.core.network.PublicProfileDto
import com.bookintok.app.core.network.RatingDto
import com.bookintok.app.data.BookRepository
import com.bookintok.app.data.UserRepository
import com.bookintok.app.ui.books.BookListViewModel
import com.bookintok.app.ui.session.SessionState
import com.bookintok.app.ui.session.SessionViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class BookListViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun book(id: Long) = BookDto(
        id = id, title = "Book $id", author = "Author", mainCategory = "Novel", language = "es",
        coverType = CoverType.PAPERBACK, condition = BookCondition.USED, status = BookStatus.AVAILABLE,
        owner = BookOwnerDto("owner", "ana"), createdAt = "2026-05-01T10:00:00Z",
    )

    private class FakeBooks(val pages: Int = 2, var failing: Boolean = false) : BookRepository {
        val searches = mutableListOf<BookSearch>()

        override suspend fun search(search: BookSearch, page: Int): PageDto<BookDto> {
            searches += search
            if (failing) throw IOException("offline")
            return PageDto(listOf(bookFor(page)), page, 1, pages.toLong(), pages)
        }

        private fun bookFor(page: Int) = BookDto(
            id = page.toLong(), title = "Book $page", author = "A", mainCategory = "N", language = "es",
            coverType = CoverType.PAPERBACK, condition = BookCondition.USED, status = BookStatus.AVAILABLE,
            owner = BookOwnerDto("o", "ana"), createdAt = "2026-05-01T10:00:00Z",
        )

        override suspend fun book(id: Long) = error("unused")
        override suspend fun booksOf(uid: String) = error("unused")
        override suspend fun save(id: Long?, request: BookRequest) = error("unused")
        override suspend fun delete(id: Long) = error("unused")
        override suspend fun uploadImage(id: Long, jpeg: ByteArray) = error("unused")
        override suspend fun deleteImage(id: Long) = error("unused")
    }

    @Test
    fun `loads the first page and appends the next one`() {
        val viewModel = BookListViewModel(FakeBooks())
        assertEquals(1, viewModel.state.value.books.size)

        viewModel.loadMore()

        val state = viewModel.state.value
        assertEquals(listOf(0L, 1L), state.books.map { it.id })
        assertFalse(state.hasMore)
    }

    @Test
    fun `selecting the same distance twice removes the filter`() {
        val books = FakeBooks()
        val viewModel = BookListViewModel(books)

        viewModel.onDistance(20)
        assertEquals(20, books.searches.last().maxDistanceKm)

        viewModel.onDistance(20)
        assertNull(books.searches.last().maxDistanceKm)
    }

    @Test
    fun `network errors are shown as a message`() {
        val viewModel = BookListViewModel(FakeBooks(failing = true))
        assertEquals("Sin conexión. Comprueba tu red e inténtalo de nuevo", viewModel.state.value.error)
    }
}

class SessionViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeAuth(uid: String?, verified: Boolean = true) : AuthRepository {
        override val currentUser = MutableStateFlow(uid?.let { AuthUser(it, "$it@example.com", verified) })
        override fun currentUserNow() = currentUser.value
        override suspend fun signIn(email: String, password: String) = Unit
        override suspend fun signUp(email: String, password: String) = Unit
        override suspend fun signInWithGoogle(googleIdToken: String) = Unit
        override suspend fun sendVerificationEmail() = Unit
        override suspend fun reloadUser() = currentUser.value
        override fun signOut() {
            currentUser.value = null
        }

        /** Simulates the user clicking the link in the verification email (Firebase does not notify it). */
        fun verifyEmailSilently() {
            currentUser.value?.let { currentUser.value = it.copy(emailVerified = true) }
        }
    }

    private class FakeUsers(var profile: () -> MyProfileDto?) : UserRepository {
        override suspend fun myProfile() = profile()
        override suspend fun register(nickname: String, displayName: String?) = error("unused")
        override suspend fun updateLocation(latitude: Double, longitude: Double, label: String?) = Unit
        override suspend fun publicProfile(uid: String): PublicProfileDto = error("unused")
        override suspend fun ratings(uid: String): List<RatingDto> = emptyList()
        override suspend fun rate(uid: String, score: Int, comment: String?) = Unit
        override suspend fun signOut() = Unit
    }

    private val profile = MyProfileDto(uid = "u1", nickname = "ana", email = "ana@example.com", latitude = 39.47, longitude = -0.38)

    private suspend fun SessionViewModel.settled() = state.first { it != SessionState.Loading }

    @Test
    fun `signed out users see the login`() = runTest {
        assertEquals(SessionState.SignedOut, SessionViewModel(FakeAuth(null), FakeUsers { null }).settled())
    }

    @Test
    fun `users without a profile are asked to create one, then enter`() = runTest {
        val users = FakeUsers { null }
        val viewModel = SessionViewModel(FakeAuth("u1"), users)
        assertEquals(SessionState.NeedsProfile, viewModel.settled())

        users.profile = { profile }
        viewModel.refresh()
        assertEquals(SessionState.Ready("u1"), viewModel.settled())
    }

    @Test
    fun `unverified email blocks the app until the user verifies it`() = runTest {
        val auth = FakeAuth("u1", verified = false)
        val viewModel = SessionViewModel(auth, FakeUsers { profile })
        assertEquals(SessionState.NeedsEmailVerification("u1@example.com"), viewModel.settled())

        auth.verifyEmailSilently()
        viewModel.refresh()
        assertEquals(SessionState.Ready("u1"), viewModel.settled())
    }

    @Test
    fun `users without a location are asked for it before entering`() = runTest {
        val users = FakeUsers { profile.copy(latitude = null, longitude = null) }
        val viewModel = SessionViewModel(FakeAuth("u1"), users)
        assertEquals(SessionState.NeedsLocation, viewModel.settled())

        users.profile = { profile }
        viewModel.refresh()
        assertEquals(SessionState.Ready("u1"), viewModel.settled())
    }

    @Test
    fun `when the server is unreachable the app opens in offline mode`() = runTest {
        val viewModel = SessionViewModel(FakeAuth("u1"), FakeUsers { throw IOException("offline") })
        assertEquals(SessionState.Ready("u1", offline = true), viewModel.settled())
    }

    @Test
    fun `api errors other than not found also fall back to offline mode`() = runTest {
        val viewModel = SessionViewModel(FakeAuth("u1"), FakeUsers { throw ApiException(500, null) })
        assertEquals(SessionState.Ready("u1", offline = true), viewModel.settled())
    }
}
