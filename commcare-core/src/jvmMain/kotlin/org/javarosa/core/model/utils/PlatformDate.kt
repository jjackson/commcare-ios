@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.utils

actual typealias PlatformDate = java.util.Date

@Suppress("DEPRECATION")
actual fun PlatformDate.getTimezoneOffset(): Int = this.timezoneOffset
