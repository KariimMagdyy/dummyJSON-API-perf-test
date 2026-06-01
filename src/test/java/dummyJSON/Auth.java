package dummyJSON;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import io.gatling.recorder.internal.bouncycastle.math.Primes;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;


public class Auth extends Simulation {

  // 1) Http configuration
  private HttpProtocolBuilder httpProtocol = http
          .baseUrl("https://dummyjson.com/auth")
          .acceptHeader("application/json")
          .contentTypeHeader("application/json");


  private static FeederBuilder .FileBased<Object> jsonFeeder = jsonFile("usersData.json").circular();


  private static final int USER_COUNT = Integer.parseInt(System.getProperty("USERS","20"));
  private static final int RAMP_DURATION = Integer.parseInt(System.getProperty("RAMP_DURATION","40"));
  private static final int TEST_DURATION = Integer.parseInt(System.getProperty("TEST_DURATION","3"));

  @Override
  public void before(){
    System.out.printf("Running test wit %d users%n", USER_COUNT);
    System.out.printf("Running users over %d seconds%n", RAMP_DURATION);
    System.out.printf("Test duration: %d minutes%n", TEST_DURATION);
  }


  private static ChainBuilder loginUserAndgetTokens =
            feed(jsonFeeder)
                    .exec(http("Login user and get tokens - User ID: #{id}")
                    .post("/login")
                    .body(StringBody("{\"username\": \"#{username}\",\n" + "\"password\": \"#{password}\"}"))
                    .check(jmesPath("accessToken").saveAs("ACCESS_TOKEN"))
                    .check((jmesPath("refreshToken").saveAs("REFRESH_TOKEN"))));

  private static ChainBuilder getCurrentAuthUser =
            exec(http("Get current auth user")
                  .get("/me")
                  .header("Authorization", "Bearer #{ACCESS_TOKEN}"));

  private static ChainBuilder refreshAuthSession =
            exec(http("Refresh auth session")
                  .post("/refresh")
                  .body(StringBody("{\"refreshToken\": \"#{REFRESH_TOKEN}\"}")));


  // 2) Scenario Definition
  private ScenarioBuilder scn = scenario("Auth Flow")
          .exec(loginUserAndgetTokens)
          .exitHereIfFailed()
          .pause(Duration.ofSeconds(1), Duration.ofSeconds(3))
          .exec(getCurrentAuthUser)
          .pause(Duration.ofSeconds(1), Duration.ofSeconds(3))
          .exec(refreshAuthSession);


  // 3) Load Simulation
  {
    setUp(
            scn.injectClosed(
                    rampConcurrentUsers(0).to(USER_COUNT).during(RAMP_DURATION),
                    constantConcurrentUsers(USER_COUNT).during(Duration.ofMinutes(TEST_DURATION))
            ).protocols(httpProtocol)
    );
  }
}
