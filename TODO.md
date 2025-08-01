# TODO - Structured Response Improvements

- [x] Remove placeholder `@{step.explanation}` and show full text
- [x] Add `fullStepExplanation` view and bind explanation in `LearningStepsAdapter`
- [x] Track expanded structured messages with `expandedStructuredMessages`
- [x] Add toggle button to show or hide structured content
- [x] Save structured content JSON or null when persisting chat history
- [x] Load structured content safely by checking field existence
- [x] Provide "Expand All" quick action for learning steps
- [x] Hook up "Quiz Me" and "More Examples" buttons to send follow-up requests
- [x] Enhance system prompt so the AI always includes actual examples and diagrams when referenced
- [x] Detect and surface AI refusals when parsing structured responses

- [x] Integrate Gemini API via OpenAI-compatible endpoint
- [x] Display new Gemini models in AI model dialog and remove outdated ones
- [x] Integrate Grok 4 model with xAI API and add to model selection dialog
