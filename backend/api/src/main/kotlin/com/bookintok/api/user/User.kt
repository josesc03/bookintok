package com.bookintok.api.user

import com.bookintok.api.common.GeoDistance
import com.bookintok.api.common.GeoPoint
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "users")
class User(
    /** Firebase uid: the identity comes from Firebase Authentication. */
    @Id
    val uid: String,

    @Column(nullable = false, unique = true)
    var nickname: String,

    @Column(nullable = false, unique = true)
    var email: String,

    var displayName: String? = null,

    var imageUrl: String? = null,

    var latitude: Double? = null,

    var longitude: Double? = null,

    /** Municipality shown to other users next to the user's books, for example "Paterna". */
    var locationLabel: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,
) {
    val location: GeoPoint?
        get() {
            val lat = latitude ?: return null
            val lon = longitude ?: return null
            return GeoPoint(lat, lon)
        }

    /** Stores only the approximate position (see [GeoDistance.approximate]), never the exact point. */
    fun moveTo(point: GeoPoint, label: String?) {
        val approximate = GeoDistance.approximate(point)
        latitude = approximate.latitude
        longitude = approximate.longitude
        locationLabel = label?.trim()?.ifEmpty { null }
    }
}
