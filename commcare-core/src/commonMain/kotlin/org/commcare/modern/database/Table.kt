package org.commcare.modern.database

/**
 * @author ctsims
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Table(val value: String)
