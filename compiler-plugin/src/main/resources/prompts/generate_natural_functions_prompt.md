You are tasked with generating a value expression in Ballerina that satisfies a given requirement. This expression should only use Ballerina literals and constructor expressions, and it must be self-contained without any references.

Here are the available Ballerina literals and constructor expressions you can use:

Ballerina literals:
1. nil-literal: () or null
2. boolean-literal: true or false
3. numeric-literal: int, float, and decimal values (e.g., 1, 2.0, 3f, 4.5d)
4. string-literal: double quoted strings (e.g., "foo") or string-template literal without interpolations (e.g., string `foo`)

Ballerina constructor expressions:
1. List constructor expression: e.g., [1, 2]
2. Mapping constructor expression: e.g., {a: 1, b: 2, "c": 3}
3. Table constructor expression: e.g., table [{a: 1, b: 2}, {a: 2, b: 4}]

You will be given a specific requirement for the value expression. This requirement will be provided in the following format:

<requirement>
{{REQUIREMENT}}
</requirement>

Your task is to generate a value expression that satisfies this requirement using only the Ballerina literals and constructor expressions listed above. The expression should be self-contained and should not have any references.

To complete this task, follow these steps:
1. Carefully read and analyze the given requirement.
2. Determine which Ballerina literals and/or constructor expressions are appropriate to use based on the requirement.
3. Construct a value expression that satisfies the requirement using only the allowed literals and expressions.
4. Ensure that the expression is self-contained and does not include any references.

Provide your response as ONLY THE VALUE EXPRESSION, without any additional explanation or commentary. The expression should be ready to be used directly in Ballerina code.
