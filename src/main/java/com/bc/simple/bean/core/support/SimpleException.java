package com.bc.simple.bean.core.support;

public class SimpleException extends RuntimeException {

	private static final long serialVersionUID = 7491351666044899350L;


	public SimpleException() {
		super();
	}


	public SimpleException(String message) {
		super(message);
	}


	public SimpleException(String message, Throwable cause) {
		super(message, cause);
	}


	public SimpleException(Throwable cause) {
		super(cause);
	}


	protected SimpleException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public SimpleException(Object... args) {
		super(concatMessage(args), getCause(args));
	}

	private static Throwable getCause(Object... args) {
		for (Object arg : args) {
			if (arg instanceof Throwable) {
				return (Throwable) arg;
			}
		}
		return null;
	}

	private static String concatMessage(Object... args) {
		StringBuffer msg = new StringBuffer();
		for (Object arg : args) {
			if (!(arg instanceof Throwable)) {
				msg.append(arg);
				msg.append("\n");
			}
		}
		return msg.toString();
	}

}
