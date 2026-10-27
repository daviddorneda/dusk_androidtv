@file:OptIn(ExperimentalFoundationApi::class)

package app.dusk.androidtv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import kotlin.math.abs

/**
 * El foco de Compose pide desplazar la lista cada vez que cambia el elemento enfocado, y lo hace
 * con los límites del elemento YA ESCALADO por el efecto de foco: eso provoca pequeños saltos
 * verticales al mover el foco de lado. Home gestiona su desplazamiento vertical a mano, así que
 * la lista vertical no reacciona a esas peticiones.
 */
val NoAutoScrollSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

/**
 * Filas horizontales: desplazamiento mínimo para que la tarjeta enfocada quede entera a la vista,
 * con un margen (como en tvOS, donde asoma la siguiente tarjeta) para que no quede pegada al borde.
 */
val RowBringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val margin = containerSize * 0.03f
        val leading = offset - margin
        val trailing = offset + size + margin
        return when {
            leading >= 0f && trailing <= containerSize -> 0f
            leading < 0f && trailing > containerSize -> 0f
            abs(leading) < abs(trailing - containerSize) -> leading
            else -> trailing - containerSize
        }
    }
}
