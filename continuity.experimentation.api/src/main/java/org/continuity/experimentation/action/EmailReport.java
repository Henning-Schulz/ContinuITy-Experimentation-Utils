package org.continuity.experimentation.action;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.continuity.experimentation.Context;
import org.continuity.experimentation.Experiment;
import org.continuity.experimentation.ExperimentReport;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends a report to a defined email address.
 *
 * @author Henning Schulz
 *
 */
public class EmailReport implements IExperimentAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(EmailReport.class);

	public static final String DEFAULT_PATH = "continuity.email.credentials.properties";

	public static final String KEY_FROM = "continuity.email.from";
	public static final String KEY_USER = "continuity.email.user";
	public static final String KEY_PASSWORD = "continuity.email.password";
	public static final String KEY_HOST = "continuity.email.host";
	public static final String KEY_RECIPIENT = "continuity.email.recipient";

	private final Properties credentials = new Properties();

	private final IOException excpetionDuringConstruction;

	private Experiment experiment;

	private static EmailReport instance;

	/**
	 * Creates a new instance using the {@link #DEFAULT_PATH}.
	 */
	private EmailReport() {
		this(DEFAULT_PATH);
	}

	private EmailReport(String credentialsPath) {
		IOException exception = null;

		if (new File(credentialsPath).exists()) {
			try {
				credentials.load(new FileReader(credentialsPath));
			} catch (IOException e) {
				exception = e;
			}
		}

		this.excpetionDuringConstruction = exception;
	}

	public static void configure(String credentialsPath) {
		instance = new EmailReport(credentialsPath);
	}

	public static EmailReport send() {
		if (instance == null) {
			instance = new EmailReport();
		}

		return instance;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void bypassExperiment(Experiment experiment) {
		this.experiment = experiment;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
		if (excpetionDuringConstruction != null) {
			throw excpetionDuringConstruction;
		}

		ExperimentReport report = experiment.createReport();

		String severity = "OK";

		if (report.isWarning()) {
			severity = "WARNING";
		} else if (report.isError()) {
			severity = "ERROR";
		}

		String subject = severity + ": Experiment '" + experiment.getName() + "' (" + report.getContext() + ")";

		StringBuilder messageBuilder = new StringBuilder();

		if (report.isError()) {
			messageBuilder.append("<font color=\"red\"><b><i>The experiment has been aborted!</b></i></font><br><br>");
		} else {
			messageBuilder.append("<font color=\"green\"><i>The experiment is running.</i></font><br><br>");
		}

		messageBuilder.append("<b>Current context:</b> ");
		messageBuilder.append(report.getContext());

		if (!report.getUncaughtExceptions().isEmpty()) {
			messageBuilder.append("<br><br><font color=\"red\"><b>Thrown exceptions:</b></font><br>");

			for (Exception exc : report.getUncaughtExceptions()) {
				messageBuilder.append(formatException(exc));
				messageBuilder.append("<br><br>");
			}
		}

		if (!report.getCaughtExceptions().isEmpty()) {
			messageBuilder.append("<br><br><b>Caught exceptions:</b><br>");

			for (Exception exc : report.getCaughtExceptions()) {
				messageBuilder.append(formatException(exc));
				messageBuilder.append("<br>");
			}
		}


		messageBuilder.append("<br><br><i>The report was created at ");
		messageBuilder.append(new Date());
		messageBuilder.append("</i>");

		sendEmail(subject, messageBuilder.toString());
	}

	private void sendEmail(String subject, String html) throws AddressException, MessagingException {
		Properties props = new Properties();
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.host", credentials.getProperty(KEY_HOST));

		Session session = Session.getInstance(props, new javax.mail.Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(credentials.getProperty(KEY_USER), credentials.getProperty(KEY_PASSWORD));
			}
		});

		Message message = new MimeMessage(session);
		message.setFrom(new InternetAddress(credentials.getProperty(KEY_FROM)));
		message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(credentials.getProperty(KEY_RECIPIENT)));
		message.setSubject(subject);
		message.setContent(html, "text/html");
		Transport.send(message);

		LOGGER.info("Sent Email successfully.");
	}

	private String formatException(Exception exc) {
		StringWriter writer = new StringWriter();
		exc.printStackTrace(new PrintWriter(writer));
		return writer.toString().replaceAll("\\\n", "<br>");
	}

}
