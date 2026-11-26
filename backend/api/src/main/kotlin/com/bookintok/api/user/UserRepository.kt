package com.bookintok.api.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, String> {
    fun existsByNicknameIgnoreCase(nickname: String): Boolean

    fun existsByEmailIgnoreCase(email: String): Boolean
}
