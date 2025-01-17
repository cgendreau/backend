package life.catalogue.dw.mail;


import io.dropwizard.ConfiguredBundle;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import life.catalogue.dw.ManagedUtils;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.mailer.MailerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bundle that sets up a SMTP mailer.
 * If no host is configured no mailer is created.
 */
public class MailBundle implements ConfiguredBundle<MailBundleConfig> {
  
  private static final Logger LOG = LoggerFactory.getLogger(MailBundle.class);
  private Mailer mailer;

  @Override
  public void run(MailBundleConfig config, Environment env) throws Exception {
    final MailConfig cfg = config.getMailConfig();

    if (cfg.host != null) {
      LOG.info("Configuring mail server {}:{}", cfg.host, cfg.port);
      if (cfg.block) {
        LOG.warn("Mail server configured to block while sending");
      }
      mailer = MailerBuilder
        .withSMTPServer(cfg.host, cfg.port, cfg.username, cfg.password)
        .withTransportStrategy(cfg.transport)
        .withDebugLogging(cfg.block)
        .withThreadPoolSize(cfg.threads)
        .buildMailer();
      // health tests
      if (env != null) {
        env.healthChecks().register("mail-connection", new MailServerConnectionCheck(mailer));
        env.lifecycle().manage(new ManagedMailer(mailer));
      }
    } else {
      LOG.warn("No mail server configured");
    }
  }

  static class ManagedMailer implements Managed  {
    private final Mailer mailer;

    public ManagedMailer(Mailer mailer) {
      this.mailer = mailer;
    }

    @Override
    public void start() throws Exception {
    }

    @Override
    public void stop() throws Exception {
      mailer.shutdownConnectionPool();
    }
  }

  public Mailer getMailer() {
    return mailer;
  }

  @Override
  public void initialize(Bootstrap<?> bootstrap) {
    //Do nothing
  }
  
}