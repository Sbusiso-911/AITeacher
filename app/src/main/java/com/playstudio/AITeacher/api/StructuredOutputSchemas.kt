package com.playstudio.aiteacher.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * NEW Learning-Focused Schema Definitions
 * Redesigned for comprehensive subject teaching like textbooks and experienced teachers
 */
object StructuredOutputSchemas {

    /**
     * MAIN: Comprehensive Learning Content Schema
     * Structured like a textbook chapter with natural knowledge flow
     */
    fun getLearningContentSchema(): JsonObject {
        val schemaJson = """
        {
          "type": "json_schema",
          "json_schema": {
            "name": "learning_content",
            "strict": true,
            "schema": {
              "type": "object",
              "properties": {
                "topic_title": {
                  "type": "string",
                  "description": "Clear, engaging title for the topic"
                },
                "subject_area": {
                  "type": "string",
                  "description": "Subject like mathematics, science, programming, history"
                },
                "content_type": {
                  "type": "string",
                  "description": "Type of educational content",
                  "enum": [
                    "comprehensive_explanation",
                    "concept_overview",
                    "detailed_guide",
                    "practical_tutorial"
                  ]
                },
                "introduction": {
                  "type": "object",
                  "description": "Opening section to engage and orient learners",
                  "properties": {
                    "hook": {
                      "type": "string",
                      "description": "Engaging opening that captures interest"
                    },
                    "overview": {
                      "type": "string",
                      "description": "What this topic covers and why it matters"
                    },
                    "real_world_relevance": {
                      "type": "string",
                      "description": "How this applies in real life"
                    }
                  },
                  "required": ["hook", "overview", "real_world_relevance"],
                  "additionalProperties": false
                },
                "core_content": {
                  "type": "object",
                  "description": "Main educational content organized logically",
                  "properties": {
                    "fundamental_concepts": {
                      "type": "string",
                      "description": "Core ideas explained clearly and thoroughly"
                    },
                    "detailed_explanation": {
                      "type": "string",
                      "description": "Comprehensive explanation building on fundamentals"
                    },
                    "key_principles": {
                      "type": "array",
                      "description": "Important principles to understand",
                      "items": {
                        "type": "object",
                        "properties": {
                          "principle": {"type": "string"},
                          "explanation": {"type": "string"},
                          "importance": {"type": "string"}
                        },
                        "required": ["principle", "explanation", "importance"],
                        "additionalProperties": false
                      }
                    },
                    "advanced_concepts": {
                      "type": "string",
                      "description": "More sophisticated ideas for deeper understanding"
                    }
                  },
                  "required": ["fundamental_concepts", "detailed_explanation", "key_principles", "advanced_concepts"],
                  "additionalProperties": false
                },
                "practical_examples": {
                  "type": "array",
                  "description": "Real-world examples showing practical application",
                  "items": {
                    "type": "object",
                    "properties": {
                      "example_title": {"type": "string"},
                      "context": {"type": "string", "description": "Real-world situation"},
                      "application": {"type": "string", "description": "How the concept applies"},
                      "outcome": {"type": "string", "description": "What this achieves"}
                    },
                    "required": ["example_title", "context", "application", "outcome"],
                    "additionalProperties": false
                  }
                },
                "applications": {
                  "type": "object",
                  "description": "How this knowledge is used in practice",
                  "properties": {
                    "common_uses": {
                      "type": "array",
                      "description": "Typical ways this is applied",
                      "items": {"type": "string"}
                    },
                    "professional_applications": {
                      "type": "array",
                      "description": "How professionals use this knowledge",
                      "items": {"type": "string"}
                    },
                    "everyday_relevance": {
                      "type": "string",
                      "description": "How this affects daily life"
                    }
                  },
                  "required": ["common_uses", "professional_applications", "everyday_relevance"],
                  "additionalProperties": false
                },
                "reading_time_minutes": {
                  "type": "integer",
                  "description": "Estimated reading time in minutes"
                }
              },
              "required": [
                "topic_title",
                "subject_area",
                "content_type",
                "introduction",
                "core_content",
                "practical_examples",
                "applications",
                "reading_time_minutes"
              ],
              "additionalProperties": false
            }
          }
        }
        """.trimIndent()

        return JsonParser.parseString(schemaJson).asJsonObject
    }

    /**
     * INTERACTIVE SESSION Schema - Separate dedicated Q&A mode
     * Used when users explicitly want to test their knowledge
     */
    fun getInteractiveSessionSchema(): JsonObject {
        val schemaJson = """
        {
          "type": "json_schema",
          "json_schema": {
            "name": "interactive_session",
            "description": "Dedicated interactive Q&A session for knowledge testing",
            "strict": true,
            "schema": {
              "type": "object",
              "properties": {
                "session_type": {
                  "type": "string",
                  "description": "Type of interactive session",
                  "enum": [
                    "knowledge_check",
                    "practice_quiz",
                    "discussion_questions",
                    "application_scenarios"
                  ]
                },
                "topic_focus": {
                  "type": "string",
                  "description": "The specific topic being tested"
                },
                "session_intro": {
                  "type": "string",
                  "description": "Brief introduction to this interactive session"
                },
                "questions": {
                  "type": "array",
                  "description": "Interactive questions for the user",
                  "items": {
                    "type": "object",
                    "properties": {
                      "question_id": {"type": "string"},
                      "question_text": {"type": "string"},
                      "question_type": {
                        "type": "string",
                        "enum": ["multiple_choice", "open_ended", "true_false", "scenario_based"]
                      },
                      "options": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "Options for multiple choice questions"
                      },
                      "correct_answer": {"type": "string"},
                      "explanation": {
                        "type": "string",
                        "description": "Why this answer is correct"
                      }
                    },
                    "required": ["question_id", "question_text", "question_type", "correct_answer", "explanation"],
                    "additionalProperties": false
                  }
                },
                "encouragement": {
                  "type": "string",
                  "description": "Encouraging message for the session"
                }
              },
              "required": ["session_type", "topic_focus", "session_intro", "questions", "encouragement"],
              "additionalProperties": false
            }
          }
        }
        """.trimIndent()

        return JsonParser.parseString(schemaJson).asJsonObject
    }

    /**
     * Quick explanation schema for simple questions
     */
    fun getQuickExplanationSchema(): JsonObject {
        val schemaJson = """
        {
            "type": "json_schema",
            "json_schema": {
                "name": "quick_explanation",
                "description": "Quick structured explanation for simple questions",
                "strict": true,
                "schema": {
                    "type": "object",
                    "properties": {
                        "explanation": {
                            "type": "string",
                            "description": "Main explanation"
                        },
                        "key_points": {
                            "type": "array",
                            "description": "Key points to remember",
                            "items": {
                                "type": "string"
                            }
                        },
                        "example": {
                            "type": ["string", "null"],
                            "description": "Simple example if applicable"
                        },
                        "follow_up_questions": {
                            "type": "array",
                            "description": "Suggested follow-up questions",
                            "items": {
                                "type": "string"
                            }
                        }
                    },
                    "required": ["explanation", "key_points"],
                    "additionalProperties": false
                }
            }
        }
        """.trimIndent()

        return JsonParser.parseString(schemaJson).asJsonObject
    }

    /**
     * Schema for step-by-step math solutions
     */
    fun getMathSolutionSchema(): JsonObject {
        val schemaJson = """
        {
            "type": "json_schema",
            "json_schema": {
                "name": "math_solution",
                "description": "Step-by-step mathematical solution",
                "strict": true,
                "schema": {
                    "type": "object",
                    "properties": {
                        "problem_type": {
                            "type": "string",
                            "description": "Type of math problem (algebra, calculus, geometry, etc.)"
                        },
                        "steps": {
                            "type": "array",
                            "description": "Step-by-step solution",
                            "items": {
                                "type": "object",
                                "properties": {
                                    "step_number": {
                                        "type": "integer"
                                    },
                                    "explanation": {
                                        "type": "string"
                                    },
                                    "mathematical_expression": {
                                        "type": "string"
                                    },
                                    "reasoning": {
                                        "type": "string"
                                    }
                                },
                                "required": ["step_number", "explanation", "mathematical_expression", "reasoning"],
                                "additionalProperties": false
                            }
                        },
                        "final_answer": {
                            "type": "string",
                            "description": "The final answer"
                        },
                        "verification": {
                            "type": ["string", "null"],
                            "description": "How to verify the answer is correct"
                        }
                    },
                    "required": ["problem_type", "steps", "final_answer"],
                    "additionalProperties": false
                }
            }
        }
        """.trimIndent()

        return JsonParser.parseString(schemaJson).asJsonObject
    }

    /**
     * Schema for UI generation responses
     */
    fun getUiSchema(): JsonObject {
        val schemaJson = """
        {
          "type": "json_schema",
          "json_schema": {
            "name": "ui_response",
            "description": "Generated UI structure",
            "strict": true,
            "schema": {
              "type": "object",
              "properties": {
                "ui": { "${'$'}ref": "#/${'$'}defs/ui" }
              },
              "required": ["ui"],
              "${'$'}defs": {
                "ui": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "enum": ["div", "button", "header", "section", "field", "form"]
                    },
                    "label": {"type": "string"},
                    "children": {
                      "type": "array",
                      "items": { "${'$'}ref": "#/${'$'}defs/ui" }
                    },
                    "attributes": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "name": {"type": "string"},
                          "value": {"type": "string"}
                        },
                        "required": ["name", "value"],
                        "additionalProperties": false
                      }
                    }
                  },
                  "required": ["type", "label", "children", "attributes"],
                  "additionalProperties": false
                }
              },
              "additionalProperties": false
            }
          }
        }
        """.trimIndent()

        return JsonParser.parseString(schemaJson).asJsonObject
    }

    /**
     * NEW: Simplified Security Analysis Schema
     * Clear, user-friendly problem-solution format
     */
    fun getSecurityAnalysisSchema(): JsonObject {
        val schemaJson = """
        {
          "type": "json_schema",
          "json_schema": {
            "name": "security_analysis",
            "description": "Simplified security analysis using clear problem-solution format",
            "strict": true,
            "schema": {
              "type": "object",
              "properties": {
                "analysis_type": {
                  "type": "string",
                  "description": "Type of security analysis",
                  "enum": [
                    "account_security",
                    "data_protection", 
                    "network_security",
                    "system_vulnerability"
                  ]
                },
                "problem": {
                  "type": "object",
                  "description": "What was broken or vulnerable",
                  "properties": {
                    "title": {
                      "type": "string",
                      "description": "Simple, clear problem title"
                    },
                    "description": {
                      "type": "string", 
                      "description": "Plain English explanation of what was wrong"
                    },
                    "severity": {
                      "type": "string",
                      "description": "How serious the problem is",
                      "enum": ["low", "medium", "high", "critical"]
                    },
                    "impact": {
                      "type": "string",
                      "description": "What could happen because of this problem"
                    }
                  },
                  "required": ["title", "description", "severity", "impact"],
                  "additionalProperties": false
                },
                "solution": {
                  "type": "object", 
                  "description": "What we fixed or implemented",
                  "properties": {
                    "title": {
                      "type": "string",
                      "description": "Simple, clear solution title"
                    },
                    "description": {
                      "type": "string",
                      "description": "Plain English explanation of what was done"
                    },
                    "how_it_works": {
                      "type": "string",
                      "description": "Simple explanation of how the solution works"
                    }
                  },
                  "required": ["title", "description", "how_it_works"],
                  "additionalProperties": false
                },
                "result": {
                  "type": "object",
                  "description": "Clear before/after comparison", 
                  "properties": {
                    "before": {
                      "type": "string",
                      "description": "What the situation was like before"
                    },
                    "after": {
                      "type": "string", 
                      "description": "What the situation is like now"
                    },
                    "outcome": {
                      "type": "string",
                      "description": "The clear, positive outcome"
                    }
                  },
                  "required": ["before", "after", "outcome"],
                  "additionalProperties": false
                },
                "next_action": {
                  "type": "object",
                  "description": "One clear action for the user (optional)",
                  "properties": {
                    "title": {
                      "type": "string",
                      "description": "Clear action title"
                    },
                    "description": {
                      "type": "string", 
                      "description": "Simple explanation of what the user should do"
                    },
                    "is_urgent": {
                      "type": "boolean",
                      "description": "Whether this action is urgent"
                    }
                  },
                  "required": ["title", "description"],
                  "additionalProperties": false
                }
              },
              "required": [
                "analysis_type",
                "problem", 
                "solution",
                "result"
              ],
              "additionalProperties": false
            }
          }
        }
        """.trimIndent()

        return JsonParser.parseString(schemaJson).asJsonObject
    }

    /**
     * NEW: System prompt for comprehensive learning content
     */
    fun getLearningContentSystemPrompt(): String {
        return """
        You are AI Teacher, providing comprehensive educational content like an experienced teacher or textbook author.

        CRITICAL APPROACH: Think like a textbook chapter, not a problem solver.

        CORE PRINCIPLES:
        1. COMPREHENSIVE COVERAGE: Provide full, thorough information about the topic
        2. NATURAL FLOW: Start with engaging introduction → build core understanding → show real applications
        3. PRACTICAL FOCUS: Emphasize real-world relevance and applications
        4. PROGRESSIVE DEPTH: Begin with fundamentals, naturally build to more sophisticated concepts
        5. ENGAGING TEACHING: Use compelling examples and clear explanations

        CONTENT STRUCTURE:
        - Introduction: Hook interest, explain relevance, overview what's covered
        - Core Content: Comprehensive explanation building from basics to advanced
        - Examples: Real-world situations showing practical application
        - Applications: How this knowledge is actually used in life and work

        WRITING STYLE:
        - Write like an engaging textbook or experienced teacher
        - Use clear, accessible language appropriate for the topic
        - Include relevant analogies and comparisons
        - Show enthusiasm for the subject
        - Focus on understanding, not just mechanics

        AVOID:
        - Step-by-step problem solving unless specifically requested
        - Academic jargon like "learning objectives" or "prerequisites"
        - Breaking content into disconnected pieces
        - Formula-heavy responses without context
        - Abstract concepts without practical grounding

        GOAL: Help users develop comprehensive understanding of topics, not just solve specific problems.
        """.trimIndent()
    }

    /**
     * NEW: System prompt for simplified security analysis
     */
    fun getSecurityAnalysisSystemPrompt(): String {
        return """
        You are a security analysis assistant. Your goal is to make security issues crystal clear to users who are NOT security experts.

        CRITICAL RULES:
        1. Start with the PROBLEM in plain English - What was broken?
        2. Explain the SOLUTION simply - What did we fix?  
        3. Show the RESULT clearly - What works now?
        4. Give ONE clear action (if any) - What should the user do?

        NEVER USE:
        - Technical jargon without context
        - Complex role-based sections  
        - Code snippets users can't understand
        - Tables with unclear meaning
        - Multiple action items
        - Industry compliance language

        ALWAYS USE:
        - Clear problem statement 
        - Simple solution explanation
        - Obvious before/after comparison
        - One clear outcome
        - Simple language anyone can understand

        Your goal: Make the user say "Oh, I get it!" instead of "What is this?"

        Focus on making security understandable, not impressive.
        """.trimIndent()
    }
}