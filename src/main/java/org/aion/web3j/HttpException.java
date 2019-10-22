package org.aion.web3j;

import org.apache.http.StatusLine;

public class HttpException extends RuntimeException {

    private final String httpStatus;
    private final int httpStatusCode;
    public HttpException(String httpStatus, int httpStatusCode) {
        super("Failed with: "+httpStatusCode + "->"+httpStatus);
        if (httpStatusCode==200) throw new IllegalArgumentException();
        this.httpStatus = httpStatus;
        this.httpStatusCode = httpStatusCode;
    }

    public HttpException(StatusLine statusLine){
        this(statusLine.getReasonPhrase(), statusLine.getStatusCode());
    }
}
