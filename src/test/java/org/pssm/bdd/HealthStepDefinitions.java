package org.pssm.bdd;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class HealthStepDefinitions {

    @Autowired
    private MockMvc mockMvc;

    private ResultActions response;

    @When("I request the application health status")
    public void iRequestTheApplicationHealthStatus() throws Exception {
        response = mockMvc.perform(get("/api/health"));
    }

    @Then("the HTTP response should be {int}")
    public void theHttpResponseShouldBe(int statusCode) throws Exception {
        response.andExpect(status().is(statusCode));
    }

    @Then("the JSON field {string} should be {string}")
    public void theJsonFieldShouldBe(String jsonPathExpression, String expectedValue) throws Exception {
        response.andExpect(jsonPath(jsonPathExpression).value(expectedValue));
    }
}
