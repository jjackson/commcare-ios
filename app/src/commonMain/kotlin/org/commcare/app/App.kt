package org.commcare.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.javarosa.xpath.XPathParseTool

@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            EngineStatusScreen()
        }
    }
}

@Composable
fun EngineStatusScreen() {
    var xpathResult by remember { mutableStateOf("Evaluating...") }

    LaunchedEffect(Unit) {
        xpathResult = try {
            // Parse a simple XPath expression to verify the engine works
            val expr = XPathParseTool.parseXPath("1 + 1")
            "XPath engine OK: parsed '1 + 1' -> ${expr::class.simpleName}"
        } catch (e: Exception) {
            "XPath engine error: ${e.message}"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        Text(
            text = "CommCare iOS",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Engine Status",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = xpathResult)
    }
}
