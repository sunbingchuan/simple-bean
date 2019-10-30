package com.bc.simple.bean.core.support;

public class CurrencyException extends RuntimeException {

	private static final long serialVersionUID = 7491351666044899350L;


	public CurrencyException() {
		super();
	}


	public CurrencyException(String message) {
		super(message);
	}


	public CurrencyException(String message, Throwable cause) {
		super(message, cause);
	}


	public CurrencyException(Throwable cause) {
		super(cause);
	}


	protected CurrencyException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public CurrencyException(Object... args) {
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
