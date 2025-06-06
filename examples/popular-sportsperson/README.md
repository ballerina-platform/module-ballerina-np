# Get popular sportsperson

Use a natural function to find a popular sportsperson who has the specified name segment in their name and was born in the decade starting from the specified year, and retrieve information about them.

## Steps

1. Define a record type representing the information required about the sportsperson.

    ```ballerina
    # Represents a person who plays a sport.
    type SportsPerson record {|
        # First name of the person
        string firstName;
        # Last name of the person
        string lastName;
        # Year the person was born
        int yearOfBirth;
        # Sport that the person plays
        string sport;
    |};
    ```

2. Define a natural function to retrieve the information.

    ```ballerina
    function getPopularSportsPerson(string nameSegment, int decadeStart) 
            returns SportsPerson|error? => natural {
        Who is a popular sportsperson that was born in the decade starting 
        from ${decadeStart} with ${nameSegment} in their name?
    };
    ```

    - Specify the prompt in natural language in a natural expression. Note how interpolations can refer to in-scope symbols.
    - Use the type defined above (`SportsPerson`) in the return type along with `error` (to allow for failures when calling the LLM or attempting to bind the response to the target type) and optionally nil (`?`, representing `null` to allow for no match).

3. Call the function with arguments for `nameSegment` and `decadeStart` in the `main` function and access the required fields from the returned `SportsPerson` value.

    ```ballerina
    public function main() returns error? {
        string nameSegment = "Simone";
        int decadeStart = 1990;

        SportsPerson? person = check getPopularSportsPerson(nameSegment, decadeStart);
        if person is SportsPerson {
            io:println("Full name: ", person.firstName, " ", person.lastName);
            io:println("Born in: ", person.yearOfBirth);
            io:println("Sport: ", person.sport);
        } else {
            io:println("No matching sportsperson found");
        }
    }
    ```

4. Provide configuration for the LLM via `configurable` variables. 

    You can use your keys and configuration for OpenAI or Azure OpenAI by configuring the `defaultModelConfig` variable in the `np` module.

    E.g., add the following for Azure OpenAI in the Config.toml file

    ```toml
    [ballerina.np.defaultModelConfig]
    serviceUrl = "<SERVICE_URL>"
    deploymentId = "<DEPLOYMENT_ID>"
    apiVersion = "<API_VERSION>"
    connectionConfig.auth.apiKey = "<YOUR_API_KEY>"
    ```

    Alternatively, you can use the default model made available via WSO2 Copilot. Log in to WSO2 Copilot, open up the VS Code command palette (`Ctrl + Shift + P` or `command + shift + P`), and run `Configure Default Model for Natural Functions`. This will add configuration for the default model into the Config.toml file. Please note that this will require VS Code being open in the relevant directory.

    You can also configure and set the model in the code, by introducing an argument of type `np:ModelProvider` to the natural expression.

    ```ballerina
    import ballerina/np;

    function getPopularSportsPerson(string nameSegment, int decadeStart, np:ModelProvider model) 
        returns SportsPerson|error? => natural (model) {
        Who is a popular sportsperson that was born in the decade starting 
        from ${decadeStart} with ${nameSegment} in their name?
    };
    ```

    The [ballerinax/np](https://central.ballerina.io/ballerinax/np/0.9.0) package provides implementations of `np:ModelProvider` for commonly-used providers.

5. Run the sample using the Ballerina run command passing the `--experimental` option.

    ```cmd
    $ bal run --experimental popular_sportsperson.bal
    Compiling source
        popular_sportsperson.bal

    Running executable

    Full name: Simone Biles
    Born in: 1997
    Sport: Gymnastics
    ```
