package at.usmile.panshot.exception;

import java.io.IOException;

/**
 * Exception to uniquely communicate SD card not available problems.
 * 
 * @author Rainhard Findling
 * @date 7 Apr 2015
 * @version 1
 */
public class SdCardNotAvailableException extends IOException {

	public SdCardNotAvailableException() {
		super();
	}

	public SdCardNotAvailableException(String _message, Throwable _cause) {
		super(_message, _cause);
	}

	public SdCardNotAvailableException(String _detailMessage) {
		super(_detailMessage);
	}

	public SdCardNotAvailableException(Throwable _cause) {
		super(_cause);
	}
}
