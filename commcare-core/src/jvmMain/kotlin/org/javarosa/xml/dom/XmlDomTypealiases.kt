package org.javarosa.xml.dom

/**
 * JVM-only typealiases for kxml2 DOM types.
 * These files use the kxml2 DOM tree for XForm parsing on JVM.
 * On iOS, an alternative DOM implementation will be needed.
 */
typealias XmlDocument = org.kxml2.kdom.Document
typealias XmlElement = org.kxml2.kdom.Element
typealias XmlNode = org.kxml2.kdom.Node
