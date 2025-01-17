package life.catalogue.dw.jersey;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.dropwizard.Configuration;
import io.dropwizard.client.DropwizardApacheConnector;

import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.client.JerseyClientBuilder;

import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.validation.Validators;
import io.dropwizard.setup.Environment;

import io.dropwizard.testing.ResourceHelpers;

import life.catalogue.WsServer;
import life.catalogue.WsServerConfig;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.common.util.YamlUtils;

import life.catalogue.doi.service.UserAgentFilter;

import org.apache.http.impl.client.CloseableHttpClient;
import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.RequestEntityProcessing;
import org.glassfish.jersey.client.spi.ConnectorProvider;
import org.glassfish.jersey.logging.LoggingFeature;
import org.junit.rules.ExternalResource;
import org.neo4j.cypher.internal.v3_4.functions.E;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Feature;

import java.io.IOException;
import java.util.logging.Level;

public class JerseyClientRule extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(JerseyClientRule.class);

  private Client client;
  private CloseableHttpClient httpClient;

  public Client getClient() {
    return client;
  }

  @Override
  protected void before() throws Throwable {
    var cfg = YamlUtils.read(JerseyClientConfiguration.class, "/jersey-config.yaml");
    // use a custom jackson mapper
    ObjectMapper om = ApiModule.configureMapper(Jackson.newMinimalObjectMapper());
    var env = new Environment("DataciteTest", om, Validators.newValidatorFactory(), new MetricRegistry(), ClassLoader.getSystemClassLoader(), new HealthCheckRegistry(), new Configuration());

    httpClient = new HttpClientBuilder(env).using(cfg).build("COLTest");

    JerseyClientBuilder builder = new JerseyClientBuilder(env)
      .withProperty(CommonProperties.FEATURE_AUTO_DISCOVERY_DISABLE, Boolean.TRUE)
      .withProperty(ClientProperties.REQUEST_ENTITY_PROCESSING, RequestEntityProcessing.BUFFERED)
      .withProvider(new UserAgentFilter("COLTest"))
      .using(cfg)
      .using((ConnectorProvider) (cl, runtimeConfig)
        -> new DropwizardApacheConnector(httpClient, WsServer.requestConfig(cfg), cfg.isChunkedEncodingEnabled())
      );
    client = builder.build("COLTest");
  }

  @Override
  protected void after() {
    client.close();
    try {
      httpClient.close();
    } catch (Exception e) {
      // doesnt matter
    }
  }
}
