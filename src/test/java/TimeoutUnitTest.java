import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

public class TimeoutUnitTest
{
  private static Logger log = LoggerFactory.getLogger(TimeoutUnitTest.class);

  @ClassRule
  public static WireMockRule wireMock = new WireMockRule(8089);

  private static Client client;

  private static WebTarget target;

  @BeforeClass
  public static void setup()
  {
    stubFor(get(urlEqualTo("/respond"))
        .willReturn(aResponse()
            .withStatus(200)));

    stubFor(get(urlEqualTo("/timeout"))
        .willReturn(aResponse()
            .withStatus(200)
            .withFixedDelay(2500)));

    stubFor(get(urlEqualTo("/error"))
        .willReturn(aResponse()
            .withStatus(500)));

    stubFor(get(urlEqualTo("/reset"))
        .willReturn(aResponse()
            .withFault(Fault.CONNECTION_RESET_BY_PEER)));

    client = ClientBuilder.newBuilder()
        .property(ClientProperties.CONNECT_TIMEOUT, 1000)
        .property(ClientProperties.READ_TIMEOUT, 2000)
        .build();

    target = client.target("http://localhost:8089");
  }

  @Test
  public void respondTest()
  {
    Response response = target
        .path("/respond")
        .request()
        .get();

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void timeoutTest()
  {
    assertThatThrownBy(() ->
        target
            .path("/timeout")
            .request()
            .get()
    ).isExactlyInstanceOf(ProcessingException.class)
        .hasCauseExactlyInstanceOf(SocketTimeoutException.class);
  }

  @Test
  public void errorTest()
  {
    Response response = target
        .path("/error")
        .request()
        .get();

    assertThat(response.getStatus()).isEqualTo(500);
  }

  @Test
  public void resetTest()
  {
    assertThatThrownBy(() ->
        target
            .path("/reset")
            .request()
            .get()
    ).isExactlyInstanceOf(ProcessingException.class)
        .hasCauseExactlyInstanceOf(SocketException.class);
  }

  @Test
  public void connectTest()
  {
    WebTarget nonexistentTarget = client.target("http://localhost:8090");

    assertThatThrownBy(() ->
        nonexistentTarget
            .path("/nothere")
            .request()
            .get()
    ).isExactlyInstanceOf(ProcessingException.class)
        .hasCauseExactlyInstanceOf(ConnectException.class);
  }

  @Test
  public void exceptionHandlingTest()
  {
    Stream.<Runnable>of(
        () -> target.path("/timeout").request().get(),
        () -> target.path("/reset").request().get(),
        () -> client.target("http://localhost:8090").path("/nothere").request().get()
    ).forEach(this::runTest);
  }

  private void runTest(Runnable func)
  {
    try
    {
      func.run();
      fail("An exception should always occur");
    }
    catch (ProcessingException e)
    {
      log.info("Processing exception: {}", e.getMessage());
    }
    catch (Exception e)
    {
      fail("All exceptions should be caught earlier");
    }
  }
}
