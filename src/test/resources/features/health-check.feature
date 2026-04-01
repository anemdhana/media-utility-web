@bdd @health
Feature: Service health endpoint
  As an API consumer
  I want a clear health check response
  So that I know the service is running

  @health-up-status
  Scenario: The health endpoint reports the application as up
    When I request the application health status
    Then the HTTP response should be 200
    And the JSON field "$.status" should be "UP"
    And the JSON field "$.service" should be "media-utility-web"
