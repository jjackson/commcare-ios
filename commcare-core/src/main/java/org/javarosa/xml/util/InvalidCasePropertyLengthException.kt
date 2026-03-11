package org.javarosa.xml.util

class InvalidCasePropertyLengthException(val caseProperty: String) :
    InvalidStructureException("Invalid <$caseProperty>, value must be 255 characters or less")
