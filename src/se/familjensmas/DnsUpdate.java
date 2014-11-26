package se.familjensmas;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.Authenticator;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * @author jorgen.smas@entercash.com
 */
public class DnsUpdate {

	private static Properties props = new Properties();
	private static File logfile;
	private static boolean verbose = false;

	public static void main(String[] args) throws Exception {
		logfile = new File(args[0], "log.txt");
		try {
			props.load(new FileReader(new File(args[0], "conf.properties")));
			setBasicAuth();
			File currentIpFile = new File(args[0], "current.txt");
			String oldIP = readFile(currentIpFile);
			String newIP = getNewIp();
			verbose("old: " + oldIP);
			verbose("new: " + newIP);
			if (newIP.equals(oldIP)) {
				verbose("No change.");
			} else {
				verbose("IP has changed.");
				updateDns(newIP);
				verbose("DNS updated.");
				write(newIP, currentIpFile);
				verbose("New ip saved.");
				sendMail(oldIP, newIP);
				verbose("Mail sent.");
				log("NEW " + newIP);
			}
		} catch (Exception e) {
			log("FAIL " + e.toString());
			throw e;
		}
	}

	private static void setBasicAuth() {
		Authenticator.setDefault(new Authenticator() {

			@Override
			protected java.net.PasswordAuthentication getPasswordAuthentication() {
				return new java.net.PasswordAuthentication(getProperty("dnsuser"), getProperty("dnspassword")
						.toCharArray());
			}
		});
	}

	private static void log(String string) throws IOException {
		append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " " + string, logfile);
	}

	private static void append(String string, File file) throws IOException {
		try (FileWriter w = new FileWriter(file, true)) {
			w.write(string + "\n");
		}
	}

	private static void updateDns(String newIP) throws IOException {
		for (String host : getProperty("host").split(",")) {
			StringBuilder s = new StringBuilder();
			s.append("http://dns.loopia.se/XDynDNSServer/XDynDNS.php");
			s.append("?system=custom");
			s.append("&hostname=").append(host);
			s.append("&myip=").append(newIP);
			s.append("&wildcard=ON");
			URL url = new URL(s.toString());
			try (InputStream stream = url.openStream()) {
				String result = read(stream);
				verbose("DNS update response: " + result + " for " + host);
			}
		}
	}

	private static String read(InputStream stream) throws IOException {
		try (Reader in = new InputStreamReader(stream)) {
			StringBuilder s = new StringBuilder();
			try (BufferedReader r = new BufferedReader(in)) {
				String line;
				while ((line = r.readLine()) != null) {
					if (s.length() > 0)
						s.append("\n");
					s.append(line);
				}
				return s.toString();
			}
		}
	}

	private static void verbose(String string) {
		if (verbose)
			System.out.println(string);
	}

	private static void write(String data, File file) throws IOException {
		try (FileWriter w = new FileWriter(file)) {
			w.write(data);
		}
	}

	private static String getNewIp() throws IOException {
		URL url = new URL("http://dns.loopia.se/checkip/checkip.php");
		InputStream s = url.openStream();
		String line = read(s);
		verbose("Line: " + line);
		Pattern p = Pattern.compile(".*[^0-9]([0-9]*\\.[0-9]*\\.[0-9]*\\.[0-9]*).*");
		Matcher m = p.matcher(line);
		m.matches();
		return m.group(1);
	}

	private static String readFile(File file) throws IOException {
		try (FileReader fr = new FileReader(file)) {
			try (BufferedReader r = new BufferedReader(fr)) {
				return r.readLine().trim();
			}
		}
	}

	private static String getProperty(String key) {
		String value = props.getProperty(key);
		if (value == null)
			throw new RuntimeException("Properties does not contain any key with name '" + key + "'");
		else
			return value;
	}

	private static void sendMail(String oldIP, String newIP) {
		final String username = getProperty("mailusername");
		final String password = getProperty("mailpassword");

		Properties props = new Properties();
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.host", "smtp.gmail.com");
		props.put("mail.smtp.port", "587");

		Session session = Session.getInstance(props, new javax.mail.Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(username, password);
			}
		});

		try {

			Message message = new MimeMessage(session);
			message.setFrom(new InternetAddress(getProperty("mailfrom")));
			message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(getProperty("mailto")));
			message.setSubject("IP address changed.");
			message.setText("IP address update from " + oldIP + " to " + newIP);

			Transport.send(message);

		} catch (MessagingException e) {
			throw new RuntimeException(e);
		}
	}
}
