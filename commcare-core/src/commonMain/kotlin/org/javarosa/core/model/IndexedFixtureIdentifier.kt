package org.javarosa.core.model

/**
 * Model representation for a row of "IndexedFixtureIndex" table
 *
 * Represents a IndexedFixture root level properties like the fixture's
 * base name and child name along with the root level attributes.
 */
class IndexedFixtureIdentifier(
    val fixtureBase: String,
    val fixtureChild: String,
    val rootAttributes: ByteArray?
)
