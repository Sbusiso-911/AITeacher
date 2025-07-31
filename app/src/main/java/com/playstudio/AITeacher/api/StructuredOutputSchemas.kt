package com.playstudio.aiteacher.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * JSON Schema definitions for OpenAI Structured Outputs
 * These schemas ensure consistent, reliable educational content formatting
 */
object StructuredOutputSchemas {

    /**
     * Simplified educational response schema that's guaranteed to work with OpenAI
     */
    fun getEducationalResponseSchema(): JsonObject {
        val schemaJson = """
        {
          "type": "json_schema",
          "json_schema": {
            "name": "educational_response",
            "description": "Structured educational content for AI Teacher",
            "strict": true,
            "schema": {
              "type": "object",
              "properties": {
                "response_type": {
                  "type": "string",
                  "description": "Type of educational response",
                  "enum": [
                    "explanation",
                    "step_by_step",
                    "quiz",
                    "lesson",
                    "practice",
                    "summary",
                    "code_tutorial"
                  ]
                },
                "subject": {
                  "type": "string",
                  "description": "Subject area like mathematics, programming, science"
                },
                "difficulty_level": {
                  "type": "string",
                  "description": "Difficulty level of the content",
                  "enum": ["beginner", "intermediate", "advanced", "expert"]
                },
                "content": {
                  "type": "object",
                  "description": "Structured content, including explanation, steps, examples, code snippets, and formulas",
                  "properties": {
                    "main_explanation": {"type": "string"},
                    "steps": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "step_number": {"type": "integer"},
                          "title": {"type": "string"},
                          "explanation": {"type": "string"}
                        },
                        "required": ["step_number", "title", "explanation"],
                        "additionalProperties": false
                      }
                    },
                    "examples": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "title": {"type": "string"},
                          "problem": {"type": "string"},
                          "solution": {"type": "string"}
                        },
                        "required": ["title", "problem", "solution"],
                        "additionalProperties": false
                      }
                    },
                    "practice_questions": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "question": {"type": "string"},
                          "answer": {"type": "string"}
                        },
                        "required": ["question", "answer"],
                        "additionalProperties": false
                      }
                    },
                    "key_concepts": {"type": "array", "items": {"type": "string"}},
                    "estimated_reading_time": {"type": "integer"}
                  },
                  "required": [
                    "main_explanation",
                    "steps",
                    "examples",
                    "practice_questions",
                    "key_concepts",
                    "estimated_reading_time"
                  ],
                  "additionalProperties": false
                },
                "metadata": {
                  "type": "object",
                  "properties": {
                    "estimated_reading_time": {"type": "integer"},
                    "prerequisites": {"type": "array", "items": {"type": "string"}},
                    "next_topics": {"type": "array", "items": {"type": "string"}},
                    "learning_objectives": {"type": "array", "items": {"type": "string"}},
                    "tags": {"type": "array", "items": {"type": "string"}},
                    "confidence_score": {"type": "number"}
                  },
                  "required": [
                    "estimated_reading_time",
                    "prerequisites",
                    "next_topics",
                    "learning_objectives",
                    "tags",
                    "confidence_score"
                  ],
                  "additionalProperties": false
                },
                "interactive_elements": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "element_type": {"type": "string", "enum": [
                        "progress_tracker",
                        "interactive_diagram",
                        "code_playground",
                        "formula_renderer",
                        "quiz_widget"
                      ]},
                      "title": {"type": "string"},
                      "description": {"type": "string"},
                      "data": {"type": "string"}
                    },
                    "required": ["element_type", "title", "description", "data"],
                    "additionalProperties": false
                  }
                }
              },
              "required": [
                "response_type",
                "subject",
                "difficulty_level",
                "content",
                "metadata",
                "interactive_elements"
              ],
              "additionalProperties": false
            }
          }
        }
        """.trimIndent()

        return JsonParser.parseString(schemaJson).asJsonObject
    }

    /**
     * Simplified schema for quick explanations
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
                "ui": { "$ref": "#/$defs/ui" }
              },
              "required": ["ui"],
              "$defs": {
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
                      "items": { "$ref": "#/$defs/ui" }
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
     * Get system prompt for structured educational responses
     */
    fun getEducationalSystemPrompt(): String {
        return """
        You are AI Teacher, an advanced educational AI assistant. Your responses must be structured, comprehensive, and pedagogically sound.

        IMPORTANT: Always respond using the provided JSON schema format.

        Guidelines for educational responses:
        1. Always assess the difficulty level appropriate for the user's question
        2. Break down complex topics into manageable steps
        3. Provide practical examples when possible
        4. Include practice questions for reinforcement
        5. Suggest prerequisites if the topic is advanced
        6. Recommend next topics for continued learning
        7. Estimate reading time accurately
        8. Use clear, encouraging language suitable for learning

        For mathematics:
        - Show step-by-step solutions
        - Explain the reasoning behind each step
        - Provide verification methods
        - Include relevant formulas with explanations

        For programming:
        - Include working code examples
        - Explain code line by line when helpful
        - Show expected outputs
        - Suggest improvements or variations

        General:
        - If you mention a graph, diagram, or example, provide the actual content
          or a simple textual representation immediately. Do not reference
          materials without including them.

        For science topics:
        - Use analogies to explain complex concepts
        - Include real-world applications
        - Break down processes into clear steps
        - Connect to prior knowledge

        Always maintain an encouraging, patient tone that promotes learning and curiosity.
        """.trimIndent()
    }
}