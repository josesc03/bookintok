package com.bookintok.api.rating

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RatingRepository : JpaRepository<Rating, Long> {

    @EntityGraph(attributePaths = ["rater"])
    fun findByRatedUid(ratedUid: String, pageable: Pageable): Page<Rating>

    fun existsByRatedUidAndRaterUid(ratedUid: String, raterUid: String): Boolean

    /** The average is computed on read, so it can never drift from the stored ratings. */
    @Query(
        """
        select new com.bookintok.api.rating.RatingSummary(round(avg(r.score), 2), count(r))
        from Rating r where r.rated.uid = :uid
        """,
    )
    fun summaryFor(@Param("uid") uid: String): RatingSummary
}
