package org.cli

import junit.framework.TestCase.assertTrue
import org.commcare.util.cli.ApplicationHost
import org.commcare.util.cli.CliCommand
import org.commcare.util.screen.SessionUtils
import org.javarosa.core.util.externalizable.LivePrototypeFactory
import org.junit.Assert
import org.junit.Test
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.PrintStream
import java.io.StringReader
import java.nio.charset.StandardCharsets

/**
 * Tests for the CommCare CLI
 *
 * Uses a specific, highly paired format to deal with the CLI's I/O
 *
 * @author wpride
 */
class CliTests {

    private inner class CliTestRun<E : CliTestReader>(
        applicationPath: String,
        restoreResource: String,
        processor: CliStepProcessor,
        steps: String,
        endpointId: String?,
        endpointArgs: Array<String>?,
        debug: Boolean,
        sessionUtils: SessionUtils?
    ) {
        init {
            val host = buildApplicationHost(
                applicationPath, restoreResource, processor, steps, debug, sessionUtils
            )
            var passed = false
            try {
                host.run(endpointId, endpointArgs)
            } catch (e: TestPassException) {
                passed = true
            }
            assertTrue(passed)
        }

        private fun buildApplicationHost(
            applicationResource: String,
            restoreResource: String,
            processor: CliStepProcessor,
            steps: String,
            debug: Boolean,
            sessionUtils: SessionUtils?
        ): ApplicationHost {
            val classLoader = javaClass.classLoader
            val applicationPath = File(classLoader.getResource(applicationResource).file).absolutePath
            val prototypeFactory = LivePrototypeFactory()

            val engine = CliCommand.configureApp(applicationPath, prototypeFactory)
            val baos = ByteArrayOutputStream()
            val outStream = PrintStream(baos)

            val reader = CliTestReader(steps, baos, processor)
            reader.setDebug(debug)

            val host = ApplicationHost(engine, prototypeFactory, reader, outStream)
            host.setUsernamePassword("test", "test")
            val utils = sessionUtils ?: MockSessionUtils()
            host.setSessionUtils(utils)
            val restoreFile = File(classLoader.getResource(restoreResource).file)
            val restorePath = restoreFile.absolutePath
            host.setRestoreToLocalFile(restorePath)
            return host
        }
    }

    @Test
    fun testConstraintsForm() {
        // Start a basic form
        CliTestRun<CliTestReader>(
            "basic_app/basic.ccz",
            "case_create_basic.xml",
            BasicTestReader(),
            "1 0 \n",
            null,
            null, false, null
        )
    }

    @Test
    fun testCaseSelection() {
        // Perform case selection
        CliTestRun<CliTestReader>(
            "basic_app/basic.ccz",
            "basic_app/restore.xml",
            CaseTestReader(),
            "2 1 5 1 \n \n",
            null,
            null, false, null
        )
    }

    @Test
    fun testSessionEndpoint() {
        // Run CLI with session endpoint arg
        CliTestRun<CliTestReader>(
            "basic_app/basic.ccz",
            "basic_app/restore.xml",
            SessionEndpointTestReader(),
            "\n",
            "m5_endpoint",
            arrayOf("124938b2-c228-4107-b7e6-31a905c3f4ff"), false, null
        )
    }

    @Test
    fun testEntryWithPost_multipleEntriesInMenu() {
        // Run CLI with session endpoint arg
        CliTestRun<CliTestReader>(
            "session-tests-template/profile.ccpr",
            "session-tests-template/user_restore.xml",
            PostTestReader(),
            "2 0 \n 2",
            null,
            null, false, null
        )
    }

    @Test
    fun testEntryWithPost_singleEntriesInMenu() {
        // Run CLI with session endpoint arg
        CliTestRun<CliTestReader>(
            "session-tests-template/profile.ccpr",
            "session-tests-template/user_restore.xml",
            PostTestReader(),
            "3 0 \n 0",
            null,
            null, false, null
        )
    }

    @Test
    fun testMultiSelectCaseList() {
        val processor = CliStepProcessor { stepIndex, output ->
            when (stepIndex) {
                0 -> Assert.assertTrue(output.contains("4) Multi select case list"))
                1 -> Assert.assertTrue(output.contains("0) Name"))
                2 -> {
                    Assert.assertTrue(output.contains("0) Jack"))
                    Assert.assertTrue(output.contains("1) Lucy"))
                }
                3 -> {
                    Assert.assertTrue(output.contains("0) multi-select form with auto-launch case list"))
                    throw TestPassException()
                }
            }
        }
        val sessionUtils = MockSessionUtils(
            this.javaClass.getResourceAsStream("/session-tests-template/query_response.xml")
        )
        CliTestRun<CliTestReader>(
            "session-tests-template/profile.ccpr",
            "session-tests-template/user_restore.xml",
            processor,
            "4 name 0,1",
            null,
            null, true, sessionUtils
        )
    }

    fun interface CliStepProcessor {
        fun processLine(stepIndex: Int, output: String)
    }

    /**
     * The CliTestReader overrides the Reader (usually System.in) passed into the CLI
     * and so is able to provide input through the readLine() function that the CLI
     * reads from. We are also able to get the output at this point and make assertions
     * about its content.
     */
    open class CliTestReader(
        steps: String,
        private val outStream: ByteArrayOutputStream,
        private val processor: CliStepProcessor
    ) : BufferedReader(StringReader("Unused dummy reader")) {

        private val steps: Array<String> = steps.split(" ").toTypedArray()
        private var stepIndex: Int = 0
        private var debug: Boolean = false

        @Throws(IOException::class)
        override fun readLine(): String {
            val output = String(outStream.toByteArray(), StandardCharsets.UTF_8)
            if (debug) {
                println(output)
            }
            processLine(stepIndex, output)
            if (stepIndex >= this.steps.size) {
                throw RuntimeException("Insufficient steps")
            }
            val ret = this.steps[stepIndex++]
            if (debug) {
                println("Input: " + if (ret == "\n") "[enter]" else ret)
            }
            outStream.reset()
            // Return the next input for the CLI to process
            return ret
        }

        fun setDebug(debug: Boolean) {
            this.debug = debug
        }

        private fun processLine(stepIndex: Int, output: String) {
            this.processor.processLine(stepIndex, output)
        }
    }

    class BasicTestReader : CliStepProcessor {
        override fun processLine(stepIndex: Int, output: String) {
            when (stepIndex) {
                0 -> {
                    Assert.assertTrue(output.contains("Basic Tests"))
                    Assert.assertTrue(output.contains("0) Basic Form Tests"))
                }
                1 -> Assert.assertTrue(output.contains("0) Constraints"))
                2 -> Assert.assertTrue(output.contains("Press Return to proceed"))
                3 -> {
                    Assert.assertTrue(output.contains("This form tests different logic constraints."))
                    throw TestPassException()
                }
                else -> throw RuntimeException(
                    String.format("Did not recognize output %s at stepIndex %s", output, stepIndex)
                )
            }
        }
    }

    class CaseTestReader : CliStepProcessor {
        override fun processLine(stepIndex: Int, output: String) {
            when (stepIndex) {
                0 -> {
                    Assert.assertTrue(output.contains("Basic Tests"))
                    Assert.assertTrue(output.contains("0) Basic Form Tests"))
                }
                1 -> Assert.assertTrue(output.contains("0) Create a Case"))
                2 -> {
                    // m2_case_short
                    Assert.assertTrue(output.contains("Case | vl1"))
                    Assert.assertTrue(output.contains("Date Opened"))
                    Assert.assertTrue(output.contains("case one"))
                }
                3 -> {
                    // Tab 0 of m2_case_long
                    Assert.assertTrue(output.contains("Phone Number"))
                    Assert.assertTrue(output.contains("9632580741"))
                }
                4 -> {
                    // Tab 1 of m2_case_long
                    Assert.assertTrue(output.contains("Geodata"))
                    Assert.assertTrue(output.contains("17.4469641 78.3719456 543.4 24.36"))
                }
                5 -> Assert.assertTrue(output.contains("Form Start: Press Return to proceed"))
                6 -> {
                    Assert.assertTrue(output.contains("This form will allow you to add and update"))
                    throw TestPassException()
                }
                else -> throw RuntimeException(
                    String.format("Did not recognize output %s at stepIndex %s", output, stepIndex)
                )
            }
        }
    }

    class SessionEndpointTestReader : CliStepProcessor {
        override fun processLine(stepIndex: Int, output: String) {
            when (stepIndex) {
                0 -> {
                    Assert.assertTrue(output.contains("0) Update a Case"))
                    Assert.assertTrue(output.contains("1) Close a Case"))
                    throw TestPassException()
                }
                else -> throw RuntimeException(
                    String.format("Did not recognize output %s at stepIndex %s", output, stepIndex)
                )
            }
        }
    }

    class PostTestReader : CliStepProcessor {
        override fun processLine(stepIndex: Int, output: String) {
            when (stepIndex) {
                0 -> {
                    Assert.assertTrue(output.contains("test [36]"))
                    Assert.assertTrue(output.contains("2) Module 2"))
                }
                1 -> {
                    // Tab 0 of case_short
                    Assert.assertTrue(output.contains("0) Test Case 1"))
                }
                2 -> {
                    // Tab 0 of case_long
                    Assert.assertTrue(output.contains("Case | test"))
                    Assert.assertTrue(output.contains("name"))
                    Assert.assertTrue(output.contains("Test Case 1"))
                }
                3 -> Assert.assertTrue(output.contains("Module 2 Form 2"))
                4 -> {
                    Assert.assertTrue(output.contains("Sync complete, press Enter to continue"))
                    throw TestPassException()
                }
                else -> throw RuntimeException(
                    String.format("Did not recognize output %s at stepIndex %s", output, stepIndex)
                )
            }
        }
    }

    // Because the CLI is a REPL that will loop indefinitely unless certain code paths are
    // reached we need to provide a way for tests to exit early. This exception will be
    // caught at the top level of the CliTestRun and set the tests to pass when thrown.
    private class TestPassException : RuntimeException()
}
