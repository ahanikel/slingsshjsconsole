package com.day.people.ahanikel;


import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import jline.ConsoleReader;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.Factory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.osgi.service.component.ComponentContext;

@Component(
		label="Javascript SSH Console Service for Sling",
		description="Javascript SSH Console Service for Sling",
		metatype=true, 
		immediate=true
)
public class JavascriptConsole {
	
	@Reference
	protected Repository repo;
	protected Session session;

	@Property(value = "2222", label = "Listening port", description = "Where SSH listens for connections")
	private static final String PORT = "listeningPort";

	public static String PROMPT = "jsshell> ";
	private static SshServer sshd;
	
	protected class JavascriptShellFactory implements Factory<Command> {

		protected class JavascriptShell implements Command {

			private InputStream in;
			private OutputStream out;
			private boolean started = false;
			private Thread interpreterThread;
			
			public JavascriptShell() {
			}

			public void setInputStream(InputStream in) {
				this.in = in;
			}

			public void setOutputStream(OutputStream out) {
				
				this.out = new FilterOutputStream(out) {
					
					// Prevents staircase effect on output
					@Override
					public void write(int b) throws IOException {
						
						if (b == '\n') {
							super.write('\r');
							super.write(b);
							flush();
						} else {
							super.write(b);
							flush();
						}
					}
				};
			}

			public void setErrorStream(OutputStream err) {
			}

			public void setExitCallback(ExitCallback callback) {
			}

			public synchronized void start(Environment env) throws IOException {
				
				if (started)
					throw new IllegalStateException("This interpreter has already been started.");
				
				interpreterThread = new Thread() {
					
					@Override
					public void run() {
						
						PrintWriter pw = new PrintWriter(out);
						
						ConsoleReader reader = null;
						try {
							reader = new jline.ConsoleReader(in, pw);
						} catch (IOException ioe) {
							ioe.printStackTrace();
						}
						
						if (reader == null)
							pw.println("jline.ConsoleReader is null!");
						
						Context cx = Context.enter();
						Scriptable scope = cx.initStandardObjects();
						Object jsRepo = Context.javaToJS(repo, scope);
						ScriptableObject.putProperty(scope, "repo", jsRepo);
						Object jsSession = Context.javaToJS(session, scope);
						ScriptableObject.putProperty(scope, "session", jsSession);
						
						try {
							reader.printNewline();
							reader.printString("Welcome to the repository shell! Your current repository is available as variable 'repo', your session is 'session'.");
							reader.printNewline();
							reader.printNewline();
							reader.printString(PROMPT);
							String line;
							while ((line = reader.readLine()) != null) {
								if (line.equals("exit")) {
									reader.printNewline();
									reader.flushConsole();
									break;
								}
								else {
									try {
										Object result = cx.evaluateString(scope, line, "<cmd>", 1, null);
										reader.printString(cx.toString(result));
										reader.printNewline();
										reader.printString(PROMPT);
										reader.flushConsole();
									}
									catch (Throwable e) {
										reader.printString(e.getMessage());
										reader.printNewline();
										reader.printString(PROMPT);
										reader.flushConsole();
									}
								}
							}
						}
						catch (IOException e) {
							e.printStackTrace();
						}
						finally {
							pw.close();
							try {
								in.close();
							} catch (IOException ioe) {
							}
							Context.exit();
						}
					}
				};
				
				interpreterThread.start();
				started = true;
				
			}

			@SuppressWarnings("deprecation")
			public void destroy() {
				interpreterThread.stop();
			}
		}

		public Command create() {
			return new JavascriptShell();
		}
	}

	protected synchronized void activate(ComponentContext ctx) {
		
		int port = Integer.parseInt(ctx.getProperties().get(PORT).toString());
		System.out.println("Starting SSH listener on port " + port);
		
		if (sshd != null)
			return;
		
		sshd = SshServer.setUpDefaultServer();
		sshd.setPort(port);
		sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider("/tmp/serverstate.ser"));
		sshd.setShellFactory(new JavascriptShellFactory());
		
		sshd.setPasswordAuthenticator(new PasswordAuthenticator() {
			public boolean authenticate(String username, String password,
					ServerSession s) {
				try {
					// throws a LoginException if login fails
					session = repo.login(new SimpleCredentials(username, password.toCharArray()), "crx.default");
					return true;
				} catch(Exception e) {
				}
				return false;
			}
		});
		
		Thread sshdThread = new Thread("sshd server") {
			@Override
			public void run() {
				try {
					sshd.start();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};
		sshdThread.start();
	}
	
	protected synchronized void deactivate(ComponentContext ctx) {
		
		System.out.println("Stopping SSH listener");
		
		if (sshd == null)
			return;
		
		try {
			sshd.stop(true);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		sshd = null;
	}
}
