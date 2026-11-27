package com.bookintok.app.core.legal

import com.bookintok.app.BuildConfig

data class LegalSection(val heading: String, val paragraphs: List<String>)

data class LegalDocument(val title: String, val updated: String, val intro: String, val sections: List<LegalSection>)

/**
 * Privacy policy and terms of use shown in the app. The owner and the contact address come from
 * local.properties (legal.owner, legal.email), so they are not hard-coded in the repository.
 */
object LegalTexts {
    private const val UPDATED = "27 de noviembre de 2026"

    val owner: String = BuildConfig.LEGAL_OWNER.ifBlank { "[Nombre del responsable]" }
    val email: String = BuildConfig.LEGAL_EMAIL.ifBlank { "[email de contacto]" }

    val privacy = LegalDocument(
        title = "Política de privacidad",
        updated = UPDATED,
        intro = "Bookintok es una aplicación gratuita para intercambiar libros con gente cercana. " +
            "Aquí explicamos qué datos usamos, para qué y cómo puedes controlarlos.",
        sections = listOf(
            LegalSection(
                "Quién es el responsable",
                listOf(
                    "El responsable del tratamiento es $owner. Puedes escribir a $email para cualquier consulta sobre tus datos.",
                    "Bookintok es un proyecto personal sin ánimo de lucro: no vende datos, no muestra publicidad y no usa herramientas de analítica.",
                ),
            ),
            LegalSection(
                "Qué datos tratamos",
                listOf(
                    "Cuenta: tu email y el identificador de tu cuenta de inicio de sesión (Firebase), tu nombre de usuario y, si lo indicas, tu nombre visible.",
                    "Ubicación aproximada: guardamos tu posición redondeada a unos 1 km y el nombre del municipio o provincia. Nunca guardamos tu posición exacta y la app solo pide la ubicación aproximada del sistema.",
                    "Libros y fotos: lo que publicas. Al subir una foto eliminamos sus metadatos (incluida la ubicación en la que se hizo).",
                    "Chats y valoraciones: los mensajes que envías y las valoraciones que das o recibes.",
                    "Notificaciones: un identificador del dispositivo para enviarte avisos.",
                    "Seguridad: los usuarios que bloqueas y las denuncias que envías o que recibes.",
                ),
            ),
            LegalSection(
                "Para qué y con qué base legal",
                listOf(
                    "Para prestarte el servicio: mostrar libros cercanos, gestionar intercambios y chats (ejecución de los términos de uso, art. 6.1.b del RGPD).",
                    "Para mantener la comunidad segura: revisar denuncias y aplicar bloqueos (interés legítimo, art. 6.1.f del RGPD).",
                    "Para enviarte notificaciones, solo si das permiso en tu teléfono; puedes desactivarlas en cualquier momento desde Ajustes.",
                ),
            ),
            LegalSection(
                "Quién puede ver tus datos",
                listOf(
                    "Otros usuarios ven tu nombre de usuario, tus libros, tus valoraciones y tu municipio con una distancia redondeada. Nunca ven tu email ni tu posición.",
                    "Proveedores: Google Firebase gestiona el inicio de sesión y las notificaciones como encargado del tratamiento. Esto puede implicar transferencias fuera del Espacio Económico Europeo, amparadas por el Marco de Privacidad de Datos UE-EE. UU. y cláusulas contractuales tipo.",
                    "Mapas: al usar el mapa, tu teléfono descarga las imágenes de los servidores de OpenStreetMap, que reciben tu dirección IP.",
                    "No cedemos tus datos a nadie más salvo obligación legal.",
                ),
            ),
            LegalSection(
                "Cuánto tiempo los guardamos",
                listOf(
                    "Mientras tengas la cuenta. Si la eliminas desde Ajustes, borramos tu perfil, tus libros y fotos, tus intercambios con sus chats (los mensajes de ambos participantes), tus valoraciones, tus bloqueos y tus dispositivos.",
                ),
            ),
            LegalSection(
                "Tus derechos",
                listOf(
                    "Puedes acceder a tus datos, corregirlos, eliminarlos, oponerte a su uso, limitarlo o pedir una copia escribiendo a $email. Puedes eliminar tu cuenta tú mismo desde Ajustes.",
                    "Si crees que no hemos tratado bien tus datos, puedes reclamar ante la Agencia Española de Protección de Datos (www.aepd.es).",
                ),
            ),
            LegalSection(
                "Edad mínima",
                listOf("Necesitas tener al menos 14 años para usar Bookintok."),
            ),
            LegalSection(
                "Cambios",
                listOf("Si cambiamos esta política, actualizaremos la fecha de arriba y te avisaremos en la app si el cambio es importante."),
            ),
        ),
    )

    val terms = LegalDocument(
        title = "Términos de uso",
        updated = UPDATED,
        intro = "Al crear una cuenta en Bookintok aceptas estas condiciones. Son pocas y sencillas.",
        sections = listOf(
            LegalSection(
                "Qué es Bookintok",
                listOf(
                    "Un servicio gratuito para que particulares intercambien libros. Bookintok no vende libros, no cobra comisiones ni gestiona pagos.",
                ),
            ),
            LegalSection(
                "Tu cuenta",
                listOf(
                    "Debes tener al menos 14 años y usar datos reales. Cada persona puede tener una cuenta y es responsable de lo que se haga con ella.",
                ),
            ),
            LegalSection(
                "Normas de la comunidad",
                listOf(
                    "Publica solo libros que sean tuyos, con fotos y descripciones reales.",
                    "No está permitido vender ni pedir dinero, publicar contenido ilegal, ofensivo o sexual, acosar a otros usuarios, enviar spam, intentar estafas ni compartir datos personales de terceros.",
                ),
            ),
            LegalSection(
                "Los intercambios",
                listOf(
                    "Los intercambios son un acuerdo entre usuarios. Bookintok no participa en ellos y no garantiza el estado de los libros ni que la otra persona cumpla.",
                    "Por tu seguridad, queda en lugares públicos y no compartas más datos de los necesarios.",
                ),
            ),
            LegalSection(
                "Denuncias y moderación",
                listOf(
                    "Puedes denunciar o bloquear a cualquier usuario desde su perfil o desde el chat. Revisamos las denuncias y podemos retirar contenido o suspender cuentas que incumplan estas normas.",
                ),
            ),
            LegalSection(
                "Tu contenido",
                listOf(
                    "Lo que publicas sigue siendo tuyo. Nos das permiso para mostrarlo dentro de la app mientras esté publicado.",
                ),
            ),
            LegalSection(
                "Responsabilidad",
                listOf(
                    "Ofrecemos el servicio tal como está, sin garantizar que esté siempre disponible. En la medida que permita la ley, no respondemos de los daños derivados de los intercambios entre usuarios.",
                ),
            ),
            LegalSection(
                "Baja y cambios",
                listOf(
                    "Puedes eliminar tu cuenta cuando quieras desde Ajustes. Si cambiamos estos términos, te lo indicaremos en la app.",
                ),
            ),
            LegalSection(
                "Ley aplicable y contacto",
                listOf("Estos términos se rigen por la ley española. Para cualquier duda escribe a $email."),
            ),
        ),
    )

    fun byKey(key: String) = if (key == TERMS) terms else privacy

    const val PRIVACY = "privacy"
    const val TERMS = "terms"
}
