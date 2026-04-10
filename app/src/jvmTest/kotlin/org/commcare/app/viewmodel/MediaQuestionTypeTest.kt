package org.commcare.app.viewmodel

import org.commcare.app.engine.FormEntrySession
import org.javarosa.xform.util.XFormUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests that media question types (image, signature, audio, video,
 * document) map correctly through the engine to [QuestionType].
 *
 * Actual capture requires hardware (camera, mic) that the simulator
 * doesn't have. These tests verify the ViewModel layer: that the
 * engine propagates the control type and appearance correctly, and
 * that the form can be navigated and serialized even when media
 * questions are left unanswered.
 *
 * Phase 9 Wave 5c — media question types.
 */
class MediaQuestionTypeTest {

    private fun loadForm(): FormEntryViewModel {
        val stream = this::class.java.getResourceAsStream("/test_media_questions.xml")
        assertNotNull(stream, "test_media_questions.xml fixture missing")
        val formDef = XFormUtils.getFormFromInputStream(stream)
        assertNotNull(formDef)
        val session = FormEntrySession(formDef)
        val vm = FormEntryViewModel(session)
        vm.loadForm()
        return vm
    }

    @Test
    fun photoQuestionMapsToImageType() {
        val vm = loadForm()
        assertEquals(QuestionType.IMAGE, vm.questions[0].questionType, "upload image/* → IMAGE")
    }

    @Test
    fun signatureQuestionMapsToSignatureType() {
        val vm = loadForm()
        vm.nextQuestion()
        val q = vm.questions[0]
        assertEquals(QuestionType.SIGNATURE, q.questionType, "upload image/* appearance=signature → SIGNATURE")
        assertNotNull(q.appearance)
        assertTrue(q.appearance!!.contains("signature"))
    }

    @Test
    fun audioQuestionMapsToAudioType() {
        val vm = loadForm()
        repeat(2) { vm.nextQuestion() }
        assertEquals(QuestionType.AUDIO, vm.questions[0].questionType, "upload audio/* → AUDIO")
    }

    @Test
    fun videoQuestionMapsToVideoType() {
        val vm = loadForm()
        repeat(3) { vm.nextQuestion() }
        assertEquals(QuestionType.VIDEO, vm.questions[0].questionType, "upload video/* → VIDEO")
    }

    @Test
    fun documentQuestionMapsToUploadOrImageType() {
        val vm = loadForm()
        repeat(4) { vm.nextQuestion() }
        // The engine may map application/* uploads to IMAGE (CONTROL_IMAGE_CHOOSE)
        // since there's no dedicated document control type in the XForm spec.
        // Both IMAGE and UPLOAD are acceptable — the UI renders a file-picker
        // button either way.
        val type = vm.questions[0].questionType
        assertTrue(
            type == QuestionType.UPLOAD || type == QuestionType.IMAGE,
            "upload application/* → UPLOAD or IMAGE, got $type"
        )
    }

    @Test
    fun canNavigateEntireMediaFormWithoutAnswering() {
        val vm = loadForm()
        var steps = 0
        while (!vm.isComplete && steps < 20) {
            vm.nextQuestion()
            steps++
        }
        assertTrue(vm.isComplete, "should reach end of form (media questions are optional)")
    }

    @Test
    fun canSerializeMediaFormWithoutAnswers() {
        val vm = loadForm()
        var steps = 0
        while (!vm.isComplete && steps < 20) {
            vm.nextQuestion()
            steps++
        }
        val xml = vm.serializeForm()
        assertNotNull(xml, "form should serialize even with empty media questions")
        assertTrue(xml.contains("<photo"), "XML should contain photo element")
        assertTrue(xml.contains("<signature"), "XML should contain signature element")
    }
}
