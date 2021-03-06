/*
 * Protocoder 
 * A prototyping platform for Android devices 
 * 
 * Victor Diaz Barrales victormdb@gmail.com
 *
 * Copyright (C) 2013 Motorola Mobility LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions: 
 * 
 * The above copyright notice and this permission notice shall be included in all 
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN 
 * THE SOFTWARE.
 * 
 */

package org.protocoder.apprunner;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;

import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.commonjs.module.Require;

import android.app.Activity;
import android.util.Log;

/**
 * Original sourcecode from Droid Script :
 * https://github.com/divineprog/droidscript Copyright (c) Mikael Kindborg 2010
 * Source code license: MIT
 */

public class AppRunnerInterpreter {

	private static final String TAG = "AppRunnerInterpreter";

	static ScriptContextFactory contextFactory;
	public Interpreter interpreter;
	private WeakReference<AppRunnerActivity> a;
	private InterpreterInfo listener;

	static String scriptPrefix = "//Prepend text for all scripts \n" + "var window = this; \n";

	static final String SCRIPT_POSTFIX = "//Appends text for all scripts \n" + "function onAndroidPause(){ }  \n"
			+ "// End of Append Section" + "\n";

	public AppRunnerInterpreter(Activity a) {
		this.a = new WeakReference<AppRunnerActivity>((AppRunnerActivity) a);
	}

	public Object eval(final String code) {
		return eval(code, "");
	}

	public Object eval(final String code, final String sourceName) {
		final AtomicReference<Object> result = new AtomicReference<Object>(null);

		a.get().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				try {
					result.set(interpreter.eval(code, sourceName));
				} catch (Throwable e) {
					reportError(e);
					result.set(e);
				}
			}
		});
		while (null == result.get()) {
			Thread.yield();
		}

		return result.get();
	}

	/**
	 * This works because method is called from the "onXXX" methods which are
	 * called in the UI-thread. Thus, no need to use run on UI-thread. TODO:
	 * Could be a problem if someone calls it from another class, make private
	 * for now.
	 */
	Object callJsFunction(String funName, Object... args) {
		try {
			return interpreter.callJsFunction(funName, args);
		} catch (Throwable e) {
			reportError(e);
			return false;
		}
	}

	protected void createInterpreter() {
		// Initialize global context factory with our custom factory.
		if (null == contextFactory) {
			contextFactory = new ScriptContextFactory(this);
			ContextFactory.initGlobal(contextFactory);
			Log.i(TAG, "Creating ContextFactory");
		}

		contextFactory.setActivity(a.get());

		if (null == interpreter) {
			// Get the interpreter, if previously created.
			Object obj = a.get().getLastNonConfigurationInstance();
			if (null == obj) {
				// Create interpreter.
				interpreter = new Interpreter();
			} else {
				// Restore interpreter state.
				interpreter = (Interpreter) obj;
			}
		}

		interpreter.setActivity(a.get());
	}

	public interface InterpreterInfo {
		public void onError(String message);
	}

	public void addListener(InterpreterInfo listener) {
		this.listener = listener;
	}

	public void reportError(Object e) {
		// Create error message.
		String message = "";
		if (e instanceof RhinoException) {
			RhinoException error = (RhinoException) e;
			message = error.getMessage() + " " + error.lineNumber() + " (" + error.columnNumber() + "): "
					+ (error.sourceName() != null ? " " + error.sourceName() : "")
					+ (error.lineSource() != null ? " " + error.lineSource() : "") + "\n" + error.getScriptStackTrace();

			Log.d(TAG, "lallala");

			listener.onError(message);

		} else {
			message = e.toString();
		}

		// Log the error message.
		Log.i(TAG, "JavaScript Error: " + message);
	}

	public static String preprocess(String code) throws Exception {
		return preprocessMultiLineStrings(extractCodeFromAppRunnerTags(code));
	}

	public static String extractCodeFromAppRunnerTags(String code) throws Exception {
		String startDelimiter = "DROIDSCRIPT_BEGIN";
		String stopDelimiter = "DROIDSCRIPT_END";

		// Find start delimiter
		int start = code.indexOf(startDelimiter, 0);
		if (-1 == start) {
			// No delimiter found, return code untouched
			return code;
		}

		// Find stop delimiter
		int stop = code.indexOf(stopDelimiter, start);
		if (-1 == stop) {
			// No delimiter found, return code untouched
			return code;
		}

		// Extract the code between start and stop.
		String result = code.substring(start + startDelimiter.length(), stop);

		// Replace escaped characters with plain characters.
		// TODO: Add more characters here
		return result.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"");
	}

	public static String preprocessMultiLineStrings(String code) throws Exception {
		StringBuilder result = new StringBuilder(code.length() + 1000);

		String delimiter = "\"\"\"";
		int lastStop = 0;
		while (true) {
			// Find next multiline delimiter
			int start = code.indexOf(delimiter, lastStop);
			if (-1 == start) {
				// No delimiter found, append rest of the code
				// to result and break
				result.append(code.substring(lastStop, code.length()));
				break;
			}

			// Find terminating delimiter
			int stop = code.indexOf(delimiter, start + delimiter.length());
			if (-1 == stop) {
				// This is an error, throw an exception with error message
				throw new Exception("Multiline string not terminated");
			}

			// Append the code from last stop up to the start delimiter
			result.append(code.substring(lastStop, start));

			// Set new lastStop
			lastStop = stop + delimiter.length();

			// Append multiline string converted to JavaScript code
			result.append(convertMultiLineStringToJavaScript(code.substring(start + delimiter.length(), stop)));
		}

		return result.toString();
	}

	public static String convertMultiLineStringToJavaScript(String s) {
		StringBuilder result = new StringBuilder(s.length() + 1000);

		char quote = '\"';
		char newline = '\n';
		String backslashquote = "\\\"";
		String concat = "\\n\" + \n\"";

		result.append(quote);

		for (int i = 0; i < s.length(); ++i) {
			char c = s.charAt(i);
			if (c == quote) {
				result.append(backslashquote);
			} else if (c == newline) {
				result.append(concat);
			} else {
				result.append(c);
			}
			// Log.i("Multiline", result.toString());
		}

		result.append(quote);

		return result.toString();
	}

	public String addInterface(Class c) {
		String pkg = "Packages." + c.getName().toString();
		String clsName = c.getSimpleName();

		String c1 = "var " + clsName + " = " + pkg + "; \n";
		String c2 = "var " + clsName.substring(1).toLowerCase() + "=" + clsName + "(Activity); \n";

		String prefix = c1 + c2;

		scriptPrefix += prefix;

		return prefix;
	}

	public static class Interpreter {
		public Context context;
		public Scriptable scope;
		Require require;

		public Interpreter() {
			// Creates and enters a Context. The Context stores information
			// about the execution environment of a script.
			context = Context.enter();
			context.setOptimizationLevel(-1);

			// Initialize the standard objects (Object, Function, etc.)
			// This must be done before scripts can be executed. Returns
			// a scope object that we use in later calls.
			scope = context.initStandardObjects();
		}

		public Interpreter setActivity(Activity activity) {
			// ScriptAssetProvider provider = new ScriptAssetProvider(activity);
			// Require require = new Require(context, scope, provider, null,
			// null, true);
			// require.install(scope);

			// Set the global JavaScript variable Activity.
			ScriptableObject.putProperty(scope, "Activity", Context.javaToJS(activity, scope));
			return this;
		}

		public Interpreter setErrorReporter(ErrorReporter reporter) {
			context.setErrorReporter(reporter);
			return this;
		}

		public void exit() {
			Context.exit();
		}

		public Object eval(String code, String sourceName) throws Throwable {
			String processedCode = preprocess(code);
			return context.evaluateString(scope, processedCode, sourceName, 1, null);
		}

		public Object callJsFunction(String funName, Object... args) throws Throwable {
			Log.d(TAG, "calling " + funName);
			Object fun = scope.get(funName, scope);
			if (fun instanceof Function) {
				Log.i(TAG, "Calling JsFun " + funName);
				Function f = (Function) fun;
				Object result = f.call(context, scope, scope, args);
				return Context.toString(result); // Why did I use this?
			} else {

				return null;
			}
		}
	}

	public static class ScriptContextFactory extends ContextFactory {
		AppRunnerActivity activity;
		private AppRunnerInterpreter appRunnerInterpreter;

		ScriptContextFactory(AppRunnerInterpreter appRunnerInterpreter) {
			this.appRunnerInterpreter = appRunnerInterpreter;
		}

		public ScriptContextFactory setActivity(AppRunnerActivity activity) {
			this.activity = activity;
			return this;
		}

		@Override
		protected Object doTopCall(Callable callable, Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
			try {
				return super.doTopCall(callable, cx, scope, thisObj, args);
			} catch (Throwable e) {
				Log.i(TAG, "ContextFactory catched error: " + e);
				if (null != activity) {
					appRunnerInterpreter.reportError(e);
				}
				return e;
			}
		}
	}

	public <T> void callback(String fn, T... args) {

		try {
			// c.get().interpreter.callJsFunction(fn,"");
			String f1 = fn;
			boolean firstarg = true;

			if (fn.contains("function")) {
				f1 = "var fn = " + fn + "\n fn(";
				for (T t : args) {
					if (firstarg) {
						firstarg = false;
					} else {
						f1 = f1 + ",";
					}

					f1 = f1 + t;
				}

				f1 = f1 + ");";
			}
			a.get().interp.eval(f1);

		} catch (Throwable e) {

			// TODO
		}

	}

}
