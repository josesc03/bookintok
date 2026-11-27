package com.bookintok.app.core.catalog

/**
 * Fixed choices for book data, so search filters match what people publish.
 * Values are stored in Spanish, as the app is published for Spain.
 */
object BookCatalog {
    /** The ten most spoken languages in the world (total speakers). */
    val LANGUAGES = listOf(
        "Español",
        "Inglés",
        "Chino mandarín",
        "Hindi",
        "Árabe",
        "Francés",
        "Bengalí",
        "Portugués",
        "Ruso",
        "Urdu",
    )

    val CATEGORIES = listOf(
        "Novela",
        "Ciencia ficción",
        "Fantasía",
        "Misterio y thriller",
        "Terror",
        "Romántica",
        "Novela histórica",
        "Infantil",
        "Juvenil",
        "Cómic y manga",
        "Poesía",
        "Teatro",
        "Ensayo",
        "Biografía",
        "Autoayuda",
        "Ciencia y divulgación",
        "Historia",
        "Arte y fotografía",
        "Cocina",
        "Viajes",
        "Libros de texto",
    )

    /** Picking this lets the user type a category that is not on the list. */
    const val OTHER = "Otra"
}
