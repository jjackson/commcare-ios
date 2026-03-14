package org.javarosa.xml.dom

/**
 * Cross-platform XML document, replacing kxml2's Document.
 * Simply wraps a root XmlElement.
 */
class XmlDocument(val rootElement: XmlElement)
