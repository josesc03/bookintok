package com.bookintok.api.safety

import com.bookintok.api.common.BusinessRuleException
import com.bookintok.api.common.ForbiddenException
import com.bookintok.api.common.uid
import com.bookintok.api.exchange.ExchangeRepository
import com.bookintok.api.user.UserService
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant

enum class ReportReason { SPAM, SCAM, HARASSMENT, INAPPROPRIATE, OTHER }

data class ReportRequest(
    val reason: ReportReason,
    @field:Size(max = 1000)
    val comment: String? = null,
    /** The chat the report is about, if any. */
    val exchangeId: Long? = null,
)

data class BlockedUserResponse(val uid: String, val nickname: String, val blockedAt: Instant)

@Repository
class BlockRepository(private val jdbc: JdbcClient) {

    fun block(blocker: String, blocked: String, at: Instant) {
        jdbc.sql(
            """
            INSERT INTO user_blocks (blocker_uid, blocked_uid, created_at) VALUES (:blocker, :blocked, :at)
            ON CONFLICT DO NOTHING
            """,
        ).param("blocker", blocker).param("blocked", blocked).param("at", java.sql.Timestamp.from(at)).update()
    }

    fun unblock(blocker: String, blocked: String) {
        jdbc.sql("DELETE FROM user_blocks WHERE blocker_uid = :blocker AND blocked_uid = :blocked")
            .param("blocker", blocker).param("blocked", blocked).update()
    }

    /** Whether either user blocked the other. */
    fun blockedBetween(a: String, b: String): Boolean = jdbc.sql(
        """
        SELECT count(*) FROM user_blocks
        WHERE (blocker_uid = :a AND blocked_uid = :b) OR (blocker_uid = :b AND blocked_uid = :a)
        """,
    ).param("a", a).param("b", b).query(Long::class.java).single() > 0

    /** Users whose books [uid] must not see: the ones they blocked and the ones who blocked them. */
    fun hiddenFrom(uid: String): Set<String> = jdbc.sql(
        """
        SELECT blocked_uid FROM user_blocks WHERE blocker_uid = :uid
        UNION SELECT blocker_uid FROM user_blocks WHERE blocked_uid = :uid
        """,
    ).param("uid", uid).query(String::class.java).list().filterNotNull().toSet()

    fun blockedBy(uid: String): List<BlockedUserResponse> = jdbc.sql(
        """
        SELECT u.uid, u.nickname, b.created_at FROM user_blocks b JOIN users u ON u.uid = b.blocked_uid
        WHERE b.blocker_uid = :uid ORDER BY b.created_at DESC
        """,
    ).param("uid", uid).query { rs, _ ->
        BlockedUserResponse(rs.getString("uid"), rs.getString("nickname"), rs.getTimestamp("created_at").toInstant())
    }.list()
}

@Repository
class ReportRepository(private val jdbc: JdbcClient) {
    fun save(reporter: String, reported: String, request: ReportRequest, at: Instant) {
        jdbc.sql(
            """
            INSERT INTO user_reports (reporter_uid, reported_uid, exchange_id, reason, comment, created_at)
            VALUES (:reporter, :reported, :exchange, :reason, :comment, :at)
            """,
        )
            .param("reporter", reporter)
            .param("reported", reported)
            .param("exchange", request.exchangeId)
            .param("reason", request.reason.name)
            .param("comment", request.comment?.trim()?.ifEmpty { null })
            .param("at", java.sql.Timestamp.from(at))
            .update()
    }
}

@Service
@Transactional(readOnly = true)
class SafetyService(
    private val blocks: BlockRepository,
    private val reports: ReportRepository,
    private val exchanges: ExchangeRepository,
    private val users: UserService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Blocking also cancels the open exchanges between both users. */
    @Transactional
    fun block(blocker: String, blocked: String) {
        requireOther(blocker, blocked)
        users.getUser(blocked)
        val now = Instant.now(clock)
        blocks.block(blocker, blocked, now)
        exchanges.findPendingBetween(blocker, blocked).forEach { it.discard(now) }
    }

    @Transactional
    fun unblock(blocker: String, blocked: String) = blocks.unblock(blocker, blocked)

    fun blockedBy(uid: String) = blocks.blockedBy(uid)

    fun hiddenFrom(uid: String) = blocks.hiddenFrom(uid)

    fun requireNotBlocked(a: String, b: String) {
        if (blocks.blockedBetween(a, b)) throw ForbiddenException("You cannot interact with this user")
    }

    @Transactional
    fun report(reporter: String, reported: String, request: ReportRequest) {
        requireOther(reporter, reported)
        users.getUser(reported)
        reports.save(reporter, reported, request, Instant.now(clock))
        log.warn("User {} reported {} for {}", reporter, reported, request.reason)
    }

    private fun requireOther(me: String, other: String) {
        if (me == other) throw BusinessRuleException("You cannot do this to yourself")
    }
}

@RestController
class SafetyController(private val safety: SafetyService) {

    @PutMapping("/api/v1/users/me/blocks/{uid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun block(@AuthenticationPrincipal jwt: Jwt, @PathVariable uid: String) = safety.block(jwt.uid, uid)

    @DeleteMapping("/api/v1/users/me/blocks/{uid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unblock(@AuthenticationPrincipal jwt: Jwt, @PathVariable uid: String) = safety.unblock(jwt.uid, uid)

    @GetMapping("/api/v1/users/me/blocks")
    fun blocked(@AuthenticationPrincipal jwt: Jwt) = safety.blockedBy(jwt.uid)

    @PostMapping("/api/v1/users/{uid}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    fun report(@AuthenticationPrincipal jwt: Jwt, @PathVariable uid: String, @Valid @RequestBody request: ReportRequest) =
        safety.report(jwt.uid, uid, request)
}
