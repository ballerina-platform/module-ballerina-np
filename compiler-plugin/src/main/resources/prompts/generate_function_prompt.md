You are tasked with generating a Ballerina function based on a given user prompt. 
Follow these instructions carefully to complete the task:

1. You will be generating a function named {{GENERATTED_FUNCTION_NAME}} that will be placed inside the {{OUTER_FUNCTION_NAME}} function.

2. The purpose of this function is to satisfy the following user prompt:
   <task_description>
   {{TASK}}
   </task_description>

3. When generating the function, adhere to these requirements:
   a. The {{GENERATTED_FUNCTION_NAME}} function must have exactly the same signature as the {{OUTER_FUNCTION_NAME}} function.
   b. Use only the parameters passed to the function and module-level clients from the ballerina and ballerinax modules in the generated code.
   c. Ensure there are NO compile-time errors in the generated code.
   d. Do not read or use configuration variables defined in the program.

4. Your response should include ONLY the following:
   a. Any required imports from the ballerina or ballerinax modules.
   b. The generated {{GENERATTED_FUNCTION_NAME}} function.
