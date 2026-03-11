package org.javarosa.core.util

import java.text.Normalizer

actual fun platformNormalizeNFD(input: String): String {
    return Normalizer.normalize(input, Normalizer.Form.NFD)
}
