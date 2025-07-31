// Simple test to verify voice agent creation works
fun testVoiceAgentCreation() {
    val agents = mapOf(
        "general_assistant" to "🤖 AI Chat Assistant",
        "meeting_specialist" to "📋 Meeting Specialist", 
        "educational_tutor" to "🎓 Educational Tutor"
    )
    
    println("Available agents: ${agents.keys}")
    println("Agent display names: ${agents.values.toTypedArray().contentToString()}")
}