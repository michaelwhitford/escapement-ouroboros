---
type: mementum/memory
description: Statechart worker holds LLM while chart sees only declared event-tools, never real tools
---
💡 Architectural insight: The statechart model separates the LLM worker from the chart's view. A worker thread holds the LLM while its chart state is active, but the chart sees only declared event-tools, never real tools. Real tools are dispatched in the worker and are invisible to the chart. The :type :internal flag keeps the worker alive across hops. This design ensures the chart, not the model, controls flow while tools remain safely isolated in the worker.