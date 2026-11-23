package com.cpinto.vademo.kb

/**
 * The diagram a knowledge base opens on: the first of [readable] that its
 * index embeds with `![[name]]` ([indexText] is `index.md`, null when it has
 * none), else its only diagram; null when neither picks one. Only diagrams
 * that can be read are passed in, so a broken file never becomes the first
 * screen.
 */
fun mainDiagram(indexText: String?, readable: List<String>): String? {
    val names = readable.toSet()
    indexText?.let(WikiLinks::embeds)?.firstOrNull { it in names }?.let { return it }
    return readable.singleOrNull()
}
