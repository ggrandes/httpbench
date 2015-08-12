package org.javastack.httpbench;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class HttpBench implements Runnable {
	private static enum Option {
		/**
		 * Connect timeout (millis) (int)
		 */
		connectTimeout(30000),
		/**
		 * Read timeout (millis) (int)
		 */
		readTimeout(30000),
		/**
		 * Total Request (int)
		 */
		totalRequest(1),
		/**
		 * Concurrent connections (int)
		 */
		concurrency(1),
		/**
		 * Mime type (String)
		 */
		contentType("text/plain"),
		/**
		 * HTTP Method (String)
		 */
		method("GET"),
		/**
		 * Use Keepalive (boolean)
		 */
		keepAlive(false),
		//
		; // End Of Options

		final String def;

		Option(final String def) {
			this.def = def;
		}

		Option(final int def) {
			this.def = String.valueOf(def);
		}

		Option(final boolean def) {
			this.def = String.valueOf(def);
		}

		public String getDef() {
			return def;
		}
	}

	// Config
	private static final Map<Option, String> opts = new HashMap<Option, String>();
	private static final ArrayList<String> urls = new ArrayList<String>();

	private final byte[] buf = new byte[2048];
	private final Context ctx;

	public HttpBench(final Context ctx) {
		this.ctx = ctx;
	}

	public void run() {
		try {
			try {
				ctx.start.countDown();
				ctx.start.await();
			} catch (InterruptedException e) {
			}
			while (ctx.total.decrementAndGet() >= 0) {
				boolean isOK = false;
				try {
					isOK = (request(null, 0) == HttpURLConnection.HTTP_OK);
				} catch (Exception e) {
				} finally {
					(isOK ? ctx.requestOk : ctx.requestFail).incrementAndGet();
				}
			}
		} finally {
			ctx.stop.countDown();
		}
	}

	private int request(final InputStream doOutput, final int contentLength) throws IOException {
		HttpURLConnection conn = null;
		InputStream urlIs = null;
		OutputStream urlOs = null;
		//
		try {
			conn = (HttpURLConnection) ctx.url.openConnection();
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod(Context.method);
			conn.setDoInput(true);
			if (doOutput != null) {
				conn.setDoOutput(true);
				if (contentLength > 0)
					conn.setFixedLengthStreamingMode(contentLength);
			}
			conn.setConnectTimeout(Context.connectTimeout);
			conn.setReadTimeout(Context.readTimeout);
			if (doOutput != null) {
				conn.setRequestProperty("Content-Type", Context.contentType);
				conn.connect();
				int len = 0;
				urlOs = conn.getOutputStream();
				while ((len = doOutput.read(buf)) > 0) {
					urlOs.write(buf, 0, len);
				}
				urlOs.flush();
			} else {
				conn.connect();
			}
			// Get the response
			final int resCode = conn.getResponseCode();
			// final int len = conn.getContentLength();
			// System.out.println("Request " + method + " " + url + " > code=" + resCode + " length=" + len);
			try {
				urlIs = conn.getInputStream();
			} catch (Exception e) {
				urlIs = conn.getErrorStream();
			}
			int len = 0;
			while ((len = urlIs.read(buf)) != -1) {
				ctx.htmlTransferred.addAndGet(len);
			}
			return resCode;
		} finally {
			closeQuietly(urlIs);
			closeQuietly(urlOs);
		}
	}

	private static final void closeQuietly(final Closeable c) {
		if (c != null) {
			try {
				c.close();
			} catch (Exception ign) {
			}
		}
	}

	// Shared Context
	private static class Context {
		static int connectTimeout;
		static int readTimeout;
		static int totalRequest;
		static int concurrency;
		static String contentType;
		static String method;
		static boolean keepAlive;

		final AtomicInteger total, requestOk, requestFail;
		final AtomicLong htmlTransferred;
		final CountDownLatch start, stop;
		final URL url;

		Context(final URL url) {
			this.url = url;
			this.requestOk = new AtomicInteger();
			this.requestFail = new AtomicInteger();
			this.htmlTransferred = new AtomicLong();
			this.total = new AtomicInteger(totalRequest);
			this.start = new CountDownLatch(concurrency + 1);
			this.stop = new CountDownLatch(concurrency);
		}
	}

	private static void parseOptions(final String[] args) {
		for (int i = 0; i < args.length; i++) {
			final String arg = args[i];
			if (arg.isEmpty())
				continue;
			if (arg.startsWith("--")) {
				final String opt = arg.substring(2);
				if (opt.equals("help")) {
					printHelp();
				}
				final int offEq = opt.indexOf('=');
				String key = null, value = null;
				if (offEq != -1) {
					key = opt.substring(0, offEq);
					value = opt.substring(offEq + 1);
				} else {
					key = opt;
					value = "";
				}
				final Option k = optionOf(key);
				if (k != null) {
					opts.put(k, value);
				}
			} else {
				if (!arg.startsWith("http")) {
					throw new RuntimeException("Invalid URL: " + arg);
				}
				urls.add(arg);
			}
		}
		if (urls.isEmpty()) {
			throw new RuntimeException("Not URLs specified");
		}
	}

	private static Option optionOf(final String name) {
		try {
			return Option.valueOf(name);
		} catch (Exception e) {
			throw new RuntimeException("Unknown option: " + name);
		}
	}

	private static String getOptStr(final Option name) {
		String value = opts.get(name);
		if (value == null)
			value = name.getDef();
		return value;
	}

	private static int getOptInt(final Option name) {
		return Integer.parseInt(getOptStr(name));
	}

	private static boolean getOptBool(final Option name) {
		final String value = getOptStr(name);
		return ((value != null) ? value.isEmpty() : Boolean.parseBoolean(value));
	}

	private static void printHelp() {
		System.out.println(HttpBench.class.getName() + " [<options>] <url> [<url> ...]");
		System.out.println();
		System.out.println("Options:");
		System.out.println();
		for (final Option o : Option.values()) {
			System.out.println("--" + o + " (default: " + o.getDef() + ")");
		}
		throw new RuntimeException();
	}

	public static void main(final String[] args) throws Throwable {
		try {
			parseOptions(args);
		} catch (Exception e) {
			if (e.getMessage() != null) {
				System.out.println("ERROR: " + e.getMessage());
				System.out.println();
			}
			try {
				printHelp();
			} catch (Exception ign) {
			}
			return;
		}
		//
		Context.connectTimeout = getOptInt(Option.connectTimeout);
		Context.readTimeout = getOptInt(Option.readTimeout);
		Context.totalRequest = getOptInt(Option.totalRequest);
		Context.concurrency = getOptInt(Option.concurrency);
		Context.contentType = getOptStr(Option.contentType);
		Context.method = getOptStr(Option.method);
		Context.keepAlive = getOptBool(Option.keepAlive);
		//
		// http://docs.oracle.com/javase/7/docs/technotes/guides/net/properties.html
		System.setProperty("http.keepAlive", String.valueOf(Context.keepAlive));
		System.setProperty("http.maxConnections", String.valueOf(Context.concurrency));
		//
		for (int i = 0; i < urls.size(); i++) {
			final String url = urls.get(i);
			final Context ctx = new Context(new URL(url));
			for (int j = 0; j < Context.concurrency; j++) {
				new Thread(new HttpBench(ctx)).start();
			}
			ctx.start.countDown();
			ctx.start.await();
			final long ts = System.currentTimeMillis();
			ctx.stop.await();
			final float time = (System.currentTimeMillis() - ts) / 1000f;
			final String reqSec = String
					.format(Locale.US, "%.2f", (Context.totalRequest / Math.max(time, 1)));
			System.out.println("URL:                    " + ctx.url);
			System.out.println("Concurrency Level:      " + Context.concurrency);
			System.out.println("Use KeepAlive:          " + Context.keepAlive);
			System.out.println("Time taken for tests:   " + time + " seconds");
			System.out.println("Complete requests:      " + ctx.requestOk.get());
			System.out.println("Failed requests:        " + ctx.requestFail.get());
			System.out.println("HTML transferred:       " + ctx.htmlTransferred.get() + " bytes");
			System.out.println("Requests per second:    " + reqSec + " [#/sec] (mean)");
		}
	}
}
