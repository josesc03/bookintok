package com.bookintok.api.user

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

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,
) {
    val location: GeoPoint?
        get() {
            val lat = latitude ?: return null
            val lon = longitude ?: return null
            return GeoPoint(lat, lon)
        }

    fun moveTo(point: GeoPoint) {
        latitude = point.latitude
        longitude = point.longitude
    }
}
